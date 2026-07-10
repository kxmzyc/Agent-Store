import json
import logging
import os
import re
import asyncio
import time
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from contextvars import ContextVar
from decimal import Decimal
from typing import Any

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.tools import tool
from pydantic import BaseModel
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker

try:
    from langchain_openai import ChatOpenAI, OpenAIEmbeddings
except Exception:  # pragma: no cover
    ChatOpenAI = None
    OpenAIEmbeddings = None

logger = logging.getLogger("smart_mall_agent")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")

BACKEND_BASE = os.getenv("BACKEND_BASE", "http://localhost:8080")
DATABASE_URL = os.getenv("DATABASE_URL", "mysql+pymysql://root:smartmall_root@localhost:3306/smart_mall?charset=utf8mb4")
INTERNAL_SERVICE_SECRET = os.getenv("INTERNAL_SERVICE_SECRET", "agent-internal-secret")
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL") or None
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-4o-mini")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")
LLM_PLACEHOLDER = "__PLACEHOLDER_WILL_BE_PROVIDED_BY_USER__"

engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine)
memory: dict[str, deque[dict[str, str]]] = defaultdict(lambda: deque(maxlen=12))
pending_cart_actions: dict[str, dict[str, int]] = {}
cart_confirmation_grants: dict[tuple[int, int, int], int] = defaultdict(int)
active_tool_user_id: ContextVar[int | None] = ContextVar("active_tool_user_id", default=None)


class Utf8JSONResponse(JSONResponse):
    media_type = "application/json; charset=utf-8"


def is_configured(value: str | None) -> bool:
    return bool(value and value.strip() and value != LLM_PLACEHOLDER)


def llm_enabled() -> bool:
    return bool(is_configured(LLM_API_KEY) and ChatOpenAI)


@asynccontextmanager
async def lifespan(app: FastAPI):
    ensure_tables()
    logger.info("agent service started, backend=%s, llm_enabled=%s", BACKEND_BASE, llm_enabled())
    yield


app = FastAPI(title="Smart Mall Agent Service", lifespan=lifespan, default_response_class=Utf8JSONResponse)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class ChatRequest(BaseModel):
    userId: int | None = None
    sessionId: str
    message: str


class ChatResponse(BaseModel):
    reply: str
    toolsUsed: list[str]
    sessionId: str


class AgentTiming(BaseCallbackHandler):
    def __init__(self) -> None:
        self._llm_starts: list[float] = []
        self._tool_starts: list[float] = []
        self.llm_calls: list[float] = []
        self.tool_calls: list[float] = []

    def on_llm_start(self, *args: Any, **kwargs: Any) -> None:
        self._llm_starts.append(time.perf_counter())

    def on_chat_model_start(self, *args: Any, **kwargs: Any) -> None:
        self._llm_starts.append(time.perf_counter())

    def on_llm_end(self, *args: Any, **kwargs: Any) -> None:
        if self._llm_starts:
            self.llm_calls.append(time.perf_counter() - self._llm_starts.pop())

    def on_llm_error(self, *args: Any, **kwargs: Any) -> None:
        self.on_llm_end(*args, **kwargs)

    def on_tool_start(self, *args: Any, **kwargs: Any) -> None:
        self._tool_starts.append(time.perf_counter())

    def on_tool_end(self, *args: Any, **kwargs: Any) -> None:
        if self._tool_starts:
            self.tool_calls.append(time.perf_counter() - self._tool_starts.pop())

    def on_tool_error(self, *args: Any, **kwargs: Any) -> None:
        self.on_tool_end(*args, **kwargs)

    def llm_duration(self, index: int) -> float:
        return self.llm_calls[index] if len(self.llm_calls) > index else 0.0

    def llm_after_first_total(self) -> float:
        return sum(self.llm_calls[1:])

    def tool_total(self) -> float:
        return sum(self.tool_calls)


class QueueStreamCallback(BaseCallbackHandler):
    def __init__(self, loop: asyncio.AbstractEventLoop, queue: asyncio.Queue[dict[str, Any]]) -> None:
        self.loop = loop
        self.queue = queue

    def _send(self, payload: dict[str, Any]) -> None:
        self.loop.call_soon_threadsafe(self.queue.put_nowait, payload)

    def on_llm_new_token(self, token: str, **kwargs: Any) -> None:
        if token:
            self._send({"type": "token", "content": token})

    def on_tool_start(self, serialized: dict[str, Any], input_str: str, **kwargs: Any) -> None:
        name = serialized.get("name") or "tool"
        self._send({"type": "tool_start", "name": name})

    def on_tool_end(self, output: Any, **kwargs: Any) -> None:
        self._send({"type": "tool_end"})


class ChatResult(BaseModel):
    reply: str
    toolsUsed: list[str]
    sessionId: str
    timing: dict[str, float]


class AuthenticatedUser(BaseModel):
    id: int
    username: str
    role: str


class PreferenceResponse(BaseModel):
    tag: str
    weight: float


def _internal_headers() -> dict[str, str]:
    return {
        "X-Internal-Service": "agent",
        "X-Internal-Secret": INTERNAL_SERVICE_SECRET,
    }


def _bearer_token(authorization: str | None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="请先登录后再使用 AI 助手")
    return authorization[7:]


def current_user(authorization: str | None = Header(default=None)) -> AuthenticatedUser:
    token = _bearer_token(authorization)
    try:
        with httpx.Client(timeout=8) as client:
            resp = client.get(
                f"{BACKEND_BASE}/api/user/profile",
                headers={"Authorization": f"Bearer {token}"},
            )
            if resp.status_code == 401:
                raise HTTPException(status_code=401, detail="登录已过期，请重新登录")
            resp.raise_for_status()
            data = resp.json()
    except HTTPException:
        raise
    except Exception as exc:
        logger.info("failed to validate user token via backend, error=%s", exc)
        raise HTTPException(status_code=401, detail="无法校验登录状态")
    return AuthenticatedUser(id=int(data["id"]), username=data["username"], role=data.get("role", "USER"))


def require_tool_user(user_id: int) -> int:
    active_user_id = active_tool_user_id.get()
    if active_user_id is not None and int(user_id) != active_user_id:
        logger.warning("blocked cross-user tool call, active_user_id=%s, requested_user_id=%s", active_user_id, user_id)
        raise ValueError("工具调用用户身份不匹配")
    return int(user_id)


