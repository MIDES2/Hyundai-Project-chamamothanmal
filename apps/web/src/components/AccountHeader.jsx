import { Icon } from "../Icons.jsx";
import CarLogin from "./CarLogin.jsx";
export default function AccountHeader({ title, onBack, onLogin }) {
  return (
    <div className="back-header account-header">
      <button onClick={onBack} aria-label="뒤로">
        <Icon name="back" />
      </button>
      <strong>{title}</strong>
      <CarLogin onClick={onLogin} />
    </div>
  );
}
