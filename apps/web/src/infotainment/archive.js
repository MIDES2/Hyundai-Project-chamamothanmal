export const ARCHIVE_KEY = "chama-ui-vehicle-archive-v1";
export const RETENTION_KEY = "chama-ui-vehicle-retention-v1";
export const DEFAULT_RETENTION = { unit: "week", count: 1 };
export function normalizeRetention(value) {
  const unit = ["week", "month", "year", "forever"].includes(value?.unit)
    ? value.unit
    : "week";
  const count = Math.max(
    1,
    Math.min(999, Math.floor(Number(value?.count) || 1)),
  );
  return { unit, count };
}
export function expiresAt(message) {
  const { unit, count } = normalizeRetention(message.retention);
  if (unit === "forever") return null;
  if (unit === "week") return message.savedAt + count * 7 * 86400000;
  // Calendar months/years in Korean time, clamped to the month's last day.
  const offset = 9 * 3600000,
    date = new Date(message.savedAt + offset),
    day = date.getUTCDate();
  date.setUTCDate(1);
  date.setUTCMonth(date.getUTCMonth() + count * (unit === "year" ? 12 : 1));
  const last = new Date(
    Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0),
  ).getUTCDate();
  date.setUTCDate(Math.min(day, last));
  return date.getTime() - offset;
}
export function pruneArchive(items, now = Date.now()) {
  const kept = items.filter((m) => expiresAt(m) === null || expiresAt(m) > now);
  return kept.length === items.length ? items : kept;
}
export function readArchive() {
  try {
    const data = JSON.parse(localStorage.getItem(ARCHIVE_KEY) || "[]");
    return Array.isArray(data)
      ? pruneArchive(
          data
            .filter(
              (m) => m && m.id === "mother-demo" && Number.isFinite(m.savedAt),
            )
            .slice(0, 1)
            .map((m) => ({ ...m, retention: normalizeRetention(m.retention) })),
        )
      : [];
  } catch {
    return [];
  }
}
export function readRetention() {
  try {
    return normalizeRetention(JSON.parse(localStorage.getItem(RETENTION_KEY)));
  } catch {
    return { ...DEFAULT_RETENTION };
  }
}