def memory_key(user_id: int, session_id: str) -> str:
    return f"{user_id}:{session_id}"


def _json_default(value: Any) -> str:
    if isinstance(value, Decimal):
        return str(value)
    return str(value)


def embedding_enabled() -> bool:
    return bool(is_configured(LLM_API_KEY) and OpenAIEmbeddings)


def _embedding_client() -> Any | None:
    if not embedding_enabled():
        return None
    try:
        kwargs: dict[str, Any] = {
            "api_key": LLM_API_KEY,
            "model": EMBEDDING_MODEL,
        }
        if is_configured(LLM_BASE_URL):
            kwargs["base_url"] = LLM_BASE_URL
        return OpenAIEmbeddings(**kwargs)
    except Exception as exc:
        logger.info("embedding client unavailable, falling back to keyword search, error=%s", exc)
        return None


def _tokenize_for_search(text_value: str) -> set[str]:
    text_value = (text_value or "").lower()
    latin_terms = re.findall(r"[a-z0-9]+", text_value)
    cjk_chars = re.findall(r"[\u4e00-\u9fff]", text_value)
    cjk_bigrams = ["".join(cjk_chars[index:index + 2]) for index in range(max(len(cjk_chars) - 1, 0))]
    return {term for term in [*latin_terms, *cjk_chars, *cjk_bigrams] if term.strip()}


def _cosine_similarity(left: list[float], right: list[float]) -> float:
    if not left or not right or len(left) != len(right):
        return 0.0
    dot = sum(a * b for a, b in zip(left, right))
    left_norm = sum(a * a for a in left) ** 0.5
    right_norm = sum(b * b for b in right) ** 0.5
    if left_norm == 0 or right_norm == 0:
        return 0.0
    return dot / (left_norm * right_norm)


def _knowledge_rows(query: str, limit: int = 4) -> list[dict[str, Any]]:
    with SessionLocal() as db:
        rows = db.execute(
            text("""
                SELECT id, source_type, source_id, content, embedding_json
                FROM knowledge_chunk
                ORDER BY id DESC LIMIT 600
            """)
        ).mappings().all()

    if not rows:
        return []

    scored: list[tuple[float, dict[str, Any]]] = []
    has_stored_embeddings = any(row.get("embedding_json") for row in rows)
    embedding_client = _embedding_client() if has_stored_embeddings else None
    query_embedding: list[float] | None = None
    if embedding_client is not None:
        try:
            query_embedding = list(embedding_client.embed_query(query))
        except Exception as exc:
            logger.info("knowledge query embedding failed, falling back to keyword search, error=%s", exc)

    if query_embedding is not None:
        for row in rows:
            raw_embedding = row.get("embedding_json")
            if not raw_embedding:
                continue
            try:
                score = _cosine_similarity(query_embedding, json.loads(raw_embedding))
            except Exception:
                score = 0.0
            if score > 0:
                scored.append((score, dict(row)))

    if not scored:
        query_tokens = _tokenize_for_search(query)
        for row in rows:
            content = row["content"] or ""
            content_tokens = _tokenize_for_search(content)
            overlap = query_tokens & content_tokens
            score = len(overlap) / max(len(query_tokens), 1)
            if score > 0:
                scored.append((score, dict(row)))

    scored.sort(key=lambda item: (item[0], item[1]["id"]), reverse=True)
    return [row for _, row in scored[:limit]]


def _format_knowledge_result(rows: list[dict[str, Any]]) -> str:
    if not rows:
        return "知识库中没有找到相关商品说明、评价或平台规则。"
    formatted = []
    for row in rows:
        source_id = f"#{row['source_id']}" if row.get("source_id") is not None else ""
        formatted.append(f"[{row['source_type']}{source_id}] {row['content']}")
    return "\n".join(formatted)


@tool
def search_products(keyword: str, max_price: float | None = None) -> str:
    """根据关键词搜索真实商品，可选传入最高预算价格筛选。"""
    logger.info("tool search_products called, keyword=%s, max_price=%s", keyword, max_price)
    params = {"keyword": keyword, "page": 1, "size": 5}
    with httpx.Client(timeout=8) as client:
        resp = client.get(f"{BACKEND_BASE}/api/products/search", params=params)
        resp.raise_for_status()
        products = resp.json()["list"]
    if max_price is not None:
        products = [p for p in products if float(p["price"]) <= max_price]
    if not products:
        return "未找到符合条件的商品"
    return "\n".join(f"ID {p['id']} - {p['name']} - ¥{p['price']} - 库存{p['stock']}" for p in products)


@tool
def get_product_detail(product_id: int) -> str:
    """查询指定商品的真实详情，包括名称、价格、库存和描述。"""
    logger.info("tool get_product_detail called, product_id=%s", product_id)
    with httpx.Client(timeout=8) as client:
        resp = client.get(f"{BACKEND_BASE}/api/products/{product_id}")
        resp.raise_for_status()
        product = resp.json()
    return (
        f"商品ID {product['id']}：{product['name']}\n"
        f"价格：¥{product['price']}，库存：{product['stock']}，销量：{product['salesCount']}\n"
        f"描述：{product.get('description') or '暂无描述'}"
    )


@tool
def compare_products(product_ids: list[int]) -> str:
    """对比多个真实商品的价格、库存、销量和描述，最多对比 4 个。"""
    ids = [int(product_id) for product_id in product_ids[:4]]
    logger.info("tool compare_products called, product_ids=%s", ids)
    if len(ids) < 2:
        return "请至少提供两个商品ID用于对比"

    rows = []
    with httpx.Client(timeout=8) as client:
        for product_id in ids:
            resp = client.get(f"{BACKEND_BASE}/api/products/{product_id}")
            resp.raise_for_status()
            product = resp.json()
            rows.append(
                f"ID {product['id']}｜{product['name']}｜¥{product['price']}｜"
                f"库存{product['stock']}｜销量{product['salesCount']}｜{product.get('description') or '暂无描述'}"
            )
    return "\n".join(rows)


