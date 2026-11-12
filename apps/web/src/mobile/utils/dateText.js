export default function dateText(date) {
  const d = new Date(date + "T12:00:00");
  return (
    date.replaceAll("-", "/") +
    "/(" +
    ["일", "월", "화", "수", "목", "금", "토"][d.getDay()] +
    ")"
  );
}
