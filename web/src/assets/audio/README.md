# Audio Assets

## `flashcard-flip.wav`

- Source: original procedural synthesis, no third-party samples.
- Generator: `web/scripts/generate-flashcard-flip-sound.mjs`.
- License: distributed with this repository under the project license.
- Format: mono, 44.1 kHz, 16-bit PCM WAV.
- Use: one-shot card reveal feedback through Phaser `SoundManager`.

Regenerate deterministically:

```sh
cd web
npm run audio:flip
```
