import {
  AlertTriangle,
  ArrowLeftRight,
  ArrowRight,
  CheckCircle2,
  Percent,
  RefreshCw,
  UploadCloud,
  Wallet,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import ReconciliationChart from "../components/charts/ReconciliationChart.jsx";
import StatusPieChart from "../components/charts/StatusPieChart.jsx";
import DataTable from "../components/DataTable.jsx";
import Loader from "../components/Loader.jsx";
import StatusBadge from "../components/StatusBadge.jsx";
import SummaryCard from "../components/SummaryCard.jsx";
import reconciliationService from "../services/reconciliationService.js";
import reportService from "../services/reportService.js";
import { getErrorMessage } from "../utils/errors.js";
import { formatCurrency, formatNumber, formatSignedCurrency } from "../utils/formatCurrency.js";
import { formatDateTime } from "../utils/formatDate.js";

const RECENT_COLUMNS = [
  {
    key: "orderId",
    header: "Order",
    render: (row) => <span className="cell-strong mono">{row.orderId ?? "—"}</span>,
  },
  {
    key: "transactionId",
    header: "Transaction",
    render: (row) => <span className="mono">{row.transactionId ?? "—"}</span>,
  },
  { key: "gateway", header: "Gateway", render: (row) => row.gateway ?? "—" },
  {
    key: "difference",
    header: "Difference",
    align: "right",
    render: (row) => (
      <span className={Number(row.difference) === 0 ? "muted" : "negative"}>
        {formatSignedCurrency(row.difference)}
      </span>
    ),
  },
  {
    key: "status",
    header: "Status",
    render: (row) => <StatusBadge status={row.status} />,
  },
  {
    key: "reconciledAt",
    header: "Reconciled",
    render: (row) => <span className="muted">{formatDateTime(row.reconciledAt)}</span>,
  },
];

export default function Dashboard() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [recent, setRecent] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [running, setRunning] = useState(false);
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [summaryData, recentData] = await Promise.all([
        reportService.summary(),
        reconciliationService.list({ page: 0, size: 6, sortBy: "id", direction: "desc" }),
      ]);
      setSummary(summaryData);
      setRecent(recentData.content ?? []);
    } catch (err) {
      setError(getErrorMessage(err, "Could not load the dashboard."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const runReconciliation = async () => {
    setRunning(true);
    setNotice("");
    setError("");
    try {
      const result = await reconciliationService.run();
      setNotice(
        `Reconciliation finished: ${formatNumber(result.matched)} matched and ${formatNumber(
          result.totalRecords - result.matched
        )} issue(s) across ${formatNumber(result.totalRecords)} record(s).`
      );
      await load();
    } catch (err) {
      setError(getErrorMessage(err, "Reconciliation could not be run."));
    } finally {
      setRunning(false);
    }
  };

  if (loading) {
    return <Loader label="Loading dashboard…" />;
  }

  if (!summary) {
    return (
      <div className="card card-body padded">
        <div className="alert alert-error">{error || "The dashboard is unavailable."}</div>
        <button type="button" className="btn btn-secondary" style={{ marginTop: 14 }} onClick={load}>
          <RefreshCw size={15} />
          Try again
        </button>
      </div>
    );
  }

  const totalRecords = summary.totalReconciliationRecords ?? 0;
  const matchRate = totalRecords
    ? Math.round((summary.matchedRecords / totalRecords) * 100)
    : null;
  const gatewayData = (summary.gateways ?? []).map((gateway) => ({
    name: gateway.gateway,
    value: gateway.records,
  }));

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">Reconciliation overview</h1>
          <p className="page-description">
            {summary.lastReconciledAt
              ? `Last run ${formatDateTime(summary.lastReconciledAt)}. Review open issues before closing the period.`
              : "No reconciliation has been run yet. Import gateway data, then run the engine."}
          </p>
        </div>
        <div className="page-actions">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => navigate("/upload")}
            disabled={running}
          >
            <UploadCloud size={16} />
            Upload data
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={runReconciliation}
            disabled={running}
          >
            <RefreshCw size={16} className={running ? "spin" : undefined} />
            {running ? "Reconciling…" : "Run reconciliation"}
          </button>
        </div>
      </div>

      {error && (
        <div className="alert alert-error" style={{ marginBottom: 16 }}>
          <AlertTriangle size={16} />
          {error}
        </div>
      )}
      {notice && (
        <div className="alert alert-success" style={{ marginBottom: 16 }}>
          <CheckCircle2 size={16} />
          {notice}
        </div>
      )}

      <div className="grid grid-4">
        <SummaryCard
          label="Internal orders"
          value={formatNumber(summary.totalOrders)}
          hint={`${formatCurrency(summary.orderAmountTotal)} order value`}
          icon={ArrowLeftRight}
        />
        <SummaryCard
          label="Gateway transactions"
          value={formatNumber(summary.totalTransactions)}
          hint={`${formatCurrency(summary.transactionAmountTotal)} received`}
          icon={UploadCloud}
          tone="violet"
        />
        <SummaryCard
          label="Match rate"
          value={matchRate === null ? "—" : `${matchRate}%`}
          hint={
            totalRecords
              ? `${formatNumber(summary.matchedRecords)} of ${formatNumber(totalRecords)} records matched`
              : "Run reconciliation to calculate"
          }
          icon={Percent}
          tone="success"
        />
        <SummaryCard
          label="Amount at risk"
          value={formatCurrency(summary.amountAtRisk)}
          hint={`${formatNumber(summary.issueRecords)} open issue(s)`}
          icon={Wallet}
          tone="danger"
        />
      </div>

      <div className="grid grid-main section-gap">
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Records by reconciliation status</div>
              <div className="card-subtitle">Result of the most recent engine run</div>
            </div>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              onClick={() => navigate("/reconciliation")}
            >
              View results
              <ArrowRight size={14} />
            </button>
          </div>
          <ReconciliationChart statusCounts={summary.statusCounts} />
        </div>

        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Gateway mix</div>
              <div className="card-subtitle">Transactions per payment provider</div>
            </div>
          </div>
          <StatusPieChart data={gatewayData} unit="record" />
        </div>
      </div>

      <div className="card section-gap">
        <div className="card-header">
          <div>
            <div className="card-title">Recent reconciliation results</div>
            <div className="card-subtitle">Latest six classified records</div>
          </div>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => navigate("/reconciliation")}
          >
            Open reconciliation
            <ArrowRight size={14} />
          </button>
        </div>

        <DataTable
          columns={RECENT_COLUMNS}
          rows={recent}
          emptyTitle="No reconciliation results yet"
          emptyMessage="Upload a gateway CSV and run the reconciliation engine to see classified records here."
          emptyIcon={AlertTriangle}
          emptyAction={
            <button type="button" className="btn btn-primary btn-sm" onClick={runReconciliation}>
              <RefreshCw size={14} />
              Run reconciliation
            </button>
          }
        />
      </div>
    </>
  );
}
