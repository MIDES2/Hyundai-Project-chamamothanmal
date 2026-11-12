export default function EmptyHomePage({ setScreen }) {
  return (
    <>
      <div className="no-message">
        새로운 메시지가 없어요
        <span />
      </div>
      <button className="v-pill" onClick={() => setScreen("list")}>
        전달 받은 마음 목록
      </button>
    </>
  );
}
