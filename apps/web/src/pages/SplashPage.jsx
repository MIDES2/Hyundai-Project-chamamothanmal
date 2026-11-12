import CarLogin from "../components/CarLogin.jsx";
export default function SplashPage({ setPage, goLogin }) {
  return (
    <main className="splash" onClick={() => setPage("welcome")}>
      <h1>차마못한말</h1>
      <CarLogin onClick={goLogin} />
      <div className="heart-sign">
        <span>차에 꽂아두는 마음</span>
        <i />
      </div>
      <button className="splash-hit" aria-label="시작하기" />
    </main>
  );
}
