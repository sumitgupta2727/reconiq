export default function Loader({ label = "Loading…", inline = false }) {
  if (inline) {
    return (
      <span className="status-line">
        <span className="spinner sm" aria-hidden="true" />
        {label && <span>{label}</span>}
      </span>
    );
  }

  return (
    <div className="loader" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      {label && <span>{label}</span>}
    </div>
  );
}
