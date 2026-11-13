import whiteCar from "./assets/car-white.svg";
import darkCar from "./assets/car-dark.svg";
export function Icon({ name }) {
  const p = {
    home: (
      <path
        d="M2 10 12 2l10 8h-4v12h-4v-8h-4v8H6V10Z"
        fill="currentColor"
        stroke="none"
      />
    ),
    voice: <path d="m2 17 4-7-1 8 6-15-2 18 6-16-1 14 4-9-1 8 3-3 2 2" />,
    sign: (
      <>
        <path d="M2 3h20v13H2zM12 16v7" />
        <path d="m5 10 2-3 2 6 3-8 2 7 2-4 3 2" />
      </>
    ),
    basket: (
      <>
        <rect x="2" y="5" width="20" height="4" rx=".5" />
        <path d="M3 9v11h18V9M9 12h6" />
      </>
    ),
    openBasket: (
      <>
        <path d="M3 7h18v13H3ZM3 7l3-3h12l3 3M9 11h6" />
      </>
    ),
    user: (
      <>
        <circle cx="12" cy="5" r="3" />
        <path d="M3 22v-5a9 9 0 0 1 18 0v5" />
      </>
    ),
    bell: <path d="M5 17h14l-2-3V9a5 5 0 0 0-10 0v5zM10 20h4" />,
    car: (
      <>
        <path d="m5 9 2-6h10l2 6M3 9h18v10H3zM5 19v3M19 19v3M6 13h2M16 13h2M8 17h8" />
      </>
    ),
    pin: (
      <>
        <path d="M19 9c0 5-7 13-7 13S5 14 5 9a7 7 0 0 1 14 0Z" />
        <path d="M8 9h8M9 6h6" />
      </>
    ),
    calendar: (
      <path d="M3 5h18v16H3zM3 10h18M7 2v6M17 2v6M7 14h2M13 14h3M7 18h2" />
    ),
    settings: (
      <>
        <path d="m9 2 1 2h4l1-2 4 3-1 2 2 4h2v4l-2 1-2 3 1 2-4 2-1-2h-4l-1 2-4-2 1-2-2-3-2-1v-4h2l2-4-1-2z" />
        <circle cx="12" cy="12" r="4" />
      </>
    ),
    terms: <path d="m7 2 13 2-4 18-13-2zM8 7h8M7 12h8M6 17h6" />,
    search: (
      <>
        <circle cx="10" cy="10" r="7" />
        <path d="m15 15 7 7" />
      </>
    ),
    mail: <path d="M2 5h20v14H2zM2 6l10 7L22 6" />,
    lock: <path d="M5 10h14v12H5zM8 10V6a4 4 0 0 1 8 0v4M12 15v3" />,
    back: <path d="m16 2-11 10 11 10" />,
  };
  return (
    <svg
      viewBox="0 0 24 24"
      className="icon"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {p[name]}
    </svg>
  );
}
export function Car({ dark = false, angled = false, artwork }) {
  return (
    <img
      className={"car-art" + (angled ? " angled" : "")}
      src={artwork || (dark ? darkCar : whiteCar)}
      alt="직접 그린 현대 H·기아 K 바퀴의 자동차"
    />
  );
}
