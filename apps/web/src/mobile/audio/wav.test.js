import test from "node:test";
import assert from "node:assert/strict";
import { encodePcm16 } from "./wav.js";
test("WAV contains actual mono 16-bit PCM with clipped samples and consistent RIFF size", async () => {
  const blob=encodePcm16(new Float32Array([-2,-1,0,1,2]));
  const bytes=await blob.arrayBuffer(), view=new DataView(bytes);
  assert.equal(blob.type,"audio/wav"); assert.equal(bytes.byteLength,54);
  assert.equal(new TextDecoder().decode(bytes.slice(0,4)),"RIFF");
  assert.equal(view.getUint32(4,true),46); assert.equal(view.getUint16(20,true),1);
  assert.equal(view.getUint16(22,true),1); assert.equal(view.getUint32(24,true),16000);
  assert.equal(view.getUint16(34,true),16); assert.equal(view.getUint32(40,true),10);
  assert.deepEqual(Array.from({length:5},(_,i)=>view.getInt16(44+i*2,true)),[-32768,-32768,0,32767,32767]);
});
