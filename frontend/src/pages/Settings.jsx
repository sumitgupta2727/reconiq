import {
  CheckCircle2,
  Database,
  KeyRound,
  LogOut,
  RefreshCw,
  Server,
  ShieldCheck,
  User,
  XCircle,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "../hooks/useAuth.jsx";
import { API_BASE_URL } from "../services/api.js";
import healthService from "../services/healthService.js";
import { getErrorMessage } from "../utils/errors.js";
import { formatDateTime, initials } from "../utils/formatDate.js";

function Row({ label, value }) {
  return (
    <div className="kv">
      <span className="kv-key">{label}</span>
      <span className="kv-value">{value ?? "—"}</span>
    </div>
  );
}

export default function Settings() {
  const { user, signOut } = useAuth();
  const [health, setHealth] = useState({ state: "checking", data: null, error: "", checkedAt: null });

  const checkHealth = useCallback(async () => {
    setHealth((current) => ({ ...current, state: "checking" }));
    try {
      const data = await healthService.check();
      setHealth({ state: "up", data, error: "", checkedAt: new Date() });
    } catch (err) {
      setHealth({
        state: "down",
        data: null,
        error: getErrorMessage(err, "Backend is not reachable."),
        checkedAt: new Date(),
      });
    }
  }, []);

  useEffect(() => {
    checkHealth();
  }, [checkHealth]);

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">Settings</h1>
          <p className="page-description">
            Your profile, the health of the ReconIQ API and the security posture of this workspace.
          </p>
        </div>
        <div className="page-actions">
          <button type="button" className="btn btn-secondary" onClick={checkHealth}>
            <RefreshCw size={16} />
            Recheck API
          </button>
          <button type="button" className="btn btn-danger" onClick={signOut}>
            <LogOut size={16} />
            Sign out
          </button>
        </div>
      </div>

      <div className="grid grid-2">
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Profile</div>
              <div className="card-subtitle">Signed in with an employee ID</div>
            </div>
            <User size={18} className="muted" />
          </div>
          <div className="card-body">
            <div style={{ display: "flex", alignItems: "center", gap: 14, marginBottom: 12 }}>
              <span className="avatar dark" style={{ width: 48, height: 48, fontSize: "1rem" }}>
                {initials(user?.name)}
              </span>
              <div>
                <div className="strong" style={{ fontSize: "1rem" }}>
                  {user?.name ?? "Analyst"}
                </div>
                <div className="small muted">{user?.role ?? "ANALYST"}</div>
              </div>
            </div>

            <Row label="Employee ID" value={user?.employeeId} />
            <Row label="Email" value={user?.email} />
            <Row label="Role" value={user?.role} />
            <Row label="Session" value="Stored in this browser (localStorage)" />
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">System status</div>
              <div className="card-subtitle">Live check of the REST API</div>
            </div>
            <Server size={18} className="muted" />
          </div>
          <div className="card-body">
            <div className="status-line" style={{ marginBottom: 10 }}>
              {health.state === "checking" && <span className="spinner sm" />}
              {health.state === "up" && <span className="status-dot" />}
              {health.state === "down" && <span className="status-dot down" />}
              <span>
                {health.state === "checking"
                  ? "Checking the API…"
                  : health.state === "up"
                    ? "API is online and responding"
                    : "API is not reachable"}
              </span>
            </div>

            {health.state === "down" && <div className="alert alert-error">{health.error}</div>}

            <div style={{ marginTop: 8 }}>
              <Row label="Service" value={health.data?.service ?? "—"} />
              <Row label="Version" value={health.data?.version ?? "—"} />
              <Row
                label="API base URL"
                value={<span className="mono small">{API_BASE_URL}</span>}
              />
              <Row
                label="Last checked"
                value={health.checkedAt ? formatDateTime(health.checkedAt) : "—"}
              />
            </div>
          </div>
        </div>
      </div>

      <div className="card section-gap">
        <div className="card-header">
          <div>
            <div className="card-title">Security and deployment notes</div>
            <div className="card-subtitle">What this build does and does not protect</div>
          </div>
          <ShieldCheck size={18} className="muted" />
        </div>
        <div className="card-body">
          <div className="stack" style={{ gap: 12 }}>
            <div className="alert alert-success">
              <CheckCircle2 size={16} />
              <span>
                Passwords are hashed with BCrypt. Database credentials are supplied through
                environment variables, never committed to the repository.
              </span>
            </div>
            <div className="alert alert-info">
              <Database size={16} />
              <span>
                Demo accounts and sample records are seeded only when the database is empty. Disable
                seeding with <code>SEED_ENABLED=false</code> for a production deployment.
              </span>
            </div>
            <div className="alert alert-warning">
              <KeyRound size={16} />
              <span>
                Authentication is session-based in the browser and API calls are not yet protected by
                JWTs. Add Spring Security plus JWT before exposing this API publicly. See the README
                roadmap.
              </span>
            </div>
            <div className="alert alert-error">
              <XCircle size={16} />
              <span>
                Change <code>SEED_ADMIN_PASSWORD</code> and the MySQL password before deploying. The
                default demo credentials are public.
              </span>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
