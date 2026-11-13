import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  GitCompare,
  RefreshCw,
  ShieldAlert,
  Wallet,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import DataTable from "../components/DataTable.jsx";
import Modal from "../components/Modal.jsx";
import Pagination from "../components/Pagination.jsx";
import SearchFilterBar from "../components/SearchFilterBar.jsx";
import StatusBadge from "../components/StatusBadge.jsx";
import SummaryCard from "../components/SummaryCard.jsx";
import useDebounce from "../hooks/useDebounce.js";
import reconciliationService from "../services/reconciliationService.js";
import reportService from "../services/reportService.js";
import { RECONCILIATION_STATUSES } from "../utils/constants.js";
import { getErrorMessage } from "../utils/errors.js";
import { formatCurrency, formatNumber, formatSignedCurrency } from "../utils/formatCurrency.js";
import { formatDateTime } from "../utils/formatDate.js";

const EMPTY_FILTERS = { gateway: "", status: "", from: "", to: "" };

function Detail({ label, value, span = false }) {
  return (
    <div className={`detail-item ${span ? "span-2" : ""}`.trim()}>
      <div className="detail-label">{label}</div>
      <div className="detail-value">{value ?? "—"}</div>
    </div>
  );
}

export default function Reconciliation() {
  const [summary, setSummary] = useState(null);
  const [summaryError, setSummaryError] = useState("");

  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 400);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [running, setRunning] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [selected, setSelected] = useState(null);
  const [gateways, setGateways] = useState([]);

  const loadSummary = useCallback(async () => {
    try {
      setSummary(await reportService.summary());
      setSummaryError("");
    } catch (err) {
      setSummaryError(getErrorMessage(err, "Could not load the reconciliation summary."));
    }
  }, []);

  useEffect(() => {
    loadSummary();
    reconciliationService
      .gateways()
      .then((list) => setGateways(list ?? []))
      .catch(() => {});
  }, [loadSummary]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");

    reconciliationService
      .list({
        page,
        size,
        sortBy: "id",
        direction: "desc",
        search: debouncedSearch.trim() || undefined,
        gateway: filters.gateway || undefined,
        status: filters.status || undefined,
        from: filters.from || undefined,
        to: filters.to || undefined,
      })
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err) => {
        if (!cancelled) setError(getErrorMessage(err, "Could not load reconciliation results."));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [page, size, debouncedSearch, filters, refreshKey]);

  const changeFilter = (field) => (event) => {
    setFilters((current) => ({ ...current, [field]: event.target.value }));
    setPage(0);
  };

  const resetFilters = () => {
    setSearch("");
    setFilters(EMPTY_FILTERS);
    setPage(0);
  };

  const run = async () => {
    setRunning(true);
    setError("");
    setNotice("");
    try {
      const result = await reconciliationService.run();
      setNotice(
        `Reconciliation complete in ${formatNumber(result.durationMs)} ms — ` +
          `${formatNumber(result.matched)} matched, ` +
          `${formatNumber(result.totalRecords - result.matched)} issue(s), ` +
          `${formatCurrency(result.discrepancyAmount)} discrepancy.`
      );
      await loadSummary();
      setRefreshKey((key) => key + 1);
      setPage(0);
    } catch (err) {
      setError(getErrorMessage(err, "Reconciliation could not be run."));
    } finally {
      setRunning(false);
    }
  };

  const columns = [
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
      key: "orderAmount",
      header: "Order amount",
      align: "right",
      render: (row) => formatCurrency(row.orderAmount),
    },
    {
      key: "transactionAmount",
      header: "Txn amount",
      align: "right",
      render: (row) => formatCurrency(row.transactionAmount),
    },
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

  const hasFilters = Boolean(search || filters.gateway || filters.status || filters.from || filters.to);
  const totalRecords = summary?.totalReconciliationRecords ?? 0;

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">Reconciliation</h1>
          <p className="page-description">
            A record matches only when the order ID links, the transaction ID is unique and the
            amounts are exactly equal. Re-running replaces the previous result set.
          </p>
        </div>
        <div className="page-actions">
          <button type="button" className="btn btn-primary" onClick={run} disabled={running}>
            <RefreshCw size={16} className={running ? "spin" : undefined} />
            {running ? "Reconciling…" : "Run reconciliation"}
          </button>
        </div>
      </div>

      {(error || summaryError) && (
        <div className="alert alert-error" style={{ marginBottom: 16 }}>
          <AlertTriangle size={16} />
          {error || summaryError}
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
          label="Matched"
          value={formatNumber(summary?.matchedRecords ?? 0)}
          hint={`of ${formatNumber(totalRecords)} classified record(s)`}
          icon={CheckCircle2}
          tone="success"
        />
        <SummaryCard
          label="Open issues"
          value={formatNumber(summary?.issueRecords ?? 0)}
          hint="Mismatch, duplicate, missing, failed or pending"
          icon={ShieldAlert}
          tone="warning"
        />
        <SummaryCard
          label="Amount at risk"
          value={formatCurrency(summary?.amountAtRisk ?? 0)}
          hint="Absolute difference on non-matched records"
          icon={Wallet}
          tone="danger"
        />
        <SummaryCard
          label="Last run"
          value={summary?.lastReconciledAt ? formatDateTime(summary.lastReconciledAt) : "Never"}
          hint="Every run replaces the previous result set"
          icon={Clock3}
        />
      </div>

      <div className="section-gap">
        <SearchFilterBar
          search={search}
          onSearchChange={(value) => {
            setSearch(value);
            setPage(0);
          }}
          placeholder="Search order or transaction ID…"
          showReset={hasFilters}
          onReset={resetFilters}
          busy={loading}
        >
          <select className="select filter-select" value={filters.gateway} onChange={changeFilter("gateway")}>
            <option value="">All gateways</option>
            {gateways.map((gateway) => (
              <option key={gateway} value={gateway}>
                {gateway}
              </option>
            ))}
          </select>

          <select className="select filter-select" value={filters.status} onChange={changeFilter("status")}>
            <option value="">All statuses</option>
            {RECONCILIATION_STATUSES.map((status) => (
              <option key={status} value={status}>
                {status.replaceAll("_", " ")}
              </option>
            ))}
          </select>

          <input
            type="date"
            className="input filter-date"
            value={filters.from}
            onChange={changeFilter("from")}
            aria-label="From date"
          />
          <input
            type="date"
            className="input filter-date"
            value={filters.to}
            onChange={changeFilter("to")}
            aria-label="To date"
          />
        </SearchFilterBar>
      </div>

      <div className="card section-gap">
        <DataTable
          columns={columns}
          rows={data?.content ?? []}
          loading={loading}
          onRowClick={(row) => setSelected(row)}
          emptyTitle={hasFilters ? "No matching results" : "No reconciliation results"}
          emptyMessage={
            hasFilters
              ? "Adjust the filters to see more records."
              : "Run the reconciliation engine to compare every order with its gateway payments."
          }
          emptyIcon={GitCompare}
          emptyAction={
            !hasFilters ? (
              <button type="button" className="btn btn-primary btn-sm" onClick={run} disabled={running}>
                <RefreshCw size={14} />
                Run reconciliation
              </button>
            ) : undefined
          }
        />

        {data && data.totalElements > 0 && (
          <Pagination
            page={data.page}
            size={data.size}
            totalElements={data.totalElements}
            totalPages={data.totalPages}
            onPageChange={setPage}
            onSizeChange={(nextSize) => {
              setSize(nextSize);
              setPage(0);
            }}
          />
        )}
      </div>

      <Modal
        open={Boolean(selected)}
        onClose={() => setSelected(null)}
        title="Reconciliation record"
        subtitle={selected ? `${selected.orderId ?? "—"} → ${selected.transactionId ?? "no payment"}` : undefined}
        footer={
          <button type="button" className="btn btn-secondary" onClick={() => setSelected(null)}>
            Close
          </button>
        }
      >
        {selected && (
          <div className="detail-grid">
            <Detail label="Order ID" value={<span className="mono">{selected.orderId ?? "—"}</span>} />
            <Detail
              label="Transaction ID"
              value={<span className="mono">{selected.transactionId ?? "—"}</span>}
            />
            <Detail label="Gateway" value={selected.gateway} />
            <Detail label="Status" value={<StatusBadge status={selected.status} />} />
            <Detail label="Order amount" value={formatCurrency(selected.orderAmount)} />
            <Detail label="Transaction amount" value={formatCurrency(selected.transactionAmount)} />
            <Detail
              label="Difference"
              value={
                <span className={Number(selected.difference) === 0 ? "" : "negative"}>
                  {formatSignedCurrency(selected.difference)}
                </span>
              }
            />
            <Detail label="Reconciled at" value={formatDateTime(selected.reconciledAt)} />
          </div>
        )}
      </Modal>
    </>
  );
}
