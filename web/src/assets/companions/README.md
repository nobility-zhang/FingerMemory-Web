# Companion and Inn Assets

These original assets were generated for FingerMemory with the built-in ImageGen
tool. They are project-produced illustrations and do not use remote hotlinks or
third-party character artwork. Lia's H1-01 and Serin's H1-02 redesigns were
produced on 2026-11-17; the scene assets are from the earlier visual baseline.

## Lia

Lia is an original 15-year-old traveling companion. Her H1-01 redesign uses
natural same-age proportions, apricot-pink hair with a side braid, a cream-white
short-sleeve cotton-linen blouse, a mist-blue knee-length skirt, practical shoes,
a cloth backpack, herb pouch, and an antique warm-glass lantern. The pose, outfit
and camera are non-sexualized and have no heavy cloak or floor-length dress.

- Generation and edit mode: built-in ImageGen, transparent background.
- Identity method: one new `1024x1536` neutral anchor; every other expression
  was edited from that exact anchor with identity, outfit, pose, lighting, and canvas
  invariants.
- Runtime method: macOS `sips -Z 720`, preserving RGBA alpha and a shared
  `480x720` canvas.
- Expressions: `neutral`, `welcome`, `cheer`, `concern`, `relief`, `surprise`.
- The source images are kept under `lia/source/` for future expression edits.

Source SHA256:

| Expression | SHA256 |
| --- | --- |
| neutral | `4dff3093f1e4bb5561ad7cfaca9c97e1144416211f0707d981072cfa1e94a088` |
| welcome | `5f1dcbed07d02fe41ee66568f578a6cd780982ee3768ce46bd417f546331e93c` |
| cheer | `6e99e49d07614eb8d4433a730bf5805a42f2a2aaed0fc213934eafe80591ed11` |
| concern | `8c6d5bfe825f97b18f02faaaecd771cd6a04a3f606ccc6a30d4a5d16839fb9e7` |
| relief | `2132c3524c1ccd138ec7191e14d9663a04f3cbb9317985c8543a0556aca9c5ed` |
| surprise | `e5cd9805538ec68e0594c01c1de4f79d2ad697905051f76ae086df50b1ad757d` |

Runtime SHA256:

| Expression | SHA256 |
| --- | --- |
| neutral | `16a48f6a040c44d376cccb4711896348e7300fe57ab3e92ed9cbf4112e9f0c75` |
| welcome | `0936dc50c16c15d1ee92029764dc8339d38e992b69e0ba04c323526e9021e091` |
| cheer | `327b96fb73e7b605333592c58abf509aef151458641b96bfaedcfd41f52383b8` |
| concern | `ed5cd6a4e86bea5af1bcdf8cdaf5124c1848995fa0895100432413f32745f88d` |
| relief | `eba5da83584053b0335460c24c8adeec79f7c9ae673b2bee55839f639abe0f2b` |
| surprise | `9b5b680d4c9cf34665b8b6dab9842e9333c72789cf08e9378f2e1553347a5448` |

## Warm Lantern Inn

Original 16:9 inn interior for the opening chapter. The room uses stone,
walnut wood, a hearth, hanging herbs, parchment maps, a tea table, an old brass
bell, and a star lantern. It contains no people, text, UI, logos, watermarks,
modern equipment, or neon.

- Source: `scenes/inn/source/inn-warm-lantern.png`, `1672x941`, SHA256
  `33ce73fbb744dc387a8ea025d0dfbb83948f1be012f204bad269fc6b880b67d1`.
- Runtime derivative: `scenes/inn/runtime/inn-warm-lantern.jpg`, `1440x810`,
  JPEG quality 82, SHA256
  `fac8220d24eb692daabb55ac7fcb4885249846cbb000c59010e17e36e9d76e48`.

The full source PNG files are intentionally retained for later edits. The six
runtime portraits plus the runtime background total approximately 2.4 MiB.

## Serin

Serin is an original 14-year-old traveling note-keeper. Her H1-02 redesign uses
natural same-age proportions, silver-green braided hair, teal-green eyes, a
moss-green short-sleeve cotton blouse, an ivory-white knee-length skirt, leaf
ornament, practical shoes, a small leather field notebook, cloth bag and a light
wooden traveling staff. The pose, outfit and camera are non-sexualized and have
no heavy shawl, cloak or floor-length dress.

- Generation and edit mode: built-in ImageGen, transparent background.
- Reference role: the new Lia neutral source image was used only for rendering
  style, linework, lighting, and canvas consistency. Serin has an independent
  face, silhouette, outfit, props, and palette.
- Identity method: one `1024x1536` neutral anchor; every expression was edited
  from that exact anchor with identity, outfit, staff, journal, pose, lighting,
  and canvas invariants.
- Runtime method: macOS `sips -Z 720`, preserving RGBA alpha and a shared
  `480x720` canvas.
- Expressions: `neutral`, `welcome`, `cheer`, `concern`, `relief`, `surprise`.

Source SHA256:

| Expression | SHA256 |
| --- | --- |
| neutral | `c2f67d931fc40761ce48adfd5078efc3b6d834b0b2f2489dc954b0eeabb84798` |
| welcome | `8682d62f791cbb35b72319cbc20aaef7fdb2e7a8e25e2196d81e4d84a370872e` |
| cheer | `8a90c0947ea3be585d13ead20392d973b186be8583e360a0f1baddaff35d2d71` |
| concern | `1ce24a49605b10481a5b555ee8d119b4457868e6b5d9ef44f1bf07c781b2d4ed` |
| relief | `370ee99bd4a72a22180db3c0c9541f59730931d289b3d6f9879664c371329ee4` |
| surprise | `e4b5a634c14fe218cf6a40acf89bc14cab671fc0e48367af72718b4500f22e59` |

Runtime SHA256:

| Expression | SHA256 |
| --- | --- |
| neutral | `113c8d68e6abc50b82ed09e422a2c8db4801554454e2131fda49332ffcc54885` |
| welcome | `c0cab44343dfd4ed627945dba550d8591657d2ed948c6bcf954a6850d43f7501` |
| cheer | `c94b8b5597485a7264498f958b919d4e1e28e44966b030f2a09edb65f5984566` |
| concern | `85e1bf0d58e6564220ef39e6dd8c9c874a895a0ce3558f91a4ac95ca9b15d716` |
| relief | `22009294c1eb429d7b0589d0d73e9c991096eec3959dd76087e0a467a618dac2` |
| surprise | `635499a1b58d2e771af095d92209b50477ed50ffb9cb5444d9f383ddda13cb5e` |

The two characters keep separate source and runtime trees. Their first-screen
runtime files total approximately 4.6 MiB including the inn JPEG.
