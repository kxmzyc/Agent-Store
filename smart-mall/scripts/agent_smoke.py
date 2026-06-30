import argparse
import json
import sys
import urllib.error
import urllib.request
import uuid


def post_chat(base_url: str, user_id: int, session_id: str, message: str) -> dict:
    body = json.dumps({"userId": user_id, "sessionId": session_id, "message": message}).encode("utf-8")
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/agent/chat",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code}: {detail}") from exc


def require_tool(payload: dict, tool_name: str) -> None:
    tools = payload.get("toolsUsed", [])
    if tool_name not in tools:
        raise AssertionError(f"expected tool {tool_name}, got {tools}: {payload.get('reply')}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke test the Smart Mall Agent service.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--user-id", type=int, default=2)
    args = parser.parse_args()

    product_session = f"smoke-product-{uuid.uuid4().hex[:8]}"
    product = post_chat(args.base_url, args.user_id, product_session, "我喜欢机械键盘，有没有适合敲代码的键盘，预算500")
    require_tool(product, "search_products")

    order_session = f"smoke-order-{uuid.uuid4().hex[:8]}"
    order = post_chat(args.base_url, args.user_id, order_session, "我上次买的东西到哪了")
    require_tool(order, "query_order_status")

    memory_session = f"smoke-memory-{uuid.uuid4().hex[:8]}"
    memory = post_chat(args.base_url, args.user_id, memory_session, "有什么新品推荐")
    require_tool(memory, "search_products")
    if "机械键盘" not in memory.get("reply", "") and "键盘" not in memory.get("reply", ""):
        raise AssertionError(f"expected long-term keyboard preference in reply: {memory.get('reply')}")

    weather_session = f"smoke-weather-{uuid.uuid4().hex[:8]}"
    weather = post_chat(args.base_url, args.user_id, weather_session, "今天天气怎么样")
    if weather.get("toolsUsed"):
        raise AssertionError(f"weather question should not use tools: {weather}")

    print("Agent smoke passed.")
    print(f"product tools={product['toolsUsed']}")
    print(f"order tools={order['toolsUsed']}")
    print(f"memory tools={memory['toolsUsed']}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Agent smoke failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
