# Audio Assets

All files are original procedural synthesis with no third-party samples. They
are distributed with this repository under the project license and are managed
by Git LFS.

## `flashcard-flip.wav`

- Source: original procedural synthesis, no third-party samples.
- Generator: `web/scripts/generate-flashcard-flip-sound.mjs`.
- License: distributed with this repository under the project license.
- Format: mono, 44.1 kHz, 16-bit PCM WAV.
- Use: one-shot card reveal feedback through Phaser `SoundManager`; H4-01 also
  uses it as the first accepted battle learning/card feedback until H4-02 adds
  the full effect matrix.

Regenerate deterministically:

```sh
cd web
npm run audio:flip
```

## `battle-*.wav`

- Generator: `web/scripts/generate-battle-sounds.mjs`.
- License: distributed with this repository under the project license.
- Format: mono, 44.1 kHz, 16-bit PCM WAV.
- Use: confirmed battle presentation events only.

| File | Event |
| --- | --- |
| `battle-draw.wav` | cards drawn / recycled |
| `battle-play.wav` | card played |
| `battle-hit.wav` | card, burn or enemy damage |
| `battle-block.wav` | block or shield gained |
| `battle-heal.wav` | healing or companion assistance |
| `battle-energy.wav` | learning energy granted |
| `battle-enemy-turn.wav` | enemy intent execution |
| `battle-victory.wav` | victory |
| `battle-rescue.wav` | defeat/rescue |

Regenerate deterministically:

```sh
cd web
npm run audio:battle
```
