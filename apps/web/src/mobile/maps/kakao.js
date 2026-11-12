const key = import.meta.env.VITE_KAKAO_MAP_JS_KEY?.trim();
let pendingSdk;

export function loadKakaoMaps() {
  if (!["http:", "https:"].includes(window.location.protocol))
    return Promise.reject(
      new Error(
        "지도 검색은 웹 주소에서 사용할 수 있어요. http://localhost:5173 에서 열어주세요.",
      ),
    );
  if (!key)
    return Promise.reject(
      new Error(
        "카카오 지도 JavaScript 키 설정이 필요해요. .env.local에 VITE_KAKAO_MAP_JS_KEY를 입력한 뒤 개발 서버를 다시 실행해주세요.",
      ),
    );
  if (window.kakao?.maps?.services) return Promise.resolve(window.kakao.maps);
  if (pendingSdk) return pendingSdk;
  pendingSdk = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    const params = new URLSearchParams({
      appkey: key,
      autoload: "false",
      libraries: "services",
    });
    script.src = "https://dapi.kakao.com/v2/maps/sdk.js?" + params;
    script.async = true;
    const timer = setTimeout(
      () =>
        fail(
          "지도를 불러오지 못했어요. 네트워크, JavaScript 키와 등록 도메인을 확인해주세요.",
        ),
      15000,
    );
    let settled = false;
    function fail(message) {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      script.remove();
      reject(new Error(message));
    }
    script.onerror = () =>
      fail(
        "지도를 연결하지 못했어요. 네트워크 또는 카카오 지도 키·등록 도메인을 확인해주세요.",
      );
    script.onload = () => {
      if (!window.kakao?.maps?.load)
        return fail(
          "지도 SDK를 사용할 수 없어요. JavaScript 키와 등록 도메인을 확인해주세요.",
        );
      window.kakao.maps.load(() => {
        if (settled) return;
        if (!window.kakao.maps.services)
          return fail("장소 검색 서비스를 불러오지 못했어요.");
        settled = true;
        clearTimeout(timer);
        resolve(window.kakao.maps);
      });
    };
    document.head.append(script);
  }).catch((error) => {
    pendingSdk = undefined;
    throw error;
  });
  return pendingSdk;
}

function request(maps, service, method, query, page) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(
      () =>
        reject(
          new Error("장소 검색이 지연되고 있어요. 잠시 뒤 다시 검색해주세요."),
        ),
      10000,
    );
    try {
      service[method](
        query,
        (data, status, pagination) => {
          clearTimeout(timer);
          if (status === maps.services.Status.OK) resolve({ data, pagination });
          else if (status === maps.services.Status.ZERO_RESULT)
            resolve({ data: [], pagination: null });
          else
            reject(
              new Error(
                "장소를 검색하지 못했어요. 네트워크 또는 카카오 지도 설정을 확인해주세요.",
              ),
            );
        },
        { page, size: 5 },
      );
    } catch {
      clearTimeout(timer);
      reject(
        new Error("장소 검색 서비스를 사용할 수 없어요. 다시 시도해주세요."),
      );
    }
  });
}
function coordinates(item) {
  const longitude = Number(item.x),
    latitude = Number(item.y);
  return Number.isFinite(longitude) &&
    Number.isFinite(latitude) &&
    longitude >= -180 &&
    longitude <= 180 &&
    latitude >= -90 &&
    latitude <= 90
    ? { longitude, latitude }
    : null;
}
export async function searchKakaoPlaces(maps, query, page = 1) {
  const keyword = await request(
    maps,
    new maps.services.Places(),
    "keywordSearch",
    query,
    page,
  );
  if (keyword.data.length) {
    const places = keyword.data
      .map((item) => {
        const point = coordinates(item);
        return (
          point && {
            id: "kakao-" + item.id,
            name: item.place_name,
            address: item.address_name || "",
            roadAddress: item.road_address_name || "",
            ...point,
          }
        );
      })
      .filter(Boolean);
    return { places, hasNext: Boolean(keyword.pagination?.hasNextPage), page };
  }
  const address = await request(
    maps,
    new maps.services.Geocoder(),
    "addressSearch",
    query,
    page,
  );
  const places = address.data
    .map((item) => {
      const point = coordinates(item);
      return (
        point && {
          id: "address-" + item.x + "," + item.y,
          name: item.road_address?.building_name || item.address_name,
          address: item.address?.address_name || item.address_name || "",
          roadAddress: item.road_address?.address_name || "",
          ...point,
        }
      );
    })
    .filter(Boolean);
  return { places, hasNext: Boolean(address.pagination?.hasNextPage), page };
}
