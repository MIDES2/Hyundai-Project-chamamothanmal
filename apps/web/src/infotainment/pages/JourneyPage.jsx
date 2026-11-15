import waitingCar from "../../shared/assets/car-white-depth.svg";
export default function JourneyPage({
  screen,
  setScreen,
  routeSvg,
  arrivalSign,
}) {
  return (
    <button
      className={"waiting-view" + (screen === "arrived" ? " arrived" : "")}
      onClick={() => {
        if (screen === "waiting") setScreen("arrived");
      }}
      aria-label="목적지 도착 시연"
    >
      <span role="status">
        {screen === "arrived"
          ? "목적지에 도착했어요!"
          : "목적지를 향해 가고있어요"}
      </span>
      <svg
        ref={routeSvg}
        className="route-svg"
        viewBox="0 0 600 460"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <path
          d="M80 50H520Q550 50 550 80V380Q550 410 520 410H80Q50 410 50 380V80Q50 50 80 50Z"
          className="car-track-trail"
          pathLength="1000"
          fill="none"
          stroke="#7e83a1"
          strokeWidth="4"
          strokeLinecap="round"
          strokeDasharray="16 9"
        />
        <g className="route-car">
          <image
            href={waitingCar}
            x="-48"
            y="-25"
            width="96"
            height="50"
            transform="rotate(180)"
          />
          <animateMotion
            dur="8s"
            repeatCount="indefinite"
            rotate="auto"
            path="M80 50H520Q550 50 550 80V380Q550 410 520 410H80Q50 410 50 380V80Q50 50 80 50Z"
          />
        </g>
        {arrivalSign && (
          <g
            className="arrival-sign"
            transform={
              "translate(" +
              arrivalSign.x +
              " " +
              arrivalSign.y +
              ") rotate(" +
              arrivalSign.angle +
              ") scale(0.8)"
            }
            fill="#ffffff"
          >
            <rect x="-21" y="-40" width="42" height="24" rx="1" />
            <rect x="-2" y="-16" width="4" height="20" />
            <rect x="-13" y="4" width="26" height="4" rx="1" />
          </g>
        )}
      </svg>
    </button>
  );
}
