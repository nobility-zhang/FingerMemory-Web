import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const outputPath = resolve(
  scriptDirectory,
  '../src/assets/audio/flashcard-flip.wav',
);
const sampleRate = 44_100;
const durationSeconds = 0.24;
const sampleCount = Math.floor(sampleRate * durationSeconds);
const samples = new Float32Array(sampleCount);

let randomState = 0x51f15e;
function random() {
  randomState ^= randomState << 13;
  randomState ^= randomState >>> 17;
  randomState ^= randomState << 5;
  return ((randomState >>> 0) / 0xffffffff) * 2 - 1;
}

let lowPassedNoise = 0;
let highPassedNoise = 0;
let previousNoise = 0;
let phase = 0;
for (let index = 0; index < sampleCount; index += 1) {
  const time = index / sampleRate;
  const noise = random();
  lowPassedNoise = lowPassedNoise * 0.82 + noise * 0.18;
  highPassedNoise = noise - previousNoise * 0.94;
  previousNoise = noise;

  const attack = Math.min(1, time / 0.008);
  const paperEnvelope = attack * Math.exp(-time * 17);
  const sweepFrequency = 1150 - time * 3300;
  phase += (Math.PI * 2 * Math.max(180, sweepFrequency)) / sampleRate;
  const sweepEnvelope = attack * Math.exp(-time * 23);

  const tapOne = Math.exp(-Math.pow((time - 0.014) / 0.006, 2));
  const tapTwo = Math.exp(-Math.pow((time - 0.118) / 0.009, 2));
  const tap =
    tapOne * Math.sin(Math.PI * 2 * 1450 * time)
    + tapTwo * Math.sin(Math.PI * 2 * 980 * time) * 0.45;

  samples[index] =
    highPassedNoise * paperEnvelope * 0.34
    + lowPassedNoise * paperEnvelope * 0.18
    + Math.sin(phase) * sweepEnvelope * 0.045
    + tap * 0.13;
}

let peak = 0;
for (const sample of samples) {
  peak = Math.max(peak, Math.abs(sample));
}
const gain = peak === 0 ? 0 : 0.82 / peak;
const pcm = Buffer.alloc(sampleCount * 2);
for (let index = 0; index < sampleCount; index += 1) {
  const fadeOut = Math.min(1, (sampleCount - index) / (sampleRate * 0.018));
  const value = Math.max(
    -1,
    Math.min(1, samples[index] * gain * fadeOut),
  );
  pcm.writeInt16LE(Math.round(value * 32_767), index * 2);
}

const header = Buffer.alloc(44);
header.write('RIFF', 0);
header.writeUInt32LE(36 + pcm.length, 4);
header.write('WAVE', 8);
header.write('fmt ', 12);
header.writeUInt32LE(16, 16);
header.writeUInt16LE(1, 20);
header.writeUInt16LE(1, 22);
header.writeUInt32LE(sampleRate, 24);
header.writeUInt32LE(sampleRate * 2, 28);
header.writeUInt16LE(2, 32);
header.writeUInt16LE(16, 34);
header.write('data', 36);
header.writeUInt32LE(pcm.length, 40);

await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, Buffer.concat([header, pcm]));
console.log(pathForDisplay(outputPath));

function pathForDisplay(path) {
  return path.replace(`${process.cwd()}/`, '');
}
