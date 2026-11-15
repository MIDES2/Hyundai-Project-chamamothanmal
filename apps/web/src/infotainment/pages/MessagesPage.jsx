import { Icon } from "../../shared/Icons.jsx";
import Back from "../components/Back.jsx";
export default function MessagesPage({ home, deleted, replay, setDeleted }) {
  return (
    <>
      <Back label="마음 목록" onClick={home} />
      <div className="list-count">〈 {deleted ? 0 : 1} 〉</div>
      {!deleted && (
        <div className="received-message">
          <Icon name="mail" />
          <strong>'엄마'의 메시지</strong>
          <button aria-label="엄마의 메시지 재생" onClick={replay}>
            <span className="triangle" />
          </button>
          <button
            className="remove-message"
            aria-label="메시지 삭제"
            onClick={() => setDeleted(true)}
          >
            −
          </button>
        </div>
      )}
    </>
  );
}
