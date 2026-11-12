export const NOTIFICATION_KEY = "chama-ui-notifications-v1";
export function readNotifications() {
  try {
    const data = JSON.parse(localStorage.getItem(NOTIFICATION_KEY) || "[]");
    return Array.isArray(data)
      ? data.filter(
          (n) =>
            n &&
            typeof n.id === "string" &&
            typeof n.ownerId === "string" &&
            typeof n.title === "string" &&
            typeof n.body === "string" &&
            Number.isFinite(n.createdAt) &&
            typeof n.read === "boolean",
        )
      : [];
  } catch {
    return [];
  }
}
const timeFormat = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  month: "numeric",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit",
});
export default function NotificationList({ items }) {
  return items.length ? (
    <ul className="notification-list">
      {items.map((item) => (
        <li key={item.id}>
          <strong>{item.title}</strong>
          <p>{item.body}</p>
          <time dateTime={new Date(item.createdAt).toISOString()}>
            {timeFormat.format(new Date(item.createdAt))}
          </time>
        </li>
      ))}
    </ul>
  ) : (
    <p className="notification-empty">아직 받은 알림이 없어요.</p>
  );
}
