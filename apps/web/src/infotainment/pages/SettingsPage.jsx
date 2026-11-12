import Back from "../components/Back.jsx";
export default function SettingsPage({
  home,
  setDetail,
  connected,
  receiving,
  setReceiving,
  detail,
  volume,
  setVolume,
}) {
  return (
    <>
      <Back label="설정" onClick={home} />
      <div className="settings-list">
        <button onClick={() => setDetail("connect")}>
          차량 가족 연결 <span>{connected ? "연결됨" : "연결 해제"} ›</span>
        </button>
        <button onClick={() => setReceiving(!receiving)}>
          메시지 수신 <span>{receiving ? "ON" : "OFF"} ›</span>
        </button>
        <button
          onClick={() => setDetail(detail === "volume" ? null : "volume")}
        >
          메시지 재생 음량 <span>›</span>
        </button>
        {detail === "volume" && (
          <label className="v-volume">
            <span>◖))</span>
            <input
              type="range"
              min="0"
              max="100"
              value={volume}
              onChange={(e) => setVolume(Number(e.target.value))}
              aria-label="메시지 재생 음량"
            />
          </label>
        )}
        <button onClick={() => setDetail("privacy")}>
          개인정보 및 보안 <span>›</span>
        </button>
        <button onClick={() => setDetail("guide")}>
          서비스 이용 안내 <span>›</span>
        </button>
      </div>
    </>
  );
}
