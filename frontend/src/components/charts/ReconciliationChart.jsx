import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { STATUS_COLORS, STATUS_LABELS } from "../../utils/constants.js";

function BarTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const item = payload[0].payload;
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-label">{item.label}</div>
      <div>
        {item.count} record{item.count === 1 ? "" : "s"}
      </div>
    </div>
  );
}

export default function ReconciliationChart({ statusCounts = {} }) {
  const data = Object.entries(statusCounts).map(([status, count]) => ({
    status,
    label: STATUS_LABELS[status] ?? status,
    count,
    fill: STATUS_COLORS[status] ?? "#94a3b8",
  }));

  if (!data.some((item) => item.count > 0)) {
    return (
      <div className="chart-body" style={{ display: "grid", placeItems: "center" }}>
        <p className="muted small">Run a reconciliation to populate this chart.</p>
      </div>
    );
  }

  return (
    <div className="chart-body">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 12, right: 6, left: -22, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#eef1f7" />
          <XAxis
            dataKey="label"
            tick={{ fontSize: 10.5, fill: "#667085" }}
            axisLine={false}
            tickLine={false}
            interval={0}
          />
          <YAxis
            allowDecimals={false}
            tick={{ fontSize: 11, fill: "#667085" }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip content={<BarTooltip />} cursor={{ fill: "#f5f8ff" }} />
          <Bar dataKey="count" radius={[6, 6, 0, 0]} maxBarSize={44}>
            {data.map((item) => (
              <Cell key={item.status} fill={item.fill} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
