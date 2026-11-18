# Companion and Inn Assets

These original assets were generated for FingerMemory with the built-in ImageGen
tool. They are project-produced illustrations and do not use remote hotlinks or
third-party character artwork. Lia's H1-01 and Serin's H1-02 redesigns were
produced on 2026-11-17. Fina's H5-03 portrait was produced on 2026-11-18. The
inn is from the earlier visual baseline; the first chapter forest, bridge, and
camp scenes were also produced on 2026-11-18.

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

## First Chapter Scenes

Three original 16:9 environment backgrounds use the built-in ImageGen tool and
the same painterly medieval-fantasy direction as the inn. They contain no people,
text, UI, logos, watermarks, modern equipment, or sexualized imagery. Runtime
files are `1440x810` JPEG quality 86 derivatives from the `1672x941` source PNGs.

- Forest path prompt intent: late golden dusk, mossy broadleaf forest, handmade
  signpost, path leading toward a distant bridge, open dialogue space.
  Source `scenes/forest/source/forest-path.png` SHA256
  `8d4b37adf93490912a0ec017f99c67228847feaf4d8a8c7a5ed60a242b139dfd`;
  runtime `scenes/forest/runtime/forest-path.jpg` SHA256
  `afd9f0551fb5ce83ba3dec94c9dd8e024622c7d19068ca917b4f48cbcf1ef470`.
- Bridge prompt intent: old timber-and-stone footbridge over a shallow forest
  stream, blue-white mist, two warm lanterns, no visible monster.
  Source `scenes/bridge/source/bridge-guardian.png` SHA256
  `92b5d7fde10c3930c777dbd938b87a2dc070ce3684b2188947b01a5c52d04520`;
  runtime `scenes/bridge/runtime/bridge-guardian.jpg` SHA256
  `bac767067825cd5ab447eac75ecc184ace41ebb3ca956b724d92a65ec123d631`.
- Camp prompt intent: safe woodland clearing at night, small fire, hanging warm
  lantern, folded traveler blanket, starry sky, no people.
  Source `scenes/camp/source/camp-fire.png` SHA256
  `c3d1611e796bdbcaff3214a7e268550bd8e8f18ed73a7f9c13c484dbcb0dd730`;
  runtime `scenes/camp/runtime/camp-fire.jpg` SHA256
  `c5118c5f5ceae28c6c55d6daea182af4249316ad53554aad28b0d0a30b9f258e`.

## Coast Scenes

Three original 16:9 coast backgrounds extend the same painterly medieval
fantasy direction. They contain no people, text, UI, logos, watermarks, modern
equipment, or sexualized imagery. Sources are `1672x941` PNGs; runtime files are
`1440x810` JPEG quality 86 derivatives.

- Beach: broad sand, gentle surf, shells, driftwood, tide pools, and distant
  cliffs at warm late afternoon.
  Source `scenes/coast/source/coast-beach.png` SHA256
  `686b308a4ce640d0403221578b3b0ad6712b491415a35fc923b5b7f9d007d717`;
  runtime `scenes/coast/runtime/coast-beach.jpg` SHA256
  `8102ff79deaa6fa84e28d40aad007c84564cdd57a956d214a2eae31ae35063a6`.
- Tidepools: low-tide rocks, clear shallow pools, seaweed, shells, a hand-made
  marker, and a folded drift letter.
  Source `scenes/coast/source/coast-tidepools.png` SHA256
  `459c16a61d5006152786eae662c37a0cc932a78510268d39d046c567b80fc361`;
  runtime `scenes/coast/runtime/coast-tidepools.jpg` SHA256
  `8b74922a150e57fe42e9e861937599882378b045de7d0d49a6b13e45205976de`.
- Lighthouse: old stone lighthouse, cliff walk, warm lantern, sea mist, and
  seabirds at blue hour.
  Source `scenes/coast/source/coast-lighthouse.png` SHA256
  `8d28fcda16abc7137e556ce729290ca5a0b69657314ffb0b4d86a906e38a651b`;
  runtime `scenes/coast/runtime/coast-lighthouse.jpg` SHA256
  `51cd26158ca648902ace340db4d6aabdbe921fdf4f80a27a9d536fdaafb4cde7`.

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

## Beach Swimwear Variants

Lia and Serin each have a `beach-swimwear` costume with the same six stable
expressions. These are ordinary age-appropriate one-piece beach outfits, made
from the existing character identity anchors rather than recolored travel
clothes. Lia wears a mist-blue swim dress with cream piping and carries a folded
towel. Serin wears a moss-green short-sleeved swim dress with ivory trim and
carries a straw beach bag with her field notebook. Both keep their established
faces, hair, accessories, natural same-age proportions, relaxed standing pose,
transparent background and non-sexualized camera.

The source files are `1024x1536` RGBA PNGs. Runtime files are shared
`480x720` RGBA derivatives.

### Lia Beach Swimwear Source SHA256

| Expression | SHA256 |
| --- | --- |
| neutral | `cbd64790b5c3910f9c57d1f36e37a15faca76ba1e6c47800555ed8039f91d208` |
| welcome | `d1f746cb2613ec13e850e5a93a9af9a2a954e93838ba37d5b617816a42c6835e` |
| cheer | `89b9e3b866dfa6d5c844f5f727d0b919449009251ba8f60c76c2fa7d63e471f2` |
| concern | `79620687aeebf25901221f70f74b5c2cb2675f7dec245260b92de81a08e863b5` |
| relief | `12ccbe086cece5ceb171c3397ae688e48b9e5f09e696ee01939e16f65beb2c91` |
| surprise | `533c6c2c8c24e506c42040c84d0ceada66b981119387cd58f9ba18b9a89126b9` |

