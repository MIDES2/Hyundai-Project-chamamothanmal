import { useState } from "react";
export default function Calendar({ date, onChange }) {
  const [month, setMonth] = useState(() => new Date(date + "T12:00:00"));
  const year = month.getFullYear(),
    m = month.getMonth(),
    blank = new Date(year, m, 1).getDay(),
    days = new Date(year, m + 1, 0).getDate();
  return (
    <div className="calendar">
      <div className="month">
        <button
          type="button"
          aria-label="이전 달"
          onClick={() => setMonth(new Date(year, m - 1, 1))}
        >
          ‹
        </button>
        <span>
          {year} / {m + 1}
        </span>
        <button
          type="button"
          aria-label="다음 달"
          onClick={() => setMonth(new Date(year, m + 1, 1))}
        >
          ›
        </button>
      </div>
      <div className="calendar-grid">
        {Array.from("일월화수목금토", (x) => (
          <span key={x}>{x}</span>
        ))}
        {Array.from({ length: blank }, (_, i) => (
          <span key={"b" + i} />
        ))}
        {Array.from({ length: days }, (_, i) => {
          const value =
            year +
            "-" +
            String(m + 1).padStart(2, "0") +
            "-" +
            String(i + 1).padStart(2, "0");
          return (
            <button
              type="button"
              key={i}
              onClick={() => onChange(value)}
              className={date === value ? "chosen" : ""}
            >
              {i + 1}
            </button>
          );
        })}
      </div>
    </div>
  );
}
