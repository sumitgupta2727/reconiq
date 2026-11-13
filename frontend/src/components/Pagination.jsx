import { ChevronLeft, ChevronRight } from "lucide-react";
import { PAGE_SIZE_OPTIONS, pageRange } from "../utils/constants.js";

function buildPages(current, total) {
  if (total <= 7) return Array.from({ length: total }, (_, index) => index);
  const pages = new Set([0, total - 1, current, current - 1, current + 1]);
  if (current <= 2) [1, 2, 3].forEach((page) => pages.add(page));
  if (current >= total - 3) [total - 2, total - 3, total - 4].forEach((page) => pages.add(page));
  const sorted = [...pages].filter((page) => page >= 0 && page < total).sort((a, b) => a - b);

  const withGaps = [];
  sorted.forEach((page, index) => {
    if (index > 0 && page - sorted[index - 1] > 1) withGaps.push(`gap-${page}`);
    withGaps.push(page);
  });
  return withGaps;
}

export default function Pagination({
  page = 0,
  size = 20,
  totalElements = 0,
  totalPages = 0,
  onPageChange,
  onSizeChange,
}) {
  if (!totalElements) return null;

  return (
    <div className="pagination">
      <span className="pagination-info">{pageRange(page, size, totalElements)}</span>

      <div className="pagination-controls">
        {onSizeChange && (
          <select
            className="page-size"
            value={size}
            onChange={(event) => onSizeChange(Number(event.target.value))}
            aria-label="Records per page"
          >
            {PAGE_SIZE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option} / page
              </option>
            ))}
          </select>
        )}

        <button
          type="button"
          className="page-btn"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0}
          aria-label="Previous page"
        >
          <ChevronLeft size={15} />
        </button>

        {buildPages(page, Math.max(totalPages, 1)).map((item) =>
          typeof item === "number" ? (
            <button
              type="button"
              key={item}
              className={`page-btn ${item === page ? "active" : ""}`.trim()}
              onClick={() => onPageChange(item)}
              aria-current={item === page ? "page" : undefined}
            >
              {item + 1}
            </button>
          ) : (
            <span key={item} className="pagination-info">
              …
            </span>
          )
        )}

        <button
          type="button"
          className="page-btn"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          aria-label="Next page"
        >
          <ChevronRight size={15} />
        </button>
      </div>
    </div>
  );
}