@tool
def recommend_by_preference(user_id: int, max_price: float | None = None) -> str:
    """根据用户长期偏好标签推荐真实商品，可选传入最高预算。"""
    user_id = require_tool_user(user_id)
    tags = preference_tags(user_id)
    keyword = product_keyword("", tags)
    logger.info("tool recommend_by_preference called, user_id=%s, tags=%s, max_price=%s", user_id, tags, max_price)
    result = search_products.invoke({"keyword": keyword, "max_price": max_price})
    return f"用户偏好标签：{', '.join(tags) or '暂无'}\n推荐结果：\n{result}"


@tool
def add_to_cart(user_id: int, product_id: int, quantity: int = 1) -> str:
    """确认用户要购买后，将真实商品加入该用户购物车。调用前必须已获得用户明确确认。"""
    user_id = require_tool_user(user_id)
    logger.info("tool add_to_cart called, user_id=%s, product_id=%s, quantity=%s", user_id, product_id, quantity)
    quantity = max(1, int(quantity))
    grant_key = (int(user_id), int(product_id), quantity)
    if cart_confirmation_grants[grant_key] <= 0:
        with httpx.Client(timeout=8) as client:
            resp = client.get(f"{BACKEND_BASE}/api/products/{product_id}")
            resp.raise_for_status()
            product = resp.json()
        pending_cart_actions[memory_key(int(user_id), "__pending_cart__")] = {
            "product_id": int(product_id),
            "quantity": quantity,
        }
        return f"请先向用户确认：是否将「{product['name']}」x {quantity} 加入购物车？用户确认后再调用本工具。"
    cart_confirmation_grants[grant_key] -= 1
    with httpx.Client(timeout=8) as client:
        resp = client.post(
            f"{BACKEND_BASE}/api/internal/cart",
            headers=_internal_headers(),
            json={"userId": user_id, "productId": product_id, "quantity": quantity},
        )
        resp.raise_for_status()
        item = resp.json()
    return (
        f"已加入购物车：{item['productName']} x {item['quantity']}，"
        f"小计 ¥{item['subtotal']}。你可以到购物车结算。"
    )


@tool
def query_order_status(user_id: int, order_id: int | None = None) -> str:
    """查询指定用户的真实订单状态。不传 order_id 时返回最近订单。"""
    user_id = require_tool_user(user_id)
    logger.info("tool query_order_status called, user_id=%s, order_id=%s", user_id, order_id)
    with httpx.Client(timeout=8) as client:
        if order_id:
            resp = client.get(
                f"{BACKEND_BASE}/api/internal/orders/{order_id}",
                params={"userId": user_id},
                headers=_internal_headers(),
            )
        else:
            resp = client.get(
                f"{BACKEND_BASE}/api/internal/orders",
                params={"userId": user_id, "size": 3},
                headers=_internal_headers(),
            )
        resp.raise_for_status()
        data = resp.json()
    rows = data if isinstance(data, list) else [data]
    if not rows:
        return "暂时没有查到订单"
    return "\n".join(f"{o['orderNo']} - {o['status']} - ¥{o['totalAmount']}" for o in rows)


@tool
def search_knowledge_base(query: str) -> str:
    """Search the RAG knowledge base built from product descriptions, product reviews, and platform FAQ."""
    logger.info("tool search_knowledge_base called, query=%s", query)
    return _format_knowledge_result(_knowledge_rows(query))


def _parse_product_ids(product_ids: str | list[int]) -> list[int]:
    if isinstance(product_ids, list):
        return [int(product_id) for product_id in product_ids[:8]]
    return [int(value) for value in re.findall(r"\d+", str(product_ids))[:8]]


@tool
def build_purchase_plan(product_ids: str, budget: float | None = None) -> str:
    """Build a multi-step purchase plan from real product IDs, including total cost, stock, and budget fit."""
    ids = _parse_product_ids(product_ids)
    logger.info("tool build_purchase_plan called, product_ids=%s, budget=%s", ids, budget)
    if not ids:
        return "请先提供候选商品ID，我才能生成购买方案。"

    items: list[dict[str, Any]] = []
    with httpx.Client(timeout=8) as client:
        for product_id in ids:
            resp = client.get(f"{BACKEND_BASE}/api/products/{product_id}")
            resp.raise_for_status()
            items.append(resp.json())

    total = sum(float(item["price"]) for item in items)
    lines = ["购买方案："]
    for index, item in enumerate(items, start=1):
        stock_note = "可购买" if int(item.get("stock") or 0) > 0 else "已售罄"
        lines.append(
            f"{index}. ID {item['id']} - {item['name']} - ¥{item['price']} - 库存{item['stock']} - {stock_note}"
        )
    lines.append(f"合计：¥{total:.2f}")
    if budget is not None:
        diff = float(budget) - total
        if diff >= 0:
            lines.append(f"预算：¥{float(budget):.2f}，在预算内，剩余约 ¥{diff:.2f}。")
        else:
            lines.append(f"预算：¥{float(budget):.2f}，超出约 ¥{abs(diff):.2f}，建议删减或换低价款。")
    sold_out = [str(item["id"]) for item in items if int(item.get("stock") or 0) <= 0]
    if sold_out:
        lines.append(f"注意：商品ID {', '.join(sold_out)} 当前无库存，不能直接加入购物车。")
    lines.append("下一步：如果用户明确确认，再调用 add_to_cart；不要自动加购。")
    return "\n".join(lines)


search_products.description = "Search real products by keyword and optional max price. Returns product IDs, names, prices, and stock."
get_product_detail.description = "Get full details for one real product by product ID."
compare_products.description = "Compare up to four real products by price, stock, sales, and description."
recommend_by_preference.description = "Recommend real products according to the current user's long-term preference tags."
add_to_cart.description = "Add a product to the current user's cart only after explicit user confirmation."
query_order_status.description = "Query the current user's real order status through the backend internal API."


TOOLS = [
    search_products,
    get_product_detail,
    compare_products,
    recommend_by_preference,
    add_to_cart,
    query_order_status,
    search_knowledge_base,
    build_purchase_plan,
]

