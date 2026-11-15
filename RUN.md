# How to run ReconIQ (Windows / PowerShell)

Two processes: the Spring Boot API on **:8080** and the React dev server on **:5173**.
Demo login: **ADMIN001 / admin123**

## 1. Backend (demo data, no MySQL needed)

```powershell
cd backend
mvn -Ph2 spring-boot:run "-Dspring-boot.run.profiles=h2"
```

Leave that window open. Check it is up: <http://localhost:8080/api/health>

## 2. Frontend

Open a second PowerShell window:

```powershell
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173> and sign in with `ADMIN001 / admin123`.

## 3. What to click

1. **Upload data** → *Orders* tab → upload `sample-files/orders.csv`.
2. **Upload data** → *Gateway* tab → upload `sample-files/gateway_transactions.csv`
   (or `gateway_transactions_with_errors.csv` to see skipped-row reporting).
3. **Reconciliation** → *Run reconciliation*.
4. **Reports** → download the PDF or email it.

Sample CSVs also live in `frontend/public/samples/` and are linked from the upload page.

## 4. Optional: email the PDF report

The email panel needs a Gmail app password (normal Gmail passwords do not work):

```powershell
$env:MAIL_USER = "you@gmail.com"
$env:MAIL_PASS = "your-16-char-app-password"
mvn -Ph2 spring-boot:run "-Dspring-boot.run.profiles=h2"
```

Create the app password at <https://myaccount.google.com/apppasswords>.
Without these variables the report is still downloadable as a PDF — only sending is disabled.

## 5. Optional: run against MySQL instead of H2

```powershell
docker compose up -d      # MySQL 8 on :3306, schema + sample data loaded once

cd backend
$env:DB_URL      = "jdbc:mysql://localhost:3306/reconiq_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata"
$env:DB_USERNAME = "reconiq"
$env:DB_PASSWORD = "reconiq123"
mvn spring-boot:run
```

## 6. Build / test

```powershell
cd backend
mvn test                              # 34 tests
mvn -Ph2 -DskipTests package          # produces target/reconiq-backend-1.0.0.jar

cd ..\frontend
npm run build                         # produces dist/
```

Run the packaged jar:

```powershell
java -jar backend\target\reconiq-backend-1.0.0.jar --spring.profiles.active=h2
```

## Troubleshooting

- **"Unable to rename ...jar"** while packaging: the jar is still running. Stop it
  (`Get-Process java | Stop-Process`) and package again.
- **404 on `/api/...`**: another copy of ReconIQ may own port 8080.
  `Get-NetTCPConnection -LocalPort 8080 | Select-Object OwningProcess` then stop that PID.
- **H2 data disappeared**: expected — the in-memory database resets on every restart.
