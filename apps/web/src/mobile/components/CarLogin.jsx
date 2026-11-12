import { Car } from "../../shared/Icons.jsx";
export default function CarLogin({ onClick, dark = false }) {
  return (
    <button
      type="button"
      className="car-login"
      aria-label="차량 아이콘 · 로그인으로 이동"
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
    >
      <Car dark={dark} />
    </button>
  );
}
