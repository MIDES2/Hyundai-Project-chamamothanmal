import { Icon } from "../../Icons.jsx";
export default function OfferPage({ start, receiving, connected, giveUp }) {
  return (
    <>
      <div className="new-message private-message">
        <div className="private-message-title">
          <Icon name="mail" />
          <strong>새로운 메시지 1개</strong>
        </div>
        <span>-혼자 듣는 메세지가 도착했습니다 -</span>
      </div>
      <button
        className="v-pill receive"
        onClick={start}
        disabled={!receiving || !connected}
      >
        <span className="action-chevron" aria-hidden="true">
          &gt;
        </span>
        오늘의 마음 듣기
      </button>
      <button className="v-pill give-up" onClick={giveUp}>
        <span className="action-chevron" aria-hidden="true">
          &gt;
        </span>
        포기하기
      </button>
    </>
  );
}
