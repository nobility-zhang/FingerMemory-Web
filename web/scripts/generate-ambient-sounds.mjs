import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const outputDirectory = resolve(scriptDirectory, '../src/assets/audio');
const sampleRate = 44_100;
const durationSeconds = 12;

let randomState = 0x41c0a57;
function noise() {
  randomState ^= randomState << 13;
  randomState ^= randomState >>> 17;
  randomState ^= randomState << 5;
  return ((randomState >>> 0) / 0xffffffff) * 2 - 1;
}

function sine(time, frequency) {
  return Math.sin(Math.PI * 2 * frequency * time);
}

function pulse(time, center, width) {
  return Math.exp(-Math.pow((time - center) / width, 2));
}

function swell(time) {
  return 0.48 + 0.31 * Math.sin(time * 0.55) ** 2;
}

let seaNoise = 0;
function seaTexture() {
  seaNoise = seaNoise * 0.985 + noise() * 0.015;
  return seaNoise;
}

function gull(time, center, pitch) {
  const first = pulse(time, center, 0.045);
  const second = pulse(time, center + 0.16, 0.06);
  return (
    sine(time, pitch + (time - center) * 120) * first * 0.045
    + sine(time, pitch * 1.13 + (time - center) * 90) * second * 0.035
  );
}

async function writeWav(name, synthesize) {
  const sampleCount = Math.floor(sampleRate * durationSeconds);
  const samples = new Float32Array(sampleCount);
  for (let index = 0; index < sampleCount; index += 1) {
    samples[index] = synthesize(index / sampleRate);
  }

  let peak = 0;
  for (const sample of samples) {
    peak = Math.max(peak, Math.abs(sample));
  }
  const gain = peak === 0 ? 0 : 0.78 / peak;
  const pcm = Buffer.alloc(sampleCount * 2);
  for (let index = 0; index < sampleCount; index += 1) {
    const time = index / sampleRate;
    const fadeIn = Math.min(1, time / 0.4);
    const fadeOut = Math.min(1, (durationSeconds - time) / 0.4);
    const value = Math.max(
      -1,
      Math.min(1, samples[index] * gain * fadeIn * fadeOut),
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

  await writeFile(
    resolve(outputDirectory, `${name}.wav`),
    Buffer.concat([header, pcm]),
  );
}

await mkdir(outputDirectory, { recursive: true });
await writeWav('ambient-coast', (time) => {
  const texture = seaTexture();
  const lowWave = sine(time, 0.18) * 0.11;
  const wash = sine(time, 0.31 + Math.sin(time * 0.2) * 0.03) * 0.035;
  const surf = texture * (0.055 + swell(time) * 0.11);
  const gulls = gull(time, 2.35, 1180)
    + gull(time, 6.5, 1040)
    + gull(time, 9.7, 1260);
  return lowWave + wash + surf + gulls;
});

console.log('Generated coast ambience in src/assets/audio');
