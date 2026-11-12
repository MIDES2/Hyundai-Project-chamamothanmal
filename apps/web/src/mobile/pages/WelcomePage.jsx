import CarLogin from "../components/CarLogin.jsx";
import Primary from "../components/Primary.jsx";
export default function WelcomePage({ setPage, goLogin }) {
  return (
    <main className="welcome">
      <h1>차마못한말</h1>
      <CarLogin dark onClick={goLogin} />
      <Primary onClick={() => setPage("loading")}>로그인 및 회원가입</Primary>
    </main>
  );
}
