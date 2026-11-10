/** Runtime user token, not the Kakao key. Use the server origin already used for message APIs. */
export async function searchPlaces({ baseUrl, token, query, type = 'keyword', page = 1, size = 10, signal }) {
  const params = new URLSearchParams({ query, type, page: String(page), size: String(size) });
  const response = await fetch(`${baseUrl.replace(/\/$/, '')}/api/v2/places?${params}`, {
    headers: { Authorization: `Bearer ${token}` }, redirect: 'error', signal
  });
  const data = await response.json();
  if (!response.ok) {
    const error = new Error(`HTTP ${response.status}: ${data.error?.code ?? 'HTTP_ERROR'}`);
    error.status = response.status;
    throw error;
  }
  return data;
}
