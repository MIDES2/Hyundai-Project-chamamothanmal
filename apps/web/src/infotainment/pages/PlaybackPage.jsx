import { Icon } from "../../shared/Icons.jsx";
export default function PlaybackPage({
  home,
  playing,
  progress,
  total,
  setProgress,
  setPlaying,
  played,
  isSaved,
  setScreen,
  title = "'엄마'의 메시지예요",
  onToggle,
}) {
  return (
    <>
      <div className="v-screen-head">
        <h1>
          {title} <Icon name="mail" />
        </h1>
        <button aria-label="홈" onClick={home}>
          <Icon name="home" />
        </button>
      </div>
      <div className="v-audio">
        <button
          className="v-play-toggle"
          aria-label={playing ? "일시 정지" : "재생"}
          onClick={onToggle || (() => {
            if (progress >= total) setProgress(0);
            setPlaying(!playing);
          })}
        >
          {playing ? (
            <span className="pause-mark">
              <i />
              <i />
            </span>
          ) : (
            <span className="triangle" />
          )}
        </button>
        <div className={"v-wave " + (playing ? "moving" : "")}>
          {Array.from({ length: 16 }, (_, i) => (
            <i
              key={i}
              style={{
                height:
                  [
                    30, 57, 90, 63, 105, 44, 80, 55, 100, 86, 69, 58, 92, 108,
                    64, 33,
                  ][i] + "%",
                animationDelay: i * 0.08 + "s",
                opacity: i / 16 <= progress / total ? 1 : 0.5,
              }}
            />
          ))}
        </div>
      </div>
      <div className="v-duration">
        {Math.floor(progress / 60) + ":" + String(Math.floor(progress % 60)).padStart(2, "0")} / {Math.floor(total / 60) + ":" + String(Math.floor(total % 60)).padStart(2, "0")}
      </div>
      {played && (
        <div className="playback-actions">
          {isSaved && (
            <span className="auto-saved-note" role="status">
              자동 보관됨 ✓
            </span>
          )}
          <button
            className="v-pill reply-link"
            onClick={() => {
              setPlaying?.(false);
              setScreen("reaction");
            }}
          >
            반응하기 ›
          </button>
        </div>
      )}
    </>
  );
}
