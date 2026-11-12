import { Icon } from "../../Icons.jsx";
export default function Back({ label, onClick }) {
  return (
    <button className="v-back" onClick={onClick}>
      <Icon name="back" />
      {label}
    </button>
  );
}
