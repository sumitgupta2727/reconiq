import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import Navbar from "../components/Navbar.jsx";
import Sidebar from "../components/Sidebar.jsx";

const PAGE_META = {
  "/": {
    title: "Dashboard",
    subtitle: "Reconciliation health at a glance",
  },
  "/transactions": {
    title: "Transactions",
    subtitle: "Gateway payments received and stored in ReconIQ",
  },
  "/reconciliation": {
    title: "Reconciliation",
    subtitle: "Order-to-payment matching and discrepancy review",
  },
  "/upload": {
    title: "Upload data",
    subtitle: "Import orders and gateway transaction CSVs",
  },
  "/reports": {
    title: "Reports",
    subtitle: "Summaries, exports and report delivery",
  },
  "/settings": {
    title: "Settings",
    subtitle: "Profile, system status and workspace information",
  },
};

export default function MainLayout() {
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  const meta = PAGE_META[location.pathname] ?? {
    title: "ReconIQ",
    subtitle: "Smart payment reconciliation",
  };

  return (
    <div className="app-shell">
      <Sidebar open={mobileOpen} onNavigate={() => setMobileOpen(false)} />
      {mobileOpen && (
        <div
          className="sidebar-overlay"
          role="presentation"
          onClick={() => setMobileOpen(false)}
        />
      )}

      <div className="main-area">
        <Navbar title={meta.title} subtitle={meta.subtitle} onMenu={() => setMobileOpen(true)} />
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
