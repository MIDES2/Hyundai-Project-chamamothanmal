/** Copy into the web project; this is not a complete recording screen.
 * Supply an authenticated local test token at runtime, never a bundled secret.
 * Keep requestId and payload identical on retry. Do not set multipart Content-Type manually.
 */
export async function registerVoice({ baseUrl, token, audio, vehicleId, placeName,
  latitude, longitude, address = '', expiresInHours = 24, radiusMeters = 100, requestId }) {
  const form = new FormData();
  for (const [name, value] of Object.entries({ vehicleId, placeName, address, latitude, longitude,
    expiresInHours, radiusMeters })) form.append(name, String(value));
  form.append('audio', audio, 'voice.wav');
  const response = await fetch(`${baseUrl.replace(/\/$/, '')}/api/v2/messages`, {
    method: 'POST', redirect: 'error', headers: {
      Authorization: `Bearer ${token}`, 'Idempotency-Key': requestId
    }, body: form
  });
  const data = await response.json();
  if (!response.ok) {
    const error = new Error(`HTTP ${response.status}: ${data.error?.code ?? 'HTTP_ERROR'}`);
    error.status = response.status;
    throw error;
  }
  return data.messageId;
}
