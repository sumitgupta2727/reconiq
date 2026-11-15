# ReconIQ — Smart Payment Reconciliation Platform

ReconIQ compares a business's **internal orders** with **payment-gateway statements** and classifies every record, so finance teams can see exactly where money is missing, duplicated, mismatched or stuck.

![Java](https://img.shields.io/badge/Java-17-1f4fd8)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6db33f)
![React](https://img.shields.io/badge/React-18-61dafb)
![MySQL](https://img.shields.io/badge/MySQL-8-4479a1)
![CI](https://github.com/sumitgupta2727/reconiq/actions/workflows/ci.yml/badge.svg)

<!--
SCREENSHOTS: create a folder called `screenshots` in the repo, add your images
(for example dashboard.png, reconciliation.png), then delete the opening and
closing comment markers around this block so the images show up.

## Screenshots

![Dashboard](screenshots/dashboard.png)
![Reconciliation](screenshots/reconciliation.png)
-->

## Features

- **Dashboard** — totals, match rate, amount at risk, status breakdown and gateway mix charts.
- **Orders** — create, edit, delete, search and filter internal orders.
- **Transactions** — search, filter by gateway / status / date, sortable columns and server-side pagination; add, edit and delete gateway records.
- **CSV upload** — separate Orders and Gateway uploads with drag-and-drop, tolerant header/date/status parsing, row-level error reporting and a permanent upload history. Orders are upserted by `order_id`, so corrected files can be re-uploaded safely.
- **Reconciliation engine** — one click re-runs the whole matching process and reports matched amounts, discrepancies and duration.
- **Reports** — CSV export, a formatted PDF report, and PDF email delivery through Gmail or any SMTP server.
- **Login** — BCrypt-verified demo credentials seeded on first start.

## Reconciliation rules

| Status | Rule |
| --- | --- |
| `MATCHED` | Order ID is linked to a `SUCCESS` transaction with an exactly equal amount. |
| `AMOUNT_MISMATCH` | Linked and successful, but the amounts differ. |
| `DUPLICATE` | More than one transaction points at the same order (after the primary is picked). |
| `MISSING` | The order has no gateway transaction at all. |
| `FAILED` | The linked transaction is reported as `FAILED`. |
| `PENDING` | The linked transaction is reported as `PENDING`. |
| `UNMATCHED` | A gateway transaction references an order that does not exist. |

The engine prefers a `SUCCESS` transaction with the exact expected amount as the primary match, so a stray duplicate cannot hide the real payment.

## Tech stack

| Layer | Technology |
| --- | --- |
| Frontend | React 18, Vite 6, React Router 6, Axios, Recharts, Lucide icons |
| Backend | Java 17, Spring Boot 3.5, Spring Data JPA, Bean Validation |
| Database | MySQL 8 (H2 in-memory profile for a zero-setup demo) |
| Reporting | Apache Commons CSV, OpenPDF, Spring Mail |
| Tests | JUnit 5, AssertJ, MockMvc, H2 |

## Getting started

### Prerequisites

- Java 17 or newer
- Maven
- Node.js and npm
- MySQL 8 (only for Option B)

### Option A — zero setup (in-memory H2)

**Terminal 1 — backend**

```bash
cd backend
mvn -Ph2 -DskipTests package
java -jar target/reconiq-backend-1.0.0.jar --spring.profiles.active=h2
```

On Windows you can simply run the helper script from the project root:

```powershell
.\run.ps1 -H2
```

**Terminal 2 — frontend**

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173> and sign in with:

| Employee ID | Password |
| --- | --- |
| `ADMIN001` | `admin123` |

The H2 profile creates the schema on startup, seeds a demo dataset and exposes a console at <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:reconiq`, user `sa`, empty password). Data is discarded when the backend stops.

Health check: <http://localhost:8080/api/health>

### Option B — MySQL (data persists)

1. Create the database and load the schema and sample data:

   ```bash
   mysql -u root -p < database/schema.sql
   mysql -u root -p reconiq_db < database/sample-data.sql
   ```

   Or with Docker:

   ```bash
   cp .env.example .env        # adjust passwords
   docker compose up -d        # MySQL 8 on :3306, schema + sample data applied once
   ```

2. Copy `.env.example` to `.env` and fill in your database password (and optionally the email settings). The `.env` file is git-ignored — never commit it.

3. Start the backend and frontend. On Windows, `.\run.ps1` loads `.env` automatically. Otherwise export the variables yourself:

   ```bash
   cd backend
   export DB_URL="jdbc:mysql://localhost:3306/reconiq_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata"
   export DB_USERNAME=root
   export DB_PASSWORD=your-password
   mvn -DskipTests package
   java -jar target/reconiq-backend-1.0.0.jar
   ```

The backend only **validates** the schema at startup (`ddl-auto=validate`); it never modifies your tables.

## Try it in 60 seconds

1. Sign in with `ADMIN001 / admin123`.
2. Open **Upload data → Orders** and upload `sample-files/orders.csv`.
3. Switch to **Gateway** and upload `sample-files/gateway_transactions.csv`. (`gateway_transactions_with_errors.csv` demonstrates the partial-import path.)
4. Open **Reconciliation** and click **Run reconciliation**.
5. Filter by `AMOUNT_MISMATCH` or `MISSING` to inspect the issues.
6. Export CSV or PDF from **Reports**.

## Configuration

All backend settings are environment variables with safe defaults (see `backend/src/main/resources/application.properties` and `.env.example`).

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/reconiq_db?...` | JDBC connection string. |
| `DB_USERNAME` | `root` | Database user. |
| `DB_PASSWORD` | *(empty)* | Database password. Never commit it. |
| `SERVER_PORT` | `8080` | API port. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Allowed browser origins. |
| `MAX_UPLOAD_SIZE` | `10MB` | Upload limit. |
| `SEED_ENABLED` | `true` | Create the demo account. |
| `SEED_DEMO_DATA` | `true` | Seed sample data when the database is empty. |
| `SEED_ADMIN_EMPLOYEE_ID` / `SEED_ADMIN_PASSWORD` | `ADMIN001` / `admin123` | Demo credentials. |
| `REPORT_EMAIL_ENABLED` | `true` | Enable PDF report emails. |
| `REPORT_EMAIL_TO` | *(empty)* | Default report recipient. |
| `MAIL_USER` | *(empty)* | SMTP sender address (for example your Gmail address). |
| `MAIL_PASS` | *(empty)* | SMTP app password. Never commit it. |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | `smtp.gmail.com` / `587` | Optional SMTP override. |

Frontend: copy `frontend/.env.example` to `frontend/.env` and set `VITE_API_BASE_URL` (defaults to `http://localhost:8080/api`).

### Emailing the PDF report

1. Create a Gmail **app password** at <https://myaccount.google.com/apppasswords> (normal account passwords do not work for SMTP).
2. Put `MAIL_USER`, `MAIL_PASS` and `REPORT_EMAIL_TO` in your `.env`.
3. Open **Reports**, enter a recipient and click **Send report**. The email contains the same PDF as the export button plus an HTML summary.

If `MAIL_USER` and `MAIL_PASS` are not set, the Reports page shows a configuration hint instead of the send form.

## API

Every endpoint, query parameter and response shape is documented in [`docs/API.md`](docs/API.md).

| Area | Endpoints |
| --- | --- |
| Health | `GET /api/health` |
| Auth | `POST /api/auth/login` |
| Orders | `GET/POST /api/orders`, `GET/PUT/DELETE /api/orders/{id}` |
| Transactions | `GET/POST /api/transactions`, `GET/PUT/DELETE /api/transactions/{id}` |
| Uploads | `POST /api/uploads`, `POST /api/uploads/orders`, `GET /api/uploads` |
| Reconciliation | `POST /api/reconciliation/run`, `GET /api/reconciliation` |

## Testing

```bash
cd backend
mvn test
```

The 34 tests cover the reconciliation rules, both CSV parsers, a full HTTP integration suite (login, run, filtered search, pagination, CSV/PDF export, orders upsert and CRUD, valid/partial/invalid uploads) and a hermetic SMTP test that checks the emailed report really contains a PDF attachment.

```bash
cd frontend
npm run build
```

## Project structure

```
.
├── backend/                    Spring Boot REST API
│   ├── src/main/java/com/reconiq
│   │   ├── config/             CORS, BCrypt, demo data seeder
│   │   ├── controller/         REST endpoints
│   │   ├── dto/                Request/response records
│   │   ├── entity/             JPA entities + enums
│   │   ├── repository/         Spring Data queries
│   │   ├── service/            Reconciliation engine, CSV parsers, reports
│   │   └── web/                Safe pagination helper
│   ├── src/main/resources/     application.properties (+ h2 profile)
│   └── src/test/               Unit + integration tests
├── database/                   MySQL schema and sample data
├── docs/API.md                 Endpoint reference
├── frontend/                   React single-page app
├── sample-files/               Ready-to-upload CSV files
├── .github/workflows/ci.yml    Build + test pipeline
├── docker-compose.yml          MySQL 8 with auto-init scripts
├── run.ps1                     Windows launcher (loads .env)
└── .env.example                Configuration template
```

## Security notes

- Passwords are stored as BCrypt hashes; plain text is never persisted or returned.
- Database and email credentials come from environment variables, never from source control.
- Uploads are extension-checked, size-limited, row-capped and filename-sanitised.
- Sort fields are allow-listed and page size is capped to prevent query abuse.
- Error responses never include stack traces.

**Current limitation:** login is a demo-level flow — the frontend keeps the session in `localStorage` and the API is not yet protected by tokens. Before deploying to production, add Spring Security with JWT, role-based authorisation and HTTPS, and change the seeded demo credentials.

## Roadmap

- JWT authentication and role-based access (ADMIN / ANALYST) on every endpoint
- Scheduled reconciliation runs and email alerts on new discrepancies
- Direct gateway API integrations (Razorpay, Paytm, Stripe)
- Multi-currency support and configurable matching tolerances
- Docker image for the backend and single-command deployment

## Author

**Sumit Kumar Gupta** — Computer Science student, Chandigarh University

GitHub: [@sumitgupta2727](https://github.com/sumitgupta2727)
