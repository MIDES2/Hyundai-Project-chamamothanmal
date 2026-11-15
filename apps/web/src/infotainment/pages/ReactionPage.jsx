import { Icon } from "../../shared/Icons.jsx";
import Back from "../components/Back.jsx";
export default function ReactionPage({ setScreen, home, played, react }) {
  return (
    <>
      <div className="v-screen-head">
        <Back label="이모지 반응" onClick={() => setScreen("playback")} />
        <button aria-label="홈" onClick={home}>
          <Icon name="home" />
        </button>
      </div>
      <div className="reaction-options" aria-label="선택형 이모지 반응">
        {[
          ["👍", "좋아요"],
          ["🤣", "크게 웃음"],
          ["🤔", "생각 중"],
          ["🫠", "녹아내리는 얼굴"],
          ["🙂‍↕️", "끄덕임"],
          ["🥹", "감동"],
          ["😭", "울음"],
          ["♥️", "하트"],
          ["😡", "화남"],
          ["🥺", "애원하는 얼굴"],
        ].map(([emoji, label]) => (
          <button
            key={emoji}
            aria-label={label + " 반응"}
            disabled={!played}
            onClick={() => react(emoji)}
          >
            {emoji}
          </button>
        ))}
      </div>
      <p className="reaction-note">
        반응은 선택이에요. 이모지를 고르지 않으면 반응은 보내지지 않아요.
      </p>
    </>
  );
}
