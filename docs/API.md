# ReconIQ REST API

Base URL: `http://localhost:8080/api`

All request and response bodies are JSON unless noted otherwise. Timestamps are ISO-8601
strings (`2026-06-01T10:15:00`), dates are `yyyy-MM-dd`, and money values are decimal
numbers with two places.

> **Authentication:** the current release uses a demo-level login (BCrypt check +
> client-side session). Endpoints are not yet protected by JWT. See the roadmap in the
> root README.

## Health

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/health` | Liveness probe. Returns `status`, `service`, `version`, `timestamp`. |

```json
{ "status": "UP", "service": "reconiq-backend", "version": "1.0.0", "timestamp": "2026-06-01T10:15:00" }
```

## Auth

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/auth/login` | Verify an employee ID and password. |

Request:

```json
{ "employeeId": "ADMIN001", "password": "admin123" }
```

Response `200`:

```json
{ "id": 1, "employeeId": "ADMIN001", "name": "ReconIQ Admin", "email": "admin@reconiq.local", "role": "ADMIN" }
```

Errors: `401` for unknown employee ID or wrong password, `400` for blank fields.

## Orders (internal records)

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/orders` | Paged search. |
| `GET` | `/orders/{id}` | One order. |
| `GET` | `/orders/gateways` | Distinct gateways, sorted. |
| `POST` | `/orders` | Create an order (`201`). |
| `PUT` | `/orders/{id}` | Update an order. |
| `DELETE` | `/orders/{id}` | Delete an order (`204`). |

`GET /orders` query parameters:

| Name | Default | Notes |
| --- | --- | --- |
| `page` | `0` | Zero-based. |
| `size` | `20` | Clamped to `100`. |
| `sortBy` | `orderDate` | `id`, `orderId`, `customerName`, `orderAmount`, `orderDate`, `gateway`, `status`, `createdAt`. |
| `direction` | `desc` | `asc` or `desc`. |
| `search` | – | Matches order ID or customer name (case-insensitive). |
| `status` | – | `CREATED`, `PAID`, `CANCELLED`. |
| `gateway` | – | Exact gateway name. |

Create/update body:

```json
{
  "orderId": "ORD2001",
  "customerName": "Neha Kapoor",
  "orderAmount": 1499.00,
  "orderDate": "2026-06-01",
  "gateway": "Razorpay",
  "status": "PAID"
}
```

## Transactions (gateway statements)

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/transactions` | Paged search. |
| `GET` | `/transactions/{id}` | One transaction. |
| `GET` | `/transactions/gateways` | Distinct gateways, sorted. |
| `POST` | `/transactions` | Add a transaction manually (`201`). |
| `PUT` | `/transactions/{id}` | Update a transaction. |
| `DELETE` | `/transactions/{id}` | Delete a transaction (`204`). |
| `GET` | `/transactions?from=...&to=...` | Inclusive date range on `transactionDate`. |

Supports `page`, `size`, `sortBy`, `direction` (`transactionDate`, `transactionAmount`,
`transactionId`, `orderId`, `customerName`, `gateway`, `paymentStatus`, `createdAt`, `id`),
`search`, `gateway`, `status` (`SUCCESS`, `FAILED`, `PENDING`), `from`, `to`.

Create/update body:

```json
{
  "transactionId": "TXN2001",
  "orderId": "ORD2001",
  "customerName": "Neha Kapoor",
  "gateway": "Razorpay",
  "transactionAmount": 1499.00,
  "transactionDate": "2026-06-01T10:15:00",
  "paymentStatus": "SUCCESS"
}
```

