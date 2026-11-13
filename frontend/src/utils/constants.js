export const RECONCILIATION_STATUSES = [
  "MATCHED",
  "AMOUNT_MISMATCH",
  "DUPLICATE",
  "MISSING",
  "FAILED",
  "PENDING",
  "UNMATCHED",
];

export const PAYMENT_STATUSES = ["SUCCESS", "FAILED", "PENDING"];

export const ORDER_STATUSES = ["CREATED", "PAID", "CANCELLED"];

export const GATEWAYS = ["Razorpay", "Paytm", "Stripe", "PhonePe", "PayU", "Cashfree"];

export const PAGE_SIZE_OPTIONS = [10, 20, 50];

export const STATUS_LABELS = {
  MATCHED: "Matched",
  AMOUNT_MISMATCH: "Amount mismatch",
  DUPLICATE: "Duplicate",
  MISSING: "Missing",
  FAILED: "Failed",
  PENDING: "Pending",
  UNMATCHED: "Unmatched",
  SUCCESS: "Success",
  CREATED: "Created",
  PAID: "Paid",
  CANCELLED: "Cancelled",
  PROCESSING: "Processing",
  COMPLETED: "Completed",
  PARTIAL: "Partial",
};

export const STATUS_COLORS = {
  MATCHED: "#15803d",
  AMOUNT_MISMATCH: "#d97706",
  DUPLICATE: "#ea580c",
  MISSING: "#b91c1c",
  FAILED: "#dc2626",
  PENDING: "#0284c7",
  UNMATCHED: "#7c3aed",
  SUCCESS: "#15803d",
  CREATED: "#0284c7",
  PAID: "#15803d",
  CANCELLED: "#64748b",
  PROCESSING: "#0284c7",
  COMPLETED: "#15803d",
  PARTIAL: "#d97706",
};

export const CHART_PALETTE = [
  "#1f4fd8",
  "#7c3aed",
  "#0d9488",
  "#ea580c",
  "#d97706",
  "#dc2626",
  "#0284c7",
];

export function humanizeStatus(status) {
  if (!status) return "—";
  return STATUS_LABELS[status] ?? status.replaceAll("_", " ").toLowerCase();
}

export function pageRange(page, size, totalElements) {
  if (!totalElements) return "0 records";
  const start = page * size + 1;
  const end = Math.min(totalElements, (page + 1) * size);
  return `${start}–${end} of ${totalElements}`;
}
