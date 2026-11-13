import { Icon } from "../../shared/Icons.jsx";
import CarLogin from "../components/CarLogin.jsx";
import Primary from "../components/Primary.jsx";
export default function LoginPage({
  setPage,
  goLogin,
  login,
  email,
  setEmail,
  password,
  sessionActive,
  setPassword,
  visible,
  setVisible,
  notice,
  setNotice,
  setModal,
}) {
  return (
    <>
      <div className="back-header">
        <button onClick={() => setPage("splash")} aria-label="뒤로">
          <Icon name="back" />
        </button>
      </div>
      <main className="login">
        <div className="login-logo">
          <CarLogin onClick={goLogin} />
          <h1>차마못한말</h1>
        </div>
        <form
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            login();
          }}
        >
          <div className="login-input">
            <Icon name="mail" />
            <input
              aria-label="이메일/아이디"
              type="text"
              placeholder="이메일/아이디"
              value={email}
              autoComplete="username"
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="login-input">
            <Icon name="lock" />
            <input
              aria-label="비밀번호"
              type={visible && password ? "text" : "password"}
              placeholder="비밀번호"
              value={password || (sessionActive ? "••••••••" : "")}
              autoComplete="current-password"
              onChange={(e) => setPassword(e.target.value)}
              onFocus={(e) => { if (sessionActive && !password) e.currentTarget.select(); }}
              required
            />
            <button
              type="button"
              disabled={sessionActive && !password}
              onClick={() => setVisible(!visible)}
              aria-label={visible ? "비밀번호 숨기기" : "비밀번호 표시"}
            >
              ◉
            </button>
          </div>
          <div className="login-links">
            <button
              type="button"
              onClick={() => setModal("아이디 찾기")}
            >
              아이디 찾기
            </button>
            <span> | </span>
            <button type="button" onClick={() => setModal("비밀번호 찾기/변경")}>
              비밀번호 찾기/변경
            </button>
          </div>
          {notice && (
            <p className="signup-notice" role="status">
              {notice}
            </p>
          )}
          <Primary disabled={!email.trim() || (!password.trim() && !sessionActive)}>로그인</Primary>
        </form>
        <div className="login-divider" />
        <button
          type="button"
          className="pleos-login"
          onClick={() => {
            if (email.trim() && (password.trim() || sessionActive)) login();
            else
              setNotice(
                "이메일/아이디와 비밀번호를 입력해주세요. 실제 Pleos 인증은 연결 전입니다.",
              );
          }}
        >
          Pleos 계정 로그인
        </button>
        <div className="login-divider" />
        <p className="account-note">
          운전자 계정과 녹음자 계정으로 가입하고
          <br />
          아이디 또는 전화번호로 친구를 연결해보세요.
          <br />
          <button onClick={() => setPage("signup")}>회원가입&gt;</button>
        </p>
        <p className="account-help login-help">
          실제 인증 없이 동작하는 UI 시연입니다.
        </p>
      </main>
    </>
  );
}