## CSV upload

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/uploads` | Upload a gateway CSV (`multipart/form-data`). |
| `GET` | `/uploads` | Upload history, newest first. |
| `GET` | `/uploads/{id}` | One batch with imported record count. |
| `GET` | `/uploads/template` | Download a CSV template. |

Multipart fields:

| Field | Required | Notes |
| --- | --- | --- |
| `file` | yes | `.csv`, up to `MAX_UPLOAD_SIZE` (default `10MB`). |
| `uploadedBy` | no | Employee ID used to attribute the batch. |

Accepted headers (aliases in brackets):

```
order_id [orderid, order_no, order_number, order],
transaction_id [txn_id, payment_id, reference],
customer_name [customer, name],
gateway [payment_gateway, provider],
transaction_amount [amount, paid_amount, payment_amount, value],
transaction_date [date, payment_date, created_at, timestamp],
payment_status [status, payment_state]
```

`transaction_id`, `gateway`, `transaction_amount`, `transaction_date` and
`payment_status` are required. Dates accept ISO or `dd/MM/yyyy`, `dd-MM-yyyy`,
`MM/dd/yyyy` (optionally with a time). Statuses accept common gateway synonyms such as
`PAID`, `CAPTURED`, `DECLINED`, `PROCESSING`.

Rows that fail validation are skipped and reported in `errorMessage`, while valid rows
are imported. The batch status is then `PARTIAL` instead of `COMPLETED`; a completely
unreadable file creates a `FAILED` batch and returns `400`.

Response `201`:

```json
{
  "id": 7,
  "fileName": "gateway-june.csv",
  "fileType": "CSV",
  "totalRecords": 2,
  "importedRecords": 2,
  "status": "COMPLETED",
  "errorMessage": null,
  "uploadedBy": "ReconIQ Admin",
  "uploadedAt": "2026-06-01T10:15:30"
}
```

## Reconciliation

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/reconciliation/run` | Re-run the engine, replacing previous results (`201`). |
| `GET` | `/reconciliation` | Paged results with filters. |
| `GET` | `/reconciliation/gateways` | Distinct gateways in the result set. |
| `GET` | `/reconciliation/{id}` | One result row. |

Run response:

```json
{
  "runAt": "2026-06-01T10:20:00",
  "totalRecords": 8,
  "matched": 2,
  "unmatched": 1,
  "amountMismatch": 1,
  "duplicate": 1,
  "missing": 1,
  "failed": 1,
  "pending": 1,
  "matchedAmount": 500.00,
  "discrepancyAmount": 425.00,
  "durationMs": 42
}
```

Search parameters: `page`, `size`, `sortBy` (`id`, `orderId`, `transactionId`, `gateway`,
`orderAmount`, `transactionAmount`, `difference`, `reconciliationStatus`, `reconciledAt`),
`direction`, `search` (order/transaction ID), `gateway`, `status`, `from`, `to`.

Statuses and how they are produced:

| Status | Meaning |
| --- | --- |
| `MATCHED` | Order and successful transaction amounts are exactly equal. |
| `AMOUNT_MISMATCH` | Linked and successful, but the amounts differ. |
| `DUPLICATE` | An extra transaction points at the same order. |
| `MISSING` | The order has no gateway transaction. |
| `FAILED` | The linked transaction has `paymentStatus = FAILED`. |
| `PENDING` | The linked transaction has `paymentStatus = PENDING`. |
| `UNMATCHED` | A transaction points at an order that does not exist. |

## Reports

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/reports/summary` | Aggregated dashboard numbers. |
| `GET` | `/reports/export/csv` | UTF-8 CSV download (Excel BOM included). |
| `GET` | `/reports/export/pdf` | PDF summary with the largest discrepancies. |
| `POST` | `/reports/email` | Email the PDF report (requires SMTP configuration). |
| `GET` | `/reports/email/config` | Whether email is enabled and the default recipient. |

Summary response (abridged):

```json
{
  "generatedAt": "2026-06-01T10:25:00",
  "lastReconciledAt": "2026-06-01T10:20:00",
  "totalOrders": 12,
  "totalTransactions": 13,
  "totalReconciliationRecords": 14,
  "matchedRecords": 8,
  "issueRecords": 6,
  "orderAmountTotal": 19487.00,
  "transactionAmountTotal": 19487.00,
  "netDifference": 0.00,
  "amountAtRisk": 550.00,
  "statusCounts": { "MATCHED": 8, "UNMATCHED": 1, "AMOUNT_MISMATCH": 1, "DUPLICATE": 2, "MISSING": 1, "FAILED": 1, "PENDING": 1 },
  "statusDifferences": { "MATCHED": 0.00, "UNMATCHED": 500.00 },
  "gateways": [
    { "gateway": "Razorpay", "records": 8, "matched": 5, "issues": 3, "difference": 550.00 }
  ]
}
```

Email request (`to` is optional; the configured default recipient is used when omitted):

```json
{ "to": "finance@example.com" }
```

Email is disabled by default. Set `REPORT_EMAIL_ENABLED=true` and the standard
`SPRING_MAIL_*` properties to enable it. When disabled the endpoint returns `400` with a
clear message.

## Pagination envelope

Every list endpoint returns the same shape:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

## Errors

Failures use a single JSON shape:

```json
{
  "timestamp": "2026-06-01T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Only .csv files are supported. Download the template if you need an example.",
  "path": "/api/uploads"
}
```

| Status | When |
| --- | --- |
| `400` | Validation failure, malformed CSV, unsupported sort field, invalid page size. |
| `401` | Wrong credentials (login only). |
| `404` | Unknown order, transaction, upload batch or reconciliation row. |
| `500` | Unexpected server error (stack traces are never returned). |