def ensure_tables() -> None:
    with engine.begin() as conn:
        conn.execute(text("""
            CREATE TABLE IF NOT EXISTS agent_conversation (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              session_id VARCHAR(64) NOT NULL,
              user_id BIGINT NOT NULL,
              role VARCHAR(10) NOT NULL,
              content TEXT NOT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              INDEX idx_session (session_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """))
        conn.execute(text("""
            CREATE TABLE IF NOT EXISTS user_preference (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              user_id BIGINT NOT NULL,
              preference_tag VARCHAR(50) NOT NULL,
              weight DECIMAL(3,2) NOT NULL DEFAULT 1.0,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              UNIQUE KEY uk_user_tag (user_id, preference_tag)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """))
        conn.execute(text("""
            CREATE TABLE IF NOT EXISTS knowledge_chunk (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              source_type VARCHAR(20) NOT NULL,
              source_id BIGINT NULL,
              content TEXT NOT NULL,
              embedding_json MEDIUMTEXT NULL,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              INDEX idx_knowledge_source (source_type, source_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """))


def load_history(session_id: str, user_id: int | None = None) -> list[dict[str, str]]:
    key = memory_key(user_id, session_id) if user_id is not None else session_id
    if memory[key]:
        return list(memory[key])
    with SessionLocal() as db:
        if user_id is None:
            rows = db.execute(
                text("""
                    SELECT role, content FROM agent_conversation
                    WHERE session_id = :session_id
                    ORDER BY id DESC LIMIT 12
                """),
                {"session_id": session_id},
            ).mappings().all()
        else:
            rows = db.execute(
                text("""
                    SELECT role, content FROM agent_conversation
                    WHERE session_id = :session_id AND user_id = :user_id
                    ORDER BY id DESC LIMIT 12
                """),
                {"session_id": session_id, "user_id": user_id},
            ).mappings().all()
    restored = [{"role": row["role"], "content": row["content"]} for row in reversed(rows)]
    memory[key].extend(restored)
    return restored


def save_message(session_id: str, user_id: int, role: str, content: str) -> None:
    memory[memory_key(user_id, session_id)].append({"role": role, "content": content})
    with engine.begin() as conn:
        conn.execute(
            text("""
                INSERT INTO agent_conversation(session_id, user_id, role, content)
                VALUES (:session_id, :user_id, :role, :content)
            """),
            {"session_id": session_id, "user_id": user_id, "role": role, "content": content},
        )


def preference_tags(user_id: int) -> list[str]:
    with SessionLocal() as db:
        rows = db.execute(
            text("""
                SELECT preference_tag FROM user_preference
                WHERE user_id = :user_id
                ORDER BY weight DESC, updated_at DESC LIMIT 3
            """),
            {"user_id": user_id},
        ).mappings().all()
    return [r["preference_tag"] for r in rows]


def upsert_preferences(user_id: int, tags: list[str]) -> None:
    if not tags:
        return
    with engine.begin() as conn:
        for tag in tags[:5]:
            conn.execute(
                text("""
                    INSERT INTO user_preference(user_id, preference_tag, weight)
                    VALUES (:user_id, :tag, 1.0)
                    ON DUPLICATE KEY UPDATE
                      weight = LEAST(2.0, weight + 0.1),
                      updated_at = CURRENT_TIMESTAMP
                """),
                {"user_id": user_id, "tag": tag[:50]},
            )
    logger.info("preferences updated, user_id=%s, tags=%s", user_id, tags[:5])


def extract_budget(message: str) -> float | None:
    """只在出现预算语境时识别金额，避免把普通数字（如"推荐3款键盘"）误判为预算。"""
    patterns = [
        r"(?:预算|不超过|不高于|低于|最多|控制在)\s*[¥￥]?\s*(\d+(?:\.\d+)?)",
        r"[¥￥]\s*(\d+(?:\.\d+)?)",
        r"(\d+(?:\.\d+)?)\s*(?:元|块钱|块|rmb|预算以内|以内|以下|左右)",
    ]
    for pattern in patterns:
        match = re.search(pattern, message, re.IGNORECASE)
        if match:
            return float(match.group(1))
    return None


def extract_product_id(message: str) -> int | None:
    patterns = [
        r"(?:商品\s*ID|ID|编号)\s*[:：]?\s*(\d+)",
        r"第\s*(\d+)\s*(?:个|款|件)",
    ]
    for pattern in patterns:
        match = re.search(pattern, message, re.IGNORECASE)
        if match:
            return int(match.group(1))
    return None


def extract_quantity(message: str) -> int:
    match = re.search(r"(\d+)\s*(?:件|个|台|把|份)", message)
    if match:
        return max(1, int(match.group(1)))
    return 1


def wants_add_to_cart(message: str) -> bool:
    return any(word in message for word in ["加入购物车", "加购物车", "放购物车", "加到购物车", "加入我的购物车"])


def is_confirmation(message: str) -> bool:
    normalized = message.strip().lower()
    return normalized in {"确认", "确定", "可以", "是的", "好的", "好", "ok", "yes", "y"} or "确认加入" in normalized


def confirm_pending_cart(user_id: int) -> str:
    pending_key = memory_key(user_id, "__pending_cart__")
    action = pending_cart_actions.pop(pending_key)
    grant_key = (user_id, action["product_id"], action["quantity"])
    cart_confirmation_grants[grant_key] += 1
    return add_to_cart.invoke({
        "user_id": user_id,
        "product_id": action["product_id"],
        "quantity": action["quantity"],
    })


def request_cart_confirmation(user_id: int, message: str) -> tuple[str, list[str]]:
    product_id = extract_product_id(message)
    if product_id is None:
        return "请告诉我要加入购物车的商品 ID，例如：把商品 ID 1 加入购物车。", []
    quantity = extract_quantity(message)
    pending_cart_actions[memory_key(user_id, "__pending_cart__")] = {"product_id": product_id, "quantity": quantity}
    detail = get_product_detail.invoke({"product_id": product_id})
    return f"{detail}\n确认将商品 ID {product_id} x {quantity} 加入购物车吗？", ["get_product_detail"]


