export default function DonePage({ reaction }) {
  return (
    <div className="v-done reaction-done">
      <span role="img" aria-label="선택한 반응">
        {reaction}
      </span>
      <p>반응을 보냈어요</p>
    </div>
  );
}
