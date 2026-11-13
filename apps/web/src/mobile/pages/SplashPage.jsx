import CarLogin from "../components/CarLogin.jsx";
import depthCar from "../../shared/assets/car-white-depth.svg";
import VoiceBubble from "../components/VoiceBubble.jsx";
export default function SplashPage({ setPage, goLogin }) {
  const withCopy = document.documentElement.dataset.introCopy === "true" || new URLSearchParams(window.location.search).get("introCopy") === "on";
  return (
    <main className={"splash" + (withCopy ? " splash-with-copy" : "")} onClick={() => setPage("welcome")}>
      <svg width="0" height="0" className="splash-lighting" aria-hidden="true" focusable="false">
        <defs>
          <filter id="splash-car-depth" x="-20%" y="-25%" width="140%" height="160%" colorInterpolationFilters="sRGB">
            <feGaussianBlur in="SourceAlpha" stdDeviation="0.65" result="surface" />
            <feSpecularLighting in="surface" surfaceScale="1.5" specularConstant="0.3" specularExponent="22" lightingColor="#fff5e9" result="light">
              <feDistantLight azimuth="225" elevation="50" />
            </feSpecularLighting>
            <feComposite in="light" in2="SourceAlpha" operator="in" result="clipped-light" />
            <feBlend in="SourceGraphic" in2="clipped-light" mode="screen" result="lit-car" />
            <feDropShadow in="lit-car" dx="0" dy="3" stdDeviation="2" floodColor="#081c30" floodOpacity="0.45" />
          </filter>
        </defs>
      </svg>
      <div className="splash-title-spacer" aria-hidden="true" />
      <p className="splash-caption">차에 꽂아두는 마음</p>
      <h1>차마못한말</h1>
      {withCopy && <div className="splash-description-spacer" aria-hidden="true" />}
      <div className="splash-car-scene">
        <CarLogin onClick={goLogin} artwork={depthCar} />
        <VoiceBubble />
      </div>
      <button className="splash-start" type="button" onClick={() => setPage("welcome")}>
        <span>마음 전하러 가기</span>
        <span className="splash-start-arrow" aria-hidden="true">→</span>
      </button>
    </main>
  );
}
