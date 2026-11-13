import { useEffect, useState } from "react";
import { GATEWAYS, PAYMENT_STATUSES } from "../utils/constants.js";
import { toInputDateTime } from "../utils/formatDate.js";
import Modal from "./Modal.jsx";

const EMPTY_FORM = {
  transactionId: "",
  orderId: "",
  customerName: "",
  gateway: "Razorpay",
  transactionAmount: "",
  transactionDate: toInputDateTime(),
  paymentStatus: "SUCCESS",
};

function validate(form) {
  const errors = {};
  if (!form.transactionId.trim()) errors.transactionId = "Transaction ID is required";
  if (form.transactionId.trim().length > 30) errors.transactionId = "Maximum 30 characters";
  if (form.orderId.trim().length > 30) errors.orderId = "Maximum 30 characters";
  if (!form.gateway.trim()) errors.gateway = "Gateway is required";
  if (form.transactionAmount === "" || Number(form.transactionAmount) < 0) {
    errors.transactionAmount = "Enter a valid amount";
  }
  if (!form.transactionDate) errors.transactionDate = "Transaction date is required";
  if (!form.paymentStatus) errors.paymentStatus = "Payment status is required";
  return errors;
}

export default function TransactionFormModal({ open, onClose, onSubmit, busy, serverError }) {
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (open) {
      setForm({ ...EMPTY_FORM, transactionDate: toInputDateTime() });
      setErrors({});
    }
  }, [open]);

  const update = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  };

  const submit = async (event) => {
    event.preventDefault();
    const nextErrors = validate(form);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;

    await onSubmit({
      transactionId: form.transactionId.trim(),
      orderId: form.orderId.trim() || null,
      customerName: form.customerName.trim() || null,
      gateway: form.gateway.trim(),
      transactionAmount: Number(form.transactionAmount),
      transactionDate: form.transactionDate,
      paymentStatus: form.paymentStatus,
    });
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Add transaction"
      subtitle="Record a gateway payment manually"
      wide
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" form="transaction-form" className="btn btn-primary" disabled={busy}>
            {busy ? "Saving…" : "Save transaction"}
          </button>
        </>
      }
    >
      <form id="transaction-form" className="form-grid" onSubmit={submit} noValidate>
        {serverError && <div className="alert alert-error">{serverError}</div>}

        <div className="form-row">
          <div className="form-group">
            <label className="label" htmlFor="transactionId">
              Transaction ID <span className="required">*</span>
            </label>
            <input
              id="transactionId"
              className={`input ${errors.transactionId ? "invalid" : ""}`.trim()}
              value={form.transactionId}
              onChange={update("transactionId")}
              placeholder="TXN9014"
            />
            {errors.transactionId && <span className="form-error">{errors.transactionId}</span>}
          </div>

          <div className="form-group">
            <label className="label" htmlFor="orderId">
              Order ID
            </label>
            <input
              id="orderId"
              className={`input ${errors.orderId ? "invalid" : ""}`.trim()}
              value={form.orderId}
              onChange={update("orderId")}
              placeholder="ORD1012"
            />
            {errors.orderId && <span className="form-error">{errors.orderId}</span>}
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label className="label" htmlFor="customerName">
              Customer name
            </label>
            <input
              id="customerName"
              className="input"
              value={form.customerName}
              onChange={update("customerName")}
              placeholder="Customer name"
            />
          </div>

          <div className="form-group">
            <label className="label" htmlFor="gateway">
              Gateway <span className="required">*</span>
            </label>
            <input
              id="gateway"
              className={`input ${errors.gateway ? "invalid" : ""}`.trim()}
              value={form.gateway}
              onChange={update("gateway")}
              list="gateway-options"
              placeholder="Razorpay"
            />
            <datalist id="gateway-options">
              {GATEWAYS.map((gateway) => (
                <option key={gateway} value={gateway} />
              ))}
            </datalist>
            {errors.gateway && <span className="form-error">{errors.gateway}</span>}
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label className="label" htmlFor="transactionAmount">
              Amount (₹) <span className="required">*</span>
            </label>
            <input
              id="transactionAmount"
              type="number"
              min="0"
              step="0.01"
              className={`input ${errors.transactionAmount ? "invalid" : ""}`.trim()}
              value={form.transactionAmount}
              onChange={update("transactionAmount")}
              placeholder="1500.00"
            />
            {errors.transactionAmount && (
              <span className="form-error">{errors.transactionAmount}</span>
            )}
          </div>

          <div className="form-group">
            <label className="label" htmlFor="paymentStatus">
              Payment status <span className="required">*</span>
            </label>
            <select
              id="paymentStatus"
              className="select"
              value={form.paymentStatus}
              onChange={update("paymentStatus")}
            >
              {PAYMENT_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="form-group">
          <label className="label" htmlFor="transactionDate">
            Transaction date <span className="required">*</span>
          </label>
          <input
            id="transactionDate"
            type="datetime-local"
            className={`input ${errors.transactionDate ? "invalid" : ""}`.trim()}
            value={form.transactionDate}
            onChange={update("transactionDate")}
          />
          {errors.transactionDate && <span className="form-error">{errors.transactionDate}</span>}
        </div>

        <p className="form-hint">
          Run reconciliation afterwards to compare this payment with its internal order.
        </p>
      </form>
    </Modal>
  );
}
