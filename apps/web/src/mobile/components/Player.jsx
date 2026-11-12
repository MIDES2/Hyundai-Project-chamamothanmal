import { useEffect, useRef, useState } from "react";
export default function Player({ src, duration = 0, compact = false }) {
  const audio = useRef(null),
    [playing, setPlaying] = useState(false),
    [current, setCurrent] = useState(0),
    [length, setLength] = useState(duration),
    [error, setError] = useState("");
  useEffect(() => {
    setCurrent(0);
    setPlaying(false);
    setLength(duration);
    setError("");
  }, [src, duration]);
  const format = (n) =>
    Math.floor(n / 60) + ":" + String(Math.floor(n % 60)).padStart(2, "0");
  async function play() {
    if (!src) return;
    try {
      if (playing) audio.current.pause();
      else await audio.current.play();
    } catch {
      setError("음성을 재생할 수 없습니다.");
    }
  }
  return (
    <div className={"player" + (compact ? " compact" : "")}>
      {src && (
        <audio
          ref={audio}
          src={src}
          onLoadedMetadata={(e) => {
            if (Number.isFinite(e.currentTarget.duration))
              setLength(e.currentTarget.duration);
          }}
          onTimeUpdate={(e) => setCurrent(e.currentTarget.currentTime)}
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onEnded={() => {
            setPlaying(false);
            setCurrent(0);
          }}
          onError={() => setError("음성을 재생할 수 없습니다.")}
        />
      )}
      <div className="progress-line">
        <div
          style={{
            width: (length ? Math.min((current / length) * 100, 100) : 0) + "%",
          }}
        />
      </div>
      <div className="player-times">
        <span>{format(current)}</span>
        <span>{format(length)}</span>
      </div>
      <button
        type="button"
        className={"play-button " + (playing ? "pause" : "")}
        onClick={play}
        aria-label={playing ? "일시 정지" : "재생"}
        disabled={!src}
      >
        {playing ? (
          <>
            <i />
            <i />
          </>
        ) : (
          <span />
        )}
      </button>
      {error && (
        <span role="alert" className="error">
          {error}
        </span>
      )}
    </div>
  );
}
