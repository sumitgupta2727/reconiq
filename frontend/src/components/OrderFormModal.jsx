import { useEffect, useState } from "react";
import { GATEWAYS, ORDER_STATUSES } from "../utils/constants.js";
import { toInputDate } from "../utils/formatDate.js";
import Modal from "./Modal.jsx";

const EMPTY_FORM = {
  orderId: "",
  customerName: "",
  orderAmount: "",
  orderDate: toInputDate(),
  gateway: "Razorpay",
  status: "CREATED",
};

function validate(form) {
  const errors = {};
  if (!form.orderId.trim()) errors.orderId = "Order ID is required";
  if (form.orderId.trim().length > 30) errors.orderId = "Maximum 30 characters";
  if (!form.customerName.trim()) errors.customerName = "Customer name is required";
  if (form.customerName.trim().length > 100) errors.customerName = "Maximum 100 characters";
  if (form.orderAmount === "" || Number(form.orderAmount) <= 0) {
    errors.orderAmount = "Enter an amount greater than zero";
  }
  if (!form.orderDate) errors.orderDate = "Order date is required";
  if (form.gateway.trim().length > 30) errors.gateway = "Maximum 30 characters";
  return errors;
}

export default function OrderFormModal({
  open,
  order = null,
  onClose,
  onSubmit,
  busy,
  serverError,
}) {
  const editing = Boolean(order);
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (!open) return;
    setErrors({});
    setForm(
      order
        ? {
            orderId: order.orderId ?? "",
            customerName: order.customerName ?? "",
            orderAmount: order.orderAmount != null ? String(order.orderAmount) : "",
            orderDate: order.orderDate ?? toInputDate(),
            gateway: order.gateway ?? "",
            status: order.status ?? "CREATED",
          }
        : { ...EMPTY_FORM, orderDate: toInputDate() }
    );
  }, [open, order]);

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
      orderId: form.orderId.trim(),
      customerName: form.customerName.trim(),
      orderAmount: Number(form.orderAmount),
      orderDate: form.orderDate,
      gateway: form.gateway.trim() || null,
      status: form.status || "CREATED",
    });
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={editing ? `Edit order ${order.orderId}` : "Add order"}
      subtitle={editing ? "Update the internal order record" : "Record an internal order manually"}
      wide
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" form="order-form" className="btn btn-primary" disabled={busy}>
            {busy ? "Saving…" : editing ? "Save changes" : "Save order"}
          </button>
        </>
      }
    >
      <form id="order-form" className="form-grid" onSubmit={submit} noValidate>
        {serverError && <div className="alert alert-error">{serverError}</div>}

        <div className="form-row">
          <div className="form-group">
            <label className="label" htmlFor="orderId">
              Order ID <span className="required">*</span>
            </label>
            <input
              id="orderId"
              className={`input ${errors.orderId ? "invalid" : ""}`.trim()}
              value={form.orderId}
              onChange={update("orderId")}
              placeholder="ORD1013"
            />
            {errors.orderId && <span className="form-error">{errors.orderId}</span>}
          </div>

          <div className="form-group">
            <label className="label" htmlFor="customerName">
              Customer name <span className="required">*</span>
            </label>
            <input
              id="customerName"
              className={`input ${errors.customerName ? "invalid" : ""}`.trim()}
              value={form.customerName}
              onChange={update("customerName")}
              placeholder="Customer name"
            />
            {errors.customerName && <span className="form-error">{errors.customerName}</span>}
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label className="label" htmlFor="orderAmount">
              Amount (₹) <span className="required">*</span>
            </label>
            <input
              id="orderAmount"
              type="number"
              min="0.01"
              step="0.01"
              className={`input ${errors.orderAmount ? "invalid" : ""}`.trim()}
              value={form.orderAmount}
              onChange={update("orderAmount")}
              placeholder="1500.00"
            />
            {errors.orderAmount && <span className="form-error">{errors.orderAmount}</span>}
          </div>

          <div className="form-group">
            <label className="label" htmlFor="orderDate">
              Order date <span className="required">*</span>
            </label>
            <input
              id="orderDate"
              type="date"
              className={`input ${errors.orderDate ? "invalid" : ""}`.trim()}
              value={form.orderDate}
              onChange={update("orderDate")}
            />
            {errors.orderDate && <span className="form-error">{errors.orderDate}</span>}
          </div>
        </div>

        <div className="form-row">
          <div className="form-group">
            <label className="label" htmlFor="orderGateway">
              Gateway
            </label>
            <input
              id="orderGateway"
              className={`input ${errors.gateway ? "invalid" : ""}`.trim()}
              value={form.gateway}
              onChange={update("gateway")}
              list="order-gateway-options"
              placeholder="Razorpay"
            />
            <datalist id="order-gateway-options">
              {GATEWAYS.map((gateway) => (
                <option key={gateway} value={gateway} />
              ))}
            </datalist>
            {errors.gateway && <span className="form-error">{errors.gateway}</span>}
          </div>

          <div className="form-group">
            <label className="label" htmlFor="orderStatus">
              Status <span className="required">*</span>
            </label>
            <select id="orderStatus" className="select" value={form.status} onChange={update("status")}>
              {ORDER_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </div>
        </div>

        <p className="form-hint">
          Uploading an orders CSV updates the same records by order ID, so manual edits are kept in
          sync with the next import.
        </p>
      </form>
    </Modal>
  );
}
