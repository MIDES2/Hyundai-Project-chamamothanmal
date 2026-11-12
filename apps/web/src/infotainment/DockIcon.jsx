export default function DockIcon({ name }) {
  const icons = {
    fan: (
      <>
        <circle cx="32" cy="32" r="5" />
        <path d="M30 28C19 24 17 13 25 10c10-4 15 6 10 18ZM36 29c4-11 15-13 18-5 4 10-6 15-18 10ZM34 36c11 4 13 15 5 18-10 4-15-6-10-18ZM27 34C23 45 12 47 9 39c-4-10 6-15 18-10Z" />
      </>
    ),
    music: (
      <>
        <path d="M35 15v30c-3-2-8-1-11 2-4 4-3 9 2 10s13-2 13-9V25l15 7V20Z" />
        <path
          d="m10 13 13 13-7 6V7l7 6-13 13"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </>
    ),
    phone: (
      <path d="M16 7c-3 0-7 4-7 9 0 18 21 40 39 40 5 0 10-5 10-8 0-2-13-9-15-9-2 0-4 6-7 5-7-3-15-11-18-18-1-3 5-5 5-7 0-2-5-12-7-12Z" />
    ),
    navigation: (
      <path
        d="m7 27 49-18-17 49-9-22Z"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinejoin="round"
      />
    ),
  };
  return (
    <svg
      className="dock-symbol"
      viewBox="0 0 64 64"
      fill="currentColor"
      aria-hidden="true"
    >
      {icons[name]}
    </svg>
  );
}
