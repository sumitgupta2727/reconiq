import {
  ArrowLeftRight,
  ClipboardList,
  FileText,
  GitCompare,
  LayoutDashboard,
  LogOut,
  Settings,
  UploadCloud,
} from "lucide-react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../hooks/useAuth.jsx";
import { initials } from "../utils/formatDate.js";

const WORKSPACE_LINKS = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/orders", label: "Orders", icon: ClipboardList },
  { to: "/transactions", label: "Transactions", icon: ArrowLeftRight },
  { to: "/reconciliation", label: "Reconciliation", icon: GitCompare },
  { to: "/upload", label: "Upload data", icon: UploadCloud },
];

const INSIGHT_LINKS = [
  { to: "/reports", label: "Reports", icon: FileText },
  { to: "/settings", label: "Settings", icon: Settings },
];

export default function Sidebar({ open = false, onNavigate }) {
  const { user, signOut } = useAuth();

  const linkClass = ({ isActive }) => `nav-link ${isActive ? "active" : ""}`.trim();

  return (
    <aside className={`sidebar ${open ? "open" : ""}`.trim()}>
      <div className="sidebar-brand">
        <span className="sidebar-logo" aria-hidden="true">
          R
        </span>
        <div>
          <div className="sidebar-brand-name">ReconIQ</div>
          <div className="sidebar-brand-tag">Reconciliation</div>
        </div>
      </div>

      <nav className="sidebar-section" aria-label="Main navigation">
        <div className="sidebar-section-label">Workspace</div>
        <ul className="nav-list">
          {WORKSPACE_LINKS.map(({ to, label, icon: Icon, end }) => (
            <li key={to}>
              <NavLink to={to} end={end} className={linkClass} onClick={onNavigate}>
                <Icon size={17} aria-hidden="true" />
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <nav className="sidebar-section" aria-label="Insights">
        <div className="sidebar-section-label">Insights</div>
        <ul className="nav-list">
          {INSIGHT_LINKS.map(({ to, label, icon: Icon }) => (
            <li key={to}>
              <NavLink to={to} className={linkClass} onClick={onNavigate}>
                <Icon size={17} aria-hidden="true" />
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <div className="sidebar-spacer" />

      <div className="sidebar-footer">
        <div className="sidebar-user">
          <span className="avatar dark">{initials(user?.name)}</span>
          <div style={{ minWidth: 0 }}>
            <div className="sidebar-user-name truncate">{user?.name ?? "Analyst"}</div>
            <div className="sidebar-user-role">{user?.role ?? "ANALYST"}</div>
          </div>
        </div>
        <button type="button" className="sidebar-logout" onClick={signOut}>
          <LogOut size={15} aria-hidden="true" />
          Sign out
        </button>
      </div>
    </aside>
  );
}
