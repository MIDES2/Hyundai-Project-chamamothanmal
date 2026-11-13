import SplashPage from "./pages/SplashPage.jsx";
import WelcomePage from "./pages/WelcomePage.jsx";
import LoadingPage from "./pages/LoadingPage.jsx";
import LoginPage from "./pages/LoginPage.jsx";
import HomePage from "./pages/HomePage.jsx";
import WaitingPage from "./pages/WaitingPage.jsx";
import ArchivePage from "./pages/ArchivePage.jsx";
import MyPage from "./pages/MyPage.jsx";
import NotificationList from "./Notifications.jsx";
import { useEffect, useRef, useState } from "react";
import { Icon } from "../shared/Icons.jsx";
import CarLogin from "./components/CarLogin.jsx";
import SignupPage from "./pages/SignupPage.jsx";
import FriendsPage from "./pages/FriendsPage.jsx";
import { roleName, digits } from "./accounts.js";
import { request, setAccessToken, toCard } from "../shared/api.js";

const today = () => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const initial = () => ({ audience: "shared", vehicle: "들어줄레이", place: "", recipient: "", recipientId: "", date: today() });
export default function App() {
  const [page, setPage] = useState("splash"), [tab, setTab] = useState("home");
  const [draft, setDraft] = useState(initial), [clip, setClip] = useState(null);
  const [waiting, setWaiting] = useState([]), [archived, setArchived] = useState([]);
  const [modal, setModal] = useState(null), [email, setEmail] = useState(""), [password, setPassword] = useState("");
  const [visible, setVisible] = useState(false), [notice, setNotice] = useState("");
  const [profile, setProfile] = useState(null), [friends, setFriends] = useState([]), [friendQuery, setFriendQuery] = useState("");
  const [busy, setBusy] = useState(false), [screenError, setScreenError] = useState("");
  const accountId = profile?.id;
  const profiles = profile ? [profile, ...friends] : [];
  const editing = null;
  const audioUrls = useRef(new Map()), generation = useRef(0), upload = useRef(null);
  const notifications = waiting.filter(m => m.completedAt).map(m => ({ id: m.id, ownerId: accountId, title: "마음이 재생됐어요", body: m.place, createdAt: m.completedAt, read: true }));
  const accountNotifications = notifications, hasUnread = false;
  function clearAudio() { for (const url of audioUrls.current.values()) URL.revokeObjectURL(url); audioUrls.current.clear(); }
  useEffect(() => () => clearAudio(), []);
  useEffect(() => {
    const url=clip?.url;
    return () => { if(url && ![...audioUrls.current.values()].includes(url)) URL.revokeObjectURL(url); };
  }, [clip?.url]);
  useEffect(() => {
    if (page !== "loading") return;
    const timer = setTimeout(() => setPage("login"), 850); return () => clearTimeout(timer);
  }, [page]);
  async function refresh(expected = generation.current) {
    const [self, linked, sent, archive] = await Promise.all([request("/profile"), request("/friends/profiles"), request("/friend-messages/sent"), request("/friend-messages/archive")]);
    if (expected !== generation.current) return;
    setProfile(self); setFriends(linked); setWaiting(sent.map(m => ({ ...toCard(m, linked), url: audioUrls.current.get(m.id) })));
    setArchived(archive.map(m => ({ ...toCard(m, linked), url: audioUrls.current.get(m.id) })));
  }
  useEffect(() => {
    if (page !== "main" || !profile) return;
    const current = generation.current;
    refresh(current).catch(error => { if (current === generation.current) setScreenError(error.message); });
  }, [tab, page]);
  function goLogin() {
    if (profile) request("/auth/logout", { method: "POST" }).catch(() => {});
    generation.current++; setAccessToken(null); setProfile(null); setFriends([]); setWaiting([]); setArchived([]);
    clearAudio(); setClip(null); setDraft(initial()); setPassword(""); setModal(null); setPage("login"); setScreenError(""); upload.current = null;
  }
  async function login() {
    if (busy) return;
    setBusy(true); setNotice(""); const current = ++generation.current;
    try {
      const result = await request("/auth/login", { method: "POST", body: { login: email.trim(), password } });
      if (current !== generation.current) return;
      setAccessToken(result.accessToken); setPassword(""); await refresh(current);
      if (current === generation.current) { setPage("main"); setTab("home"); setScreenError(""); }
    } catch (error) { if (current === generation.current) { setAccessToken(null); setNotice(error.message); } }
    finally { setBusy(false); }
  }
  async function register(form) {
    try {
      await request("/auth/register", { method: "POST", body: form });
      setEmail(form.handle.trim() || digits(form.phone)); setPassword(""); setNotice("회원가입이 완료됐어요. 로그인해주세요."); setPage("login"); return null;
    } catch (error) { return error.message; }
  }
  async function searchFriend(query) { return (await request(`/accounts/search?query=${encodeURIComponent(query.trim())}`))[0]; }
  async function connect(otherId) {
    try { await request("/friends", { method: "PUT", body: { accountId: otherId } }); await refresh(); return null; }
    catch (error) { return error.message; }
  }
  function selectFriend(friend) { setDraft(d => ({ ...d, recipient: friend.name, recipientId: friend.id })); setPage("main"); setTab("home"); }
  async function submit() {
    if (busy) return;
    const point = draft.placeDetails;
    if (!Number.isFinite(point?.latitude) || !Number.isFinite(point?.longitude)) throw new Error("검색 결과나 좌표가 있는 장소를 선택해주세요.");
    if (!clip?.blob || clip.blob.type !== "audio/wav") throw new Error("WAV 음성을 준비해주세요.");
    if (draft.date < today()) throw new Error("오늘 이후의 수신 시작일을 선택해주세요.");
    const metadata = { recipientId: draft.recipientId, placeName: draft.place, address: point.roadAddress || point.address || "",
      latitude: point.latitude, longitude: point.longitude, listeningMode: draft.audience === "private" ? "PRIVATE" : "TOGETHER", scheduledDate: draft.date };
    const fingerprint = JSON.stringify(metadata);
    if (!upload.current || upload.current.fingerprint !== fingerprint || upload.current.blob !== clip.blob)
      upload.current = { key: crypto.randomUUID(), fingerprint, blob: clip.blob };
    const form = new FormData(); form.append("message", new Blob([fingerprint], { type: "application/json" })); form.append("audio", clip.blob, "voice.wav");
    const current = generation.current; setBusy(true);
    try {
      const result = await request("/friend-messages", { method: "POST", body: form, key: upload.current.key });
      if (current !== generation.current) return;
      audioUrls.current.set(result.id, clip.url); upload.current = null; setClip(null); setDraft(initial());
      await refresh(current); if (current === generation.current) setTab("waiting");
    } finally { setBusy(false); }
  }
  async function deleteArchive(ids) {
    try { await Promise.all(ids.map(id => request(`/friend-messages/${id}`, { method: "DELETE" }))); await refresh(); }
    catch (error) { setScreenError(error.message); throw error; }
  }
  async function loadArchiveAudio(id) {
    const current = generation.current;
    const blob = await request(`/friend-messages/${id}/audio`, { binary: true });
    if (current !== generation.current) return;
    const url = URL.createObjectURL(blob); if (audioUrls.current.has(id)) URL.revokeObjectURL(audioUrls.current.get(id));
    audioUrls.current.set(id, url); setArchived(items => items.map(m => m.id === id ? { ...m, url } : m));
  }
  function openNotifications() { setModal("알림"); }
  function edit() { setScreenError("전송된 메시지 수정은 지원하지 않습니다."); }
  const tabs = [
    ["waiting", "sign", "꽂아둔말"],
    ["home", "home", "홈"],
    ["archive", "basket", "보관함"],
  ];
  const shownWaiting = waiting;
  return (
    <div className="phone">
      {page === "splash" ? (
        <SplashPage setPage={setPage} goLogin={goLogin} />
      ) : page === "welcome" ? (
        <WelcomePage setPage={setPage} goLogin={goLogin} />
      ) : page === "loading" ? (
        <LoadingPage />
      ) : page === "signup" ? (
        <SignupPage onBack={goLogin} onLogin={goLogin} onRegister={register} />
      ) : page === "friends" && profile ? (
        <FriendsPage
          initialQuery={friendQuery}
          profile={profile}
          profiles={profiles}
          onBack={() => setPage("main")}
          onLogin={goLogin}
          onSearch={searchFriend}
          onConnect={connect}
          onSelect={selectFriend}
        />
      ) : page === "login" ? (
        <LoginPage
          setPage={setPage}
          goLogin={goLogin}
          login={login}
          busy={busy}
          email={email}
          setEmail={setEmail}
          password={password}
          setPassword={setPassword}
          visible={visible}
          setVisible={setVisible}
          notice={notice}
          setNotice={setNotice}
          setModal={setModal}
        />
      ) : (
        <>
          <header>
            <div className="brand">
              <CarLogin onClick={goLogin} />
              <strong>차마못한말</strong>
            </div>
            <div className="header-actions">
              <button
                className="notification-bell"
                aria-label="알림"
                aria-haspopup="dialog"
                title={hasUnread ? "새 알림이 있어요" : "알림"}
                onClick={openNotifications}
              >
                <Icon name="bell" />
                {hasUnread && (
                  <span
                    className="notification-dot"
                    aria-label="읽지 않은 알림 있음"
                  />
                )}
              </button>
              <button
                aria-label="마이 페이지"
                aria-pressed={tab === "my"}
                onClick={() => setTab("my")}
              >
                <Icon name="user" />
              </button>
            </div>
          </header>
          <main className="main-content">
            {screenError && <p role="alert" className="error">{screenError}</p>}
            <button type="button" onClick={() => refresh().catch(e => setScreenError(e.message))}>새로고침</button>
            <button type="button" onClick={goLogin}>로그아웃</button>
            {tab === "home" ? (
              <HomePage
                busy={busy}
                accountId={accountId}
                draft={draft}
                setDraft={setDraft}
                clip={clip}
                setClip={setClip}
                onSubmit={submit}
                friends={friends}
                onManageFriends={(query) => {
                  setFriendQuery(query || "");
                  setPage("friends");
                }}
                isEditing={Boolean(editing)}
                onCancel={() => {
                  setDraft(initial());
                }}
              />
            ) : tab === "waiting" ? (
              <WaitingPage
                shownWaiting={shownWaiting}
                edit={edit}
                goLogin={goLogin}
              />
            ) : tab === "archive" ? (
              <ArchivePage
                items={archived}
                onDelete={deleteArchive}
                onLoadAudio={loadArchiveAudio}
                onLogin={goLogin}
              />
            ) : (
              <MyPage
                profile={profile}
                setFriendQuery={setFriendQuery}
                setPage={setPage}
                setModal={setModal}
              />
            )}
          </main>
          <nav aria-label="하단 메뉴">
            {tabs.map(([id, icon, title]) => (
              <button
                key={id}
                onClick={() => setTab(id)}
                className={tab === id ? "active" : ""}
                aria-current={tab === id ? "page" : undefined}
              >
                <Icon name={icon} />
                <span>{title}</span>
              </button>
            ))}
          </nav>
        </>
      )}
      {modal && (
        <div className="modal-backdrop" onClick={() => setModal(null)}>
          <section
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-label={modal}
            onClick={(e) => e.stopPropagation()}
          >
            <button
              className="modal-close"
              onClick={() => setModal(null)}
              aria-label="닫기"
            >
              ×
            </button>
            <h2>{modal}</h2>
            {modal === "알림" && (
              <NotificationList items={accountNotifications} />
            )}{" "}
            {modal === "개인정보" && profile && (
              <p className="account-help">
                {profile.name} · {roleName(profile.role)}
                <br />
                {profile.handle}
                {profile.phone && (
                  <>
                    <br />
                    {profile.phone}
                  </>
                )}
              </p>
            )}
            {modal === "비밀번호 변경" && (
              <p className="account-help">
                비밀번호 변경은 실제 인증 서버 연결 후 제공됩니다.
              </p>
            )}
            <button className="modal-confirm" onClick={() => setModal(null)}>
              확인
            </button>
          </section>
        </div>
      )}
    </div>
  );
}
