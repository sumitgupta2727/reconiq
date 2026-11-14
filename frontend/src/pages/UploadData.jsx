import {
  AlertTriangle,
  CheckCircle2,
  ClipboardList,
  CreditCard,
  Download,
  FileSpreadsheet,
  Info,
  UploadCloud,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import DataTable from "../components/DataTable.jsx";
import Loader from "../components/Loader.jsx";
import StatusBadge from "../components/StatusBadge.jsx";
import { useAuth } from "../hooks/useAuth.jsx";
import uploadService from "../services/uploadService.js";
import { downloadBlob } from "../utils/download.js";
import { getErrorMessage } from "../utils/errors.js";
import { formatDateTime } from "../utils/formatDate.js";

const MAX_SIZE = 10 * 1024 * 1024;

const UPLOAD_MODES = {
  orders: {
    key: "orders",
    label: "Orders",
    icon: ClipboardList,
    title: "Upload orders data",
    description:
      "Import the business's own order records. Existing order IDs are updated and new ones are created, so a corrected file can be re-uploaded safely.",
    endpoint: "uploadOrders",
    templateName: "reconiq-orders-template.csv",
    dropTitle: "Drop your orders CSV here",
    importTitle: "Import an orders CSV",
    noun: "order(s)",
    successHint: "Run reconciliation to compare these orders with gateway transactions.",
    columns: [
      { key: "order_id", label: "order_id", note: "Business order reference (required, unique)" },
      { key: "customer_name", label: "customer_name", note: "Customer display name (required)" },
      { key: "order_amount", label: "order_amount", note: "Decimal amount, e.g. 1499.00 (required)" },
      { key: "order_date", label: "order_date", note: "2026-06-01 (required)" },
      { key: "gateway", label: "gateway", note: "Expected gateway, e.g. Razorpay" },
      { key: "status", label: "status", note: "PAID / CREATED / CANCELLED (default CREATED)" },
    ],
    sampleFiles: [{ name: "orders.csv", label: "orders.csv" }],
    notice:
      "Rows with the same order_id inside one file are rejected so a single order is never applied twice.",
  },
  gateway: {
    key: "gateway",
    label: "Gateway transactions",
    icon: CreditCard,
    title: "Upload gateway data",
    description:
      "Import a payment gateway statement. Every file is tracked as a batch so the source of each transaction stays auditable.",
    endpoint: "upload",
    templateName: "reconiq-gateway-template.csv",
    dropTitle: "Drop your gateway CSV here",
    importTitle: "Import a gateway CSV",
    noun: "transaction(s)",
    successHint: "Run reconciliation to match them with your orders.",
    columns: [
      { key: "order_id", label: "order_id", note: "Optional internal order reference" },
      { key: "transaction_id", label: "transaction_id", note: "Gateway payment reference (required)" },
      { key: "customer_name", label: "customer_name", note: "Optional, used for display" },
      { key: "gateway", label: "gateway", note: "Razorpay, Paytm, Stripe… (required)" },
      { key: "transaction_amount", label: "transaction_amount", note: "Decimal amount, e.g. 1499.00 (required)" },
      { key: "transaction_date", label: "transaction_date", note: "2026-06-01 14:30:00 (required)" },
      { key: "payment_status", label: "payment_status", note: "SUCCESS / FAILED / PENDING (required)" },
    ],
    sampleFiles: [
      { name: "gateway_transactions.csv", label: "gateway_transactions.csv" },
      { name: "gateway_transactions_with_errors.csv", label: "gateway_transactions_with_errors.csv" },
    ],
    notice:
      "Invalid rows are skipped and reported; the rest of the file is still imported. Each upload appears in the history below with its status.",
  },
};

const HISTORY_COLUMNS = [
  {
    key: "fileName",
    header: "File",
    render: (row) => (
      <>
        <span className="cell-strong">{row.fileName}</span>
        <span className="cell-sub">{row.fileType}</span>
      </>
    ),
  },
  {
    key: "dataType",
    header: "Type",
    render: (row) => (
      <span className="chip">
        {row.dataType === "ORDERS" ? "Orders" : "Gateway"}
      </span>
    ),
  },
  {
    key: "records",
    header: "Records",
    align: "right",
    render: (row) => (
      <span>
        {row.importedRecords}
        {row.totalRecords !== row.importedRecords ? ` / ${row.totalRecords}` : ""}
      </span>
    ),
  },
  {
    key: "status",
    header: "Status",
    render: (row) => <StatusBadge status={row.status} />,
  },
  { key: "uploadedBy", header: "Uploaded by", render: (row) => row.uploadedBy ?? "—" },
  {
    key: "uploadedAt",
    header: "Uploaded",
    render: (row) => <span className="muted">{formatDateTime(row.uploadedAt)}</span>,
  },
  {
    key: "errorMessage",
    header: "Notes",
    render: (row) =>
      row.errorMessage ? (
        <span className="muted truncate" style={{ display: "block", maxWidth: 320 }} title={row.errorMessage}>
          {row.errorMessage}
        </span>
      ) : (
        <span className="muted">—</span>
      ),
  },
];

const HISTORY_FILTERS = [
  { key: "ALL", label: "All" },
  { key: "ORDERS", label: "Orders" },
  { key: "GATEWAY", label: "Gateway" },
];

export default function UploadData() {
  const { user } = useAuth();
  const inputRef = useRef(null);

  const [mode, setMode] = useState("orders");
  const config = UPLOAD_MODES[mode];

  const [file, setFile] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [progress, setProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [warning, setWarning] = useState("");

  const [history, setHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyFilter, setHistoryFilter] = useState("ALL");

  const loadHistory = useCallback(async () => {
    setHistoryLoading(true);
    try {
      const batches = await uploadService.history();
      setHistory(batches ?? []);
    } catch (err) {
      setError(getErrorMessage(err, "Could not load the upload history."));
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  const filteredHistory = useMemo(
    () => (historyFilter === "ALL" ? history : history.filter((row) => row.dataType === historyFilter)),
    [history, historyFilter]
  );

  const resetMessages = () => {
    setError("");
    setNotice("");
    setWarning("");
    setProgress(0);
  };

  const switchMode = (nextMode) => {
    if (nextMode === mode || uploading) return;
    setMode(nextMode);
    setFile(null);
    resetMessages();
  };

  const chooseFile = (candidate) => {
    resetMessages();

    if (!candidate) return;
    if (!candidate.name.toLowerCase().endsWith(".csv")) {
      setError("Only .csv files are supported. Download the template if you need an example.");
      return;
    }
    if (candidate.size > MAX_SIZE) {
      setError("The file is larger than 10 MB. Split it into smaller files and try again.");
      return;
    }
    setFile(candidate);
  };

  const upload = async () => {
    if (!file) return;
    setUploading(true);
    resetMessages();

    try {
      const batch =
        config.key === "orders"
          ? await uploadService.uploadOrders(file, user?.employeeId, setProgress)
          : await uploadService.upload(file, user?.employeeId, setProgress);
      setFile(null);
      setProgress(100);

      if (batch.status === "PARTIAL") {
        setWarning(`Imported ${batch.importedRecords} ${config.noun}. ${batch.errorMessage ?? ""}`);
      } else {
        setNotice(
          `Imported ${batch.importedRecords} ${config.noun} from ${batch.fileName}. ${config.successHint}`
        );
      }
      await loadHistory();
    } catch (err) {
      setError(getErrorMessage(err, "The file could not be uploaded."));
      await loadHistory();
    } finally {
      setUploading(false);
    }
  };

  const downloadTemplate = async () => {
    try {
      const blob = await uploadService.downloadTemplate(config.key);
      downloadBlob(blob, config.templateName);
    } catch (err) {
      setError(getErrorMessage(err, "Could not download the template."));
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <h1 className="page-title">{config.title}</h1>
          <p className="page-description">{config.description}</p>
        </div>
        <div className="page-actions">
          <div className="segmented" role="tablist" aria-label="Upload type">
            {Object.values(UPLOAD_MODES).map((option) => {
              const Icon = option.icon;
              return (
                <button
                  key={option.key}
                  type="button"
                  role="tab"
                  aria-selected={mode === option.key}
                  className={mode === option.key ? "active" : ""}
                  onClick={() => switchMode(option.key)}
                  disabled={uploading}
                >
                  <Icon size={15} />
                  {option.label}
                </button>
              );
            })}
          </div>
          <button type="button" className="btn btn-secondary" onClick={downloadTemplate}>
            <Download size={16} />
            CSV template
          </button>
        </div>
      </div>

      {error && (
        <div className="alert alert-error" style={{ marginBottom: 16 }}>
          <AlertTriangle size={16} />
          {error}
        </div>
      )}
      {notice && (
        <div className="alert alert-success" style={{ marginBottom: 16 }}>
          <CheckCircle2 size={16} />
          {notice}
        </div>
      )}
      {warning && (
        <div className="alert alert-warning" style={{ marginBottom: 16 }}>
          <AlertTriangle size={16} />
          {warning}
        </div>
      )}

      <div className="grid grid-main">
        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">{config.importTitle}</div>
              <div className="card-subtitle">Maximum file size 10 MB · UTF-8 or Excel CSV</div>
            </div>
          </div>
          <div className="card-body">
            <div
              className={`dropzone ${dragging ? "dragging" : ""}`.trim()}
              role="button"
              tabIndex={0}
              onClick={() => inputRef.current?.click()}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") inputRef.current?.click();
              }}
              onDragOver={(event) => {
                event.preventDefault();
                setDragging(true);
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={(event) => {
                event.preventDefault();
                setDragging(false);
                chooseFile(event.dataTransfer.files?.[0]);
              }}
            >
              <span className="dropzone-icon">
                <UploadCloud size={24} />
              </span>
              <div className="dropzone-title">{config.dropTitle}</div>
              <div className="dropzone-hint">or click to browse files</div>

              <input
                ref={inputRef}
                type="file"
                accept=".csv,text/csv"
                hidden
                onChange={(event) => {
                  chooseFile(event.target.files?.[0]);
                  event.target.value = "";
                }}
              />

              {file && (
                <div className="file-pill" onClick={(event) => event.stopPropagation()}>
                  <div className="file-pill-main">
                    <FileSpreadsheet size={18} style={{ color: "var(--brand)", flex: "none" }} />
                    <div style={{ minWidth: 0 }}>
                      <div className="file-pill-name">{file.name}</div>
                      <div className="tiny muted">{(file.size / 1024).toFixed(1)} KB</div>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="page-btn"
                    onClick={() => {
                      setFile(null);
                      setProgress(0);
                    }}
                    aria-label="Remove selected file"
                  >
                    <X size={14} />
                  </button>
                </div>
              )}
            </div>

            {uploading && (
              <>
                <div className="progress">
                  <div className="progress-bar" style={{ width: `${progress}%` }} />
                </div>
                <p className="form-hint" style={{ marginTop: 8 }}>
                  Uploading and validating… {progress}%
                </p>
              </>
            )}

            <div style={{ display: "flex", gap: 10, marginTop: 16, flexWrap: "wrap" }}>
              <button
                type="button"
                className="btn btn-primary"
                onClick={upload}
                disabled={!file || uploading}
              >
                <UploadCloud size={16} />
                {uploading ? "Importing…" : "Upload and import"}
              </button>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={downloadTemplate}
                disabled={uploading}
              >
                <Download size={16} />
                Template
              </button>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <div>
              <div className="card-title">Expected columns</div>
              <div className="card-subtitle">Header aliases are accepted</div>
            </div>
          </div>
          <div className="card-body">
            <div className="stack" style={{ gap: 10 }}>
              {config.columns.map((column) => (
                <div key={column.key} className="status-line" style={{ alignItems: "flex-start" }}>
                  <code className="mono" style={{ color: "var(--brand)", minWidth: 150 }}>
                    {column.label}
                  </code>
                  <span className="muted small">{column.note}</span>
                </div>
              ))}
            </div>

            <div className="divider" />

            <div className="alert alert-info">
              <Info size={16} />
              <span>{config.notice}</span>
            </div>

            <div className="stack" style={{ gap: 6, marginTop: 14 }}>
              <span className="tiny muted strong">Sample files</span>
              {config.sampleFiles.map((sample) => (
                <a key={sample.name} href={`/samples/${sample.name}`} download className="small">
                  {sample.label}
                </a>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="card section-gap">
        <div className="card-header">
          <div>
            <div className="card-title">Upload history</div>
            <div className="card-subtitle">Batch-level audit trail for every import</div>
          </div>
          <div className="segmented" role="tablist" aria-label="Filter upload history">
            {HISTORY_FILTERS.map((filter) => (
              <button
                key={filter.key}
                type="button"
                role="tab"
                aria-selected={historyFilter === filter.key}
                className={historyFilter === filter.key ? "active" : ""}
                onClick={() => setHistoryFilter(filter.key)}
              >
                {filter.label}
              </button>
            ))}
          </div>
        </div>

        {historyLoading ? (
          <Loader label="Loading upload history…" />
        ) : (
          <DataTable
            columns={HISTORY_COLUMNS}
            rows={filteredHistory}
            emptyTitle="No uploads yet"
            emptyMessage="Your first CSV will appear here with its processing status."
            emptyIcon={FileSpreadsheet}
          />
        )}
      </div>
    </>
  );
}
