import IntroPage from "./IntroPage.jsx";
import OfferPage from "./OfferPage.jsx";
import EmptyHomePage from "./EmptyHomePage.jsx";
import { Car, Icon } from "../../shared/Icons.jsx";
export default function HomePage({
  screen,
  setScreen,
  savedMessages,
  start,
  receiving,
  connected,
  giveUp,
}) {
  return (
    <>
      <div className="v-header">
        <div className="v-brand">
          <Car surface="dark" />
          <strong>차마못한말</strong>
        </div>
        <div className="v-header-actions">
          <button
            className="archive-entry header-archive"
            aria-label="보관함"
            onClick={() => setScreen("archive")}
          >
            <Icon name="basket" />
            <span className="archive-label">보관함</span>
            <span>{savedMessages.length}</span>
          </button>
          <button
            className="v-settings-button"
            aria-label="설정"
            onClick={() => setScreen("settings")}
          >
            ≡
          </button>
        </div>
      </div>
      <div className="v-home-content">
        {screen === "intro" ? (
          <IntroPage />
        ) : screen === "offer" ? (
          <OfferPage
            start={start}
            receiving={receiving}
            connected={connected}
            giveUp={giveUp}
          />
        ) : (
          <EmptyHomePage setScreen={setScreen} />
        )}
      </div>
    </>
  );
}
