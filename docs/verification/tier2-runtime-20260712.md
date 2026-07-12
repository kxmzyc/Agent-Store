# Tier 2 Runtime Acceptance Evidence

Executed on 2026-07-12 after the restored Docker data disk was used to start the local stack. The ignored `.env` supplied the existing LLM configuration; no credentials are recorded here.

## 2.1 Product Validation

`ProductRequest.price` now requires a positive value and `ProductRequest.stock` allows zero but rejects negative values. The real HTTP flow in `BusinessFlowHttpIntegrationTest` submits both invalid payloads with an admin token and receives HTTP `400` for each. The same test then submits a valid product and continues through the image upload endpoint.

## 2.2 Refund Point Reversal

- Completed-order awards are recorded with the order number (`完成订单 <orderNo>`), so the refund path can identify the exact award without inventing a missing `order_id` column.
- Approval writes an equal negative ledger entry (`退款订单 <orderNo> 积分回滚`) and is idempotent for sequential retries.
- The approval path locks the `after_sale_request` row with `PESSIMISTIC_WRITE`; concurrent approvals therefore allow only one transaction to restore stock, change the order, and write the reversal ledger.
- If the user has spent some of the awarded points, the policy is to deduct only the available balance down to zero, write that actual negative delta, and log the unrecovered remainder. The user balance is never negative.

`AfterSaleServiceIntegrationTest` covers:

```text
completed order -> points awarded -> refund approval -> points reversed once
completed order -> user balance reduced to 25 -> refund approval -> balance 0, reversal -25
two concurrent approvals -> one success, one rejected, one stock restore, one reversal record
```

Actual Maven results:

```text
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- AfterSaleServiceIntegrationTest
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The test log also contains the live business evidence:

```text
PointService: completed order points reversed ... awardedPoints=100, reversedPoints=25, unrecoveredPoints=75
AfterSaleService: after-sale approved ...
```

## 2.3 Admin Image Upload

The existing backend endpoint is `POST /api/products/upload-image` and returns an `imageUrl`. The HTTP integration test uploads `cover.png`, asserts `201`, reads the returned URL, and verifies the served image content type.

The running admin page was checked at `http://127.0.0.1/admin` as an authenticated administrator:

```text
file input count: 1
manual image URL textbox count: 1
manual URL value accepted: https://example.com/manual-image.png
```

The form keeps manual URL entry as a fallback and the file control is wired to upload success -> `form.imageUrl` assignment. The frontend production build completed successfully:

```text
2328 modules transformed
✓ built in 18.07s
```

## Docker Runtime

Actual `docker compose ps --all` after rebuilding the changed services:

```text
agent-store-agent-service-1   Up (healthy)   0.0.0.0:8000->8000/tcp
agent-store-backend-1         Up (healthy)   0.0.0.0:8081->8080/tcp
agent-store-frontend-1        Up (healthy)   0.0.0.0:80->80/tcp
agent-store-mysql-1           Up (healthy)   0.0.0.0:3307->3306/tcp
```

Relevant startup log fragments:

```text
agent service started, backend=http://backend:8080, llm_enabled=True
Application startup complete.
Started SmartMallApplication in 14.334 seconds
nginx ... ready for start up
```

## Changed Files

- `backend/src/main/java/com/example/smartmall/api/ApiSupport.java`
- `backend/src/main/java/com/example/smartmall/repo/AfterSaleRequestRepository.java`
- `backend/src/main/java/com/example/smartmall/repo/PointRecordRepository.java`
- `backend/src/main/java/com/example/smartmall/service/AfterSaleService.java`
- `backend/src/main/java/com/example/smartmall/service/PointService.java`
- `backend/src/test/java/com/example/smartmall/AfterSaleServiceIntegrationTest.java`
- `backend/src/test/java/com/example/smartmall/BusinessFlowHttpIntegrationTest.java`
- `frontend/src/views/AdminView.vue`
