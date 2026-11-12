import { useState } from "react";
import AccountHeader from "../components/AccountHeader.jsx";
import { Icon } from "../../shared/Icons.jsx";
import { findProfile, roleName } from "../accounts.js";
export default function FriendsPage({
  profile,
  profiles,
  onBack,
  onLogin,
  onConnect,
  onSelect,
  initialQuery = "",
}) {
  const [query, setQuery] = useState(initialQuery),
    [result, setResult] = useState(() => findProfile(profiles, initialQuery)),
    [searched, setSearched] = useState(Boolean(initialQuery)),
    [message, setMessage] = useState("");
  const friends = profiles.filter((p) => profile.friendIds.includes(p.id));
  function search(e) {
    e.preventDefault();
    setMessage("");
    setResult(findProfile(profiles, query));
    setSearched(true);
  }
  function connect() {
    const error = onConnect(result.id);
    if (error) setMessage(error);
    else {
      setResult(null);
      setSearched(false);
      setQuery("");
      setMessage("친구로 연결했습니다. 전달 대상을 선택해주세요.");
    }
  }
  return (
    <>
      <AccountHeader
        title="친구 추가 · 연결"
        onBack={onBack}
        onLogin={onLogin}
      />
      <main className="account-page friends-page">
        <div className="my-profile">
          <strong>{profile.name}</strong>
          <span>{roleName(profile.role)} 계정</span>
          <small>{profile.handle}</small>
        </div>
        <h1>함께 마음을 나눌 사람</h1>
        <form className="friend-search" onSubmit={search}>
          <label htmlFor="friend-query">
            상대의 이메일/아이디 또는 전화번호
          </label>
          <div>
            <input
              id="friend-query"
              value={query}
              placeholder="아이디 또는 전화번호 검색"
              onChange={(e) => {
                setQuery(e.target.value);
                setSearched(false);
                setMessage("");
              }}
            />
            <button type="submit" aria-label="친구 검색">
              <Icon name="search" />
            </button>
          </div>
        </form>
        {searched &&
          (result ? (
            <div className="friend-card search-friend">
              <div>
                <strong>{result.name}</strong>
                <small>
                  {roleName(result.role)} · {result.handle}
                </small>
              </div>
              <button onClick={connect}>친구 추가</button>
            </div>
          ) : (
            <p className="account-help" role="status">
              계정을 찾지 못했어요. 먼저 이 브라우저에서 상대 계정으로
              회원가입해주세요.
            </p>
          ))}
        {message && (
          <p className="friend-message" role="status">
            {message}
          </p>
        )}
        <h2>
          연결된 친구 <span>{friends.length}</span>
        </h2>
        {friends.length ? (
          friends.map((friend) => (
            <div className="friend-card" key={friend.id}>
              <div>
                <strong>{friend.name}</strong>
                <small>
                  {roleName(friend.role)} · {friend.handle}
                </small>
              </div>
              <button onClick={() => onSelect(friend)}>
                전달 대상으로 선택
              </button>
            </div>
          ))
        ) : (
          <p className="account-help">
            연결된 친구가 없어요.
            <br />
            {profile.role === "driver" ? "녹음자" : "운전자"} 계정을 검색해
            추가해주세요.
          </p>
        )}
        <p className="account-help">
          친구 연결은 이 브라우저의 가입 계정끼리 적용됩니다.
          <br />
          다른 기기와의 연결·메시지 전송은 서버 연동 후 제공됩니다.
        </p>
      </main>
    </>
  );
}
