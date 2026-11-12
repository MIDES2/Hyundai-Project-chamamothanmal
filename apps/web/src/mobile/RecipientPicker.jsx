import { useState } from "react";
import { digits } from "./accounts.js";

export default function RecipientPicker({
  friends,
  selectedId,
  onSelect,
  onManageFriends,
}) {
  const [query, setQuery] = useState("");
  const search = query.trim().toLowerCase(),
    number = digits(search);
  const matches = friends.filter(
    (p) =>
      !search ||
      p.name.toLowerCase().includes(search) ||
      p.handle.toLowerCase().includes(search) ||
      (number && p.phone.includes(number)),
  );
  function choose(person) {
    onSelect(person);
    setQuery("");
  }
  return (
    <div className="recipient-options">
      <div className="recipient-search">
        <input
          type="text"
          aria-label="전달 대상 검색"
          placeholder="친구를 추가해주세요"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.nativeEvent.isComposing)
              e.preventDefault();
          }}
        />
      </div>
      {matches.map((person) => (
        <button
          type="button"
          key={person.id}
          aria-pressed={selectedId === person.id}
          onClick={() => choose(person)}
        >
          {person.name}
          <span>{selectedId === person.id ? "✓" : ""}</span>
        </button>
      ))}
      {search && matches.length === 0 && (
        <p className="recipient-empty" role="status">
          일치하는 친구가 없어요
        </p>
      )}
      <button
        type="button"
        className="manage-friends"
        onClick={() => onManageFriends(query.trim())}
      >
        ＋ 친구 추가
      </button>
    </div>
  );
}
