import argparse
import json
import sys
import threading
import time
import uuid
from dataclasses import dataclass
from typing import Any
from urllib import error, request


@dataclass
class HttpResult:
    status: int
    body: Any
    raw: str


def request_json(method: str, url: str, body: dict[str, Any] | None = None,
                 token: str | None = None, timeout: int = 30) -> HttpResult:
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(url, data=data, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            return HttpResult(resp.status, json.loads(raw) if raw else None, raw)
    except error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            body_obj = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            body_obj = raw
        return HttpResult(exc.code, body_obj, raw)


def require_status(result: HttpResult, expected: int, action: str) -> Any:
    if result.status != expected:
        raise RuntimeError(f"{action} failed: http={result.status} body={result.raw}")
    return result.body


def login(base_url: str, username: str, password: str, timeout: int) -> str:
    body = require_status(request_json("POST", f"{base_url}/api/auth/login", {
        "username": username,
        "password": password,
    }, timeout=timeout), 200, f"login {username}")
    return body["accessToken"]


def flatten_categories(categories: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result = []
    for category in categories:
        result.append(category)
        result.extend(flatten_categories(category.get("children") or []))
    return result


def choose_category(base_url: str, timeout: int) -> int:
    categories = require_status(request_json("GET", f"{base_url}/api/categories", timeout=timeout),
                                200, "load categories")
    flat = flatten_categories(categories)
    if not flat:
        raise RuntimeError("no category exists for acceptance product")
    keyboard = next((c for c in flat if "键盘" in c.get("name", "")), None)
    return int((keyboard or flat[0])["id"])


def register_user(base_url: str, run_id: str, password: str, timeout: int) -> tuple[str, str]:
    username = f"race_{run_id}"
    phone = "139" + str(abs(hash(run_id)) % 100000000).zfill(8)
    require_status(request_json("POST", f"{base_url}/api/auth/register", {
        "username": username,
        "password": password,
        "phone": phone,
    }, timeout=timeout), 201, f"register {username}")
    return username, login(base_url, username, password, timeout)


def create_product(base_url: str, admin_token: str, category_id: int, run_id: str,
                   stock: int, timeout: int) -> int:
    body = require_status(request_json("POST", f"{base_url}/api/products", {
        "categoryId": category_id,
        "name": f"并发验收商品 {run_id}",
        "description": f"acceptance concurrency fixture {run_id}",
        "price": "9.90",
        "stock": stock,
        "imageUrl": "",
        "status": 1,
        "tagIds": [],
    }, token=admin_token, timeout=timeout), 201, "create acceptance product")
    return int(body["id"])


def classify_failure(result: HttpResult) -> str:
    if result.status == 400:
        msg = ""
        if isinstance(result.body, dict):
            msg = str(result.body.get("msg", ""))
        else:
            msg = str(result.body)
        if "库存不足" in msg:
            return "stock_not_enough"
    return f"unexpected_http_{result.status}"


def run_workers(base_url: str, token: str, product_id: int, requests_count: int,
                address: str, timeout: int) -> list[dict[str, Any]]:
    start = threading.Barrier(requests_count + 1, timeout=10)
    results: list[dict[str, Any]] = [{} for _ in range(requests_count)]

    def worker(index: int) -> None:
        try:
            start.wait()
            response = request_json("POST", f"{base_url}/api/orders/direct", {
                "productId": product_id,
                "quantity": 1,
                "shippingAddress": address,
            }, token=token, timeout=timeout)
            if response.status == 201 and isinstance(response.body, dict):
                order_id = response.body.get("orderId")
                order_no = response.body.get("orderNo")
                if order_id and order_no:
                    results[index] = {"result": "success", "orderId": order_id, "orderNo": order_no}
                    return
                results[index] = {"result": "unexpected_success_shape", "body": response.body}
                return
            results[index] = {"result": classify_failure(response), "http": response.status, "body": response.body}
        except Exception as exc:
            results[index] = {"result": "exception", "error": repr(exc)}

    threads = [threading.Thread(target=worker, args=(i,), daemon=True) for i in range(requests_count)]
    for thread in threads:
        thread.start()
    start.wait()
    for thread in threads:
        thread.join(timeout + 5)
    for i, thread in enumerate(threads):
        if thread.is_alive() and not results[i]:
            results[i] = {"result": "timeout"}
    return results


def verify_orders(base_url: str, token: str, product_id: int, address: str,
                  success_order_ids: set[int], timeout: int) -> dict[str, int]:
    orders = require_status(request_json("GET", f"{base_url}/api/orders?status=all", token=token, timeout=timeout),
                            200, "list user orders")
    matching_orders = 0
    matching_items = 0
    for order in orders:
        if int(order.get("id", -1)) not in success_order_ids:
            continue
        if order.get("shippingAddress") != address:
            continue
        matching_orders += 1
        for item in order.get("items") or []:
            if int(item.get("productId", -1)) == product_id:
                matching_items += int(item.get("quantity", 0))
    return {"matchingOrders": matching_orders, "matchingItemQuantity": matching_items}


def run_round(args: argparse.Namespace, admin_token: str, category_id: int, index: int) -> dict[str, Any]:
    run_id = f"{int(time.time())}-{index}-{uuid.uuid4().hex[:10]}"
    username, user_token = register_user(args.base_url, run_id, args.password, args.timeout)
    product_id = create_product(args.base_url, admin_token, category_id, run_id, args.stock, args.timeout)
    address = f"并发验收地址 {run_id}"
    started = time.perf_counter()
    results = run_workers(args.base_url, user_token, product_id, args.requests, address, args.timeout)
    elapsed_ms = int((time.perf_counter() - started) * 1000)

    success = [r for r in results if r.get("result") == "success"]
    expected_failures = [r for r in results if r.get("result") == "stock_not_enough"]
    unexpected = [r for r in results if r.get("result") not in {"success", "stock_not_enough"}]
    order_ids = {int(r["orderId"]) for r in success}
    order_nos = {str(r["orderNo"]) for r in success}

    product = require_status(request_json("GET", f"{args.base_url}/api/products/{product_id}", timeout=args.timeout),
                             200, "load final product")
    order_checks = verify_orders(args.base_url, user_token, product_id, address, order_ids, args.timeout)

    summary = {
        "round": index,
        "runId": run_id,
        "username": username,
        "productId": product_id,
        "requests": args.requests,
        "initialStock": args.stock,
        "success": len(success),
        "stockNotEnough": len(expected_failures),
        "unexpected": len(unexpected),
        "uniqueOrderIds": len(order_ids),
        "uniqueOrderNos": len(order_nos),
        "finalStock": int(product["stock"]),
        "finalSalesCount": int(product["salesCount"]),
        "finalVersion": int(product["version"]),
        "matchingOrders": order_checks["matchingOrders"],
        "matchingItemQuantity": order_checks["matchingItemQuantity"],
        "elapsedMs": elapsed_ms,
        "unexpectedSamples": unexpected[:5],
    }
    expected = {
        "success": args.stock,
        "stockNotEnough": args.requests - args.stock,
        "unexpected": 0,
        "uniqueOrderIds": args.stock,
        "uniqueOrderNos": args.stock,
        "finalStock": 0,
        "finalSalesCount": args.stock,
        "finalVersion": args.stock,
        "matchingOrders": args.stock,
        "matchingItemQuantity": args.stock,
    }
    summary["passed"] = all(summary[key] == value for key, value in expected.items())
    summary["expected"] = expected
    return summary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Agent-Store 100/80 concurrency acceptance test")
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--admin-username", default="admin")
    parser.add_argument("--admin-password", default="123456")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--stock", type=int, default=80)
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--timeout", type=int, default=30)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.requests <= 0 or args.stock <= 0 or args.stock > args.requests:
        print("requests must be positive and stock must be in 1..requests", file=sys.stderr)
        return 2
    print(json.dumps({
        "baseUrl": args.base_url,
        "requests": args.requests,
        "stock": args.stock,
        "rounds": args.rounds,
        "dataPolicy": "creates unique user and product per round; does not reset shared database",
    }, ensure_ascii=False))
    admin_token = login(args.base_url, args.admin_username, args.admin_password, args.timeout)
    category_id = choose_category(args.base_url, args.timeout)
    summaries = [run_round(args, admin_token, category_id, i + 1) for i in range(args.rounds)]
    print(json.dumps({"rounds": summaries}, ensure_ascii=False, indent=2))
    for summary in summaries:
        print(
            f"round={summary['round']} runId={summary['runId']} "
            f"success={summary['success']} stock_not_enough={summary['stockNotEnough']} "
            f"unexpected={summary['unexpected']} stock={summary['finalStock']} "
            f"sales={summary['finalSalesCount']} version={summary['finalVersion']} "
            f"orders={summary['matchingOrders']} itemQty={summary['matchingItemQuantity']} "
            f"passed={summary['passed']}"
        )
    return 0 if all(summary["passed"] for summary in summaries) else 1


if __name__ == "__main__":
    sys.exit(main())
