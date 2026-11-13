import SplashPage from "./pages/SplashPage.jsx";
import LoadingPage from "./pages/LoadingPage.jsx";
import LoginPage from "./pages/LoginPage.jsx";
import HomePage from "./pages/HomePage.jsx";
import WaitingPage from "./pages/WaitingPage.jsx";
import ArchivePage from "./pages/ArchivePage.jsx";
import MyPage from "./pages/MyPage.jsx";
import NotificationList, {
  NOTIFICATION_KEY,
  readNotifications,
} from "./Notifications.jsx";
import { useEffect, useRef, useState } from "react";
import { Icon } from "../shared/Icons.jsx";
import CarLogin from "./components/CarLogin.jsx";
import SignupPage from "./pages/SignupPage.jsx";
import FriendsPage from "./pages/FriendsPage.jsx";
import { roleName } from "./accounts.js";
import {
  PROFILE_KEY,
  digits,
  readProfiles,
  findProfile,
  connectProfiles,
} from "./accounts.js";

const initial = {
  audience: "shared",
  vehicle: "들어줄레이",
  place: "한신대학교 정문",
  recipient: "둘째",
  date: "2026-11-15",
};
const SESSION_KEY = "chama-ui-session-v1";
function restoreAccount(profiles) {
  try {
    const id = localStorage.getItem(SESSION_KEY);
    return profiles.some((profile) => profile.id === id) ? id : null;
  } catch { return null; }
}
const sample = [
  {
    ...initial,
    id: "one",
    date: "2026-11-14",
    place: "수원역 ak플라자",
    duration: 12,
  },
  {
    ...initial,
    id: "two",
    date: "2026-11-10",
    place: "병점역 후문",
    duration: 15,
  },
];
export default function App() {
  const [page, setPage] = useState("splash"),
    [tab, setTab] = useState("home"),
    [draft, setDraft] = useState({
      ...initial,
      recipient: "",
      recipientId: "",
    }),
    [clip, setClip] = useState(null),
    [waiting, setWaiting] = useState([
      { ...initial, id: "waiting-sample", duration: 12 },
    ]),
    [editing, setEditing] = useState(null),
    [modal, setModal] = useState(null),
    [email, setEmail] = useState(""),
    [password, setPassword] = useState(""),
    [visible, setVisible] = useState(false);
  const [profiles, setProfiles] = useState(readProfiles),
    [accountId, setAccountId] = useState(() => restoreAccount(profiles)),
    [notice, setNotice] = useState("");
  useEffect(() => {
    try {
      if (accountId) localStorage.setItem(SESSION_KEY, accountId);
      else localStorage.removeItem(SESSION_KEY);
    } catch { /* Session remains in memory if browser storage is unavailable. */ }
  }, [accountId]);
  const [notifications, setNotifications] = useState(readNotifications);
  const accountNotifications = notifications.filter(
    (n) => n.ownerId === accountId,
  );
  const hasUnread = accountNotifications.some((n) => !n.read);
  const [friendQuery, setFriendQuery] = useState("");
  const [deletedArchive, setDeletedArchive] = useState(() => {
    try {
      const value = JSON.parse(
        localStorage.getItem("chama-ui-deleted-archive-v1") || "{}",
      );
      return value && typeof value === "object" && !Array.isArray(value)
        ? Object.fromEntries(
            Object.entries(value)
              .filter(([, ids]) => Array.isArray(ids))
              .map(([id, ids]) => [
                id,
                ids.filter((v) => typeof v === "string"),
              ]),
          )
        : {};
    } catch {
      return {};
    }
  });
  useEffect(() => {
    try {
      localStorage.setItem(
        "chama-ui-deleted-archive-v1",
        JSON.stringify(deletedArchive),
      );
    } catch {
      /* In-memory deletion still works. */
    }
  }, [deletedArchive]);
  const archived = sample.filter(
    (item) => !(deletedArchive[accountId] || []).includes(item.id),
  );
  function deleteArchive(ids) {
    setDeletedArchive((current) => ({
      ...current,
      [accountId]: [...new Set([...(current[accountId] || []), ...ids])],
    }));
  }
  const profile = profiles.find((p) => p.id === accountId);
  const friends = profile
    ? profiles.filter((p) => profile.friendIds.includes(p.id))
    : [];
  const ownedUrls = useRef(new Set());
  useEffect(() => {
    if (clip?.url) ownedUrls.current.add(clip.url);
  }, [clip]);
  useEffect(() => {
    const urls = ownedUrls.current;
    return () => urls.forEach((url) => URL.revokeObjectURL(url));
  }, []);
  useEffect(() => {
    try {
      localStorage.setItem(PROFILE_KEY, JSON.stringify(profiles));
    } catch {
      /* UI still works in memory when storage is unavailable. */
    }
  }, [profiles]);
  useEffect(() => {
    if (page !== "loading") return;
    const timeout = setTimeout(() => setPage("login"), 850);
    return () => clearTimeout(timeout);
  }, [page]);
  useEffect(() => {
    try {
      localStorage.setItem(NOTIFICATION_KEY, JSON.stringify(notifications));
    } catch {
      /* Keep notifications in memory when storage is unavailable. */
    }
  }, [notifications]);
  function notify(ownerId, title, body) {
    const item = {
      id:
        "notification-" +
        Date.now() +
        "-" +
        Math.random().toString(36).slice(2),
      ownerId,
      title,
      body,
      createdAt: Date.now(),
      read: modal === "알림" && ownerId === accountId,
    };
    setNotifications((items) => [item, ...items].slice(0, 500));
  }
  function openNotifications() {
    setNotifications((items) =>
      items.map((n) => (n.ownerId === accountId ? { ...n, read: true } : n)),
    );
    setModal("알림");
  }
  function goLogin() {
    setModal(null);
    if (profile) setEmail(profile.handle);
    setVisible(false);
    setPage("login");
  }
  function startApp() {
    setModal(null);
    setTab("home");
    setPage(profile ? "main" : "loading");
  }
  function logout() {
    setAccountId(null);
    setPassword("");
    setEmail("");
    setVisible(false);
    setDraft({ ...initial, recipient: "", recipientId: "" });
    setClip(null);
    setEditing(null);
    setModal(null);
    setNotice("");
    setTab("home");
    setPage("splash");
    try { localStorage.removeItem(SESSION_KEY); } catch { /* In-memory logout still applies. */ }
  }
  function login() {
    if (profile && !password && findProfile(profiles, email)?.id === profile.id) {
      setNotice("");
      setPage("main");
      setTab("home");
      return;
    }
    if (!email.trim() || !password.trim()) return;
    let match = findProfile(profiles, email);
    if (!match) {
      match = {
        id: "demo-" + Date.now() + "-" + Math.random().toString(36).slice(2),
        name: email.trim(),
        handle: email.trim(),
        phone: "",
        role: "recorder",
        friendIds: [],
      };
      setProfiles((p) => [...p, match]);
    }
    if (match.id !== accountId) {
      setDraft({ ...initial, recipient: "", recipientId: "" });
      setClip(null);
      setEditing(null);
    }
    setAccountId(match.id);
    setNotice("");
    setPage("main");
    setTab("home");
  }
  function register(form) {
    if (
      (form.handle.trim() && findProfile(profiles, form.handle)) ||
      (form.phone && findProfile(profiles, form.phone))
    )
      return "이미 가입된 이메일/아이디 또는 전화번호입니다.";
    const phone = digits(form.phone);
    const account = {
      id: "account-" + Date.now() + "-" + Math.random().toString(36).slice(2),
      name: form.name.trim(),
      handle: form.handle.trim() || phone,
      phone,
      role: form.role,
      friendIds: [],
    };
    setProfiles((p) => [...p, account]);
    notify(
      account.id,
      "회원가입이 완료됐어요",
      roleName(account.role) + " 계정으로 마음을 나눌 준비가 됐어요.",
    );
    setEmail(account.handle);
    setPassword(form.password);
    setNotice("회원가입이 완료되었습니다. 로그인해주세요.");
    setPage("login");
    return null;
  }
  function connect(otherId) {
    const result = connectProfiles(profiles, accountId, otherId);
    if (result.error) return result.error;
    setProfiles(result.profiles);
    const other = profiles.find((p) => p.id === otherId);
    notify(
      accountId,
      "친구가 연결됐어요",
      other.name + "님을 전달 대상으로 선택할 수 있어요.",
    );
    notify(
      otherId,
      "친구가 연결됐어요",
      profile.name + "님과 친구로 연결됐어요.",
    );
    return null;
  }
  function selectFriend(friend) {
    setDraft((d) => ({ ...d, recipient: friend.name, recipientId: friend.id }));
    setPage("main");
    setTab("home");
  }
  function submit() {
    const item = {
      ...draft,
      ...clip,
      id: editing || String(Date.now()),
      senderId: accountId,
    };
    setWaiting((items) =>
      editing
        ? items.map((m) => (m.id === editing ? item : m))
        : [...items, item],
    );
    notify(
      accountId,
      editing ? "마음이 수정됐어요" : "마음이 잘 등록됐어요",
      draft.recipient +
        "님에게 전할 마음을 " +
        draft.place +
        "에 꽂아두었어요.",
    );
    setClip(null);
    setEditing(null);
    setTab("waiting");
  }
  function edit(message) {
    setDraft({
      audience: message.audience || "shared",
      vehicle: message.vehicle,
      place: message.place,
      placeDetails: message.placeDetails || null,
      date: message.date,
      recipient: message.recipient,
      recipientId: message.recipientId || "",
    });
    setClip(
      message.url
        ? { url: message.url, duration: message.duration, blob: message.blob }
        : null,
    );
    setEditing(message.id);
    setTab("home");
  }
  const tabs = [
    ["waiting", "sign", "꽂아둔말"],
    ["home", "home", "홈"],
    ["archive", "basket", "보관함"],
  ];
  const shownWaiting = waiting.filter(
    (m) => !m.senderId || m.senderId === accountId,
  );
  return (
    <div className="phone">
      {page === "splash" ? (
        <SplashPage onStart={startApp} goLogin={goLogin} loggedIn={Boolean(profile)} onLogout={logout} />
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
          onConnect={connect}
          onSelect={selectFriend}
        />
      ) : page === "login" ? (
        <LoginPage
          setPage={setPage}
          goLogin={goLogin}
          login={login}
          email={email}
          setEmail={setEmail}
          password={password}
          sessionActive={Boolean(profile && findProfile(profiles, email)?.id === profile.id)}
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
            {tab === "home" ? (
              <HomePage
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
                  setEditing(null);
                  setDraft({ ...initial, recipient: "", recipientId: "" });
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
            {modal === "아이디 찾기" && <p className="account-help">아이디 찾기는 실제 인증 서버 연결 후 제공됩니다.</p>}
            {(modal === "비밀번호 변경" || modal === "비밀번호 찾기/변경") && (
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
