import json
import os
import re
from collections import defaultdict, deque
from datetime import datetime
from decimal import Decimal
from typing import Any

import httpx
from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from langchain.tools import tool
from pydantic import BaseModel
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker

try:
    from langchain_openai import ChatOpenAI
except Exception:  # pragma: no cover
    ChatOpenAI = None

BACKEND_BASE = os.getenv("BACKEND_BASE", "http://localhost:8080")
DATABASE_URL = os.getenv("DATABASE_URL", "mysql+pymysql://root:smartmall_root@localhost:3306/smart_mall?charset=utf8mb4")
INTERNAL_SERVICE_SECRET = os.getenv("INTERNAL_SERVICE_SECRET", "agent-internal-secret")
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL") or None
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-4o-mini")

engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine)
memory: dict[str, deque[dict[str, str]]] = defaultdict(lambda: deque(maxlen=12))
app = FastAPI(title="Smart Mall Agent Service")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class ChatRequest(BaseModel):
    userId: int
    sessionId: str
    message: str


class ChatResponse(BaseModel):
    reply: str
    toolsUsed: list[str]
    sessionId: str


def _internal_headers() -> dict[str, str]:
    return {
        "X-Internal-Service": "agent",
        "X-Internal-Secret": INTERNAL_SERVICE_SECRET,
    }


def _json_default(value: Any) -> str:
    if isinstance(value, Decimal):
        return str(value)
    if isinstance(value, datetime):
        return value.isoformat()
    return str(value)


@tool
def search_products(keyword: str, max_price: float | None = None) -> str:
    """根据关键词搜索商品，可选传入最高预算价格筛选。"""
    params = {"keyword": keyword, "page": 1, "size": 5}
    with httpx.Client(timeout=8) as client:
        resp = client.get(f"{BACKEND_BASE}/api/products/search", params=params)
        resp.raise_for_status()
        products = resp.json()["list"]
    if max_price is not None:
        products = [p for p in products if float(p["price"]) <= max_price]
    if not products:
        return "未找到符合条件的商品"
    return "\n".join(f"{p['name']} - ¥{p['price']} - 库存{p['stock']}" for p in products)


@tool
def query_order_status(user_id: int, order_id: int | None = None) -> str:
    """查询指定用户的订单状态。如果不传order_id，返回该用户最近的订单列表。"""
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


@app.on_event("startup")
def startup() -> None:
    ensure_tables()


def load_history(session_id: str) -> list[dict[str, str]]:
    if memory[session_id]:
        return list(memory[session_id])
    with SessionLocal() as db:
        rows = db.execute(
            text("""
                SELECT role, content FROM agent_conversation
                WHERE session_id = :session_id
                ORDER BY id DESC LIMIT 12
            """),
            {"session_id": session_id},
        ).mappings().all()
    restored = [{"role": row["role"], "content": row["content"]} for row in reversed(rows)]
    memory[session_id].extend(restored)
    return restored


def save_message(session_id: str, user_id: int, role: str, content: str) -> None:
    memory[session_id].append({"role": role, "content": content})
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


def extract_budget(message: str) -> float | None:
    match = re.search(r"(\d+(?:\.\d+)?)\s*(?:元|块|预算|以内)?", message)
    return float(match.group(1)) if match else None


def extract_preferences(message: str) -> list[str]:
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


def llm_polish(system_prompt: str, user_message: str, tool_context: str) -> str | None:
    if not LLM_API_KEY or ChatOpenAI is None:
        return None
    llm = ChatOpenAI(api_key=LLM_API_KEY, base_url=LLM_BASE_URL, model=LLM_MODEL, temperature=0.3)
    result = llm.invoke([
        ("system", system_prompt),
        ("user", f"用户问题：{user_message}\n工具结果：\n{tool_context}\n请基于工具结果用中文简洁回答。"),
    ])
    return result.content


@app.post("/agent/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    history = load_history(request.sessionId)
    tags = preference_tags(request.userId)
    tools_used: list[str] = []
    message = request.message.strip()
    system_prompt = (
        "你是智能商城的导购助手。"
        f"该用户的历史偏好标签：{', '.join(tags) or '暂无'}。"
        "只能回答购物、商品推荐和订单相关问题，不要编造不存在的商品。"
    )

    save_message(request.sessionId, request.userId, "user", message)
    try:
        if any(word in message for word in ["订单", "买的", "到哪", "物流", "发货"]):
            tools_used.append("query_order_status")
            tool_result = query_order_status.invoke({"user_id": request.userId, "order_id": None})
            reply = llm_polish(system_prompt, message, tool_result) or f"我查到你的最近订单：\n{tool_result}"
        elif any(word in message for word in ["天气", "新闻", "股票", "世界杯"]):
            reply = "我是智能商城导购助手，主要帮你查商品、做推荐和查询订单，这类问题我不能可靠回答。"
        else:
            keyword = "键盘" if any(word in message for word in ["键盘", "敲代码", "机械"]) else (tags[0] if tags else message[:12])
            budget = extract_budget(message)
            tools_used.append("search_products")
            tool_result = search_products.invoke({"keyword": keyword, "max_price": budget})
            reply = llm_polish(system_prompt, message, tool_result) or (
                f"结合你的偏好（{', '.join(tags) or '暂未记录'}），我从真实商品里找到这些选择：\n"
                f"{tool_result}\n"
                "你可以继续告诉我预算、使用场景或想要的手感，我再帮你缩小范围。"
            )
    except Exception as exc:
        reply = f"服务暂时无法完成工具查询：{exc}"

    new_tags = extract_preferences(" ".join([h["content"] for h in history] + [message, reply]))
    upsert_preferences(request.userId, new_tags)
    save_message(request.sessionId, request.userId, "assistant", reply)
    return ChatResponse(reply=reply, toolsUsed=tools_used, sessionId=request.sessionId)


@app.get("/agent/history")
def history(sessionId: str = Query(...)) -> list[dict[str, str]]:
    return load_history(sessionId)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
