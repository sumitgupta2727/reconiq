import { AlertTriangle, ArrowLeftRight, CheckCircle2, Eye, Plus, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import DataTable from "../components/DataTable.jsx";
import Pagination from "../components/Pagination.jsx";
import SearchFilterBar from "../components/SearchFilterBar.jsx";
import StatusBadge from "../components/StatusBadge.jsx";
import TransactionDetailsModal from "../components/TransactionDetailsModal.jsx";
import TransactionFormModal from "../components/TransactionFormModal.jsx";
import useDebounce from "../hooks/useDebounce.js";
import transactionService from "../services/transactionService.js";
import { GATEWAYS, PAYMENT_STATUSES } from "../utils/constants.js";
import { getErrorMessage } from "../utils/errors.js";
import { formatCurrency } from "../utils/formatCurrency.js";
import { formatDateTime } from "../utils/formatDate.js";

const EMPTY_FILTERS = { gateway: "", status: "", from: "", to: "" };

const COLUMNS = [
  {
    key: "transactionId",
    header: "Transaction",
    render: (row) => (
      <>
        <span className="cell-strong mono">{row.transactionId}</span>
        {row.customerName && <span className="cell-sub">{row.customerName}</span>}
      </>
    ),
  },
  {
    key: "orderId",
    header: "Order",
    render: (row) => <span className="mono">{row.orderId || "—"}</span>,
  },
  { key: "gateway", header: "Gateway" },
  {
    key: "transactionAmount",
    header: "Amount",
    align: "right",
    render: (row) => <span className="cell-strong">{formatCurrency(row.transactionAmount)}</span>,
  },
  {
    key: "transactionDate",
    header: "Date",
    render: (row) => <span className="muted">{formatDateTime(row.transactionDate)}</span>,
  },
  {
    key: "paymentStatus",
    header: "Status",
    render: (row) => <StatusBadge status={row.paymentStatus} />,
  },
];

export default function Transactions() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 400);

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sortBy, setSortBy] = useState("transactionDate");
  const [direction, setDirection] = useState("desc");

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  const [gateways, setGateways] = useState(GATEWAYS);
  const [selected, setSelected] = useState(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formBusy, setFormBusy] = useState(false);
  const [formError, setFormError] = useState("");

  const reload = useCallback(() => setRefreshKey((key) => key + 1), []);

  useEffect(() => {
    transactionService
      .gateways()
      .then((list) => setGateways(Array.from(new Set([...GATEWAYS, ...(list ?? [])]))))
      .catch(() => {});
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");

    transactionService
      .list({
        page,
        size,
        sortBy,
        direction,
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
        if (!cancelled) setError(getErrorMessage(err, "Could not load transactions."));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [page, size, sortBy, direction, debouncedSearch, filters, refreshKey]);

  const changeFilter = (field) => (event) => {
    setFilters((current) => ({ ...current, [field]: event.target.value }));
    setPage(0);
    setNotice("");
  };

  const resetFilters = () => {
    setSearch("");
    setFilters(EMPTY_FILTERS);
    setPage(0);
  };

  const createTransaction = async (payload) => {
    setFormBusy(true);
    setFormError("");
    try {
      await transactionService.create(payload);
      setFormOpen(false);
      setNotice("Transaction added. Run reconciliation to include it in the next matching run.");
      reload();
    } catch (err) {
      setFormError(getErrorMessage(err, "Could not save the transaction."));
    } finally {
      setFormBusy(false);
    }
  };

  const removeTransaction = async (row) => {
    const confirmed = window.confirm(
      `Delete transaction ${row.transactionId}? This cannot be undone.`
    );
    if (!confirmed) return;

    try {
      await transactionService.remove(row.id);
      setNotice(`Transaction ${row.transactionId} was deleted.`);
      reload();
    } catch (err) {
      setError(getErrorMessage(err, "Could not delete the transaction."));
    }
  };

  const columns = [
    ...COLUMNS,
    {
      key: "actions",
      header: "",
      align: "right",
      width: 92,
      render: (row) => (
        <div className="table-actions">
          <button
            type="button"
            className="page-btn"
            title="View details"
            aria-label={`View ${row.transactionId}`}
            onClick={(event) => {
              event.stopPropagation();
              setSelected(row);
            }}
          >
            <Eye size={14} />
          </button>
          <button
            type="button"
            className="page-btn"
            title="Delete transaction"
            aria-label={`Delete ${row.transactionId}`}
            onClick={(event) => {
              event.stopPropagation();
              removeTransaction(row);
            }}
          >
            <Trash2 size={14} />
          </button>
        </div>
      ),
    },
  ];

  const hasFilters = Boolean(
    search || filters.gateway || filters.status || filters.from || filters.to
  );

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">Transactions</h1>
          <p className="page-description">
            {data
              ? `${data.totalElements.toLocaleString("en-IN")} gateway record(s) available for reconciliation.`
              : "Search, filter and review every gateway payment stored in ReconIQ."}
          </p>
        </div>
        <div className="page-actions">
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              setFormError("");
              setFormOpen(true);
            }}
          >
            <Plus size={16} />
            Add transaction
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

      <SearchFilterBar
        search={search}
        onSearchChange={(value) => {
          setSearch(value);
          setPage(0);
          setNotice("");
        }}
        placeholder="Search transaction, order or customer…"
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
          {PAYMENT_STATUSES.map((status) => (
            <option key={status} value={status}>
              {status}
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

        <select
          className="select filter-select"
          value={`${sortBy}:${direction}`}
          onChange={(event) => {
            const [field, order] = event.target.value.split(":");
            setSortBy(field);
            setDirection(order);
            setPage(0);
          }}
          aria-label="Sort order"
        >
          <option value="transactionDate:desc">Newest first</option>
          <option value="transactionDate:asc">Oldest first</option>
          <option value="transactionAmount:desc">Highest amount</option>
          <option value="transactionAmount:asc">Lowest amount</option>
        </select>
      </SearchFilterBar>

      <div className="card section-gap">
        <DataTable
          columns={columns}
          rows={data?.content ?? []}
          loading={loading}
          onRowClick={(row) => setSelected(row)}
          emptyTitle={hasFilters ? "No matching transactions" : "No transactions yet"}
          emptyMessage={
            hasFilters
              ? "Adjust the search or filters to see more records."
              : "Upload a gateway CSV or add a transaction manually to get started."
          }
          emptyIcon={ArrowLeftRight}
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

      <TransactionDetailsModal
        open={Boolean(selected)}
        transaction={selected}
        onClose={() => setSelected(null)}
      />

      <TransactionFormModal
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSubmit={createTransaction}
        busy={formBusy}
        serverError={formError}
      />
    </>
  );
}
