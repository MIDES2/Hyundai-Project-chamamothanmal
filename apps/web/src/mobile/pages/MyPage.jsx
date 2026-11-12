import { Icon } from "../../shared/Icons.jsx";
import { roleName } from "../accounts.js";
export default function MyPage({ profile, setFriendQuery, setPage, setModal }) {
  return (
    <>
      <div className="profile-summary">
        <strong>{profile?.name}</strong>
        <span>{profile ? roleName(profile.role) + " 계정" : ""}</span>
      </div>
      <div className="my-menu">
        {[
          ["user", "개인정보"],
          ["user", "친구 추가 · 연결"],
          ["settings", "환경설정"],
          ["terms", "이용약관"],
          [null, "Pleos 이동하기"],
        ].map(([icon, title]) => (
          <button
            key={title}
            onClick={() =>
              title === "친구 추가 · 연결"
                ? (setFriendQuery(""), setPage("friends"))
                : setModal(title)
            }
          >
            {icon ? <Icon name={icon} /> : <span className="pleos-p">P</span>}
            {title}
          </button>
        ))}
        <div />
        <div />
      </div>
    </>
  );
}
