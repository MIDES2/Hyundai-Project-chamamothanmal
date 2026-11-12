import { useState } from "react";
import { Icon } from "../../shared/Icons.jsx";
import MessageCard from "../components/MessageCard.jsx";
export default function ArchivePage({ items, onDelete, onLogin }) {
  const [editing, setEditing] = useState(false),
    [selected, setSelected] = useState([]);
  function toggle(id) {
    setSelected((ids) =>
      ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id],
    );
  }
  function finish() {
    setEditing(false);
    setSelected([]);
  }
  function remove() {
    onDelete(selected);
    finish();
  }
  return (
    <>
      <div className="archive-heading">
        <h1>
          마음보관함 <Icon name="openBasket" />
        </h1>
        <div className="archive-heading-actions">
          <span>{items.length}</span>
          <button
            type="button"
            onClick={() => (editing ? finish() : setEditing(true))}
            disabled={!items.length}
          >
            {editing ? "완료" : "수정"}
          </button>
        </div>
      </div>
      {editing && (
        <div className="archive-edit-tools">
          <span>{selected.length}개 선택</span>
          <button type="button" disabled={!selected.length} onClick={remove}>
            선택 삭제
          </button>
        </div>
      )}
      {items.map((message) => (
        <MessageCard
          key={message.id}
          message={message}
          onLogin={onLogin}
          selecting={editing}
          selected={selected.includes(message.id)}
          onToggle={toggle}
        />
      ))}
      {items.length === 0 && (
        <p className="archive-empty" role="status">
          보관한 마음이 없어요.
        </p>
      )}
    </>
  );
}
