import json
import os
from typing import Any

import httpx
from sqlalchemy import create_engine, text

try:
    from langchain_openai import OpenAIEmbeddings
except Exception:  # pragma: no cover
    OpenAIEmbeddings = None


BACKEND_BASE = os.getenv("BACKEND_BASE", "http://127.0.0.1:8081")
DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "mysql+pymysql://root:smartmall_root@127.0.0.1:3307/smart_mall?charset=utf8mb4",
)
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL") or None
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-3-small")
LLM_PLACEHOLDER = "__PLACEHOLDER_WILL_BE_PROVIDED_BY_USER__"


FAQ_ITEMS = [
    (
        "faq",
        "商品搜索支持名称和描述模糊匹配。用户描述用途或预算时，导购助手应先搜索真实商品，再结合评价和库存回答。",
    ),
    (
        "faq",
        "购物车可以修改数量、删除商品并勾选结算。库存为0的商品不能加入购物车，也不能生成有效购买方案。",
    ),
    (
        "faq",
        "下单会扣减库存并创建待支付订单。系统使用事务和乐观锁保护库存，避免并发下单时出现超卖。",
    ),
    (
        "faq",
        "支付是实训系统的模拟支付。点击模拟支付后，订单状态从 PENDING_PAYMENT 变为 PAID，不接入真实支付宝或微信支付。",
    ),
    (
        "faq",
        "订单状态流转为 PENDING_PAYMENT 到 PAID 到 SHIPPED 到 COMPLETED；待支付订单可以取消为 CANCELLED。",
    ),
    (
        "faq",
        "取消已经扣库存但未完成的订单时，系统会回补商品库存。非法状态跳转会被后端拒绝。",
    ),
    (
        "faq",
        "优惠券分为满减券和折扣券。结算时需要满足门槛才可使用，系统会计算优惠后应付金额。",
    ),
    (
        "faq",
        "积分可以在订单结算时抵扣部分金额。积分变动会记录在积分流水中，方便用户查看来源和用途。",
    ),
    (
        "faq",
        "商品评价来自完成购买后的用户反馈。导购助手回答商品是否适合时，应引用真实评价或说明暂无评价。",
    ),
    (
        "faq",
        "售后说明：本实训系统不实现退款和复杂售后流程。若用户问售后，应说明当前仅支持订单取消和确认收货演示。",
    ),
    (
        "faq",
        "配送说明：本实训系统不接入真实物流。订单发货和收货通过状态字段演示，用户可在订单列表查看状态。",
    ),
    (
        "faq",
        "账号安全：登录使用 JWT，受保护接口需要 Authorization Bearer Token，用户密码使用 BCrypt 哈希保存。",
    ),
]


def configured(value: str | None) -> bool:
    return bool(value and value.strip() and value != LLM_PLACEHOLDER)


def compact(value: Any) -> str:
    return " ".join(str(value or "").split())


def ensure_table(engine) -> None:
    with engine.begin() as conn:
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


def fetch_products(client: httpx.Client) -> list[dict[str, Any]]:
    products: list[dict[str, Any]] = []
    page = 1
    size = 100
    while True:
        resp = client.get(f"{BACKEND_BASE}/api/products", params={"page": page, "size": size})
        resp.raise_for_status()
        payload = resp.json()
        rows = payload.get("list", [])
        products.extend(rows)
        if not rows or len(products) >= int(payload.get("total", len(products))):
            return products
        page += 1


def fetch_reviews(client: httpx.Client, product_id: int) -> list[dict[str, Any]]:
    resp = client.get(f"{BACKEND_BASE}/api/products/{product_id}/reviews", params={"page": 1, "size": 5})
    resp.raise_for_status()
    return resp.json().get("list", [])


def product_chunk(product: dict[str, Any]) -> dict[str, Any]:
    tag_text = ", ".join(tag.get("name", "") for tag in product.get("tags", []) if tag.get("name"))
    content = (
        f"商品ID {product['id']}。名称：{product.get('name')}。"
        f"价格：¥{product.get('price')}。库存：{product.get('stock')}。销量：{product.get('salesCount')}。"
        f"标签：{tag_text or '暂无'}。描述：{compact(product.get('description')) or '暂无描述'}。"
    )
    return {"source_type": "product", "source_id": product["id"], "content": content}


def review_chunk(product: dict[str, Any], reviews: list[dict[str, Any]]) -> dict[str, Any] | None:
    if not reviews:
        return None
    review_text = "；".join(
        f"{review.get('username', '用户')}评分{review.get('rating')}：{compact(review.get('content')) or '未填写文字评价'}"
        for review in reviews[:5]
    )
    content = f"商品ID {product['id']} {product.get('name')} 的用户评价摘要：{review_text}"
    return {"source_type": "review", "source_id": product["id"], "content": content}


def faq_chunks() -> list[dict[str, Any]]:
    return [{"source_type": source_type, "source_id": None, "content": content} for source_type, content in FAQ_ITEMS]


def embedding_client() -> Any | None:
    if not configured(LLM_API_KEY) or OpenAIEmbeddings is None:
        return None
    kwargs: dict[str, Any] = {"api_key": LLM_API_KEY, "model": EMBEDDING_MODEL}
    if configured(LLM_BASE_URL):
        kwargs["base_url"] = LLM_BASE_URL
    return OpenAIEmbeddings(**kwargs)


def attach_embeddings(chunks: list[dict[str, Any]]) -> None:
    client = embedding_client()
    if client is None:
        for chunk in chunks:
            chunk["embedding_json"] = None
        return
    try:
        embeddings = client.embed_documents([chunk["content"] for chunk in chunks])
    except Exception as exc:
        print(f"embedding disabled after failure: {exc}")
        embeddings = [None] * len(chunks)
    for chunk, embedding in zip(chunks, embeddings):
        chunk["embedding_json"] = json.dumps(embedding, ensure_ascii=False) if embedding else None


def replace_chunks(engine, chunks: list[dict[str, Any]]) -> None:
    with engine.begin() as conn:
        conn.execute(text("DELETE FROM knowledge_chunk"))
        for chunk in chunks:
            conn.execute(
                text("""
                    INSERT INTO knowledge_chunk(source_type, source_id, content, embedding_json)
                    VALUES (:source_type, :source_id, :content, :embedding_json)
                """),
                chunk,
            )


def main() -> None:
    engine = create_engine(DATABASE_URL, pool_pre_ping=True)
    ensure_table(engine)

    chunks: list[dict[str, Any]] = []
    with httpx.Client(timeout=12) as client:
        products = fetch_products(client)
        for product in products:
            chunks.append(product_chunk(product))
            review = review_chunk(product, fetch_reviews(client, int(product["id"])))
            if review:
                chunks.append(review)
    chunks.extend(faq_chunks())
    attach_embeddings(chunks)
    replace_chunks(engine, chunks)
    print(f"knowledge base rebuilt: {len(chunks)} chunks from {BACKEND_BASE}")


if __name__ == "__main__":
    main()
