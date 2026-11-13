// Encode real PCM samples; changing a compressed file extension does not convert it.
export function encodePcm16(samples, sampleRate = 16000) {
  const bytes = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(bytes);
  const text = (at, value) => [...value].forEach((char, i) => view.setUint8(at + i, char.charCodeAt(0)));
  text(0, "RIFF"); view.setUint32(4, bytes.byteLength - 8, true); text(8, "WAVE");
  text(12, "fmt "); view.setUint32(16, 16, true); view.setUint16(20, 1, true);
  view.setUint16(22, 1, true); view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true); view.setUint16(32, 2, true);
  view.setUint16(34, 16, true); text(36, "data"); view.setUint32(40, samples.length * 2, true);
  samples.forEach((sample, i) => {
    const value = Math.max(-1, Math.min(1, sample));
    view.setInt16(44 + i * 2, Math.round(value * (value < 0 ? 32768 : 32767)), true);
  });
  return new Blob([bytes], { type: "audio/wav" });
}

export async function toPcmWav(blob, { recording = false } = {}) {
  if (blob.size > 5242880) throw new Error("5MiB 이하의 음성 파일을 선택해주세요.");
  const Offline = window.OfflineAudioContext || window.webkitOfflineAudioContext;
  if (!Offline) throw new Error("이 브라우저에서는 WAV 변환을 지원하지 않습니다.");
  const decoder = new Offline(1, 1, 16000);
  let decoded;
  try { decoded = await decoder.decodeAudioData(await blob.arrayBuffer()); }
  catch { throw new Error("음성을 읽을 수 없습니다. 다른 음성 파일을 선택해주세요."); }
  if (!Number.isFinite(decoded.duration) || decoded.duration <= 0 || (!recording && decoded.duration > 15))
    throw new Error("15초 이하의 음성을 선택해주세요.");
  // The microphone timer can stop a little late. Never upload over 15 seconds.
  const frames = Math.min(240000, Math.ceil(decoded.duration * 16000));
  const renderer = new Offline(1, frames, 16000);
  const source = renderer.createBufferSource(); source.buffer = decoded;
  source.connect(renderer.destination); source.start();
  const output = await renderer.startRendering();
  return { blob: encodePcm16(output.getChannelData(0)), duration: frames / 16000 };
}
