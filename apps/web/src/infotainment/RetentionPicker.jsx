import { normalizeRetention } from "./archive.js";
export default function RetentionPicker({ value, onChange, label }) {
  const retention = normalizeRetention(value);
  return (
    <div className="retention-picker" role="group" aria-label={label}>
      {retention.unit !== "forever" && (
        <input
          type="number"
          min="1"
          max="999"
          aria-label={label + " 수량"}
          value={retention.count}
          onChange={(e) =>
            onChange(
              normalizeRetention({ ...retention, count: e.target.value }),
            )
          }
        />
      )}
      <select
        aria-label={label + " 단위"}
        value={retention.unit}
        onChange={(e) => onChange({ ...retention, unit: e.target.value })}
      >
        <option value="week">주 (7일)</option>
        <option value="month">개월 (월)</option>
        <option value="year">년</option>
        <option value="forever">영구</option>
      </select>
    </div>
  );
}
