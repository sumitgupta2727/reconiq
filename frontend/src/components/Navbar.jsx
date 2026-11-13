import { CalendarDays, LogOut, Menu } from "lucide-react";
import { useAuth } from "../hooks/useAuth.jsx";
import { initials } from "../utils/formatDate.js";

const today = new Intl.DateTimeFormat("en-IN", {
  weekday: "short",
  day: "numeric",
  month: "short",
});

export default function Navbar({ title, subtitle, onMenu }) {
  const { user, signOut } = useAuth();

  return (
    <header className="navbar">
      <div className="navbar-left">
        <button
          type="button"
          className="icon-btn menu-btn"
          onClick={onMenu}
          aria-label="Open navigation"
        >
          <Menu size={19} />
        </button>
        <div style={{ minWidth: 0 }}>
          <div className="navbar-title">{title}</div>
          {subtitle && <div className="navbar-subtitle">{subtitle}</div>}
        </div>
      </div>

      <div className="navbar-actions">
        <span className="chip hide-sm">
          <CalendarDays size={14} aria-hidden="true" />
          {today.format(new Date())}
        </span>

        <div className="user-chip">
          <div style={{ textAlign: "right" }}>
            <div className="user-chip-name">{user?.name ?? "Analyst"}</div>
            <div className="user-chip-role">{user?.employeeId ?? ""}</div>
          </div>
          <span className="avatar">{initials(user?.name)}</span>
        </div>

        <button type="button" className="icon-btn" onClick={signOut} title="Sign out" aria-label="Sign out">
          <LogOut size={17} />
        </button>
      </div>
    </header>
  );
}
