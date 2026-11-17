# Companion and Inn Assets

These original assets were generated for FingerMemory with the built-in ImageGen
tool. They are project-produced illustrations and do not use remote hotlinks or
third-party character artwork. Lia's H1-01 redesign was produced on 2026-11-17;
the remaining character and scene assets are from the earlier visual baseline.

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

Serin is an original adult traveling priestess and note-keeper: silver-green
braided hair, teal-green eyes, moss-green shawl, ivory-white dress, leaf
ornament, old leather journal, and a wooden traveling staff.

- Generation and edit mode: built-in ImageGen, transparent background.
- Reference role: Lia's neutral source image was used only for rendering style,
  linework, lighting, and canvas consistency. Serin has an independent face,
  silhouette, outfit, props, and palette.
- Identity method: one `1024x1536` neutral anchor; every expression was edited
  from that exact anchor with identity, outfit, staff, journal, pose, lighting,
  and canvas invariants.
- Runtime method: macOS `sips -Z 720`, preserving RGBA alpha and a shared
  `480x720` canvas.
- Expressions: `neutral`, `welcome`, `cheer`, `concern`, `relief`, `surprise`.

Source SHA256:

| Expression | SHA256 |
| --- | --- |
| neutral | `efb713d1e9928d28b34af079ce9815aad9ef37c66b4cb1e2c47d844203a61e69` |
| welcome | `b088000d18f93c83527756e7801a5123af6a0f47b6bde2458dc4b50f452fcdcf` |
| cheer | `8f0c1b4dff9cfc5da75bcaee16c03b448fb276eb10f093dcdd7dcd19b285addf` |
| concern | `c8a25cd3250bb231f5057942842ba75cc4b01c1d11c02f8e40fb0eda13929345` |
| relief | `1d9b3fbbd38e2ecc17b12df91cd6c6d23e770315b54d77bc752315c42ac47bf2` |
| surprise | `b8a42ba6f43278626e70204ab3dcdc3b9a1e9ea248918824dcbf398beb9bf598` |

Runtime SHA256:

| Expression | SHA256 |
| --- | --- |
| neutral | `781c4461a11e87c2f3f597c540c662610fae8a2e1e54534485dc3a94910a8455` |
| welcome | `1b8471f91e46bf58ef707072b1a08e629dda5a74f2f79091057acbba6a57768a` |
| cheer | `a416c8d52bddc7c5caa3fd3d0d144f7e2c481a12319f660d7a3dbab7097e1801` |
| concern | `420e7f452a0b8e359776af78958f47990ae4da51b8f24cafb89171e83dc740b9` |
| relief | `2a0c7862a7408b3b46e7d9f2293a3830d12b8a49b5792360557a753d838b48ab` |
| surprise | `4db661d29a3471b88d73bc5b2b8be18197942ba1cfc685d895a4fa93f82fd94f` |

The two characters keep separate source and runtime trees. Their first-screen
runtime files total approximately 4.6 MiB including the inn JPEG.