def extract_preferences_rule(message: str) -> list[str]:
    tags = []
    rules = {
        "机械键盘": ["机械键盘", "键盘", "青轴", "茶轴", "敲代码"],
        "预算敏感": ["预算", "便宜", "性价比", "不要太贵", "以内"],
        "数码产品": ["耳机", "显示器", "充电器", "鼠标", "电脑", "数码"],
        "办公学习": ["办公", "学习", "宿舍", "编程", "敲代码"],
    }
    for tag, keywords in rules.items():
        if any(k in message for k in keywords):
            tags.append(tag)
    return tags


def has_preference_signal(message: str) -> bool:
    keywords = [
        "喜欢", "偏好", "预算", "便宜", "性价比", "不要太贵", "以内",
        "键盘", "机械", "青轴", "茶轴", "敲代码", "编程",
        "耳机", "显示器", "充电器", "鼠标", "电脑", "数码",
        "办公", "学习", "宿舍",
    ]
    return any(keyword in message for keyword in keywords)


def product_keyword(message: str, tags: list[str]) -> str:
    if any(word in message for word in ["键盘", "敲代码", "机械", "青轴", "茶轴"]):
        return "键盘"
    if any(word in message for word in ["耳机", "降噪", "蓝牙"]):
        return "耳机"
    if any(word in message for word in ["显示器", "屏幕"]):
        return "显示器"
    if any(word in message for word in ["鼠标"]):
        return "鼠标"
    for tag in tags:
        if tag in {"机械键盘", "数码产品"}:
            return "键盘" if tag == "机械键盘" else "数码"
    return message[:12] or "键盘"


def llm(streaming: bool = False) -> Any | None:
    if not llm_enabled():
        return None
    base_url = LLM_BASE_URL if is_configured(LLM_BASE_URL) else None
    return ChatOpenAI(
        api_key=LLM_API_KEY,
        base_url=base_url,
        model=LLM_MODEL,
        temperature=0.3,
        streaming=streaming,
    )


def extract_preferences(
    user_id: int,
    history: list[dict[str, str]],
    message: str,
    reply: str,
    callbacks: list[BaseCallbackHandler] | None = None,
) -> list[str]:
    conversation = "\n".join([f"{h['role']}: {h['content']}" for h in history[-8:]])
    conversation = f"{conversation}\nuser: {message}\nassistant: {reply}"
    rule_tags = extract_preferences_rule(conversation)
    if rule_tags:
        return rule_tags
    if not has_preference_signal(message):
        return []

    model = llm()
    if model is None:
        return []

    prompt = (
        "分析以下对话，提炼用户表现出的购物偏好标签，例如机械键盘、预算敏感、数码产品、办公学习。"
        "如果没有明显偏好则返回空数组。只返回 JSON 字符串数组，不要解释。\n"
        f"对话内容：{conversation}"
    )
    try:
        raw = model.invoke(prompt, config={"callbacks": callbacks or []}).content
        parsed = json.loads(raw)
        if isinstance(parsed, list):
            return [str(tag)[:50] for tag in parsed if str(tag).strip()]
    except Exception as exc:
        logger.info("llm preference extraction failed, user_id=%s, error=%s", user_id, exc)
    return []


def system_prompt(tags: list[str]) -> str:
    return (
        "你是智能商城的导购助手。"
        f"该用户的历史偏好标签：{', '.join(tags) or '暂无'}。"
        "你只能回答购物、商品推荐和订单相关问题。"
        "推荐商品时必须基于工具返回的真实商品数据，不要编造不存在的商品。"
        "回复使用简洁中文，不要使用 emoji。"
        "用户问订单时必须调用订单工具。用户问商品或推荐时必须调用商品搜索工具。"
        "用户问某个商品详情时调用 get_product_detail；用户要求对比商品时调用 compare_products。"
        "用户问新品推荐、猜你喜欢、按我的偏好推荐时优先调用 recommend_by_preference。"
        "只有用户明确确认加入购物车后，才可以调用 add_to_cart；否则先询问确认。"
        "用户问天气、新闻、股票等非购物问题时，礼貌说明自己只能处理商城购物相关问题。"
    )


def history_text(history: list[dict[str, str]]) -> str:
    if not history:
        return "暂无历史对话"
    return "\n".join(f"{item['role']}: {item['content']}" for item in history[-6:])


def run_langchain_agent(
    user_id: int,
    tags: list[str],
    history: list[dict[str, str]],
    message: str,
    callbacks: list[BaseCallbackHandler] | None = None,
    streaming: bool = False,
) -> tuple[str, list[str]]:
    model = llm(streaming=streaming)
    if model is None:
        raise RuntimeError("LLM is not configured")

    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt(tags)),
        ("human", "user_id={user_id}\n历史对话：\n{history}\n用户当前问题：{input}"),
        MessagesPlaceholder("agent_scratchpad"),
    ])
    agent = create_tool_calling_agent(model, TOOLS, prompt)
    executor = AgentExecutor(agent=agent, tools=TOOLS, verbose=False, return_intermediate_steps=True)
    result = executor.invoke({
        "input": message,
        "user_id": user_id,
        "history": history_text(history),
    }, config={"callbacks": callbacks or []})
    tools_used = [step[0].tool for step in result.get("intermediate_steps", [])]
    logger.info("langchain agent completed, user_id=%s, tools=%s", user_id, tools_used)
    return str(result["output"]), tools_used


