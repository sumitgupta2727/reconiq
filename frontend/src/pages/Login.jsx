import { Eye, EyeOff, GitCompare, ShieldCheck, Sparkles, FileText } from "lucide-react";
import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth.jsx";
import { getErrorMessage } from "../utils/errors.js";

export default function Login() {
  const { isAuthenticated, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [employeeId, setEmployeeId] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const from = location.state?.from || "/";

  if (isAuthenticated) {
    return <Navigate to={from} replace />;
  }

  const submit = async (event) => {
    event.preventDefault();
    setError("");

    if (!employeeId.trim() || !password) {
      setError("Enter your employee ID and password.");
      return;
    }

    setBusy(true);
    try {
      await signIn(employeeId.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(getErrorMessage(err, "Login failed. Check your credentials."));
    } finally {
      setBusy(false);
    }
  };

  const fillDemoAccount = () => {
    setEmployeeId("ADMIN001");
    setPassword("admin123");
    setError("");
  };

  return (
    <div className="login-page">
      <section className="login-visual">
        <div className="login-visual-content">
          <div className="login-brand">
            <span className="sidebar-logo">R</span>
            <div>
              <div className="login-brand-name">ReconIQ</div>
              <div className="login-brand-tag">Smart reconciliation</div>
            </div>
          </div>

          <h1 className="login-headline">
            Every payment, <span>accounted for.</span>
          </h1>
          <p className="login-lede">
            Match internal orders against payment gateway statements, surface mismatches,
            duplicates and missing payments — before they reach your books.
          </p>

          <div className="login-features">
            <div className="login-feature">
              <span className="login-feature-icon">
                <GitCompare size={15} />
              </span>
              One documented matching rule for every gateway
            </div>
            <div className="login-feature">
              <span className="login-feature-icon">
                <Sparkles size={15} />
              </span>
              Automatic detection of seven reconciliation statuses
            </div>
            <div className="login-feature">
              <span className="login-feature-icon">
                <FileText size={15} />
              </span>
              CSV and PDF reports for month-end close
            </div>
            <div className="login-feature">
              <span className="login-feature-icon">
                <ShieldCheck size={15} />
              </span>
              BCrypt-hashed credentials, audit-ready batches
            </div>
          </div>
        </div>

        <div className="login-visual-footer">
          <ShieldCheck size={14} />
          ReconIQ 1.0 · Built with React, Spring Boot and MySQL
        </div>
      </section>

      <section className="login-panel">
        <div className="login-card">
          <div className="login-card-brand">
            <span className="sidebar-logo" style={{ width: 38, height: 38 }}>
              R
            </span>
            <span className="login-brand-name" style={{ color: "var(--ink)" }}>
              ReconIQ
            </span>
          </div>

          <h2 className="login-title">Sign in to your workspace</h2>
          <p className="login-subtitle">
            Use the employee ID issued by your finance administrator.
          </p>

          {error && (
            <div className="alert alert-error" style={{ marginBottom: 16 }} role="alert">
              {error}
            </div>
          )}

          <form className="form-grid" onSubmit={submit} noValidate>
            <div className="form-group">
              <label className="label" htmlFor="employeeId">
                Employee ID
              </label>
              <input
                id="employeeId"
                className="input"
                value={employeeId}
                onChange={(event) => setEmployeeId(event.target.value)}
                placeholder="ADMIN001"
                autoComplete="username"
                autoFocus
              />
            </div>

            <div className="form-group">
              <label className="label" htmlFor="password">
                Password
              </label>
              <div className="input-with-icon input-with-action" style={{ position: "relative" }}>
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  className="input"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="Enter your password"
                  autoComplete="current-password"
                  style={{ paddingLeft: 12 }}
                />
                <button
                  type="button"
                  className="input-action"
                  onClick={() => setShowPassword((current) => !current)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
              {busy ? "Signing in…" : "Sign in"}
            </button>
          </form>

          <div className="login-demo">
            <strong>Demo account</strong> — employee ID <code>ADMIN001</code>, password{" "}
            <code>admin123</code>.{" "}
            <button
              type="button"
              onClick={fillDemoAccount}
              style={{
                border: 0,
                background: "none",
                color: "var(--brand)",
                fontWeight: 700,
                padding: 0,
              }}
            >
              Fill it in
            </button>
          </div>

          <p className="login-footer">
            Access is restricted to authorised finance users. Demo credentials are for local
            development only.
          </p>
        </div>
      </section>
    </div>
  );
}
