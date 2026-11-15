# How to run ReconIQ (Windows / PowerShell)

Everything is already configured on this machine: MySQL database loaded, Gmail app
password in `.env` (git-ignored), so email delivery works out of the box.

Two processes: the Spring Boot API on **:8080** and the React dev server on **:5173**.
Demo login: **ADMIN001 / admin123**

---

## Fastest path (2 terminals)

**Terminal 1 — backend with MySQL + email:**

```powershell
cd "C:\Users\rlgga\OneDrive\ドキュメント\Desktop\ReconIQ\reconiq\reconiq"
.\run.ps1
```

That script loads `.env` (MySQL password, Gmail app password) and starts Spring Boot.
If PowerShell blocks it: `powershell -ExecutionPolicy Bypass -File .\run.ps1`

**Terminal 2 — frontend:**

```powershell
cd "C:\Users\rlgga\OneDrive\ドキュメント\Desktop\ReconIQ\reconiq\reconiq\frontend"
npm install     # only the first time
npm run dev
```

Open <http://localhost:5173> → sign in with `ADMIN001 / admin123`.

Health check: <http://localhost:8080/api/health>

---

## Switches for `run.ps1`

| Command | What it does |
| --- | --- |
| `.\run.ps1` | MySQL (`reconiq_db`) + email enabled |
| `.\run.ps1 -H2` | in-memory H2 demo data, no MySQL needed (data resets on restart) |
| `.\run.ps1 -Jar` | run the packaged jar instead of `mvn spring-boot:run` |

---

## What to click

1. **Upload data** → *Orders* tab → upload `sample-files/orders.csv`
2. **Upload data** → *Gateway* tab → upload `sample-files/gateway_transactions.csv`
   (or `gateway_transactions_with_errors.csv` to see skipped-row reporting)
3. **Reconciliation** → *Run reconciliation*
4. **Reports** → *Email report* → PDF arrives at `sumitkrgupta9976@gmail.com`

Sample CSVs also live in `frontend/public/samples/` and are linked from the upload page.

---

## Configuration (`.env`)

`run.ps1` reads this git-ignored file (values on this machine are already filled in):

```
DB_URL=jdbc:mysql://localhost:3306/reconiq_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata
DB_USERNAME=root
DB_PASSWORD=<your MySQL root password>

MAIL_USER=sumitkrgupta9976@gmail.com
MAIL_PASS=<your 16-char Gmail app password>
REPORT_EMAIL_TO=sumitkrgupta9976@gmail.com
```

`MAIL_PASS` is a Gmail **app password** (16 chars, spaces optional). Normal Gmail
passwords do not work for SMTP. Create/revoke them at
<https://myaccount.google.com/apppasswords>. To change the recipient, edit
`REPORT_EMAIL_TO` and restart.

> If you ever commit or share this repo, rotate the app password first — `.env` is
> ignored by git, but anything pasted into chat should be treated as exposed.

---

## MySQL

The local Windows service **MySQL267** (`C:\Program Files\MySQL\MySQL Server 26.7`) holds the
database. It is already created and loaded (1 user, 12 orders, 13 transactions).

To recreate it from scratch:

```powershell
$exe = "C:\Program Files\MySQL\MySQL Server 26.7\bin\mysql.exe"
$env:MYSQL_PWD = "your_mysql_password"
Get-Content database\schema.sql -Raw      | & $exe -u root
Get-Content database\sample-data.sql -Raw | & $exe -u root reconiq_db
```

> `schema.sql` **drops** the ReconIQ tables first — fine for dev, never run it on data
> you want to keep.

Check what is in there:

```powershell
& $exe -u root -e "USE reconiq_db; SELECT COUNT(*) orders FROM orders; SELECT COUNT(*) txn FROM transactions;"
```

The backend only validates the schema at startup (`ddl-auto=validate`); it never alters
your tables.

---

## Build / test

```powershell
cd backend
mvn test                              # 34 tests
mvn -Ph2 -DskipTests package          # target/reconiq-backend-1.0.0.jar

cd ..\frontend
npm run build                         # dist/
```

---

## Troubleshooting

- **"Unable to rename ...jar"** while packaging: the jar is still running.
  `Get-Process java | Stop-Process -Force` then package again.
- **404 on `/api/...`**: another copy of ReconIQ owns port 8080.
  `Get-NetTCPConnection -LocalPort 8080 | Select-Object OwningProcess` → stop that PID.
- **Port 5173 busy**: Vite picks 5174 automatically — check the terminal output.
- **Email fails with `535 Authentication failed`**: the app password is wrong/revoked,
  or 2-step verification is off on the Google account.
- **Email panel says "not configured"**: `.env` was not loaded — start the backend with
  `.\run.ps1`, not a bare `mvn` command in a fresh window.
- **H2 data disappeared**: expected — the in-memory database resets on every restart.
- **`Access denied for user 'root'`**: `DB_PASSWORD` in `.env` is out of date.
