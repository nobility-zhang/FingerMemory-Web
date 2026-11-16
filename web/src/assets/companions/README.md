# Companion and Inn Assets

These original assets were generated for FingerMemory with the built-in ImageGen
tool on 2026-11-15. They are project-produced illustrations and do not use remote
hotlinks or third-party character artwork.

## Lia

Lia is an original adult traveling companion: apricot-pink hair with a side
braid, cream-white and mist-blue travel dress, short blue cloak, herb satchel,
and an antique warm-glass lantern.

- Generation and edit mode: built-in ImageGen, transparent background.
- Identity method: one `1024x1536` neutral anchor; every other expression was
  edited from that exact anchor with identity, outfit, pose, lighting, and canvas
  invariants.
- Runtime method: macOS `sips -Z 720`, preserving RGBA alpha and a shared
  `480x720` canvas.
- Expressions: `neutral`, `welcome`, `cheer`, `concern`, `relief`, `surprise`.
- The source images are kept under `lia/source/` for future expression edits.

Source SHA256:

| Expression | SHA256 |
| --- | --- |
| neutral | `cbb7948fe4b342a5706a70dd5e83131139b48808b4989d9b11d71e513fc500e9` |
| welcome | `32218247db7de89073e9ebc7cb4f5d572c5f3566dfc5c20aa898ff269078523d` |
| cheer | `a315572e3bf934f1dd9b0409bc498203be70b61d81537b18e7a5e9fa88337324` |
| concern | `2510c427bfcbe05ac5023daafb4f65ed634309fc87d3875a495b5b792c1e4c19` |
| relief | `e48dbfd9b3c6a7456a1918e88d689f9783b0a25cfd80c0a003ea4df138d9b44b` |
| surprise | `b5de3daa7407df720819e8ebe558d83a9f345c302998fb76471fd09530515510` |

Runtime SHA256:

| Expression | SHA256 |
| --- | --- |
| neutral | `095e964dec7ccb2c8b07f5e45626c3e3b4ff33bc0506f989268fba94ad623387` |
| welcome | `8b7e31046bb6debbbcb7db448d6715c9abcc16b61ec5c5392a446643317edacc` |
| cheer | `67b8267be74d166b4b4c9dbab890f7cf000f06dfdd28f05d531a344fe8bc6584` |
| concern | `eb4e71ca25f0fe41e55f641dfbacf7994f87de1d51f22e5addda5e936f12f797` |
| relief | `13cf13fb951ae7089405bfc54c75068d14978ab2694b606ca2bf34f20582991a` |
| surprise | `38ad2856aec94368e15496553777e595763fbb01396f608c7476a0b45eb990da` |

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
