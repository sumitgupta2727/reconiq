import { AlertTriangle, CheckCircle2, ClipboardList, Pencil, Plus, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import DataTable from "../components/DataTable.jsx";
import OrderFormModal from "../components/OrderFormModal.jsx";
import Pagination from "../components/Pagination.jsx";
import SearchFilterBar from "../components/SearchFilterBar.jsx";
import StatusBadge from "../components/StatusBadge.jsx";
import useDebounce from "../hooks/useDebounce.js";
import orderService from "../services/orderService.js";
import { GATEWAYS, ORDER_STATUSES } from "../utils/constants.js";
import { getErrorMessage } from "../utils/errors.js";
import { formatCurrency } from "../utils/formatCurrency.js";
import { formatDate } from "../utils/formatDate.js";

const EMPTY_FILTERS = { status: "", gateway: "" };

const COLUMNS = [
  {
    key: "orderId",
    header: "Order",
    render: (row) => (
      <>
        <span className="cell-strong mono">{row.orderId}</span>
        {row.customerName && <span className="cell-sub">{row.customerName}</span>}
      </>
    ),
  },
  { key: "gateway", header: "Gateway", render: (row) => row.gateway ?? "—" },
  {
    key: "orderAmount",
    header: "Amount",
    align: "right",
    render: (row) => <span className="cell-strong">{formatCurrency(row.orderAmount)}</span>,
  },
  {
    key: "orderDate",
    header: "Date",
    render: (row) => <span className="muted">{formatDate(row.orderDate)}</span>,
  },
  {
    key: "status",
    header: "Status",
    render: (row) => <StatusBadge status={row.status} />,
  },
];

export default function Orders() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebounce(search, 400);

  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sortBy, setSortBy] = useState("orderDate");
  const [direction, setDirection] = useState("desc");

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  const [gateways, setGateways] = useState(GATEWAYS);
  const [editing, setEditing] = useState(undefined);
  const [formBusy, setFormBusy] = useState(false);
  const [formError, setFormError] = useState("");

  const reload = useCallback(() => setRefreshKey((key) => key + 1), []);

  useEffect(() => {
    orderService
      .gateways()
      .then((list) => setGateways(Array.from(new Set([...GATEWAYS, ...(list ?? [])]))))
      .catch(() => {});
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");

    orderService
      .list({
        page,
        size,
        sortBy,
        direction,
        search: debouncedSearch.trim() || undefined,
        status: filters.status || undefined,
        gateway: filters.gateway || undefined,
      })
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err) => {
        if (!cancelled) setError(getErrorMessage(err, "Could not load orders."));
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

  const saveOrder = async (payload) => {
    setFormBusy(true);
    setFormError("");
    try {
      if (editing) {
        await orderService.update(editing.id, payload);
        setNotice(`Order ${payload.orderId} was updated.`);
      } else {
        await orderService.create(payload);
        setNotice(`Order ${payload.orderId} was added. Run reconciliation to include it in the next matching run.`);
      }
      setEditing(undefined);
      reload();
    } catch (err) {
      setFormError(getErrorMessage(err, "Could not save the order."));
    } finally {
      setFormBusy(false);
    }
  };

  const removeOrder = async (row) => {
    const confirmed = window.confirm(`Delete order ${row.orderId}? This cannot be undone.`);
    if (!confirmed) return;

    try {
      await orderService.remove(row.id);
      setNotice(`Order ${row.orderId} was deleted.`);
      reload();
    } catch (err) {
      setError(getErrorMessage(err, "Could not delete the order."));
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
            title="Edit order"
            aria-label={`Edit ${row.orderId}`}
            onClick={(event) => {
              event.stopPropagation();
              setFormError("");
              setEditing(row);
            }}
          >
            <Pencil size={14} />
          </button>
          <button
            type="button"
            className="page-btn"
            title="Delete order"
            aria-label={`Delete ${row.orderId}`}
            onClick={(event) => {
              event.stopPropagation();
              removeOrder(row);
            }}
          >
            <Trash2 size={14} />
          </button>
        </div>
      ),
    },
  ];

  const hasFilters = Boolean(search || filters.status || filters.gateway);

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">Orders</h1>
          <p className="page-description">
            {data
              ? `${data.totalElements.toLocaleString("en-IN")} internal order(s) ready for reconciliation.`
              : "The internal orders that gateway payments are matched against."}
          </p>
        </div>
        <div className="page-actions">
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              setFormError("");
              setEditing(null);
            }}
          >
            <Plus size={16} />
            Add order
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
        placeholder="Search order ID or customer…"
        showReset={hasFilters}
        onReset={resetFilters}
        busy={loading}
      >
        <select className="select filter-select" value={filters.status} onChange={changeFilter("status")}>
          <option value="">All statuses</option>
          {ORDER_STATUSES.map((status) => (
            <option key={status} value={status}>
              {status}
            </option>
          ))}
        </select>

        <select className="select filter-select" value={filters.gateway} onChange={changeFilter("gateway")}>
          <option value="">All gateways</option>
          {gateways.map((gateway) => (
            <option key={gateway} value={gateway}>
              {gateway}
            </option>
          ))}
        </select>

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
          <option value="orderDate:desc">Newest first</option>
          <option value="orderDate:asc">Oldest first</option>
          <option value="orderAmount:desc">Highest amount</option>
          <option value="orderAmount:asc">Lowest amount</option>
        </select>
      </SearchFilterBar>

      <div className="card section-gap">
        <DataTable
          columns={columns}
          rows={data?.content ?? []}
          loading={loading}
          emptyTitle={hasFilters ? "No matching orders" : "No orders yet"}
          emptyMessage={
            hasFilters
              ? "Adjust the search or filters to see more records."
              : "Upload an orders CSV or add an order manually to get started."
          }
          emptyIcon={ClipboardList}
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

      <OrderFormModal
        open={editing !== undefined}
        order={editing ?? null}
        onClose={() => setEditing(undefined)}
        onSubmit={saveOrder}
        busy={formBusy}
        serverError={formError}
      />
    </>
  );
}
