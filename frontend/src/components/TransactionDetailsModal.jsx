import { formatCurrency } from "../utils/formatCurrency.js";
import { formatDateTime } from "../utils/formatDate.js";
import Modal from "./Modal.jsx";
import StatusBadge from "./StatusBadge.jsx";

function Detail({ label, value, span = false }) {
  return (
    <div className={`detail-item ${span ? "span-2" : ""}`.trim()}>
      <div className="detail-label">{label}</div>
      <div className="detail-value">{value ?? "—"}</div>
    </div>
  );
}

export default function TransactionDetailsModal({ open, transaction, onClose }) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Transaction details"
      subtitle={transaction ? `Gateway record ${transaction.transactionId}` : undefined}
      footer={
        <button type="button" className="btn btn-secondary" onClick={onClose}>
          Close
        </button>
      }
    >
      {transaction && (
        <div className="detail-grid">
          <Detail label="Transaction ID" value={<span className="mono">{transaction.transactionId}</span>} />
          <Detail label="Order ID" value={<span className="mono">{transaction.orderId ?? "—"}</span>} />
          <Detail label="Customer" value={transaction.customerName} span />
          <Detail label="Gateway" value={transaction.gateway} />
          <Detail label="Amount" value={formatCurrency(transaction.transactionAmount)} />
          <Detail label="Payment status" value={<StatusBadge status={transaction.paymentStatus} />} />
          <Detail label="Transaction date" value={formatDateTime(transaction.transactionDate)} />
          <Detail label="Upload batch" value={transaction.uploadBatchFileName ?? "Manual entry"} span />
          <Detail label="Recorded at" value={formatDateTime(transaction.createdAt)} span />
        </div>
      )}
    </Modal>
  );
}