def offline_agent(user_id: int, tags: list[str], message: str) -> tuple[str, list[str]]:
    pending_key = memory_key(user_id, "__pending_cart__")
    if is_confirmation(message) and pending_key in pending_cart_actions:
        tool_result = confirm_pending_cart(user_id)
        return tool_result, ["add_to_cart"]

    if wants_add_to_cart(message):
        return request_cart_confirmation(user_id, message)

    if any(word in message for word in ["对比", "比较", "哪个好"]):
        ids = [int(value) for value in re.findall(r"\d+", message)[:4]]
        if len(ids) >= 2:
            tool_result = compare_products.invoke({"product_ids": ids})
            return f"我查到这些真实商品信息，方便你对比：\n{tool_result}", ["compare_products"]

    if any(word in message for word in ["详情", "详细", "具体参数", "介绍一下"]):
        product_id = extract_product_id(message)
        if product_id is not None:
            tool_result = get_product_detail.invoke({"product_id": product_id})
            return tool_result, ["get_product_detail"]

    if any(word in message for word in ["订单", "买的", "购买", "上次", "到哪", "物流", "发货", "状态"]):
        tool_result = query_order_status.invoke({"user_id": user_id, "order_id": None})
        return f"我查到你的最近订单：\n{tool_result}", ["query_order_status"]

    if any(word in message for word in ["天气", "新闻", "股票", "世界杯"]):
        return "我是智能商城导购助手，主要帮你查商品、做推荐和查询订单，这类问题我不能可靠回答。", []

    budget = extract_budget(message)
    if any(word in message for word in ["新品推荐", "猜你喜欢", "按我的偏好", "给我推荐", "推荐一下"]):
        tool_result = recommend_by_preference.invoke({"user_id": user_id, "max_price": budget})
        return f"我按你的长期偏好找了一组真实商品：\n{tool_result}", ["recommend_by_preference", "search_products"]

    keyword = product_keyword(message, tags)
    tool_result = search_products.invoke({"keyword": keyword, "max_price": budget})
    reply = (
        f"结合你的偏好（{', '.join(tags) or '暂未记录'}），我从真实商品里找到这些选择：\n"
        f"{tool_result}\n"
        "你可以继续告诉我预算、使用场景或想要的手感，我再帮你缩小范围。"
    )
    return reply, ["search_products"]


def extract_budget(message: str) -> float | None:
    patterns = [
        r"(?:预算|不超过|不高于|低于|最高|控制在|以内|以下)\s*[¥￥]?\s*(\d+(?:\.\d+)?)",
        r"[¥￥]\s*(\d+(?:\.\d+)?)",
        r"(\d+(?:\.\d+)?)\s*(?:元|块|块钱|rmb|RMB|以内|以下|左右)",
    ]
    for pattern in patterns:
        match = re.search(pattern, message)
        if match:
            return float(match.group(1))
    return None


def extract_product_id(message: str) -> int | None:
    patterns = [
        r"(?:商品\s*ID|ID|编号)\s*[:：]?\s*(\d+)",
        r"第\s*(\d+)\s*(?:个|款|件)",
    ]
    for pattern in patterns:
        match = re.search(pattern, message, re.IGNORECASE)
        if match:
            return int(match.group(1))
    return None


def extract_quantity(message: str) -> int:
    match = re.search(r"(\d+)\s*(?:件|个|台|把|份)", message)
    if match:
        return max(1, int(match.group(1)))
    return 1


def wants_add_to_cart(message: str) -> bool:
    return any(word in message for word in ["加入购物车", "加购物车", "放购物车", "加到购物车", "加入我的购物车"])


def is_confirmation(message: str) -> bool:
    normalized = message.strip().lower()
    return normalized in {"确认", "确定", "可以", "是的", "好的", "好", "ok", "yes", "y"} or "确认加入" in normalized


def extract_preferences_rule(message: str) -> list[str]:
    rules = {
        "机械键盘": ["机械键盘", "键盘", "青轴", "茶轴", "敲代码"],
        "预算敏感": ["预算", "便宜", "性价比", "不要太贵", "以内"],
        "数码产品": ["耳机", "显示器", "充电器", "鼠标", "电脑", "数码"],
        "办公学习": ["办公", "学习", "宿舍", "编程", "敲代码"],
    }
    tags = []
    for tag, keywords in rules.items():
        if any(keyword in message for keyword in keywords):
            tags.append(tag)
    return tags


def has_preference_signal(message: str) -> bool:
    keywords = [
        "喜欢", "偏好", "预算", "便宜", "性价比", "不要太贵", "以内",
        "键盘", "机械", "青轴", "茶轴", "敲代码", "编程",
        "耳机", "显示器", "充电器", "鼠标", "电脑", "数码",
        "办公", "学习", "宿舍",
    ]
    return any(keyword in message for keyword in keywords)


def product_keyword(message: str, tags: list[str]) -> str:
    keyword_rules = [
        ("键盘", ["键盘", "敲代码", "机械", "青轴", "茶轴", "程序员"]),
        ("耳机", ["耳机", "降噪", "蓝牙"]),
        ("显示器", ["显示器", "屏幕", "外接屏"]),
        ("鼠标", ["鼠标"]),
        ("充电器", ["充电器", "快充", "充电头"]),
        ("电脑", ["电脑", "笔记本"]),
        ("手账", ["礼物", "送给", "女朋友", "女生", "同学", "手账", "计划本", "文创"]),
        ("牙刷", ["牙刷", "实用礼物", "个护"]),
        ("充电宝", ["充电宝", "外出", "通勤礼物"]),
    ]
    for keyword, words in keyword_rules:
        if any(word in message for word in words):
            return keyword
    if "机械键盘" in tags:
        return "键盘"
    if "数码产品" in tags:
        return "数码"
    return message[:12] or "键盘"


def system_prompt(tags: list[str]) -> str:
    tag_text = ", ".join(tags) if tags else "暂无"
    return (
        "你是智能商城的导购助手。"
        f"该用户的历史偏好标签：{tag_text}。"
        "你只回答购物、商品推荐、平台规则、售后和订单相关问题。"
        "推荐商品时必须基于工具返回的真实数据，不要编造商品。"
        "用户问订单时必须调用 query_order_status。"
        "用户问商品是否适合、用户评价、售后政策、优惠券、积分、配送或平台 FAQ 时，优先调用 search_knowledge_base。"
        "用户要求礼物建议、搭配、清单或预算方案时，先澄清对象/场景/预算/品类；信息足够后调用 search_products 或 recommend_by_preference，再调用 build_purchase_plan。"
        "用户问商品详情时调用 get_product_detail；要求对比时调用 compare_products。"
        "只有用户明确确认加入购物车后，才能调用 add_to_cart；否则先询问确认。"
        "天气、新闻、股票等非购物问题要礼貌说明自己只能处理商城购物相关问题。"
        "回复使用简洁中文，不要使用 emoji。"
    )


def history_text(history: list[dict[str, str]]) -> str:
    if not history:
        return "暂无历史对话"
    return "\n".join(f"{item['role']}: {item['content']}" for item in history[-6:])


def _ids_from_tool_result(tool_result: str, limit: int = 3) -> list[int]:
    return [int(value) for value in re.findall(r"ID\s+(\d+)", tool_result)[:limit]]


