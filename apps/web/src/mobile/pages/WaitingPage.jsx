import { Icon } from "../../shared/Icons.jsx";
import MessageCard from "../components/MessageCard.jsx";
export default function WaitingPage({ shownWaiting, edit, goLogin }) {
  return (
    <>
      <h1 className="list-heading">
        꽂아둔 대기 목록 <Icon name="sign" />
        <span>{shownWaiting.length}</span>
      </h1>
      {shownWaiting.map((m) => (
        <MessageCard
          key={m.id}
          message={m}
          waiting
          onEdit={edit}
          onLogin={goLogin}
        />
      ))}
    </>
  );
}
