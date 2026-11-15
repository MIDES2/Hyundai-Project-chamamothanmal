import { useEffect, useRef, useState } from "react";
import { request, setAccessToken } from "../shared/api.js";
import { Car, Icon } from "../shared/Icons.jsx";
import StatusBar from "./StatusBar.jsx";

// Browser preview has no vehicle SDK. Every vehicle condition below is explicitly MOCK.
export default function ConnectedVehicleApp() {
  const [profile, setProfile] = useState(null), [login, setLogin] = useState(""), [password, setPassword] = useState("");
  const [inbox, setInbox] = useState([]), [archive, setArchive] = useState([]), [page, setPage] = useState("inbox");
  const [selected, setSelected] = useState(null), [forceConfirm, setForceConfirm] = useState(false);
  const [conditions, setConditions] = useState({ arrived: false, parked: false, closed: false, ux: false });
  const [status, setStatus] = useState("운전자 계정으로 로그인해주세요."), [busy, setBusy] = useState(false), [playing, setPlaying] = useState(false);
  const [progress, setProgress] = useState(0), [duration, setDuration] = useState(0), [pending, setPending] = useState(null);
  const playingMessage = useRef(null);
  const audio = useRef(null), audioUrl = useRef(null), generation = useRef(0), attempted = useRef(new Set()), current = useRef(null), safeRef = useRef(false), playingRef = useRef(false);
  const safe = Boolean(profile && conditions.parked && conditions.closed && conditions.ux && (selected?.completedAt || conditions.arrived));
  safeRef.current = safe; current.current = selected;
  function stop(message) {
    generation.current++; playingMessage.current = null; audio.current?.pause(); playingRef.current = false; setPlaying(false); setBusy(false);
    if (audio.current) { audio.current.removeAttribute("src"); audio.current.load(); }
    if (audioUrl.current) URL.revokeObjectURL(audioUrl.current); audioUrl.current = null;
    if (message) setStatus(message);
  }
  useEffect(() => () => { audio.current?.pause(); if (audioUrl.current) URL.revokeObjectURL(audioUrl.current); setAccessToken(null); }, []);
  useEffect(() => {
    const hide = () => { if (document.hidden) stop("화면 이탈로 중단했습니다. 수동으로 다시 시작해주세요."); };
    document.addEventListener("visibilitychange", hide); return () => document.removeEventListener("visibilitychange", hide);
  }, []);
  useEffect(() => { if (!safe && (playing || busy)) stop("모의 차량 조건 변경 · 중단, 자동 재개 안 함"); }, [safe]);
  useEffect(() => {
    if (!selected || !safe || selected.completedAt || forceConfirm || selected.listeningMode === "PRIVATE" || pending || attempted.current.has(selected.id)) return;
    attempted.current.add(selected.id); play();
  }, [selected, safe, forceConfirm]);
  async function refresh(expected = generation.current) {
    try {
      const [received, saved] = await Promise.all([request("/friend-messages/inbox"), request("/friend-messages/archive")]);
      if (expected !== generation.current) return;
      setInbox(received); setArchive(saved);
    } catch (error) { if (expected === generation.current) { stop(error.message); setInbox([]); setArchive([]); } }
  }
  async function signIn(e) {
    e.preventDefault(); if (busy) return; setBusy(true); const expected = ++generation.current;
    try {
      const result = await request("/auth/login", { method: "POST", body: { login, password } });
      if (expected !== generation.current) return;
      setAccessToken(result.accessToken); setPassword("");
      if (result.profile.role !== "driver") { await request("/auth/logout", { method: "POST" }); setAccessToken(null); throw new Error("운전자 계정으로 로그인해주세요."); }
      setProfile(result.profile); setStatus("로그인 완료 · 받은 메시지를 선택하세요."); await refresh(expected);
    } catch (error) { setAccessToken(null); setStatus(error.message); }
    finally { setBusy(false); }
  }
  function logout() {
    request("/auth/logout", { method: "POST" }).catch(() => {}); stop("로그아웃 완료"); setAccessToken(null); setProfile(null); setSelected(null);
    setInbox([]); setArchive([]); setPending(null); setPassword(""); attempted.current.clear();
  }
  async function play() {
    if (!safeRef.current || !selected || busy || playingRef.current || pending) return;
    attempted.current.add(selected.id); setBusy(true); const expected = generation.current;
    try {
      const blob = await request(`/friend-messages/${selected.id}/audio`, { binary: true });
      if (expected !== generation.current || !safeRef.current) return;
      if (audioUrl.current) URL.revokeObjectURL(audioUrl.current);
      audioUrl.current = URL.createObjectURL(blob); audio.current.src = audioUrl.current;
      playingMessage.current = { id: selected.id, completed: Boolean(selected.completedAt), generation: expected };
      await audio.current.play(); setStatus("실제 서버 음성 재생 중 · 차량 조건은 MOCK");
    } catch (error) { if (expected === generation.current) setStatus(error.message || "자동 재생이 차단됐어요. 듣기 버튼을 눌러주세요."); }
    finally { if (expected === generation.current) setBusy(false); }
  }
  async function report(id) {
    const expected = generation.current;
    try {
      const completed = await request(`/friend-messages/${id}/completion`, { method: "PUT" });
      if (expected !== generation.current) return;
      setPending(null); setSelected(completed); setStatus("완료 보고 확인 · 보관함에 7일 저장됐어요. 이모지 답장은 선택입니다."); await refresh(expected);
    } catch (error) { if (expected === generation.current) setStatus(`완료 보고 실패: ${error.message} · 재시도해주세요.`); }
  }
  async function ended() {
    playingRef.current = false; setPlaying(false);
    const message = playingMessage.current; playingMessage.current = null;
    if (!message || message.generation !== generation.current) return;
    if (message.completed) return setStatus("보관 음성 재생 완료");
    setPending(message.id); await report(message.id);
  }
  async function remove() {
    if (!conditions.parked || !conditions.ux || !selected || !window.confirm("이 메시지를 삭제할까요?")) return;
    const id = selected.id; stop("삭제 중"); const expected = generation.current;
    try { await request(`/friend-messages/${id}`, { method: "DELETE" }); if (expected === generation.current) { setSelected(null); setStatus("삭제했어요."); await refresh(expected); } }
    catch (error) { setStatus(error.message); }
  }
  async function react(emoji) {
    if (!conditions.parked || !conditions.ux || !selected?.completedAt || pending) return;
    try { await request(`/friend-messages/${selected.id}/reaction`, { method: "PUT", body: { emoji } }); setStatus("답장을 보냈습니다"); }
    catch (error) { setStatus(error.message); }
  }
  const rows = page === "archive" ? archive : inbox;
  return <main className="vehicle-display" aria-label="차마못한말 서버 연결 차량 웹">
    <StatusBar />
    <section className="vehicle-panel connected-panel">
      <div className="v-header"><div className="v-brand"><Car /><strong>차마못한말</strong></div>
        {profile && <div className="v-header-actions"><button onClick={() => { stop(); setSelected(null); setPage("inbox"); refresh(); }}>받은 마음</button><button onClick={() => { stop(); setSelected(null); setPage("archive"); refresh(); }}>보관함</button><button onClick={logout}>로그아웃</button></div>}
      </div>
      <p className="mock-banner">브라우저 차량 미리보기 · SDK 미연결 · 차량 조건은 아래에서 명시적으로 모의 설정합니다.</p>
      <p role="status">{status}</p>
      {!profile ? <form className="vehicle-login" onSubmit={signIn}>
        <h1>운전자 계정 로그인</h1><p>휴대폰 웹에서 등록한 계정으로 로그인하세요.</p>
        <input aria-label="운전자 아이디" autoComplete="username" value={login} onChange={e => setLogin(e.target.value)} placeholder="아이디 또는 전화번호" required />
        <input aria-label="비밀번호" type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} placeholder="비밀번호" required />
        <button className="v-pill" disabled={busy}>로그인</button>
      </form> : <>
        <fieldset className="mock-conditions"><legend>모의 차량 상태 · 실제 신호 아님</legend>
          {[["arrived","목적지 도착"],["parked","P단"],["closed","문 닫힘"],["ux","화면·오디오 사용 가능"]].map(([key,label]) => <label key={key}><input type="checkbox" checked={conditions[key]} onChange={e => setConditions(c => ({ ...c, [key]: e.target.checked }))} />{label}</label>)}
        </fieldset>
        <label><input type="checkbox" checked={forceConfirm} onChange={e => { stop("재생 설정 변경 · 수동으로 다시 시작해주세요."); setForceConfirm(e.target.checked); }} />운전자 설정: 항상 확인 후 재생</label>
        <h1>{page === "archive" ? "보관함 · 기본 7일" : "받은 마음"}</h1><button onClick={() => refresh()}>새로고침</button>
        <div className="vehicle-saved-list">{rows.map(message => <button className="received-message" key={message.id} onClick={() => { stop(); setSelected(message); setProgress(0); setDuration(message.audioSeconds); }}><Icon name="mail" />{message.placeName} · {message.listeningMode === "PRIVATE" ? "재생 전 확인" : "자동 재생"}</button>)}</div>
        {!rows.length && <p>메시지가 없어요. 예약일 전 메시지는 아직 표시되지 않아요.</p>}
        {selected && <section className="connected-playback"><h2>{selected.placeName}</h2>
          <p>{safe ? "모의 재생 조건 충족" : "도착·P단·문 닫힘·사용 조건 확인 후 재생 가능"}</p>
          <p>{selected.listeningMode === "PRIVATE" || forceConfirm ? "확인 후 듣기를 눌러주세요. 소리는 동승자에게도 들릴 수 있어요." : "자동 재생이 차단되면 듣기를 눌러주세요."}</p>
          <progress max={duration || 1} value={progress} aria-label="실제 음성 재생 진행" /><p>{progress.toFixed(1)} / {duration.toFixed(1)}초</p>
          <button className="v-pill" disabled={!safe || busy || playing || Boolean(pending)} onClick={play}>확인 후 듣기 / 다시 듣기</button>
          <button className="v-pill" onClick={() => stop("재생 중단 · 완료로 기록하지 않음")}>멈춤</button>
          <button className="v-pill" disabled={!conditions.parked || !conditions.ux} onClick={remove}>삭제</button>
          {selected.completedAt && <div className="reaction-options">{["👍","🥹","♥️"].map(emoji => <button disabled={!conditions.parked || !conditions.ux} key={emoji} onClick={() => react(emoji)}>{emoji}</button>)}</div>}
        </section>}
        {pending && <button className="v-pill" onClick={() => report(pending)}>완료 보고 재시도</button>}
      </>}
      <audio ref={audio} onPlay={() => { playingRef.current = true; setPlaying(true); }} onPause={() => { playingRef.current = false; setPlaying(false); }}
        onTimeUpdate={e => setProgress(e.currentTarget.currentTime)} onLoadedMetadata={e => setDuration(e.currentTarget.duration)}
        onEnded={ended} onError={() => setStatus("음성 파일을 재생할 수 없습니다. 새로고침 후 다시 시도해주세요.")} />
    </section>
  </main>;
}
