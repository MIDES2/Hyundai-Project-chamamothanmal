import CarLogin from "../components/CarLogin.jsx";
import Primary from "../components/Primary.jsx";
import depthCar from "../../shared/assets/car-dark-depth.svg";
export default function WelcomePage({ setPage, goLogin }) {
  return (
    <main className="welcome">
      <svg width="0" height="0" className="splash-lighting" aria-hidden="true" focusable="false">
        <defs>
          <filter id="welcome-car-depth" x="-20%" y="-25%" width="140%" height="160%" colorInterpolationFilters="sRGB">
            <feGaussianBlur in="SourceAlpha" stdDeviation="0.65" result="surface" />
            <feSpecularLighting in="surface" surfaceScale="1.5" specularConstant="0.3" specularExponent="22" lightingColor="#f5f7fa" result="light">
              <feDistantLight azimuth="225" elevation="50" />
            </feSpecularLighting>
            <feComposite in="light" in2="SourceAlpha" operator="in" result="clipped-light" />
            <feBlend in="SourceGraphic" in2="clipped-light" mode="screen" result="lit-car" />
            <feDropShadow in="lit-car" dx="0" dy="3" stdDeviation="2" floodColor="#343b44" floodOpacity="0.3" />
          </filter>
        </defs>
      </svg>
      <h1>차마못한말</h1>
      <CarLogin dark artwork={depthCar} onClick={goLogin} />
      <Primary onClick={() => setPage("loading")}>로그인 및 회원가입</Primary>
    </main>
  );
}
