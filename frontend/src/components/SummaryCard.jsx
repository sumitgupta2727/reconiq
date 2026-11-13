export default function SummaryCard({ label, value, hint, icon: Icon, tone = "" }) {
  return (
    <div className={`summary-card ${tone}`.trim()}>
      <div className="summary-head">
        <span className="summary-label">{label}</span>
        {Icon && (
          <span className="summary-icon">
            <Icon size={18} aria-hidden="true" />
          </span>
        )}
      </div>
      <div className="summary-value">{value}</div>
      {hint && <div className="summary-hint">{hint}</div>}
    </div>
  );
}