def is_purchase_plan_request(message: str) -> bool:
    return any(word in message for word in ["购买方案", "预算方案", "搭配", "清单", "礼物", "送给", "组合"])


def build_purchase_plan_reply(message: str, tags: list[str]) -> tuple[str, list[str]]:
    budget = extract_budget(message)
    ids = [int(value) for value in re.findall(r"\d+", message)[:4] if budget is None or float(value) != budget]
    tools_used = []
    if not ids:
        search_result = search_products.invoke({"keyword": product_keyword(message, tags), "max_price": budget})
        tools_used.append("search_products")
        ids = _ids_from_tool_result(search_result, 3)
    if ids:
        plan = build_purchase_plan.invoke({"product_ids": ",".join(str(product_id) for product_id in ids), "budget": budget})
        knowledge = search_knowledge_base.invoke({"query": message})
        tools_used.extend(["build_purchase_plan", "search_knowledge_base"])
        return f"{plan}\n\n参考知识库：\n{knowledge}", tools_used
    return "我需要先知道你想买的品类或候选商品ID，才能生成购买方案。", tools_used


def offline_agent(user_id: int, tags: list[str], message: str) -> tuple[str, list[str]]:
    pending_key = memory_key(user_id, "__pending_cart__")
    if is_confirmation(message) and pending_key in pending_cart_actions:
        return confirm_pending_cart(user_id), ["add_to_cart"]

    if wants_add_to_cart(message):
        return request_cart_confirmation(user_id, message)

    if is_purchase_plan_request(message):
        return build_purchase_plan_reply(message, tags)

    if any(word in message for word in ["订单", "买的", "购买", "上次", "到哪", "物流", "发货", "状态"]):
        tool_result = query_order_status.invoke({"user_id": user_id, "order_id": None})
        return f"我查到你的最近订单：\n{tool_result}", ["query_order_status"]

    if any(word in message for word in ["天气", "新闻", "股票", "世界杯"]):
        return "我是智能商城导购助手，主要帮你查商品、做推荐、查订单和解释商城规则，这类问题我不能可靠回答。", []

    budget = extract_budget(message)
    if any(word in message for word in ["对比", "比较", "哪个好"]):
        ids = [int(value) for value in re.findall(r"\d+", message)[:4]]
        if len(ids) >= 2:
            tool_result = compare_products.invoke({"product_ids": ids})
            return f"我查到这些真实商品信息，方便你对比：\n{tool_result}", ["compare_products"]

    if any(word in message for word in ["详情", "详细", "具体参数", "介绍一下"]):
        product_id = extract_product_id(message)
        if product_id is not None:
            tool_result = get_product_detail.invoke({"product_id": product_id})
            return tool_result, ["get_product_detail"]

    if any(word in message for word in ["购买方案", "搭配", "清单", "礼物", "送给", "预算方案", "组合"]):
        ids = [int(value) for value in re.findall(r"\d+", message)[:4] if float(value) != budget]
        tools_used = []
        if not ids:
            search_result = search_products.invoke({"keyword": product_keyword(message, tags), "max_price": budget})
            tools_used.append("search_products")
            ids = _ids_from_tool_result(search_result, 3)
        if ids:
            plan = build_purchase_plan.invoke({"product_ids": ",".join(str(product_id) for product_id in ids), "budget": budget})
            knowledge = search_knowledge_base.invoke({"query": message})
            tools_used.extend(["build_purchase_plan", "search_knowledge_base"])
            return f"{plan}\n\n参考知识库：\n{knowledge}", tools_used
        return "我需要先知道你想买的品类或候选商品ID，才能生成购买方案。", tools_used

    if any(word in message for word in ["售后", "退货", "换货", "退款", "优惠券", "积分", "配送", "平台规则", "FAQ", "评价", "评论", "适合", "好不好"]):
        knowledge = search_knowledge_base.invoke({"query": message})
        product_result = search_products.invoke({"keyword": product_keyword(message, tags), "max_price": budget})
        return f"我先查了知识库和真实商品数据：\n{knowledge}\n\n相关商品：\n{product_result}", ["search_knowledge_base", "search_products"]

    if any(word in message for word in ["新品推荐", "猜你喜欢", "按我的偏好", "给我推荐", "推荐一个"]):
        tool_result = recommend_by_preference.invoke({"user_id": user_id, "max_price": budget})
        return f"我按你的长期偏好找了一组真实商品：\n{tool_result}", ["recommend_by_preference", "search_products"]

    keyword = product_keyword(message, tags)
    product_result = search_products.invoke({"keyword": keyword, "max_price": budget})
    knowledge = search_knowledge_base.invoke({"query": message})
    reply = (
        f"结合你的偏好（{', '.join(tags) or '暂未记录'}），我从真实商品里找到这些选择：\n"
        f"{product_result}\n\n"
        f"知识库补充：\n{knowledge}\n"
        "你可以继续告诉我预算、使用场景或想要的手感，我再帮你缩小范围。"
    )
    return reply, ["search_products", "search_knowledge_base"]


def timing_payload(
    t0: float,
    t1: float,
    t4: float,
    t5: float,
    t6: float,
    agent_timing: AgentTiming,
    preference_timing: AgentTiming,
) -> dict[str, float]:
    return {
        "memory_load": t1 - t0,
        "llm_call_1": agent_timing.llm_duration(0),
        "tool_call": agent_timing.tool_total(),
        "llm_call_2": agent_timing.llm_after_first_total(),
        "preference_extract": preference_timing.llm_duration(0),
        "memory_save": t6 - t5,
        "total": t6 - t0,
    }


def log_timing(timing: dict[str, float]) -> None:
    logger.info(
        "timing breakdown: memory_load=%.2fs, llm_call_1=%.2fs, tool_call=%.2fs, "
        "llm_call_2=%.2fs, preference_extract=%.2fs, memory_save=%.2fs, total=%.2fs",
        timing["memory_load"],
        timing["llm_call_1"],
        timing["tool_call"],
        timing["llm_call_2"],
        timing["preference_extract"],
        timing["memory_save"],
        timing["total"],
    )


