import { useEffect, useRef, useState } from "react";
import { Icon } from "../shared/Icons.jsx";
import { loadKakaoMaps, searchKakaoPlaces } from "./maps/kakao.js";

const defaultPlace = {
  id: "preset-hanshin",
  name: "한신대학교 정문",
  source: "preset",
};
export default function PlacePicker({ selected, label, onSelect, onClear, accountId }) {
  const storageKey = "chama-ui-places-v1:" + accountId;
  const [saved, setSaved] = useState(() => {
    try {
      const value = JSON.parse(localStorage.getItem(storageKey) || "null");
      if (Array.isArray(value))
        return value.filter(
          (p) => p && typeof p.id === "string" && typeof p.name === "string",
        );
    } catch {
      /* Use the sketch's initial place. */
    }
    return [defaultPlace];
  });
  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(saved));
    } catch {
      /* Keep the list in memory. */
    }
  }, [saved, storageKey]);
  const [manualLatitude, setManualLatitude] = useState(""), [manualLongitude, setManualLongitude] = useState("");
  const [query, setQuery] = useState(""),
    [editing, setEditing] = useState(false),
    [results, setResults] = useState([]),
    [maps, setMaps] = useState(null),
    [loading, setLoading] = useState(false),
    [error, setError] = useState(""),
    [searched, setSearched] = useState(false),
    [pagination, setPagination] = useState(null);
  const sequence = useRef(0),
    mapElement = useRef(null),
    chooseRef = useRef(null);
  function choose(place) {
    sequence.current++;
    setLoading(false);
    setError("");
    setSaved((items) => {
      const index = items.findIndex(
        (p) => p.id === place.id || p.name === place.name,
      );
      return index < 0
        ? [...items, place]
        : items.map((p, i) => (i === index ? place : p));
    });
    onSelect(place);
    setResults([]);
    setPagination(null);
    setSearched(false);
    setQuery("");
  }
  function addTypedPlace() {
    const name = query.trim();
    const latitude=Number(manualLatitude), longitude=Number(manualLongitude);
    if (!name || !manualLatitude.trim() || !manualLongitude.trim() || !Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180)
      return setError("장소 이름과 올바른 위도·경도를 입력해주세요.");
    choose({ id: "manual:" + name + ":" + latitude + ":" + longitude, name, latitude, longitude, source: "manual" });
  }
  function removePlace(place) {
    setSaved((items) => items.filter((item) => item.id !== place.id));
    if (selected ? selected.id === place.id : place.name === label) onClear();
  }
  useEffect(() => {
    chooseRef.current = choose;
  });
  useEffect(
    () => () => {
      sequence.current++;
    },
    [],
  );
  useEffect(() => {
    if (!maps || !mapElement.current) return;
    const points = (
      results.length ? results : selected ? [selected] : []
    ).filter(
      (p) => Number.isFinite(p.latitude) && Number.isFinite(p.longitude),
    );
    if (!points.length) return;
    let observer, map;
    const container = mapElement.current;
    const markers = [];
    try {
      const center = new maps.LatLng(points[0].latitude, points[0].longitude);
      map = new maps.Map(container, { center, level: 3 });
      const bounds = new maps.LatLngBounds();
      for (const point of points) {
        const position = new maps.LatLng(point.latitude, point.longitude);
        const marker = new maps.Marker({ map, position, title: point.name });
        const click = () => chooseRef.current(point);
        maps.event.addListener(marker, "click", click);
        markers.push({ marker, click });
        bounds.extend(position);
      }
      if (points.length > 1) map.setBounds(bounds);
      if (window.ResizeObserver) {
        observer = new ResizeObserver(() => {
          map.relayout();
          if (points.length > 1) map.setBounds(bounds);
          else map.setCenter(center);
        });
        observer.observe(mapElement.current);
      }
    } catch {
      setError(
        "지도를 표시하지 못했어요. 아래 목록에서 장소를 선택하거나 다시 검색해주세요.",
      );
    }
    return () => {
      observer?.disconnect();
      markers.forEach(({ marker, click }) => {
        maps.event.removeListener(marker, "click", click);
        marker.setMap(null);
      });
      container.replaceChildren();
    };
  }, [maps, results, selected]);
  async function search(term = query, page = 1) {
    const keyword = term.trim();
    if (!keyword) {
      setError("주소나 장소 이름을 입력해주세요.");
      return;
    }
    const current = ++sequence.current;
    setLoading(true);
    setError("");
    setSearched(false);
    setResults([]);
    setPagination(null);
    try {
      const sdk = await loadKakaoMaps();
      if (sequence.current !== current) return;
      setMaps(sdk);
      const result = await searchKakaoPlaces(sdk, keyword, page);
      if (sequence.current !== current) return;
      setResults(result.places);
      setSearched(true);
      setPagination({ ...result, query: keyword });
    } catch (e) {
      if (sequence.current === current) setError(e.message);
    } finally {
      if (sequence.current === current) setLoading(false);
    }
  }
  return (
    <>
      <div className="saved-place-tools">
        <button type="button" aria-label={editing ? "장소 수정 완료" : "장소 목록 수정"} aria-pressed={editing} disabled={!saved.length && !editing} onClick={() => setEditing(!editing)}>{editing ? "완료" : "수정"}</button>
      </div>
      <div className="saved-place-list" aria-label="추가한 장소 목록">
        {saved.map((place) => {
          const checked = selected
            ? selected.id === place.id
            : place.name === label;
          return (
            <div className="saved-place-row" key={place.id}>
            <button
              type="button"
              className="place-choice selected-place"
              disabled={editing}
              aria-pressed={checked}
              onClick={() => choose(place)}
            >
              <span>{place.name}</span>
              <span aria-hidden="true">{checked ? "✓" : ""}</span>
            </button>
            {editing && <button type="button" className="saved-place-delete" aria-label={place.name + " 삭제"} onClick={() => removePlace(place)}>−</button>}
            </div>
          );
        })}
      </div>
      {selected && (
        <p className="place-address">
          {selected.roadAddress || selected.address}
        </p>
      )}
      <div className="search">
        <input
          value={query}
          aria-label="장소 입력/검색"
          placeholder="주소나 장소 입력하세요."
          onChange={(e) => {
            sequence.current++;
            setQuery(e.target.value);
            setLoading(false);
            setError("");
            setResults([]);
            setPagination(null);
            setSearched(false);
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.nativeEvent.isComposing) {
              e.preventDefault();
              search();
            }
          }}
        />
        <button
          type="button"
          aria-label="장소 검색"
          disabled={loading}
          onClick={() => search()}
        >
          <Icon name="search" />
        </button>
      </div>
      <details><summary>좌표로 장소 직접 추가</summary>
        <p className="account-help">지도 키 없이 시험할 때도 좌표가 필요해요. 장소 이름은 위 검색칸에 입력해주세요.</p>
        <input aria-label="직접 입력 위도" inputMode="decimal" value={manualLatitude} onChange={e=>setManualLatitude(e.target.value)} placeholder="위도 (-90~90)" />
        <input aria-label="직접 입력 경도" inputMode="decimal" value={manualLongitude} onChange={e=>setManualLongitude(e.target.value)} placeholder="경도 (-180~180)" />
      </details>
      {query.trim() && (
        <button
          type="button"
          className="add-typed-place"
          onClick={addTypedPlace}
        >
          ＋ 입력한 장소 추가
        </button>
      )}
      {loading && (
        <p className="place-status" role="status">
          장소를 검색하고 있어요…
        </p>
      )}
      {error && (
        <p className="error place-error" role="alert">
          {error}
        </p>
      )}
      {searched && !results.length && (
        <p className="place-status" role="status">
          검색 결과가 없어요. 다른 장소명이나 주소를 입력해주세요.
        </p>
      )}
      {maps &&
        (results.length > 0 ||
          (Number.isFinite(selected?.latitude) &&
            Number.isFinite(selected?.longitude))) && (
          <div
            ref={mapElement}
            className="place-map"
            role="region"
            aria-label="검색한 장소 지도"
          />
        )}
      {results.length > 0 && (
        <ul className="place-results">
          {results.map((place) => (
            <li key={place.id}>
              <button type="button" onClick={() => choose(place)}>
                <strong>{place.name}</strong>
                <span>{place.roadAddress || place.address}</span>
                <small>이 장소 선택</small>
              </button>
            </li>
          ))}
        </ul>
      )}
      {pagination && (pagination.page > 1 || pagination.hasNext) && (
        <div className="place-pagination">
          <button
            type="button"
            disabled={loading || pagination.page === 1}
            onClick={() => search(pagination.query, pagination.page - 1)}
          >
            이전
          </button>
          <span>{pagination.page} 페이지</span>
          <button
            type="button"
            disabled={loading || !pagination.hasNext}
            onClick={() => search(pagination.query, pagination.page + 1)}
          >
            다음
          </button>
        </div>
      )}
    </>
  );
}
