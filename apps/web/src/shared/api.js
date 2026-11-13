let accessToken = null;
export function setAccessToken(token) { accessToken = token; }
export async function request(path, { method = "GET", body, key, binary = false } = {}) {
  const headers = {};
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (key) headers["Idempotency-Key"] = key;
  if (body && !(body instanceof FormData)) { headers["Content-Type"] = "application/json"; body = JSON.stringify(body); }
  let response;
  try { response = await fetch(`/api/v2${path}`, { method, headers, body, cache: "no-store", redirect: "error" }); }
  catch { throw new Error("서버에 연결할 수 없습니다. 서버 실행과 연결 주소를 확인해주세요."); }
  if (!response.ok) {
    const result = await response.json().catch(() => ({}));
    const error = new Error(result.error?.message || `서버 요청 실패 (${response.status})`);
    error.status = response.status; error.code = result.error?.code; throw error;
  }
  if (response.status === 204) return null;
  return binary ? response.blob() : response.json();
}
export function toCard(message, friends = []) {
  const recipient = friends.find(friend => friend.id === message.recipientId);
  return { ...message, place: message.placeName, vehicle: "차마못한말", duration: message.audioSeconds,
    date: message.scheduledDate || new Date(message.availableAt).toLocaleDateString("en-CA", { timeZone: "Asia/Seoul" }),
    recipient: recipient?.name || message.recipientId, audience: message.listeningMode === "PRIVATE" ? "private" : "shared" };
}
