import {
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
} from "recharts";
import { CHART_PALETTE } from "../../utils/constants.js";

function PieTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const item = payload[0];
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-label">{item.name}</div>
      <div>{item.value}</div>
    </div>
  );
}

/**
 * data: [{ name, value, color? }]
 */
export default function StatusPieChart({ data = [], unit = "record" }) {
  const total = data.reduce((sum, item) => sum + Number(item.value || 0), 0);

  if (!total) {
    return (
      <div className="chart-body" style={{ display: "grid", placeItems: "center" }}>
        <p className="muted small">No data available yet.</p>
      </div>
    );
  }

  return (
    <div className="chart-body" style={{ display: "flex", flexDirection: "column" }}>
      <div style={{ flex: 1, minHeight: 0 }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={data}
              dataKey="value"
              nameKey="name"
              innerRadius={54}
              outerRadius={88}
              paddingAngle={2}
              stroke="#fff"
              strokeWidth={2}
            >
              {data.map((item, index) => (
                <Cell
                  key={item.name}
                  fill={item.color ?? CHART_PALETTE[index % CHART_PALETTE.length]}
                />
              ))}
            </Pie>
            <Tooltip content={<PieTooltip />} />
          </PieChart>
        </ResponsiveContainer>
      </div>

      <div className="legend-list">
        {data.map((item, index) => (
          <div className="legend-row" key={item.name}>
            <span className="legend-key">
              <span
                className="legend-dot"
                style={{ background: item.color ?? CHART_PALETTE[index % CHART_PALETTE.length] }}
              />
              {item.name}
            </span>
            <span className="strong">
              {item.value} {unit}
              {Number(item.value) === 1 ? "" : "s"}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
