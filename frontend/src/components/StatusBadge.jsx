import { humanizeStatus } from "../utils/constants.js";

export default function StatusBadge({ status, dot = true }) {
  if (!status) return <span className="muted">—</span>;
  return (
    <span className={`badge badge-${status.toLowerCase()}`}>
      {dot && <span className="badge-dot" aria-hidden="true" />}
      {humanizeStatus(status)}
    </span>
  );
}
