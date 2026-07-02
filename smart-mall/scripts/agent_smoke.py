import argparse
import json
import sys
import urllib.error
import urllib.request
import uuid


def post_json(url: str, payload: dict, token: str | None = None, timeout: int = 30) -> dict:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def get_json(url: str, token: str, timeout: int = 30) -> dict | list:
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"}, method="GET")
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def delete_json(url: str, token: str, timeout: int = 30) -> dict:
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"}, method="DELETE")
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def login(backend_url: str, username: str, password: str) -> tuple[int, str]:
    data = post_json(f"{backend_url.rstrip('/')}/api/auth/login", {"username": username, "password": password})
    return int(data["userInfo"]["id"]), data["accessToken"]


def post_chat(base_url: str, token: str, session_id: str, message: str) -> dict:
    try:
        return post_json(
            f"{base_url.rstrip('/')}/agent/chat",
            {"sessionId": session_id, "message": message},
            token=token,
        )
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code}: {detail}") from exc


def require_tool(payload: dict, tool_name: str) -> None:
    tools = payload.get("toolsUsed", [])
    if tool_name not in tools:
        raise AssertionError(f"expected tool {tool_name}, got {tools}: {payload.get('reply')}")


def cleanup_cart_product(backend_url: str, token: str, product_id: int) -> None:
    items = get_json(f"{backend_url.rstrip('/')}/api/cart", token)
    for item in items:
        if int(item.get("productId")) == product_id:
            delete_json(f"{backend_url.rstrip('/')}/api/cart/{item['id']}", token)


def cart_has_product(backend_url: str, token: str, product_id: int) -> bool:
    items = get_json(f"{backend_url.rstrip('/')}/api/cart", token)
    return any(int(item.get("productId")) == product_id for item in items)


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke test the Smart Mall Agent service.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--backend-url", default="http://127.0.0.1:8081")
    parser.add_argument("--username", default="alice")
    parser.add_argument("--password", default="123456")
    args = parser.parse_args()

    user_id, token = login(args.backend_url, args.username, args.password)

    product_session = f"smoke-product-{uuid.uuid4().hex[:8]}"
    product = post_chat(args.base_url, token, product_session, "我喜欢机械键盘，有没有适合敲代码的键盘，预算500")
    require_tool(product, "search_products")

    detail_session = f"smoke-detail-{uuid.uuid4().hex[:8]}"
    detail = post_chat(args.base_url, token, detail_session, "商品 ID 1 详细介绍一下")
    require_tool(detail, "get_product_detail")

    compare_session = f"smoke-compare-{uuid.uuid4().hex[:8]}"
    compare = post_chat(args.base_url, token, compare_session, "对比商品 ID 1 和 ID 2 哪个更适合宿舍")
    require_tool(compare, "compare_products")

    order_session = f"smoke-order-{uuid.uuid4().hex[:8]}"
    order = post_chat(args.base_url, token, order_session, "我上次买的东西到哪了")
    require_tool(order, "query_order_status")

    memory_session = f"smoke-memory-{uuid.uuid4().hex[:8]}"
    memory = post_chat(args.base_url, token, memory_session, "有什么新品推荐")
    if "recommend_by_preference" not in memory.get("toolsUsed", []) and "search_products" not in memory.get("toolsUsed", []):
        raise AssertionError(f"expected recommendation tools, got {memory.get('toolsUsed')}: {memory.get('reply')}")
    if "机械键盘" not in memory.get("reply", "") and "键盘" not in memory.get("reply", ""):
        raise AssertionError(f"expected long-term keyboard preference in reply: {memory.get('reply')}")

    cart_product_id = 45
    cleanup_cart_product(args.backend_url, token, cart_product_id)
    cart_session = f"smoke-cart-{uuid.uuid4().hex[:8]}"
    post_chat(args.base_url, token, cart_session, f"把商品 ID {cart_product_id} 加入购物车")
    cart_confirm = post_chat(args.base_url, token, cart_session, "确认")
    require_tool(cart_confirm, "add_to_cart")
    if not cart_has_product(args.backend_url, token, cart_product_id):
        raise AssertionError("expected product to be added to cart")
    cleanup_cart_product(args.backend_url, token, cart_product_id)

    preferences = get_json(f"{args.base_url.rstrip('/')}/agent/preferences", token)
    if not any(item.get("tag") == "机械键盘" for item in preferences):
        raise AssertionError(f"expected keyboard preference, got {preferences}")

    weather_session = f"smoke-weather-{uuid.uuid4().hex[:8]}"
    weather = post_chat(args.base_url, token, weather_session, "今天天气怎么样")
    if weather.get("toolsUsed"):
        raise AssertionError(f"weather question should not use tools: {weather}")

    print(f"Agent smoke passed. user_id={user_id}")
    print(f"product tools={product['toolsUsed']}")
    print(f"detail tools={detail['toolsUsed']}")
    print(f"compare tools={compare['toolsUsed']}")
    print(f"order tools={order['toolsUsed']}")
    print(f"memory tools={memory['toolsUsed']}")
    print(f"cart tools={cart_confirm['toolsUsed']}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Agent smoke failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