def handle_chat(
    request: ChatRequest,
    user_id: int,
    callbacks: list[BaseCallbackHandler] | None = None,
    streaming: bool = False,
) -> ChatResult:
    user_id = int(user_id)
    tool_user_token = active_tool_user_id.set(user_id)
    t0 = time.perf_counter()
    try:
        history = load_history(request.sessionId, user_id)
        tags = preference_tags(user_id)
        message = request.message.strip()
        t1 = time.perf_counter()

        agent_timing = AgentTiming()
        callback_list = [agent_timing, *(callbacks or [])]
        save_message(request.sessionId, user_id, "user", message)

        pending_key = memory_key(user_id, "__pending_cart__")
        if is_confirmation(message) and pending_key in pending_cart_actions:
            try:
                reply = confirm_pending_cart(user_id)
                tools_used = ["add_to_cart"]
            except Exception as tool_exc:
                reply = f"服务暂时无法加入购物车：{tool_exc}"
                tools_used = []
        elif wants_add_to_cart(message):
            try:
                reply, tools_used = request_cart_confirmation(user_id, message)
            except Exception as tool_exc:
                reply = f"服务暂时无法读取商品信息：{tool_exc}"
                tools_used = []
        elif is_purchase_plan_request(message):
            try:
                reply, tools_used = offline_agent(user_id, tags, message)
            except Exception as tool_exc:
                reply = f"服务暂时无法生成购买方案：{tool_exc}"
                tools_used = []
        else:
            try:
                reply, tools_used = run_langchain_agent(
                    user_id,
                    tags,
                    history,
                    message,
                    callbacks=callback_list,
                    streaming=streaming,
                )
                if not reply.strip():
                    logger.info("agent reply empty, falling back to deterministic tools")
                    reply, tools_used = offline_agent(user_id, tags, message)
            except Exception as exc:
                logger.info("langchain agent unavailable, using offline fallback, error=%s", exc)
                try:
                    reply, tools_used = offline_agent(user_id, tags, message)
                except Exception as tool_exc:
                    reply = f"服务暂时无法完成工具查询：{tool_exc}"
                    tools_used = []

        t4 = time.perf_counter()
        preference_timing = AgentTiming()
        new_tags = extract_preferences(user_id, history, message, reply, callbacks=[preference_timing])
        t5 = time.perf_counter()
        upsert_preferences(user_id, new_tags)
        save_message(request.sessionId, user_id, "assistant", reply)
        t6 = time.perf_counter()

        timing = timing_payload(t0, t1, t4, t5, t6, agent_timing, preference_timing)
        log_timing(timing)
        logger.info("chat completed, user_id=%s, session_id=%s, tools=%s", user_id, request.sessionId, tools_used)
        return ChatResult(reply=reply, toolsUsed=tools_used, sessionId=request.sessionId, timing=timing)
    finally:
        active_tool_user_id.reset(tool_user_token)


@app.post("/agent/chat", response_model=ChatResponse)
def chat(request: ChatRequest, user: AuthenticatedUser = Depends(current_user)) -> ChatResponse:
    result = handle_chat(request, user.id)
    return ChatResponse(reply=result.reply, toolsUsed=result.toolsUsed, sessionId=result.sessionId)


@app.post("/agent/chat/stream")
async def chat_stream(request: ChatRequest, user: AuthenticatedUser = Depends(current_user)) -> StreamingResponse:
    async def event_generator():
        loop = asyncio.get_running_loop()
        queue: asyncio.Queue[dict[str, Any]] = asyncio.Queue()
        stream_callback = QueueStreamCallback(loop, queue)

        def run_chat() -> None:
            try:
                result = handle_chat(request, user.id, callbacks=[stream_callback], streaming=True)
                loop.call_soon_threadsafe(queue.put_nowait, {
                    "type": "done",
                    "reply": result.reply,
                    "toolsUsed": result.toolsUsed,
                    "sessionId": result.sessionId,
                    "timing": result.timing,
                })
            except Exception as exc:
                logger.exception("stream chat failed")
                loop.call_soon_threadsafe(queue.put_nowait, {
                    "type": "error",
                    "content": f"AI 服务暂时不可用：{exc}",
                })

        task = asyncio.create_task(asyncio.to_thread(run_chat))
        emitted_tokens = False
        try:
            while True:
                event = await queue.get()
                if event.get("type") == "token":
                    emitted_tokens = True
                if event.get("type") == "done" and not emitted_tokens and event.get("reply"):
                    fallback = {"type": "token", "content": event["reply"]}
                    yield f"data: {json.dumps(fallback, ensure_ascii=False)}\n\n"
                yield f"data: {json.dumps(event, ensure_ascii=False, default=_json_default)}\n\n"
                if event.get("type") in {"done", "error"}:
                    break
        finally:
            await task

    return StreamingResponse(event_generator(), media_type="text/event-stream; charset=utf-8")


@app.get("/agent/history")
def history(sessionId: str = Query(...), user: AuthenticatedUser = Depends(current_user)) -> list[dict[str, str]]:
    return load_history(sessionId, user.id)


@app.get("/agent/preferences", response_model=list[PreferenceResponse])
def preferences(user: AuthenticatedUser = Depends(current_user)) -> list[PreferenceResponse]:
    with SessionLocal() as db:
        rows = db.execute(
            text("""
                SELECT preference_tag, weight FROM user_preference
                WHERE user_id = :user_id
                ORDER BY weight DESC, updated_at DESC
            """),
            {"user_id": user.id},
        ).mappings().all()
    return [PreferenceResponse(tag=row["preference_tag"], weight=float(row["weight"])) for row in rows]


@app.delete("/agent/preferences/{tag}")
def delete_preference(tag: str, user: AuthenticatedUser = Depends(current_user)) -> dict[str, bool]:
    with engine.begin() as conn:
        conn.execute(
            text("DELETE FROM user_preference WHERE user_id = :user_id AND preference_tag = :tag"),
            {"user_id": user.id, "tag": tag},
        )
    logger.info("preference deleted, user_id=%s, tag=%s", user.id, tag)
    return {"ok": True}


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "backend": BACKEND_BASE,
        "llmEnabled": llm_enabled(),
        "tools": [tool.name for tool in TOOLS],
        "shortTermMemory": "in-memory window plus agent_conversation fallback",
        "longTermMemory": "user_preference",
    }
