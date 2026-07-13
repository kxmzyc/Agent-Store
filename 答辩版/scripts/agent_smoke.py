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
            timeout=90,
        )
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code}: {detail}") from exc


def post_chat_stream(base_url: str, token: str, session_id: str, message: str) -> dict:
    headers = {"Content-Type": "application/json", "Authorization": f"Bearer {token}"}
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/agent/chat/stream",
        data=json.dumps({"sessionId": session_id, "message": message}).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        payload = response.read().decode("utf-8")
    for block in reversed(payload.split("\n\n")):
        for line in block.splitlines():
            if not line.startswith("data: "):
                continue
            event = json.loads(line[6:])
            if event.get("type") == "done":
                return event
    raise AssertionError(f"expected SSE done event, got {payload}")


def require_tool(payload: dict, tool_name: str) -> None:
    tools = payload.get("toolsUsed", [])
    if tool_name not in tools:
        raise AssertionError(f"expected tool {tool_name}, got {tools}: {payload.get('reply')}")


def require_real_product_cards(payload: dict, backend_url: str) -> None:
    ui = payload.get("ui") or {}
    cards = ui.get("productCards") or []
    if not cards:
        raise AssertionError(f"expected product cards, got ui={ui}")
    source = ui.get("source") or {}
    if not source.get("productsQueried") or not source.get("inventoryVerified"):
        raise AssertionError(f"expected verified product source, got source={source}")

    card = cards[0]
    required_fields = {"id", "name", "price", "stock", "imageUrl", "status"}
    if not required_fields.issubset(card):
        raise AssertionError(f"product card contract is incomplete: {card}")
    product = get_json(f"{backend_url.rstrip('/')}/api/products/{card['id']}", token="")
    if card["name"] != product["name"] or float(card["price"]) != float(product["price"]) or int(card["stock"]) != int(product["stock"]):
        raise AssertionError(f"product card did not reuse backend data: card={card}, product={product}")


def require_pending_cart_confirmation(payload: dict, product_id: int) -> None:
    ui = payload.get("ui") or {}
    pending = ui.get("pendingCart") or {}
    if int(pending.get("productId", 0)) != int(product_id):
        raise AssertionError(f"expected pending cart confirmation for product {product_id}, got {ui}")
    labels = {action.get("label") for action in ui.get("suggestedActions", [])}
    if "确认加入购物车" not in labels:
        raise AssertionError(f"expected explicit cart confirmation action, got {labels}")


def require_agent_task(payload: dict, task_type: str, stage: str) -> None:
    task = (payload.get("ui") or {}).get("agentTask") or {}
    if task.get("type") != task_type or task.get("stage") != stage:
        raise AssertionError(f"expected agentTask {task_type}/{stage}, got {task}")


def cleanup_cart_product(backend_url: str, token: str, product_id: int) -> None:
    items = get_json(f"{backend_url.rstrip('/')}/api/cart", token)
    for item in items:
        if int(item.get("productId")) == product_id:
            delete_json(f"{backend_url.rstrip('/')}/api/cart/{item['id']}", token)


def cart_has_product(backend_url: str, token: str, product_id: int) -> bool:
    items = get_json(f"{backend_url.rstrip('/')}/api/cart", token)
    return any(int(item.get("productId")) == product_id for item in items)


def run_purchase_plan_flow(base_url: str, backend_url: str, token: str) -> None:
    plan_session = f"smoke-plan-{uuid.uuid4().hex[:8]}"
    first_turn = post_chat(base_url, token, plan_session, "帮我搭配一套户外跑步装备")
    if first_turn.get("toolsUsed"):
        raise AssertionError(f"plan clarification should not call tools: {first_turn}")
    if "预算" not in first_turn.get("reply", ""):
        raise AssertionError(f"plan clarification should ask only for budget: {first_turn.get('reply')}")
    require_agent_task(first_turn, "purchase_plan", "clarifying")

    stream_task = post_chat_stream(base_url, token, f"smoke-stream-plan-{uuid.uuid4().hex[:8]}", "帮我搭配一套户外跑步装备")
    require_agent_task(stream_task, "purchase_plan", "clarifying")

    plan = post_chat(base_url, token, plan_session, "预算500，刚开始跑步")
    require_tool(plan, "search_products")
    require_tool(plan, "build_purchase_plan")
    if "合计" not in plan.get("reply", "") or "库存" not in plan.get("reply", ""):
        raise AssertionError(f"expected total and stock in plan reply: {plan.get('reply')}")
    cards = (plan.get("ui") or {}).get("productCards") or []
    if not cards or len(cards) > 3:
        raise AssertionError(f"expected one to three real plan cards, got {cards}")
    action_labels = {action.get("label") for action in (plan.get("ui") or {}).get("suggestedActions", [])}
    expected_actions = {"查看详情", "调整预算", "加入方案商品", "前往购物车结算"}
    if not expected_actions.issubset(action_labels):
        raise AssertionError(f"expected plan actions {expected_actions}, got {action_labels}")
    require_agent_task(plan, "purchase_plan", "ready")

    product_id = int(cards[0]["id"])
    cleanup_cart_product(backend_url, token, product_id)
    cart_request = post_chat(base_url, token, plan_session, f"把商品 ID {product_id} 加入购物车")
    if "确认" not in cart_request.get("reply", "") or "add_to_cart" in cart_request.get("toolsUsed", []):
        raise AssertionError(f"plan cart request bypassed confirmation: {cart_request}")
    require_pending_cart_confirmation(cart_request, product_id)
    require_agent_task(cart_request, "cart_confirmation", "awaiting_confirmation")
    cart_confirm = post_chat(base_url, token, plan_session, "确认")
    require_tool(cart_confirm, "add_to_cart")
    require_agent_task(cart_confirm, "purchase_plan", "completed")
    if not cart_has_product(backend_url, token, product_id):
        raise AssertionError("expected confirmed plan product to be added to cart")
    cleanup_cart_product(backend_url, token, product_id)

    fresh_session = f"smoke-plan-fresh-{uuid.uuid4().hex[:8]}"
    isolated = post_chat(base_url, token, fresh_session, "帮我搭配一套装备")
    if isolated.get("toolsUsed") or "场景" not in isolated.get("reply", ""):
        raise AssertionError(f"unfinished plan leaked into new session: {isolated}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke test the Agent-Store Agent service.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--backend-url", default="http://127.0.0.1:8081")
    parser.add_argument("--username", default="alice")
    parser.add_argument("--password", default="123456")
    args = parser.parse_args()

    user_id, token = login(args.backend_url, args.username, args.password)

    product_session = f"smoke-product-{uuid.uuid4().hex[:8]}"
    product = post_chat(args.base_url, token, product_session, "我喜欢机械键盘，有没有适合敲代码的键盘，预算500")
    require_tool(product, "search_products")
    require_real_product_cards(product, args.backend_url)
    if not (product.get("ui") or {}).get("suggestedActions"):
        raise AssertionError(f"expected contextual suggested actions, got {product.get('ui')}")

    run_purchase_plan_flow(args.base_url, args.backend_url, token)

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
    cart_request = post_chat(args.base_url, token, cart_session, f"把商品 ID {cart_product_id} 加入购物车")
    require_pending_cart_confirmation(cart_request, cart_product_id)
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
    print("purchase plan flow=passed")
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
