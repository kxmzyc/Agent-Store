import json
import logging
import os
import re
import asyncio
import time
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from decimal import Decimal
from typing import Any

import httpx
from fastapi import FastAPI, Query
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
    from langchain_openai import ChatOpenAI
except Exception:  # pragma: no cover
    ChatOpenAI = None

logger = logging.getLogger("smart_mall_agent")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")

BACKEND_BASE = os.getenv("BACKEND_BASE", "http://localhost:8080")
DATABASE_URL = os.getenv("DATABASE_URL", "mysql+pymysql://root:smartmall_root@localhost:3306/smart_mall?charset=utf8mb4")
INTERNAL_SERVICE_SECRET = os.getenv("INTERNAL_SERVICE_SECRET", "agent-internal-secret")
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL") or None
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-4o-mini")
LLM_PLACEHOLDER = "__PLACEHOLDER_WILL_BE_PROVIDED_BY_USER__"

engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine)
memory: dict[str, deque[dict[str, str]]] = defaultdict(lambda: deque(maxlen=12))


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
    userId: int
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


def _internal_headers() -> dict[str, str]:
    return {
        "X-Internal-Service": "agent",
        "X-Internal-Secret": INTERNAL_SERVICE_SECRET,
    }


def memory_key(user_id: int, session_id: str) -> str:
    return f"{user_id}:{session_id}"


def _json_default(value: Any) -> str:
    if isinstance(value, Decimal):
        return str(value)
    return str(value)


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
    return "\n".join(f"{p['name']} - ¥{p['price']} - 库存{p['stock']}" for p in products)


@tool
def query_order_status(user_id: int, order_id: int | None = None) -> str:
    """查询指定用户的真实订单状态。不传 order_id 时返回最近订单。"""
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


TOOLS = [search_products, query_order_status]

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
    if any(word in message for word in ["订单", "买的", "购买", "上次", "到哪", "物流", "发货", "状态"]):
        tool_result = query_order_status.invoke({"user_id": user_id, "order_id": None})
        return f"我查到你的最近订单：\n{tool_result}", ["query_order_status"]

    if any(word in message for word in ["天气", "新闻", "股票", "世界杯"]):
        return "我是智能商城导购助手，主要帮你查商品、做推荐和查询订单，这类问题我不能可靠回答。", []

    keyword = product_keyword(message, tags)
    tool_result = search_products.invoke({"keyword": keyword, "max_price": extract_budget(message)})
    reply = (
        f"结合你的偏好（{', '.join(tags) or '暂未记录'}），我从真实商品里找到这些选择：\n"
        f"{tool_result}\n"
        "你可以继续告诉我预算、使用场景或想要的手感，我再帮你缩小范围。"
    )
    return reply, ["search_products"]


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
    callbacks: list[BaseCallbackHandler] | None = None,
    streaming: bool = False,
) -> ChatResult:
    t0 = time.perf_counter()
    history = load_history(request.sessionId, request.userId)
    tags = preference_tags(request.userId)
    message = request.message.strip()
    t1 = time.perf_counter()

    agent_timing = AgentTiming()
    callback_list = [agent_timing, *(callbacks or [])]
    save_message(request.sessionId, request.userId, "user", message)

    try:
        reply, tools_used = run_langchain_agent(
            request.userId,
            tags,
            history,
            message,
            callbacks=callback_list,
            streaming=streaming,
        )
        if not reply.strip():
            logger.info("agent reply empty, falling back to deterministic tools")
            reply, tools_used = offline_agent(request.userId, tags, message)
    except Exception as exc:
        logger.info("langchain agent unavailable, using offline fallback, error=%s", exc)
        try:
            reply, tools_used = offline_agent(request.userId, tags, message)
        except Exception as tool_exc:
            reply = f"服务暂时无法完成工具查询：{tool_exc}"
            tools_used = []

    t4 = time.perf_counter()
    preference_timing = AgentTiming()
    new_tags = extract_preferences(request.userId, history, message, reply, callbacks=[preference_timing])
    t5 = time.perf_counter()
    upsert_preferences(request.userId, new_tags)
    save_message(request.sessionId, request.userId, "assistant", reply)
    t6 = time.perf_counter()

    timing = timing_payload(t0, t1, t4, t5, t6, agent_timing, preference_timing)
    log_timing(timing)
    logger.info("chat completed, user_id=%s, session_id=%s, tools=%s", request.userId, request.sessionId, tools_used)
    return ChatResult(reply=reply, toolsUsed=tools_used, sessionId=request.sessionId, timing=timing)


@app.post("/agent/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    result = handle_chat(request)
    return ChatResponse(reply=result.reply, toolsUsed=result.toolsUsed, sessionId=result.sessionId)


@app.post("/agent/chat/stream")
async def chat_stream(request: ChatRequest) -> StreamingResponse:
    async def event_generator():
        loop = asyncio.get_running_loop()
        queue: asyncio.Queue[dict[str, Any]] = asyncio.Queue()
        stream_callback = QueueStreamCallback(loop, queue)

        def run_chat() -> None:
            try:
                result = handle_chat(request, callbacks=[stream_callback], streaming=True)
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
def history(sessionId: str = Query(...), userId: int | None = Query(default=None)) -> list[dict[str, str]]:
    # 传入 userId 时使用与 chat 一致的内存键（userId:sessionId），避免命名空间割裂
    return load_history(sessionId, userId)


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
