import { Icon } from "../../Icons.jsx";
import Back from "../components/Back.jsx";
import RetentionPicker from "../RetentionPicker.jsx";
import { expiresAt } from "../archive.js";
export default function ArchivePage({
  home,
  retention,
  setRetention,
  savedMessages,
  replay,
  removeSaved,
  changeRetention,
}) {
  return (
    <>
      <Back label="보관함" onClick={home} />
      <div className="archive-retention-default">
        <span>기본 보관 기한</span>
        <RetentionPicker
          label="기본 보관 기한"
          value={retention}
          onChange={setRetention}
        />
      </div>
      <div className="list-count">〈 {savedMessages.length} 〉</div>
      <div className="vehicle-saved-list">
        {savedMessages.map((message) => (
          <article className="vehicle-saved-item" key={message.id}>
            <div className="received-message saved-message">
              <Icon name="mail" />
              <strong>'엄마'의 메시지</strong>
              <button aria-label="보관한 메시지 재생" onClick={replay}>
                <span className="triangle" />
              </button>
              <button
                className="remove-message vehicle-delete-saved"
                aria-label="보관한 메시지 삭제"
                onClick={() => removeSaved(message.id)}
              >
                −
              </button>
            </div>
            <div className="saved-retention-row">
              <RetentionPicker
                label="메시지 보관 기한"
                value={message.retention}
                onChange={(value) => changeRetention(message.id, value)}
              />
            </div>
            <p className="saved-expiry">
              {expiresAt(message) === null
                ? "영구 보관"
                : new Intl.DateTimeFormat("ko-KR", {
                    timeZone: "Asia/Seoul",
                    year: "numeric",
                    month: "numeric",
                    day: "numeric",
                    hour: "2-digit",
                    minute: "2-digit",
                  }).format(new Date(expiresAt(message))) + "까지 보관"}
            </p>
          </article>
        ))}
        {savedMessages.length === 0 && (
          <p className="vehicle-archive-empty">보관한 메시지가 없어요.</p>
        )}
      </div>
    </>
  );
}
