import { useEffect, useState } from "react";

const formatter = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Seoul",
  hour: "2-digit",
  minute: "2-digit",
  hour12: true,
});

export default function StatusBar() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const update = () => setNow(new Date());
    const timer = setInterval(update, 1000);
    document.addEventListener("visibilitychange", update);
    window.addEventListener("focus", update);
    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", update);
      window.removeEventListener("focus", update);
    };
  }, []);
  return (
    <div className="v-status">
      <svg
        className="status-icon lte"
        viewBox="0 0 42 40"
        role="img"
        aria-label="LTE"
      >
        <text
          x="1"
          y="12"
          fill="currentColor"
          fontFamily="Arial, sans-serif"
          fontWeight="bold"
          fontSize="13"
        >
          LTE
        </text>
        <g stroke="currentColor" strokeWidth="4" strokeLinecap="round">
          <path d="M5 35V26M15 35V20M25 35V13M35 35V5" />
        </g>
      </svg>
      <svg
        className="status-icon"
        viewBox="0 0 40 40"
        role="img"
        aria-label="스피커"
      >
        <path
          d="M3 14h8L23 5v30L11 26H3Z"
          fill="currentColor"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinejoin="round"
        />
        <g
          fill="none"
          stroke="currentColor"
          strokeWidth="2.8"
          strokeLinecap="round"
        >
          <path d="M28 15q4 5 0 10M32 10q8 10 0 20M36 5q11 15 0 30" />
        </g>
      </svg>
      <svg
        className="status-icon"
        viewBox="0 0 40 40"
        role="img"
        aria-label="블루투스"
      >
        <path
          d="M7 8 33 29 20 37V3l13 8L7 32"
          fill="none"
          stroke="currentColor"
          strokeWidth="3.3"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <svg
        className="status-icon status-profile"
        viewBox="0 0 40 40"
        role="img"
        aria-label="사용자"
      >
        <circle cx="20" cy="20" r="20" fill="#41434a" />
        <circle cx="20" cy="13" r="6.4" fill="currentColor" />
        <path d="M6 32c0-9 7-13 14-13s14 4 14 13Z" fill="currentColor" />
      </svg>
      <time dateTime={now.toISOString()} aria-label="한국 현재 시각">
        {formatter.format(now)}
      </time>
    </div>
  );
}
