import {
  AlertTriangle,
  CheckCircle2,
  Download,
  FileSpreadsheet,
  FileText,
  Mail,
  Percent,
  RefreshCw,
  ShieldAlert,
  Wallet,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import ReconciliationChart from "../components/charts/ReconciliationChart.jsx";
import StatusPieChart from "../components/charts/StatusPieChart.jsx";
import DataTable from "../components/DataTable.jsx";
import Loader from "../components/Loader.jsx";
import StatusBadge from "../components/StatusBadge.jsx";
import SummaryCard from "../components/SummaryCard.jsx";
import reportService from "../services/reportService.js";
import { RECONCILIATION_STATUSES } from "../utils/constants.js";
import { downloadBlob } from "../utils/download.js";
import { getErrorMessage } from "../utils/errors.js";
import {
  formatCurrency,
  formatNumber,
  formatSignedCurrency,
} from "../utils/formatCurrency.js";
import { formatDateTime } from "../utils/formatDate.js";

const STATUS_COLUMNS = [
  {
    key: "status",
    header: "Status",
    render: (row) => <StatusBadge status={row.status} />,
  },
  {
    key: "count",
    header: "Records",
    align: "right",
    render: (row) => formatNumber(row.count),
  },
  {
    key: "difference",
    header: "Total difference",
    align: "right",
    render: (row) => (
      <span className={Number(row.difference) === 0 ? "muted" : "negative"}>
        {formatSignedCurrency(row.difference)}
      </span>
    ),
  },
];

const GATEWAY_COLUMNS = [
  { key: "gateway", header: "Gateway", render: (row) => <span className="cell-strong">{row.gateway}</span> },
  { key: "records", header: "Records", align: "right", render: (row) => formatNumber(row.records) },
  {
    key: "matched",
    header: "Matched",
    align: "right",
    render: (row) => <span className="positive">{formatNumber(row.matched)}</span>,
  },
  {
    key: "issues",
    header: "Issues",
    align: "right",
    render: (row) => (
      <span className={row.issues > 0 ? "negative" : "muted"}>{formatNumber(row.issues)}</span>
    ),
  },
  {
    key: "difference",
    header: "Difference",
    align: "right",
    render: (row) => formatSignedCurrency(row.difference),
  },
];

export default function Reports() {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const [csvBusy, setCsvBusy] = useState(false);
  const [pdfBusy, setPdfBusy] = useState(false);

  const [emailConfig, setEmailConfig] = useState({ enabled: false, defaultRecipient: "" });
  const [emailTo, setEmailTo] = useState("");
  const [emailBusy, setEmailBusy] = useState(false);
  const [emailError, setEmailError] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [summaryData, config] = await Promise.all([
        reportService.summary(),
        reportService.emailConfig().catch(() => ({ enabled: false, defaultRecipient: "" })),
      ]);
      setSummary(summaryData);
      setEmailConfig(config);
      setEmailTo((current) => current || config.defaultRecipient || "");
    } catch (err) {
      setError(getErrorMessage(err, "Could not load the report summary."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const exportCsv = async () => {
    setCsvBusy(true);
    setError("");
    setNotice("");
    try {
      const { blob, fileName } = await reportService.exportCsv();
      downloadBlob(blob, fileName);
      setNotice(`CSV report downloaded as ${fileName}.`);
    } catch (err) {
      setError(getErrorMessage(err, "The CSV report could not be downloaded."));
    } finally {
      setCsvBusy(false);
    }
  };

  const exportPdf = async () => {
    setPdfBusy(true);
    setError("");
    setNotice("");
    try {
      const { blob, fileName } = await reportService.exportPdf();
      downloadBlob(blob, fileName);
      setNotice(`PDF report downloaded as ${fileName}.`);
    } catch (err) {
      setError(getErrorMessage(err, "The PDF report could not be generated."));
    } finally {
      setPdfBusy(false);
    }
  };

  const sendEmail = async (event) => {
    event.preventDefault();
    setEmailBusy(true);
    setEmailError("");
    setNotice("");
    try {
      const result = await reportService.emailReport(emailTo.trim() || undefined);
      setNotice(`Report emailed to ${result.to}.`);
    } catch (err) {
      setEmailError(getErrorMessage(err, "The report could not be emailed."));
    } finally {
      setEmailBusy(false);
    }
  };

  if (loading) {
    return <Loader label="Preparing report…" />;
  }

  if (!summary) {
    return (
      <div className="card card-body padded">
        <div className="alert alert-error">{error || "The report summary is unavailable."}</div>
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

  const statusRows = RECONCILIATION_STATUSES.map((status) => ({
    status,
    count: summary.statusCounts?.[status] ?? 0,
    difference: summary.statusDifferences?.[status] ?? 0,
  }));

  const gatewayData = (summary.gateways ?? []).map((gateway) => ({
    name: gateway.gateway,
    value: gateway.records,
  }));

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">Reports</h1>
          <p className="page-description">
            Consolidated reconciliation position for {summary.generatedAt
              ? formatDateTime(summary.generatedAt)
              : "today"}
            . Export a CSV for the ledger or a PDF for management review.
          </p>
        </div>
        <div className="page-actions">
          <button type="button" className="btn btn-secondary" onClick={exportCsv} disabled={csvBusy}>
            <FileSpreadsheet size={16} />
            {csvBusy ? "Preparing…" : "Export CSV"}
          </button>
          <button type="button" className="btn btn-primary" onClick={exportPdf} disabled={pdfBusy}>
            <FileText size={16} />
            {pdfBusy ? "Generating…" : "Export PDF"}
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
          label="Classified records"
          value={formatNumber(totalRecords)}
          hint={`${formatNumber(summary.totalOrders)} orders · ${formatNumber(
            summary.totalTransactions
          )} transactions`}
          icon={FileText}
        />
        <SummaryCard
          label="Match rate"
          value={matchRate === null ? "—" : `${matchRate}%`}
          hint={`${formatNumber(summary.matchedRecords)} matched record(s)`}
          icon={Percent}
          tone="success"
        />
        <SummaryCard
          label="Open issues"
          value={formatNumber(summary.issueRecords)}
          hint="Require finance team review"
          icon={ShieldAlert}
          tone="warning"
        />
        <SummaryCard
          label="Amount at risk"
          value={formatCurrency(summary.amountAtRisk)}
          hint={`Net difference ${formatSignedCurrency(summary.netDifference)}`}
          icon={Wallet}
          tone="danger"
        />
      </div>

      <div className="grid grid-main section-gap">
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Status distribution</div>
              <div className="card-subtitle">Classified records by reconciliation outcome</div>
            </div>
          </div>
          <ReconciliationChart statusCounts={summary.statusCounts} />
        </div>

        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Gateway mix</div>
              <div className="card-subtitle">Where the payments came from</div>
            </div>
          </div>
          <StatusPieChart data={gatewayData} unit="record" />
        </div>
      </div>

      <div className="grid grid-2 section-gap">
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Breakdown by status</div>
              <div className="card-subtitle">Counts and signed differences</div>
            </div>
          </div>
          <DataTable columns={STATUS_COLUMNS} rows={statusRows} rowKey="status" />
        </div>

        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Breakdown by gateway</div>
              <div className="card-subtitle">Matched versus open issues per provider</div>
            </div>
          </div>
          <DataTable
            columns={GATEWAY_COLUMNS}
            rows={summary.gateways ?? []}
            rowKey="gateway"
            emptyTitle="No gateway data"
            emptyMessage="Run a reconciliation to populate the gateway breakdown."
          />
        </div>
      </div>

      <div className="card section-gap">
        <div className="card-header">
          <div>
            <div className="card-title">Email the PDF report</div>
            <div className="card-subtitle">
              Delivers the same PDF generated by the export button, with a summary in the email body
            </div>
          </div>
          <Mail size={18} className="muted" />
        </div>
        <div className="card-body">
          {!emailConfig.enabled ? (
            <div className="alert alert-info">
              <Mail size={16} />
              <span>
                Email delivery is disabled. Set <code>REPORT_EMAIL_ENABLED=true</code> and the{" "}
                <code>SPRING_MAIL_*</code> variables on the backend to enable it.
              </span>
            </div>
          ) : (
            <form onSubmit={sendEmail} className="filter-bar" style={{ boxShadow: "none" }}>
              <input
                type="email"
                className="input filter-search"
                value={emailTo}
                onChange={(event) => setEmailTo(event.target.value)}
                placeholder="finance@example.com"
                aria-label="Recipient email"
                required
              />
              <button type="submit" className="btn btn-primary" disabled={emailBusy}>
                <Mail size={16} />
                {emailBusy ? "Sending…" : "Send report"}
              </button>
            </form>
          )}

          {emailError && (
            <div className="alert alert-error" style={{ marginTop: 12 }}>
              <AlertTriangle size={16} />
              {emailError}
            </div>
          )}
        </div>
      </div>

      <p className="tiny muted" style={{ marginTop: 16, display: "flex", alignItems: "center", gap: 6 }}>
        <Download size={13} />
        Exports reflect the latest reconciliation run. Re-run reconciliation after new uploads for
        up-to-date figures.
      </p>
    </>
  );
}
