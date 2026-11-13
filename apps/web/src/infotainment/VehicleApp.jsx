export { default } from "./ConnectedVehicleApp.jsx";
import HomePage from "./pages/HomePage.jsx";
import JourneyPage from "./pages/JourneyPage.jsx";
import PlaybackPage from "./pages/PlaybackPage.jsx";
import ReactionPage from "./pages/ReactionPage.jsx";
import DonePage from "./pages/DonePage.jsx";
import MessagesPage from "./pages/MessagesPage.jsx";
import ArchivePage from "./pages/ArchivePage.jsx";
import SettingsPage from "./pages/SettingsPage.jsx";
import { useEffect, useRef, useState } from "react";
import { Car } from "../shared/Icons.jsx";
import StatusBar from "./StatusBar.jsx";
import DockIcon from "./DockIcon.jsx";
import {
  ARCHIVE_KEY,
  RETENTION_KEY,
  readArchive,
  readRetention,
  pruneArchive,
} from "./archive.js";
import Back from "./components/Back.jsx";
const total = 15;
function destinationSign(path) {
  if (!path) return null;
  const length = path.getTotalLength(),
    distance = (length * 5) / 8;
  const point = path.getPointAtLength((distance + 48 + 21 * 0.8 + 5) % length);
  const before = path.getPointAtLength(distance - 0.1),
    after = path.getPointAtLength(distance + 0.1);
  const angle =
    ((Math.atan2(after.y - before.y, after.x - before.x) * 180) / Math.PI +
      180) %
    360;
  return { x: point.x, y: point.y, angle };
}
export function DemoVehicleApp() {
  const [screen, setScreen] = useState("intro"),
    [accepted, setAccepted] = useState(false),
    [played, setPlayed] = useState(false),
    [playing, setPlaying] = useState(false),
    [progress, setProgress] = useState(0),
    [reaction, setReaction] = useState(""),
    [receiving, setReceiving] = useState(true),
    [connected, setConnected] = useState(true),
    [volume, setVolume] = useState(70),
    [detail, setDetail] = useState(null),
    [deleted, setDeleted] = useState(false);
  const [savedMessages, setSavedMessages] = useState(readArchive),
    [retention, setRetention] = useState(readRetention);
  useEffect(() => {
    try {
      localStorage.setItem(RETENTION_KEY, JSON.stringify(retention));
    } catch {
      /* Keep preferences in memory. */
    }
  }, [retention]);
  useEffect(() => {
    const clean = () => setSavedMessages((items) => pruneArchive(items));
    const timer = setInterval(clean, 1000);
    window.addEventListener("focus", clean);
    document.addEventListener("visibilitychange", clean);
    return () => {
      clearInterval(timer);
      window.removeEventListener("focus", clean);
      document.removeEventListener("visibilitychange", clean);
    };
  }, []);
  function changeRetention(id, value) {
    setSavedMessages((items) =>
      pruneArchive(
        items.map((m) => (m.id === id ? { ...m, retention: value } : m)),
      ),
    );
  }
  function removeSaved(id) {
    setSavedMessages((items) => items.filter((m) => m.id !== id));
  }
  const isSaved = savedMessages.some((m) => m.id === "mother-demo");
  useEffect(() => {
    try {
      localStorage.setItem(ARCHIVE_KEY, JSON.stringify(savedMessages));
    } catch {
      /* Archive remains available in this session. */
    }
  }, [savedMessages]);

  const [arrivalReached, setArrivalReached] = useState(false);
  const routeSvg = useRef(null);
  const [arrivalSign, setArrivalSign] = useState(null);
  useEffect(() => {
    if (screen !== "intro") return;
    const timer = setTimeout(() => setScreen("waiting"), 2000);
    return () => clearTimeout(timer);
  }, [screen]);
  useEffect(() => {
    if (screen !== "waiting") return;
    setArrivalSign(null);
    routeSvg.current?.setCurrentTime(0);
    routeSvg.current?.unpauseAnimations();
    // One loop takes 8 seconds; reveal the destination half a loop before arrival.
    const signTimer = setTimeout(
      () =>
        setArrivalSign(
          destinationSign(routeSvg.current?.querySelector(".car-track-trail")),
        ),
      9000,
    );
    const timer = setTimeout(() => setScreen("arrived"), 13000);
    return () => {
      clearTimeout(signTimer);
      clearTimeout(timer);
    };
  }, [screen]);
  useEffect(() => {
    if (screen !== "arrived") return;
    const svg = routeSvg.current;
    svg?.setCurrentTime(5);
    svg?.pauseAnimations();
    const path = svg?.querySelector(".car-track-trail");
    setArrivalSign(destinationSign(path));
    const timer = setTimeout(() => {
      setArrivalReached(true);
      setScreen("offer");
    }, 2000);
    return () => clearTimeout(timer);
  }, [screen]);
  useEffect(() => {
    if (screen !== "playback" || !playing) return;
    const timer = setInterval(
      () => setProgress((p) => Math.min(total, p + 0.1)),
      100,
    );
    return () => clearInterval(timer);
  }, [screen, playing]);
  useEffect(() => {
    if (screen !== "playback" || progress < total) return;
    setPlaying(false);
    setPlayed(true);
    setSavedMessages((items) =>
      items.some((m) => m.id === "mother-demo")
        ? items
        : [
            ...items,
            {
              id: "mother-demo",
              savedAt: Date.now(),
              retention: { ...retention },
            },
          ],
    );
  }, [progress, screen, retention]);
  useEffect(() => {
    if (screen !== "done") return;
    const timer = setTimeout(() => setScreen("home"), 3000);
    return () => clearTimeout(timer);
  }, [screen]);
  function start() {
    if (screen !== "offer" || !arrivalReached || !receiving || !connected)
      return;
    setAccepted(true);
    setProgress(0);
    setPlayed(false);
    setPlaying(true);
    setScreen("playback");
  }
  function giveUp() {
    setAccepted(true);
    setDeleted(true);
    setPlaying(false);
    setProgress(0);
    setPlayed(false);
    setReaction("");
    removeSaved("mother-demo");
    setScreen("home");
  }
  function replay() {
    setProgress(0);
    setPlayed(false);
    setPlaying(true);
    setScreen("playback");
  }
  function react(emoji) {
    if (!played) return;
    setReaction(emoji);
    setScreen("done");
  }
  function home() {
    setPlaying(false);
    setScreen(
      !arrivalReached ? "intro" : !accepted && !deleted ? "offer" : "home",
    );
    setDetail(null);
  }
  return (
    <main className="vehicle-display" aria-label="차마못한말 차량용 UI 시연">
      <StatusBar />
      <section className="vehicle-panel" aria-label="차량 앱 화면">
        {screen === "home" || screen === "intro" || screen === "offer" ? (
          <HomePage
            screen={screen}
            setScreen={setScreen}
            savedMessages={savedMessages}
            start={start}
            receiving={receiving}
            connected={connected}
            giveUp={giveUp}
          />
        ) : screen === "waiting" || screen === "arrived" ? (
          <JourneyPage
            screen={screen}
            setScreen={setScreen}
            routeSvg={routeSvg}
            arrivalSign={arrivalSign}
          />
        ) : screen === "playback" ? (
          <PlaybackPage
            home={home}
            playing={playing}
            progress={progress}
            total={total}
            setProgress={setProgress}
            setPlaying={setPlaying}
            played={played}
            isSaved={isSaved}
            setScreen={setScreen}
          />
        ) : screen === "reaction" ? (
          <ReactionPage
            setScreen={setScreen}
            home={home}
            played={played}
            react={react}
          />
        ) : screen === "done" ? (
          <DonePage reaction={reaction} />
        ) : screen === "list" ? (
          <MessagesPage
            home={home}
            deleted={deleted}
            replay={replay}
            setDeleted={setDeleted}
          />
        ) : screen === "archive" ? (
          <ArchivePage
            home={home}
            retention={retention}
            setRetention={setRetention}
            savedMessages={savedMessages}
            replay={replay}
            removeSaved={removeSaved}
            changeRetention={changeRetention}
          />
        ) : screen === "empty-list" ? (
          <Back label="마음 목록" onClick={home} />
        ) : (
          <SettingsPage
            home={home}
            setDetail={setDetail}
            connected={connected}
            receiving={receiving}
            setReceiving={setReceiving}
            detail={detail}
            volume={volume}
            setVolume={setVolume}
          />
        )}
        {detail && detail !== "volume" && (
          <div className="v-dialog-backdrop">
            <section
              className="v-dialog"
              role="dialog"
              aria-modal="true"
              aria-label={
                detail === "connect"
                  ? "차량 가족 연결"
                  : detail === "privacy"
                    ? "개인정보 및 보안"
                    : "서비스 이용 안내"
              }
            >
              <h2>
                {detail === "connect"
                  ? "차량 가족 연결"
                  : detail === "privacy"
                    ? "개인정보 및 보안"
                    : "서비스 이용 안내"}
              </h2>
              {detail === "connect" ? (
                <button
                  onClick={() => {
                    setConnected(!connected);
                    setDetail(null);
                  }}
                >
                  {connected ? "연결 해제" : "연결하기"}
                </button>
              ) : (
                <p>
                  {detail === "privacy"
                    ? "UI 시연 중 입력은 서버에 전송되지 않습니다."
                    : "차량 신호·음성 전송을 연결하지 않은 UI 시연입니다. 앱 버전 0.1.0"}
                </p>
              )}
              <button onClick={() => setDetail(null)}>닫기</button>
            </section>
          </div>
        )}
      </section>
      <nav className="vehicle-dock" aria-label="차량 런처">
        <button className="dock-grey" aria-label="공조" onClick={home}>
          <DockIcon name="fan" />
        </button>
        <button
          className="dock-purple"
          aria-label="블루투스 음악"
          onClick={home}
        >
          <DockIcon name="music" />
        </button>
        <button className="dock-green" aria-label="전화" onClick={home}>
          <DockIcon name="phone" />
        </button>
        <button className="dock-blue" aria-label="내비게이션" onClick={home}>
          <DockIcon name="navigation" />
        </button>
        <button className="dock-grid" aria-label="앱 목록" onClick={home}>
          <i />
          <i />
          <i />
          <i />
        </button>
        <button className="dock-app" aria-label="차마못한말" onClick={home}>
          <Car />
        </button>
      </nav>
    </main>
  );
}
