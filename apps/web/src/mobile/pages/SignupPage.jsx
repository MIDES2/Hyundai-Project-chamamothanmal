import { useState } from "react";
import AccountHeader from "../components/AccountHeader.jsx";
import { digits } from "../accounts.js";
export default function SignupPage({ onBack, onLogin, onRegister }) {
  const [form, setForm] = useState({
    role: "recorder",
    name: "",
    handle: "",
    phone: "",
    password: "",
    confirm: "",
  });
  const [error, setError] = useState("");
  const field = (key, value) => {
    setForm((f) => ({ ...f, [key]: value }));
    setError("");
  };
  async function submit(e) {
    e.preventDefault();
    if (!form.name.trim()) return setError("이름을 입력해주세요.");
    if (!form.handle.trim() && !form.phone.trim())
      return setError("이메일/아이디 또는 전화번호를 입력해주세요.");
    if (/\s/.test(form.handle.trim()))
      return setError("이메일/아이디에 공백을 넣을 수 없습니다.");
    if (form.phone && !/^\d{10,11}$/.test(digits(form.phone)))
      return setError("전화번호를 10~11자리로 입력해주세요.");
    if (form.password.length < 8)
      return setError("비밀번호를 8자 이상 입력해주세요.");
    if (form.password !== form.confirm)
      return setError("비밀번호가 일치하지 않습니다.");
    const result = await onRegister(form);
    if (result) setError(result);
  }
  return (
    <>
      <AccountHeader title="회원가입" onBack={onBack} onLogin={onLogin} />
      <main className="account-page">
        <h1>어떤 계정으로 함께할까요?</h1>
        <form onSubmit={submit} noValidate>
          <fieldset className="role-options">
            <legend>계정 유형</legend>
            {[
              ["driver", "운전자 계정", "차량으로 마음을 받는 사람"],
              ["recorder", "녹음자 계정", "목소리로 마음을 전하는 사람"],
            ].map(([role, title, description]) => (
              <label
                key={role}
                className={form.role === role ? "selected" : ""}
              >
                <input
                  type="radio"
                  name="account-role"
                  value={role}
                  checked={form.role === role}
                  onChange={() => field("role", role)}
                />
                <strong>{title}</strong>
                <small>{description}</small>
              </label>
            ))}
          </fieldset>
          <label htmlFor="signup-name">이름</label>
          <input
            id="signup-name"
            value={form.name}
            maxLength={30}
            autoComplete="name"
            onChange={(e) => field("name", e.target.value)}
          />
          <label htmlFor="signup-handle">이메일/아이디</label>
          <input
            id="signup-handle"
            value={form.handle}
            maxLength={100}
            autoComplete="username"
            placeholder="이메일 또는 사용할 아이디"
            onChange={(e) => field("handle", e.target.value)}
          />
          <label htmlFor="signup-phone">전화번호</label>
          <input
            id="signup-phone"
            type="tel"
            value={form.phone}
            maxLength={20}
            autoComplete="tel"
            placeholder="아이디 또는 전화번호로 친구를 연결해요"
            onChange={(e) => field("phone", e.target.value)}
          />
          <label htmlFor="signup-password">비밀번호</label>
          <input
            id="signup-password"
            type="password"
            value={form.password}
            autoComplete="new-password"
            onChange={(e) => field("password", e.target.value)}
          />
          <label htmlFor="signup-confirm">비밀번호 확인</label>
          <input
            id="signup-confirm"
            type="password"
            value={form.confirm}
            autoComplete="new-password"
            onChange={(e) => field("confirm", e.target.value)}
          />
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <button className="primary account-submit">회원가입</button>
          <p className="account-help">
            로컬 통합 서버에 계정을 등록합니다.
            <br />
            전화번호 소유 인증과 Pleos 계정 인증은 제공하지 않습니다.
          </p>
        </form>
      </main>
    </>
  );
}