### Lia Beach Swimwear Runtime SHA256

| Expression | SHA256 |
| --- | --- |
| neutral | `72f5818d599d71b949d5d4cb842b273869a39b8a8816db17e8b3f28823a21f66` |
| welcome | `dddca7a374e70876c66351187f76ba457b7f7429bf571c70c7669737fa978dc9` |
| cheer | `862f6566dc7d2681e188906b2ecacbe51d8df6e0881efb9384513cc2177aa529` |
| concern | `521d2fbb82db1bc5bacb10b6b670bbe63daf8123d350ff600337cdf70d09af70` |
| relief | `1f07e76ecd9564e89452773a4ba84e174f4e86b4dfaa525bc8ea86fdffd09bf1` |
| surprise | `d2e79f2719bd473afcada2c479d9c6c81934a5f324da24c5216512c35f391360` |

### Serin Beach Swimwear Source SHA256

| Expression | SHA256 |
| --- | --- |
| neutral | `7a14302cc2156978814db017a977e40c3316c46d58ec640956f0ec8b67bb7039` |
| welcome | `430cb81ee27d56596157ff8686ad2d8cca5fa112a8cf457bdbfcce5444556d1a` |
| cheer | `b54ed5296e0179d12e4fb263a3d12c614fc39c5d2fae5fc3e367395bd62dc9b9` |
| concern | `6487f2ff98f6b53e851f917a44f31017e09c8c5aed8f428cf8e0fd48d111c35b` |
| relief | `ed79aef9ac7b1191d8ab35d205107305a1217dd233737b07194cb59dc1a031c4` |
| surprise | `27c2902f4d00a78e923ea5fbdfb828636f5cfcdb1bdbd0d28db215e2d270ab02` |

### Serin Beach Swimwear Runtime SHA256

| Expression | SHA256 |
| --- | --- |
| neutral | `035a24a75706446d16d302f2c4d08241c750ce4ee55a38515248b0e07bc044a5` |
| welcome | `d8ca4e5ca90a7d395c30ae0650f36c2de86e59ae64f6de649846fa0f9209974f` |
| cheer | `ef2e40c5169dc4d5b5155edb076ac30e72ab8eb2c377f33c550c5c41d8364246` |
| concern | `51eb8197a7783c4f5dd5fa1fa40108ea488d91da63922a2cccb3322231e7937d` |
| relief | `d9550d09fd2913026c518dac859ff655bd25d68bad3c09a4653b79402eac41be` |
| surprise | `11b66e32360d08e6a0f183a9f8c540b99eb47dd3c31d4fa5e99b439980bd6143` |

## Fina

Fina is an original 16-year-old traveling cartographer. Her H5-03 standard uses
natural same-age proportions, chestnut-red hair with a short side braid and
cream hair ribbon, amber eyes, an ochre short-sleeve cotton-linen top, ordinary
knee-length travel shorts, cloth waist pouch, practical shoes, a small brass
compass and leather map tube. The stance and framing are non-sexualized, with no
midriff exposure, high heels, heavy cloak, winter coat or science-fiction gear.

- Generation and edit mode: built-in ImageGen, transparent background.
- Reference role: the visible Lia and Serin portraits were used only for
  linework, rendering, lighting and canvas consistency. Fina has an independent
  face, silhouette, outfit, palette and props.
- Identity method: one `1024x1536` neutral anchor; every expression was edited
  from that exact anchor with identity, outfit, pose, hand placement, props,
  lighting and canvas invariants.
- Runtime method: macOS `sips -Z 720`, preserving RGBA alpha and a shared
  `480x720` canvas.
- Expressions: `neutral`, `welcome`, `cheer`, `concern`, `relief`, `surprise`.

Source SHA256:

| Expression | SHA256 |
| --- | --- |
| neutral | `9532a693ceead80cb544886b30da0d865f50b4dfb7330665e8ec90e54193e3a6` |
| welcome | `12c09121f96b61d9c3f76906bf01de2a8a87d5d665f4744f712ac18e993ee5b2` |
| cheer | `38d8e91232f13f75a7a98520537566016bc5a976de227a09e0386c1850db70e5` |
| concern | `107e57cc5c4a612fef99f150dd9a8c0fdda89cb985cd2f7ee2dfa0153ce68fc9` |
| relief | `765df73dafef6f179ca3043a4356fee8b78fed27cbd872a03257a02f5a78c6ec` |
| surprise | `b4c850a00a2a05bc323d50ded0e6d61e9d51dbf23100c8c14b35620b2934f38a` |

Runtime SHA256:

| Expression | SHA256 |
| --- | --- |
| neutral | `ed10c7d85476dc8bbec2c7c510ce83e2820942af68a68cff64d5b2be5f0f59b7` |
| welcome | `dc047166176e8c8cc7d963f9775b13aeec3b5566c58540f31875b629952ede03` |
| cheer | `11b8ddcac6b30f1e4dc6dc3a9d362641d6e870062439d9463940cc840ae0ced7` |
| concern | `65626875b7522118296cabae606b538057266eb984a59883d52284fbc2920311` |
| relief | `7e3fcd3a513a4ed014e5e641b3b657faf9bd1448825c2fea1c042b9830d90f9f` |
| surprise | `e4a3372e4a664f872c98d8d453266492ea056c4fc43e858a77d9be4b0344f3a7` |
