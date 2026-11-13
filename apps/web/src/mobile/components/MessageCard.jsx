import { Icon } from "../../shared/Icons.jsx";
import Player from "./Player.jsx";
import dateText from "../utils/dateText.js";
export default function MessageCard({
  message,
  waiting,
  onEdit,
  onLogin,
  onLoadAudio,
  selecting = false,
  selected = false,
  onToggle,
}) {
  return (
    <article
      className={
        "message-card " +
        (waiting ? "waiting" : "") +
        (selected ? " archive-selected" : "")
      }
    >
      <div className="card-title">
        <button
          type="button"
          className="card-car-login"
          disabled={selecting}
          aria-label="차량 아이콘 · 로그인으로 이동"
          onClick={onLogin}
        >
          <Icon name="car" />
        </button>
        <strong>{message.vehicle}</strong>
        {waiting && onEdit && <button onClick={() => onEdit(message)}>수정</button>}
      </div>
      <p>
        <Icon name="pin" />
        {message.place}
      </p>
      <p>
        <Icon name="calendar" />
        {dateText(message.date)}
      </p>
      {message.recipientId && (
        <p className="message-recipient">전달 대상 · {message.recipient}</p>
      )}
      {!message.url && onLoadAudio && <button type="button" onClick={() => onLoadAudio(message.id)}>음성 불러오기</button>}
      {message.completedAt && <p>재생 완료{message.reaction ? ` · ${message.reaction}` : ""}</p>}
      <Player compact src={message.url} duration={message.duration} />
      {selecting && (
        <button
          type="button"
          className="archive-select-cover"
          role="checkbox"
          aria-checked={selected}
          aria-label={message.place + " 선택"}
          onClick={() => onToggle(message.id)}
        >
          <span className="archive-check">{selected ? "✓" : " "}</span>
        </button>
      )}
    </article>
  );
}
