import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const outputDirectory = resolve(scriptDirectory, '../src/assets/audio');
const sampleRate = 44_100;

let randomState = 0x7a11ce;
function noise() {
  randomState ^= randomState << 13;
  randomState ^= randomState >>> 17;
  randomState ^= randomState << 5;
  return ((randomState >>> 0) / 0xffffffff) * 2 - 1;
}

function pulse(time, center, width) {
  return Math.exp(-Math.pow((time - center) / width, 2));
}

function decay(time, rate) {
  return Math.exp(-time * rate);
}

function sine(time, frequency) {
  return Math.sin(Math.PI * 2 * frequency * time);
}

async function writeWav(name, durationSeconds, synthesize) {
  const sampleCount = Math.floor(sampleRate * durationSeconds);
  const samples = new Float32Array(sampleCount);
  for (let index = 0; index < sampleCount; index += 1) {
    samples[index] = synthesize(index / sampleRate, index);
  }

  let peak = 0;
  for (const sample of samples) {
    peak = Math.max(peak, Math.abs(sample));
  }
  const gain = peak === 0 ? 0 : 0.82 / peak;
  const pcm = Buffer.alloc(sampleCount * 2);
  for (let index = 0; index < sampleCount; index += 1) {
    const fadeOut = Math.min(1, (sampleCount - index) / (sampleRate * 0.025));
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

  await writeFile(
    resolve(outputDirectory, `${name}.wav`),
    Buffer.concat([header, pcm]),
  );
}

await mkdir(outputDirectory, { recursive: true });

await writeWav('battle-draw', 0.24, (time) => {
  const slide = pulse(time, 0.09, 0.055);
  return (
    noise() * slide * 0.18
    + sine(time, 760 - time * 900) * decay(time, 16) * 0.05
    + pulse(time, 0.018, 0.008) * sine(time, 1480) * 0.11
  );
});

await writeWav('battle-play', 0.26, (time) => {
  return (
    pulse(time, 0.025, 0.012) * sine(time, 640) * 0.34
    + sine(time, 320 - time * 260) * decay(time, 15) * 0.09
    + noise() * decay(time, 32) * 0.025
  );
});

await writeWav('battle-hit', 0.34, (time) => {
  const impact = decay(time, 13);
  return (
    noise() * impact * 0.23
    + sine(time, 92 - time * 80) * impact * 0.46
    + pulse(time, 0.006, 0.004) * sine(time, 210) * 0.28
  );
});

await writeWav('battle-block', 0.42, (time) => {
  const envelope = decay(time, 7);
  return (
    sine(time, 420) * envelope * 0.15
    + sine(time, 630) * envelope * 0.11
    + sine(time, 980) * envelope * 0.05
    + pulse(time, 0.035, 0.015) * noise() * 0.025
  );
});

await writeWav('battle-heal', 0.72, (time) => {
  const envelope = decay(time, 4.2);
  return (
    sine(time, 523.25) * envelope * 0.16
    + sine(time, 659.25) * envelope * 0.12
    + sine(time, 783.99) * envelope * 0.1
    + pulse(time, 0.16, 0.028) * sine(time, 1046.5) * 0.04
  );
});

await writeWav('battle-energy', 0.54, (time) => {
  const sweep = 460 + time * 1600;
  return (
    sine(time, sweep) * decay(time, 7.4) * 0.2
    + pulse(time, 0.12, 0.035) * sine(time, 1760) * 0.08
    + pulse(time, 0.24, 0.035) * sine(time, 2093) * 0.05
  );
});

await writeWav('battle-enemy-turn', 0.58, (time) => {
  const first = pulse(time, 0.05, 0.05);
  const second = pulse(time, 0.28, 0.06);
  return (
    sine(time, 110) * first * 0.25
    + sine(time, 82) * second * 0.24
    + noise() * first * 0.04
  );
});

await writeWav('battle-victory', 1.05, (time) => {
  const notes = [523.25, 659.25, 783.99, 1046.5];
  return notes.reduce(
    (value, frequency, index) =>
      value
      + sine(time, frequency)
        * pulse(time, 0.12 + index * 0.14, 0.18)
        * 0.11,
    0,
  );
});

await writeWav('battle-rescue', 1.1, (time) => {
  const notes = [523.25, 440, 349.23];
  return notes.reduce(
    (value, frequency, index) =>
      value
      + sine(time, frequency)
        * pulse(time, 0.16 + index * 0.2, 0.24)
        * (0.11 - index * 0.015),
    0,
  );
});

console.log('Generated battle sound matrix in src/assets/audio');
