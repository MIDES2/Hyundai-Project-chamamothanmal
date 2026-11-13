import { toPcmWav } from "../audio/wav.js";
import { useEffect, useRef, useState } from "react";
import PlacePicker from "../PlacePicker.jsx";
import RecipientPicker from "../RecipientPicker.jsx";
import Calendar from "../components/Calendar.jsx";
import Player from "../components/Player.jsx";
import Primary from "../components/Primary.jsx";
import dateText from "../utils/dateText.js";
export default function HomePage({
  draft,
  setDraft,
  clip,
  setClip,
  onSubmit,
  onCancel,
  isEditing,
  friends,
  onManageFriends,
  accountId,
  busy,
}) {
  const [places, setPlaces] = useState(true),
    [dates, setDates] = useState(false),
    [people, setPeople] = useState(true),
    [recording, setRecording] = useState(false),
    [seconds, setSeconds] = useState(0),
    [error, setError] = useState("");
  const file = useRef(null),
    recorder = useRef(null),
    stream = useRef(null),
    interval = useRef(null),
    mounted = useRef(true),
    pending = useRef(false),
    cancelled = useRef(false);
  function stop() {
    clearInterval(interval.current);
    setRecording(false);
    if (recorder.current?.state === "recording") recorder.current.stop();
  }
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      clearInterval(interval.current);
      if (recorder.current?.state === "recording") recorder.current.stop();
      stream.current?.getTracks().forEach((t) => t.stop());
    };
  }, []);
  function cancel() {
    cancelled.current = true;
    stop();
    setClip(null);
    setSeconds(0);
    setError("");
    onCancel();
  }
  async function record() {
    if (recording) {
      stop();
      return;
    }
    if (pending.current) return;
    setError("");
    cancelled.current = false;
    if (!navigator.mediaDevices?.getUserMedia) {
      setError(
        "마이크를 사용할 수 없습니다. HTTPS 또는 localhost에서 열어주세요.",
      );
      return;
    }
    pending.current = true;
    try {
      const input = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (!mounted.current || cancelled.current) {
        input.getTracks().forEach((t) => t.stop());
        return;
      }
      stream.current = input;
      const r = new MediaRecorder(input),
        chunks = [];
      recorder.current = r;
      let elapsed = 0;
      r.ondataavailable = (e) => {
        if (e.data.size) chunks.push(e.data);
      };
      r.onstop = async () => {
        input.getTracks().forEach((t) => t.stop());
        if (mounted.current && !cancelled.current) {
          pending.current = true;
          try {
            const converted = await toPcmWav(new Blob(chunks, { type: r.mimeType }), { recording: true });
            if (mounted.current && !cancelled.current)
              setClip({ ...converted, url: URL.createObjectURL(converted.blob) });
          } catch (error) { if (mounted.current) setError(error.message); }
          finally { pending.current = false; }
        }
      };
      r.start();
      setRecording(true);
      setSeconds(0);
      interval.current = setInterval(() => {
        elapsed++;
        setSeconds(elapsed);
        if (elapsed >= 15) {
          clearInterval(interval.current);
          setRecording(false);
          r.stop();
        }
      }, 1000);
    } catch {
      stream.current?.getTracks().forEach((t) => t.stop());
      setError("마이크 권한을 허용하거나 + 버튼으로 음성을 첨부해주세요.");
    } finally {
      pending.current = false;
    }
  }
  async function attach(e) {
    const file = e.target.files?.[0]; e.target.value = "";
    if (!file || pending.current) return;
    setError(""); pending.current = true;
    try {
      const converted = await toPcmWav(file);
      if (mounted.current) setClip({ ...converted, url: URL.createObjectURL(converted.blob) });
    } catch (error) { if (mounted.current) setError(error.message); }
    finally { pending.current = false; }
  }
  async function send(e) {
    e.preventDefault();
    if (!clip) {
      setError("마음을 녹음하거나 음성 파일을 첨부해주세요.");
      return;
    }
    if (
      !draft.place.trim() ||
      !draft.recipientId ||
      !friends.some((p) => p.id === draft.recipientId) ||
      !draft.date
    ) {
      setError("장소·날짜·전달 대상을 선택해주세요.");
      return;
    }
    if (pending.current) return setError("음성을 준비 중입니다. 잠시 기다려주세요.");
    try { await onSubmit(); } catch (error) { setError(error.message); }
  }
  const update = (key, value) => setDraft((d) => ({ ...d, [key]: value }));
  return (
    <form className="composer" onSubmit={send}>
      <label className="vehicle-label">
        차량
        <select
          aria-label="차량"
          value={draft.vehicle}
          onChange={(e) => update("vehicle", e.target.value)}
        >
          <option>들어줄레이</option>
        </select>
      </label>
      <div className="record-panel">
        <div>
          <strong>오늘은 어떤 마음을 꽂을까요?</strong>
          <small>마음 녹음 {recording && seconds + " / 15초"}</small>
        </div>
        <button
          className={"record-button " + (recording ? "recording" : "")}
          type="button"
          onClick={record}
          aria-label={recording ? "녹음 중지" : "마음 녹음"}
        />
      </div>
      <button
        type="button"
        className="attach"
        onClick={() => file.current.click()}
        aria-label="음성 파일 첨부"
      >
        ＋
      </button>
      <input ref={file} type="file" accept="audio/*" hidden onChange={attach} />
      <fieldset className="message-audience" aria-label="메시지 청취 범위">
        <label>
          <input
            type="radio"
            name="message-audience"
            value="shared"
            checked={(draft.audience || "shared") === "shared"}
            onChange={() => update("audience", "shared")}
          />
          <span>자동 재생 · 함께 들어도 되는 메시지</span>
        </label>
        <label>
          <input
            type="radio"
            name="message-audience"
            value="private"
            checked={draft.audience === "private"}
            onChange={() => update("audience", "private")}
          />
          <span>재생 전 확인 · 혼자 듣고 싶은 메시지</span>
        </label>
      </fieldset>
      <section className="place-section">
        <button
          type="button"
          className="section-toggle"
          aria-expanded={places}
          onClick={() => setPlaces(!places)}
        >
          장소선택 {places ? "⌃" : "⌄"}
        </button>
        {places && (
          <PlacePicker
            key={accountId}
            accountId={accountId}
            selected={draft.placeDetails}
            label={draft.place}
            onClear={() => setDraft((d) => ({ ...d, place: "", placeDetails: null }))}
            onSelect={(place) =>
              setDraft((d) => ({
                ...d,
                place: place.name,
                placeDetails: place,
              }))
            }
          />
        )}
      </section>
      <div className="date-recipient">
        <section>
          <button
            type="button"
            className="section-toggle"
            aria-expanded={dates}
            onClick={() => setDates(!dates)}
          >
            수신 시작일 {dates ? "⌃" : "⌄"}
          </button>
          {dates && (
            <Calendar date={draft.date} onChange={(v) => update("date", v)} />
          )}
        </section>
        <section>
          <button
            type="button"
            className="section-toggle"
            aria-expanded={people}
            onClick={() => setPeople(!people)}
          >
            전달 대상 {people ? "⌃" : "⌄"}
          </button>
          {people && (
            <RecipientPicker
              friends={friends}
              selectedId={draft.recipientId}
              onSelect={(person) =>
                setDraft((d) => ({
                  ...d,
                  recipient: person.name,
                  recipientId: person.id,
                }))
              }
              onManageFriends={onManageFriends}
            />
          )}
          <div className="selected-date">{dateText(draft.date)}</div>
        </section>
      </div>
      <h2 className="section-label">미리듣기</h2>
      <Player src={clip?.url} duration={clip?.duration} />
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <p className="account-help">선택한 날부터 수신 가능하며, 수신 가능 시점부터 24시간 뒤 만료돼요. 차량 스피커의 소리는 동승자에게도 들릴 수 있어요.</p>
      <Primary disabled={recording || busy}>{busy ? "전송 중…" : "등록하기"}</Primary>
      {(clip || recording || isEditing) && (
        <button type="button" className="cancel" onClick={cancel}>
          취소
        </button>
      )}
    </form>
  );
}
