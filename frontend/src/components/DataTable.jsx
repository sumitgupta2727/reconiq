import { Inbox } from "lucide-react";
import Loader from "./Loader.jsx";

/**
 * Small table primitive. Column definition:
 * { key, header, render?(row), align?, className? }
 */
export default function DataTable({
  columns,
  rows,
  rowKey = "id",
  onRowClick,
  loading = false,
  loadingLabel = "Loading records…",
  emptyTitle = "Nothing here yet",
  emptyMessage,
  emptyIcon = Inbox,
  emptyAction,
}) {
  if (loading) return <Loader label={loadingLabel} />;

  if (!rows?.length) {
    return (
      <div className="empty-state">
        <span className="empty-icon">
          {(() => {
            const Icon = emptyIcon;
            return <Icon size={24} aria-hidden="true" />;
          })()}
        </span>
        <p className="empty-title">{emptyTitle}</p>
        {emptyMessage && <p className="empty-message">{emptyMessage}</p>}
        {emptyAction}
      </div>
    );
  }

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                className={column.align ? `align-${column.align}` : undefined}
                style={column.width ? { width: column.width } : undefined}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row[rowKey]}
              className={onRowClick ? "clickable" : undefined}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
            >
              {columns.map((column) => (
                <td
                  key={column.key}
                  className={[column.align ? `align-${column.align}` : "", column.className ?? ""]
                    .filter(Boolean)
                    .join(" ")}
                >
                  {column.render ? column.render(row) : row[column.key] ?? "—"}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
