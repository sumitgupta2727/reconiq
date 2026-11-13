import { RotateCcw, Search } from "lucide-react";

export default function SearchFilterBar({
  search,
  onSearchChange,
  placeholder = "Search…",
  onReset,
  showReset = false,
  busy = false,
  children,
}) {
  return (
    <div className="filter-bar">
      <div className="input-with-icon filter-search">
        <Search size={16} aria-hidden="true" />
        <input
          className="input"
          type="search"
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder={placeholder}
          aria-label={placeholder}
        />
      </div>

      {children}

      {showReset && (
        <button type="button" className="btn btn-ghost btn-sm" onClick={onReset}>
          <RotateCcw size={14} />
          Reset
        </button>
      )}

      {busy && <span className="spinner sm" aria-label="Loading" />}
    </div>
  );
}
