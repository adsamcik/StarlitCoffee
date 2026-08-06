# Starlit Tactile production log

Date: 2026-08-02

Status: all 114 stage candidates passed the recorded review loop and are
mirrored into `drawable-nodpi` as shipped instruction assets.

## Reproducible generation contract

- Generator: Codex built-in `image_gen` tool. The model/build identifier and
  seed are not exposed, so prompt replay is stochastic.
- Exact prompts are committed under `prompts/brewing/assets/<asset-id>`.
- Accepted prompts, revision decisions, source hashes, and reference-image
  payloads are committed.
- The final v9 style master, accepted review WebPs, shipped app assets, and exact
  compact reference payloads remain in the current tree. Large chroma/alpha
  intermediates and rejected renders remain recoverable from Git history but
  are excluded from ordinary clones going forward.
- Transparency uses the installed image-generation helper with
  `--auto-key border --soft-matte --transparent-threshold 12
  --opaque-threshold 220 --despill`.
- Review WebPs are RGBA, 1024 by 768, produced with Pillow 12.1.1 using LANCZOS
  resize and WebP `quality=95, method=6`.
- Every accepted image is reviewed at 384 by 288 over `#F2E0D5` and `#52443C`.
  Review covers exact equipment/action, physical support, liquid continuity,
  text/noise absence, transparent perimeter, and image-above-copy readability.

The shared rendering reference is
`docs/brewing/illustration-style-explorations/starlit-tactile-2026-08-02/starlit_v9_rendering_reference.jpg`
(SHA-256
`f793ac48072d3010cb2346ef9f68f7ae38f9dcb9d4335b631dd2723fad7a6625`).
It calibrates rendering and composition only; exact stage briefs remain the
content authority.

## Batch 01 retained candidates

| Asset | Accepted | Generator cache lineage | Prompt lineage | Alpha / WebP SHA-256 |
| --- | --- | --- | --- | --- |
| `instruction_p1_chemex_42_700_stage_01_instruction_default` | `v2` | `exec-6b92b0a6-4d47-4720-9b30-c3c083791641.png` → `exec-51831b0e-2ac1-4094-867a-c536c7cba9a3.png` | `starlit_tactile_v1.txt` → `starlit_tactile_v2.txt` | `efda96fbba7b6754b74417e0a936e2003c96e096fa056b618ae5ea7b366202c8` / `1c12b9f8cede4e3f21d8bf3b57a47f4e56328c3b3bbf731b73d5646377325f5e` |
| `instruction_p1_clever_water_first_15_250_stage_05_instruction_default` | `v1` | `exec-7c901d2b-4cb1-41c6-b95b-ba4694aa5707.png` | `starlit_tactile_v1.txt` | `734f9ea0bb1d423e7403ea4bcd2bdb6b534f8c01c9424e37f18a35f90b75d2f2` / `5377ccb0545febb43f0674a8572d42ef1883f4bc80b40071be5472b69173ccee` |
| `instruction_p1_switch_official_20_240_stage_04_instruction_default` | `v9` | `exec-55238134-5d48-40fe-99bc-0d505123cc1b.png` | `starlit_tactile_v9.txt` | `cf0c7b412a64f7bbfc001886190dd82b02929726df5ec45d57853d4d311fa24f` / `17e443be3c46e986a4eeadb03f1180d79290db10ca806268f6b0ffe5c5e720d2` |
| `instruction_p1_phin_screw_18_120_stage_02_instruction_default` | `v3` | `exec-3d86f85c-7dad-466d-9b23-3524978571d9.png` → `exec-b1d02067-2af1-49ba-8cf8-fbb20606ad99.png` → `exec-fee89b72-3203-4713-993a-3ee9f1170e01.png` | `v1` → `v2` → `v3` | `2da9a19be28c9989e13ddf6fc13b1a12e87600e20b7ee5e103f65e9e0370baa1` / `e3ce5be87501976bb757e0f83343633e140bf24f7111e277c2a34cc79ffcc773` |
| `instruction_p1_auto_cupone_20_300_stage_03_instruction_default` | `v2` | `exec-4fe68b8a-9d6d-4b64-9807-3cf0de593fda.png` → `exec-0ed91431-d7d7-4e48-9c24-bb37d5437190.png` | `v1` → `v2` | `055b7110892c9f2c22b2bb3b900ca9bf440e958ec728f99a744e76f20c409a41` / `192f736fb81af9334606da96458404bd3936062f7d8b34a8b8a61a2b2bf98060` |
| `instruction_p1_wave185_ozone_25_400_stage_01_instruction_default` | `v4` | `exec-667d53cc-ab25-4118-83a0-657286735e87.png` → `exec-478b976f-3b9f-45ce-83b1-39862628f9a1.png` → `exec-fab5ee68-0b97-4414-882a-4d139371bf7c.png` → `exec-c00d4a66-8b56-4e6b-957b-19055bd697a0.png` | `v1` → `v2` → `v3` → `v4` | `931fff6bc186e3bbcdb8bc22d6d48de8a019fba4b0d2658cee3b68739eeddaca` / `7306013a001dea8d583a606757b5412ae310499225177510434ce3751389926b` |

### Exact reference inputs

- Chemex v1 used `switch_v8_style_reference_thumb.jpg` (192 by 144,
  SHA-256
  `bbd00d7d550abfe95c1e447a60e71edfa5ef0efbe5ce65918fc0e676394440e1`).
  Chemex v2 used the committed v1 raw as its sole edit target.
- Clever and screw-phin v1 used the shared v9 rendering reference.
- Cup-One v1 used
  `instruction_p1_auto_cupone_20_300_stage_03_instruction_default/input_geometry_reference.jpg`
  (SHA-256
  `493b6f39d125881f99893e48ce0ac1bccc9bc85c86647045d86b319542c92b3c`)
  for equipment geometry and the shared v9 reference for rendering.
- Wave v1 used
  `instruction_p1_wave185_ozone_25_400_stage_01_instruction_default/input_geometry_reference.jpg`
  (SHA-256
  `33273b43e3208df3c7afabf6e7ceaf22453ea092da6c45246282db333279d08c`)
  for equipment/action geometry and the shared v9 reference for rendering.
- Switch v9 input roles and hashes are recorded in
  `prompts/brewing/starlit-tactile-style-exploration-2026-08-02.md`.

### Transparency and phone QA

| Asset | Key | Transparent / partial pixels | Alpha bounding box | Phone-review result |
| --- | --- | --- | --- | --- |
| Chemex | `#03ec05` | `551290 / 4461` | `(146, 19, 1302, 1071)` | Three-layer stack remains at the spout; air channel stays open and traceable. |
| Clever | `#07da1a` | `644933 / 3911` | `(202, 20, 1313, 1063)` | Wide server supports the release ring; contact/outlet/stream axis stays centered. |
| Switch | `#02e512` | `586590 / 4153` | `(184, 20, 1366, 1069)` | Lever, supported centered ball, open amber path, one stream, and granular slurry stay legible. |
| Screw phin | `#04c11e` | `631035 / 3875` | `(193, 18, 1284, 1072)` | Disc rests on loose particles without compaction; relaxed grip does not read as tamping. |
| Cup-One | `#03d718` | `600479 / 4008` | `(124, 28, 1319, 1055)` | Reservoir ridge, arm, No. 1 paper, stable mug, and off state remain unambiguous. |
| Wave 185 | `#03c622` | `596903 / 4164` | `(198, 29, 1378, 1076)` | Supported kettle, open pleats, level bottom, and both water paths survive reduction. |

All four corner alpha values are zero. Every WebP is below 140 KB. No text,
logo, arrow, label, border, or unrelated prop remains in an accepted candidate.

### Rejected cache-only outputs

- Clever `exec-94bc93d9-e891-44ac-8bf2-c43fba1b949f.png`: forbidden arrows,
  industrialized actuator detail, oversized hand, and product-rendering drift.
- Switch rejected outputs are enumerated in the style-master generation record
  and are not production inputs.

## Source checks used by batch 01

- Chemex official FAQ: three filter layers face the spout so the paper cannot
  collapse into the air channel.
- Clever official product guidance: the brewer uses a standard No. 4 filter
  and placing it on a vessel activates its drain valve.
- Moccamaster official Cup-One quick-start guide: attach the outlet arm, fill
  with fresh cool water, use No. 1 paper, and center a cup below.
- The exact-stage queue remains authoritative for the screw-phin and Wave 185
  recipe-specific states and registered evidence IDs.

## Batch 02 retained candidates

The closed-loop tracker is
`docs/brewing/starlit-tactile-production-tracker-2026-08-02.json`. A generated
draft does not close an asset: source/mechanics review, exact prompt capture,
full-resolution visual review, transparency inspection, both 384 by 288 theme
reviews, and final artifact verification must all pass. Rejected attempts retain
an explicit regeneration todo until a later revision resolves it.

| Asset | Accepted | Generator cache lineage | Prompt lineage | Alpha / WebP SHA-256 |
| --- | --- | --- | --- | --- |
| `instruction_p1_auto_cupone_20_300_stage_01_instruction_default` | `v1` | `exec-41aa5373-1b7a-45d3-9aac-207c7ff81abb.png` | `starlit_tactile_v1.txt` | `d3f8deb74b06ec3de7977a4b9cea3c3fdbb20a59f9ff20e7be9663e38eb844b4` / `5fe94721b160581ac85ccca8a62d180bbbc4aeb49aecb9c021eb22f3c73eb6aa` |
| `instruction_p1_auto_cupone_20_300_stage_06_instruction_default` | `v1` | `exec-7fb7aca2-8e05-4b9b-914c-6fa2ace27a74.png` | `starlit_tactile_v1.txt` | `79c9089551950e00257238bd1e731354652a4cb5030cdc2f0be388d58b9bea8d` / `2b0aa3b7e394806dcea74f958a8c51e6f595a24b45a46458d3267ebd02f1daf3` |
| `instruction_p1_switch_ole_boen_hybrid_16_5_240_stage_03_instruction_default` | `v1` | `exec-dfbf0310-84c3-45f9-96e2-6626e6e4fb0a.png` | `starlit_tactile_v1.txt` | `ee19d481ef36371bd3bda864b4ff6eca78ce033e30a3444276ea8bab3bf2c036` / `acc4b9a0d297cc70f2cea3b0f950a123c5c8155bcd4817c03dc18d4c9e2360f8` |
| `instruction_p1_switch_gravity_15_250_stage_01_instruction_default` | `v3` | `exec-748ac3d1-a602-4df2-8cbf-3771ce49692d.png` → `exec-9aebbea8-1c8a-4008-821b-5fa35ecaba46.png` | `starlit_tactile_v2.txt` → `starlit_tactile_v3.txt` | `137da11c1a416fac801b4c9c12c2e320ddd21be684183ef8472621fae5182f7c` / `adba82b05292d216b86780f3664fd0d6a7a1046084a66673f64e1a7fcb851808` |
| `instruction_p1_phin_gravity_14_118_stage_01_instruction_default` | `v1` | `exec-678088de-a403-4fd5-bec6-73be9e0f9d4e.png` | `starlit_tactile_v1.txt` | `9501589651cd2f28d8d67e46cd2b320d925474ce098fa8de15d3463193ec6fb5` / `6cc6936206e2266f4a49233ae2b1c1d311b2a59882f25d8af5a64c167974d803` |
| `instruction_p1_phin_gravity_14_118_stage_07_instruction_default` | `v4` | `exec-5768dce6-c183-43a3-be60-df811d33c99f.png` → `exec-df25ab3e-26d2-4076-8ae6-5965f48b8edd.png` → `exec-373e72c1-fda2-44f2-a942-f2e1fc28a88b.png` | `starlit_tactile_v2.txt` → `starlit_tactile_v3.txt` → `starlit_tactile_v4.txt` | `f7a81c2e2940fa1657b3f6b4bf317950f2f5fa2981a61c3ea183e1c344822c38` / `37541b72c2c1a5821ed872a619a0ec7ef945eaa2caeb7982aac0ab7dcda41413` |

Every completed generator event's untruncated `revised_prompt` was exported
verbatim from the local Codex session record. This includes rejected attempts,
so the repository does not depend on a reconstructed prompt or chat summary.

### Exact reference inputs

Every blank-canvas Batch 02 generation used its committed 480 by 360
`input_geometry_reference.jpg` for equipment/action geometry and the shared v9
rendering reference for style only. The geometry-reference SHA-256 values are:

- Cup-One stage 01: `ea4de2e5ec25ddb5d344b06773e9bbd7a72e3db5106701bc99f704c45b1b7727`.
- Cup-One stage 06: `e56c7f7ef6e7a9416a21be03cc2d84c917ffe6f06be3e584e3573cf619933c52`.
- retained Switch hybrid: `77f4a9df83d6cd4f5b1f6c48463025f2e5d313c7ecae89a6cb43f390b2303297`.
- open Switch rinse: `358c930cee2110ed36ab1f5ae61d594132adbdc16366bdeb3724d60a5e26a0c1`.
- gravity-phin dry setup: `e64687a2a5455416c084b40c4eacbea3f7ea58cfec2a22b6c7ef047ef674f7fa`.
- gravity-phin hot removal: `f508b0e49f870e0053ab0cd453a0c0cf1e8c449255c4d1e91174cc848602b7a7`.

### Transparency and phone QA

`tools/process_starlit_tactile_candidate.py` deterministically creates the
RGBA 1024 by 768 WebP and 384 by 288 light/dark review composites. The PNG
composites are lossless; high-quality 4:4:4 JPEG copies exist only to make
direct visual inspection reliable through the Windows sandbox.

| Asset | Key | Transparent / partial pixels | Alpha bounding box | Phone-review result |
| --- | --- | --- | --- | --- |
| Cup-One stage 01 | `#04de16` | `510640 / 3599` | `(72, 18, 1366, 1061)` | One paper, one open hole, dry detached holder, empty slot, and off state remain clear in both themes. |
| Cup-One stage 06 | `#04d91d` | `552084 / 4363` | `(53, 34, 1390, 1037)` | Loose plug, dry machine, empty holder, and the tool's continuous outlet path remain clear in both themes. |
| retained Switch hybrid | `#07dd14` | `577462 / 4049` | `(182, 34, 1369, 1068)` | Seated ball, retained granular slurry, no drainage, empty server, and safe pour remain distinct. |
| open Switch rinse | `#0fd51a` | `729653 / 3647` | `(165, 70, 1260, 1012)` | Clean paper, raised ball, separate open seat, and uninterrupted clear flow survive phone reduction. |
| gravity-phin setup | `#03de0f` | `544268 / 3888` | `(147, 24, 1356, 1069)` | Full plate support, level dry granular bed, smooth chamber wall, and separate loose press remain clear. |
| gravity-phin hot removal | `#12c720` | `890350 / 2947` | `(139, 249, 1375, 960)` | Protected grip, separate black drink, contained press, and nested lid-coaster remain readable with safe margins. |

All four corner alpha values are zero. Every WebP is below 100 KB. No text,
logo, arrow, label, frame, accidental green residue, or unrelated prop remains
in an accepted candidate.

### Rejected outputs and closed regeneration todos

- Open Switch rinse v1
  `exec-be984d3f-221f-4f2d-8992-78430075fa3f.png`: the ball read as seated
  while liquid emerged below it, creating false teleporting flow physics.
- Open Switch rinse v2
  `exec-748ac3d1-a602-4df2-8cbf-3771ce49692d.png`: the valve topology was
  correct, but the kettle hand was clipped by the upper-left edge. V3 retained
  the open gap and continuous flow while restoring the complete hand.
- Hot gravity-phin v1
  `exec-25a55e5a-9391-4d4f-aa79-2ae96625d97c.png`: the lid sat separately in
  front, so the safe destination was unclear.
- Hot gravity-phin v2
  `exec-5768dce6-c183-43a3-be60-df811d33c99f.png`: the lid still missed the
  landing axis and the wrist reached the right edge.
- Hot gravity-phin v3
  `exec-df25ab3e-26d2-4076-8ae6-5965f48b8edd.png`: the phin was correctly
  supported inside the inverted lid, but the hand remained cropped. V4 keeps
  the supported metal stack and contains the complete protected hand.

### Source and mechanics checks used by batch 02

- The exact-stage queue remains the content authority for all six states and
  their registered evidence IDs.
- Moccamaster's Cup-One guidance fixes the No. 1 paper, detachable holder,
  single restricted outlet, power-off dry inspection, and unplugged cleaning
  context represented here.
- HARIO's Switch mechanism requires the retained scene to show a seated ball
  and no drainage, while gravity mode requires a visibly lifted ball and
  freely draining clean-paper rinse. A visible stream without an open ball-to-
  seat gap is rejected even if the rest of the picture is attractive.
- Nguyen Coffee Supply's gravity-phin configuration uses a loose unthreaded
  press. The setup image therefore keeps the press separate and the hot-removal
  image keeps it contained, while the matching inverted lid supports the hot
  phin and the serving choice remains outside the image.

## Batch 03 retained candidates

All six assets completed the tracker loop independently: source/mechanics
review, exact prompt capture, full-resolution inspection, chroma removal,
transparent-edge verification, both 384 by 288 theme reviews, and final WebP
verification. A failed draft reopened its asset until a later revision passed.

| Asset | Accepted | Generator cache lineage | Prompt lineage | Alpha / WebP SHA-256 |
| --- | --- | --- | --- | --- |
| `instruction_p1_auto_cupone_20_300_stage_04_instruction_default` | `v2` | `exec-95c658af-f1ee-4bd2-85c9-7af5dc829288.png` → `exec-cac6832b-75f9-42c3-94dc-eb3695277469.png` | `v1` → `v2` | `4ead237e84a26f5ba5f008e041b8e39bbd52140eff2dc43f1b51263260a73d7c` / `7a905b6a634db7848a9800bf0ccf467fd2d60b22452f6b9ef93abd7a1b3753dc` |
| `instruction_p1_clever_water_first_15_250_stage_01_instruction_default` | `v2` | `exec-82020e7e-baab-4eaf-8c4b-b1d9dc58e63f.png` → `exec-8dd11c9e-df97-459e-82df-daeb7652b4aa.png` | `v1` → `v2` | `cc9b9d674defbfdcfc54aaa50d1f6a31e7136ca9f7317e8c59389355af5e72ac` / `2a9c7bcb90b2244cd9a2a2dd7cda1b5026b4991194f44a0065b8af67cb88a04e` |
| `instruction_p1_phin_screw_18_120_stage_01_instruction_default` | `v2` | `exec-b0ee61df-31f0-4e25-89d9-56904c9c0fa2.png` → `exec-c4d36fa1-cc39-4811-aa06-37353f6f61b4.png` | `v1` → `v2` | `16da25dfaadf680739c72f1326c53f31ff441954f0878d0b62e014e55393e03c` / `de916a2998cd1b2c9c55abe7b3ad0bef60d5145cba4fe8040ff09afe607167cb` |
| `instruction_p1_phin_screw_18_120_stage_05_instruction_default` | `v2` | `exec-f63a67ef-1c6e-4746-a730-bc57b7dc3b00.png` → `exec-e9a93509-7775-4465-adbf-45f521a1694f.png` | `v1` → `v2` | `b43bec209b25713d56984295a609c8b067014c8467e5b340f5d2a69e10958fab` / `115e57346091ba6a0f150b7d2f9c0a03c2a0c4e321e1edd8af9cc1cab21196aa` |
| `instruction_p1_switch_official_20_240_stage_01_instruction_default` | `v3` | `exec-def626e4-8ed2-46e6-a60d-541d62dfafd0.png` → `exec-aa7e21c5-a0a6-4c12-b894-88919dee74b6.png` → `exec-6c8070db-e9f1-4710-a5b0-d10ee8e267a0.png` | `v1` → `v2` → `v3` | `c2bfa551e2732865e42eea668c2facf6d2a6fcaa2f23e47a1f81c4044380b941` / `dc23d56bc15fea052a659ba1436c6a14c19ec4a4377c2a4ae3074081086aac77` |
| `instruction_p1_v60_official_15_250_stage_01_instruction_default` | `v1` | `exec-71e631eb-ba1a-4a36-bb6d-12b55b9ba75a.png` | `v1` | `6fa0db74f824d7b1b58e4725de1f4b9147141f070238413e50809a8ab8cef6a7` / `062f919801fed18e942632326c21d42c8285a080e94c3ecc614f031ada953b88` |

Every completed generator event's untruncated `revised_prompt`, including all
rejected attempts, was exported verbatim from the local Codex session record.

### Exact reference inputs

Every blank-canvas Batch 03 generation used its committed 480 by 360
`input_geometry_reference.jpg` for equipment/action geometry and the shared v9
rendering reference for style only. Edit revisions used the preceding raw
chroma output as their sole composition target. Geometry-reference hashes are:

- Cup-One active brew: `e43c024614696931ed778f46cb5cb5289cda8e32837e9223bd6c0749b0514407`.
- Clever post-rinse: `7c366f3e8dd72aab9fb587247e6eb34ddf6166e1273796ffaa43fb1ab03ea1bc`.
- screw-phin dry setup: `8b339a0be41906089db55bc38a617900f1eaaac43a966679da518ec7d83e1a14`.
- screw-phin slow drip: `b58f08ee230b971a099f7ce70087160241bd62739fcbb383d458bf8239c7da0e`.
- Switch closed post-rinse: `4d065ff141a7779187a779ee783707f6d7064a62df08c0229b1b9618e1199033`.
- V60 post-rinse: `07233a6a36f1e0d3a048c73ecc0335b570998f5ccd0736b625676f2d8023c1f8`.

### Transparency and phone QA

| Asset | Key | Transparent / partial pixels | Alpha bounding box | Phone-review result |
| --- | --- | --- | --- | --- |
| Cup-One active brew | `#0adc1b` | `741858 / 3662` | `(226, 24, 1246, 1060)` | Distributed arm, wet bed, lower coffee stream, indicator, and centered mug remain distinct. |
| Clever post-rinse | `#03d720` | `536969 / 4554` | `(163, 16, 1385, 1075)` | Opposite seams, handle support, unpressed actuator, and air gap remain readable. |
| screw-phin setup | `#10d218` | `470322 / 4002` | `(40, 12, 1410, 1046)` | Full rim support, textured bed, threaded post, and insert flat on its lid remain clear. |
| screw-phin slow drip | `#03da1e` | `666174 / 3741` | `(192, 19, 1309, 1065)` | Exactly three separated drops, covered phin, and stable support remain clear. |
| Switch closed post-rinse | `#08ea16` | `628212 / 4244` | `(168, 28, 1264, 1058)` | Ball-to-seat contact, untouched lever, dry outlet, and empty server remain legible. |
| V60 post-rinse | `#02d727` | `610987 / 3963` | `(194, 30, 1353, 1061)` | Folded seam, damp seated paper, and empty supported server survive reduction. |

All four corner alpha values are zero. Every WebP is below 113 KB. Both theme
reviews show clean, borderless perimeter blending with no green residue, text,
logo, arrow, label, frame, or unrelated prop.

### Rejected outputs and closed regeneration todos

- Cup-One v1 `exec-95c658af-f1ee-4bd2-85c9-7af5dc829288.png` rendered the
  nine-hole arm as a single end nozzle. V2 keeps the upper path implicit and
  shows distributed underside perforations.
- Clever v1 `exec-82020e7e-baab-4eaf-8c4b-b1d9dc58e63f.png` did not make the
  folded #4 side and bottom seams legible. V2 exposes both without adding a
  second filter.
- screw-phin setup v1 `exec-b0ee61df-31f0-4e25-89d9-56904c9c0fa2.png` left
  the threaded insert tilted and visually unsupported. V2 rests it flat on the
  lid.
- screw-phin drip v1 `exec-f63a67ef-1c6e-4746-a730-bc57b7dc3b00.png` formed
  a near-continuous five-drop column. V2 uses exactly three separated drops.
- Switch v1 `exec-def626e4-8ed2-46e6-a60d-541d62dfafd0.png` used an ambiguous
  rectangular shelf instead of a circular valve seat. V2 corrected the seat
  but introduced a finger pressing the lever. V3 keeps the corrected seal and
  removes the hand entirely.

### Source and mechanics checks used by batch 03

- The exact-stage queue and canonical library remain authoritative for recipe-
  specific quantities, stages, equipment states, and registered evidence IDs.
- Moccamaster's current Cup-One quick guide confirms the installed nine-hole
  outlet arm, No. 1 holder/filter, centered cup, power-on brew, hot-arm warning,
  and approximately four-minute cycle context.
- HARIO's current Switch description confirms that the steel ball blocks the
  base and the button releases it. The accepted closed scene therefore shows
  direct ball-to-seat contact, no actuation, and no drainage.
- Hario's current V60 guide says to fold the paper seam, soak the whole paper,
  and discard the rinse water before brewing.
- Clever's current guidance specifies a standard No. 4 filter and a drain valve
  activated only when the brewer is placed on a mug or carafe. The accepted
  post-rinse scene keeps the actuator off-vessel and visibly unpressed.
- Nguyen Coffee Supply's phin guidance supports level grounds, clean dry holes,
  restrained insert pressure, and an observation-driven slow drip. Recipe-
  specific screw-phin dose and timing remain those in the canonical library.


## Batch 04 retained candidates

This closes the 24-asset production tracker. Every final-batch image passed the
same independent source, mechanics, prompt, full-resolution, aspect-ratio,
transparency, dual-theme phone, and final-artifact gates. Failed gates reopened
their asset until a later revision resolved them.

| Asset | Accepted | Generator cache lineage | Prompt lineage | Alpha / WebP SHA-256 |
| --- | --- | --- | --- | --- |
| `instruction_p1_switch_ole_boen_hybrid_16_5_240_stage_01_instruction_default` | `v2` | `exec-9073d887-6776-4a0a-8a4a-22583f987312.png` → `exec-ad5728cb-3415-4eb3-9757-a38d06693aeb.png` | `v1` → `v2` | `fafd9145b4048cfd5b4613d49498838823c2602e7bf3132dc9b36de01d349f4b` / `fdb74f907c323e806f620bf428ef6927cb138cbc208c86b6152ae37786ae0496` |
| `instruction_p1_v60_official_15_250_stage_04_instruction_default` | `v2` | `exec-f80b0348-bcbf-4379-929b-5b476b25e3a2.png` → `exec-5582545f-6bd6-4f20-99dd-4edfc90886b2.png` | `v1` → `v2` | `71989ee2922bfdb8393a0731b5d8065f65dcf3334a2cf3e662ba793a1ae6b8ff` / `9eb929d743c9e567972f6b45b08cba025d2ca28c09b275bd093b4b319e29e293` |
| `instruction_p1_v60_rao_20_330_stage_01_instruction_default` | `v3` | `exec-8d4db884-1126-4667-b804-ca3b082a512d.png` → `exec-5d845f12-927b-4a19-9f5c-8dd6a8765266.png` → `exec-b898fb9d-d49f-4722-8263-70bd536749fa.png` | `v1` → `v2` → `v3` | `27f12213cd9ff92f0d5be497f41704fb1ab7d58c3f60dfa50802866901a1edf4` / `1154d0387fa8a0f7762902c9478c7432c7f4b0f3383650fe858f7bc20aff7478` |
| `instruction_p1_phin_gravity_14_118_stage_02_instruction_default` | `v2` | `exec-eb002de7-a965-4311-8833-d79b6432742c.png` → `exec-1b1299ff-6992-4d1f-98b5-e86c79238313.png` | `v1` → `v2` | `a239cc1b293ed79bee7aafaad2b8b638cbf0d9ac847c3d0cb4b176a151e3c431` / `95bff7d208c70c73fdacd3cdfcf868d307829a375e9bb039fc411b5e7f22036a` |
| `instruction_p1_auto_cupone_20_300_stage_05_instruction_default` | `v1` | `exec-76c21f87-ae32-420d-a358-218d9e84404b.png` | `v1` | `7094b2269b292a7b608cfe5264f379c3f24b33463036d7a25e413d6b8eb4d645` / `f520f23978807d9c3aaf602b55170c25cea96abf9b9ac0decf3de0accc426c4f` |
| `instruction_p1_clever_water_first_15_250_stage_02_instruction_default` | `v2` | `exec-19684e38-45d6-488e-9bd6-6dc1c8b3fdcf.png` → `exec-59259ff8-a78b-4440-8d0b-cd5eb9233dd3.png` | `v1` → `v2` | `9af8a126816041a0af5deb6ba253de925f37813f15afcad44c770ca2b957adad` / `e9bacb8d5f539b1453f329f9b3449cd3c06a9558e2e17aef42fdd5f79e217bf1` |

Every successful generator event's untruncated `revised_prompt` is committed.
Upstream 500 responses produced no image payload and therefore are recorded as
infrastructure events rather than image revisions.

### Exact reference inputs

Committed 480 by 360 geometry-reference hashes are:

- Switch first bloom: `188bcfee62cc890ac66c7908403fa874e6a7b4d320fddabe9ca7e9a43eccf152`.
- official V60 pour: `edc75d169ea46c95cc5b225f1fec82cdb1ab1e0e88919bd9cdb655b4377b2bc5`.
- Rao shallow nest: `d4a00b018d6397935dd8fb9fb34960db5b10dbf63fe5b36c55e0f3e0c33e664b`.
- gravity-phin disc: `925845f8b99dfa067a1203efe670c1ad0c9a3c45b5adc1b95e43cafdce5eb02e`.
- Cup-One residual wait: `8eefa667b7d12564336b532699035f901ea1f08a7bbc61c3fb06a891fce5ff48`.
- Clever water-first fill: `3dbc450ed619de46247f298e69246deb103addf48222bd2af43762ed4c48a286`.

Switch, official V60, gravity-phin v1, Cup-One, and Clever v1 used their
geometry reference for equipment/action and the shared v9 reference for style.
Switch v2, official V60 v2, Clever v2, and Rao v3 used the preceding raw draft
as their edit target. Successful Rao v1/v2 and gravity-phin v2 were generated
from fully specified blank-canvas prompts after reference-conditioned calls
failed upstream; their exact prompts are the complete reproducible input.

### Transparency and phone QA

| Asset | Key | Transparent / partial pixels | Alpha bounding box | Phone-review result |
| --- | --- | --- | --- | --- |
| Switch first bloom | `#04d31d` | `576353 / 3859` | `(180, 29, 1375, 1065)` | Shallow wet bloom, seated ball, dry outlet, and empty server remain distinct. |
| official V60 pour | `#03d316` | `694853 / 3512` | `(231, 45, 1320, 1063)` | Complete hand, low stream, bed landing, and clean paper band remain visible. |
| Rao shallow nest | `#09ee0f` | `852997 / 3020` | `(192, 98, 1261, 994)` | Smoky molded plastic, clear glass, shallow nest, and perimeter remain clear. |
| gravity-phin disc | `#02fa02` | `392613 / 4500` | `(73, 29, 1380, 1047)` | Flat unthreaded disc, ground ring, headroom, and rim support remain legible. |
| Cup-One residual wait | `#02db0b` | `637250 / 3813` | `(191, 32, 1318, 1070)` | Single-hole arm, dark indicator, centered mug, and attached bead remain clear. |
| Clever retained fill | `#07cd1b` | `583900 / 3734` | `(152, 67, 1393, 1034)` | Clear water, complete hand, seated paper, dry scale, and actuator gap remain clear. |

All four corner alpha values are zero. Every WebP is below 134 KB. Both theme
reviews show clean perimeter blending with no green residue, text, logo, arrow,
label, frame, or unrelated prop.

### Rejected outputs and closed regeneration todos

- Switch v1 `exec-9073d887-6776-4a0a-8a4a-22583f987312.png` showed the ball
  slightly above its seat. V2 places the ball directly on the annular ring.
- Official V60 v1 `exec-f80b0348-bcbf-4379-929b-5b476b25e3a2.png` cropped the
  hand/forearm at the right edge. V2 contains the complete hand and kettle.
- Rao v1 `exec-8d4db884-1126-4667-b804-ca3b082a512d.png` left the exact plastic
  identity ambiguous at phone size. V2 corrected the smoky molded-plastic
  material but let the organic stage touch every canvas edge. V3 preserves the
  material contrast and restores generous perimeter transparency.
- Gravity-phin v1 `exec-eb002de7-a965-4311-8833-d79b6432742c.png` was emitted
  at 1122 by 1402 portrait despite correct mechanics. V2 is exact landscape
  4:3 with the same loose unthreaded-disc state.
- Clever v1 `exec-19684e38-45d6-488e-9bd6-6dc1c8b3fdcf.png` cropped the hand,
  hid the paper-seam preparation, and placed the actuator too close to the
  scale. V2 contains the hand, exposes the side fold, and leaves a broad dry
  actuator gap.
- Three Rao reference-conditioned calls and one gravity-phin composition-edit
  call returned upstream 500 errors before producing an image. They are not
  counted as visual attempts or accepted lineage.

### Source and mechanics checks used by batch 04

- The exact-stage queue and canonical library remain authoritative for recipe-
  specific quantities, timing, temperature, states, and registered evidence.
- HARIO Europe's current Ole Bøen recipe specifies a Switch 02, a closed
  dripper, a low first bloom, and 40-second retention; HARIO's product page
  confirms the steel-ball/button release mechanism.
- Hario UK's current intermediate V60 guide specifies V60 02/paper 02 and a
  slow small-circle pour. The accepted stream stays wholly over the bed.
- Hario UK's Scott Rao interview specifies a plastic V60 and a central
  bird's-nest preparation. The canonical normalized cue bounds it as shallow,
  not a deep crater.
- Nguyen Coffee Supply's current phin guide says to level the coffee and drop
  the gravity press on top. The accepted disc is loose, unthreaded, and free of
  tamping pressure.
- Moccamaster's May 2026 Cup-One guide specifies the current single-hole outlet
  arm, No. 1 paper, centered cup, hot-arm warning, and approximately four-minute
  cycle. The accepted post-auto-off frame shows no active stream or handling.
- Clever's current instructions specify a standard #4 paper, folded seams,
  water added before coffee, and a drain valve activated only on a cup/carafe.
  The accepted frame contains clear water only and keeps the actuator off-vessel.


## Android drawable integration

All 24 tracker-approved candidates are installed byte-for-byte under their
exact `instruction_<content-id>` resource names in
`app/src/main/res/drawable-nodpi`. The deterministic command is:

```text
python tools/install_starlit_tactile_candidates.py --write
```

Running the same tool without `--write` verifies installation without changing
files. `python tools/verify_instruction_assets.py` additionally checks the
tracker completion state, source/destination SHA-256 identity, static WebP
encoding, 1024 by 768 dimensions, encoded-size ceiling, RGBA mode, fully
transparent corners, and the presence of both transparent and opaque pixels.

The drawable payloads are integrated, but exact-stage runtime approval remains
fail-closed. The app does not yet contain localized exact-stage companion text
and alt text for every supported locale, so this image pass does not invent
accessibility copy or bypass `P1ExactRecipeLocalizationCoverage`.


## Batch 05 retained candidates — 2026-08-03

This batch extends the tracker from 24 to 29 accepted exact-stage assets. It
also introduces `tools/record_starlit_tactile_attempt.py`, so every accepted or
rejected revision updates the asset's attempt history and regeneration todo in
one deterministic operation. The prompt files contain the exact generator
input used for each call; the generator did not expose a separate revised
prompt for these events.

| Asset | Accepted | Generator / reuse lineage | WebP SHA-256 |
| --- | --- | --- | --- |
| `instruction_p1_clever_coffee_first_15_250_stage_04_instruction_default` | `v2` | rejected `exec-bc7a02e8-771b-47c8-9073-5b27bcd781f4.png`; then exact-state byte reuse from `instruction_p1_clever_water_first_15_250_stage_05_instruction_default` | `5377ccb0545febb43f0674a8572d42ef1883f4bc80b40071be5472b69173ccee` |
| `instruction_p1_switch_ole_boen_hybrid_16_5_240_stage_04_instruction_default` | `v1` | exact-state byte reuse from `instruction_p1_switch_official_20_240_stage_04_instruction_default` | `17e443be3c46e986a4eeadb03f1180d79290db10ca806268f6b0ffe5c5e720d2` |
| `instruction_p1_cezve_turkish_single_rise_6_65_stage_01_instruction_default` | `v2` | `exec-f073e82a-9f74-4ec0-948d-49ac1320aeec.png` → `exec-8c65ea0f-34f7-4218-a6a3-228da28ce107.png` | `3c553131d1df04bb7c097128c6864377aaa0b3efea76b500a92d0aff2a23d23f` |
| `instruction_p1_cezve_turkish_single_rise_6_65_stage_03_instruction_default` | `v2` | `exec-9bede8d3-ef08-4906-9890-a8c8a58ff701.png` → `exec-b9a84f94-4e4c-4242-be27-a1f50e683d9c.png` | `e7ce406e9bc1061e9990d7980ecd7f18cb0e3e3b58189463754799be773e3483` |
| `instruction_p1_cezve_turkish_single_rise_6_65_stage_04_instruction_default` | `v2` | `exec-180e7458-0c79-4550-8d23-c014890e9311.png` → `exec-d53b5050-58ce-4f8a-bdb1-f38c34d0c1dd.png` | `67b311c28541c9e2f6f78b2ddaf98596bcb5b8f0b102ce8b48bde5628f6f127f` |

### Exact-state reuse decisions

- Clever coffee-first stage 04 and Clever water-first stage 05 have the same
  visible release state: the bottom-actuated dripper is squarely seated on a
  stable server, its valve is open, and retained slurry drains continuously.
  Their recipe IDs, stage IDs, text, timing, quantities, and runtime asset IDs
  remain separate.
- Ole Bøen hybrid stage 04 and official Switch stage 04 have the same visible
  valve-release state: the lever raises the steel ball clear of its seat and a
  continuous outlet path drains to the server. The hybrid recipe's 2:10 cue
  remains text/runtime data and is not implied by the bitmap.

### Rejected outputs and closed regeneration todos

- Clever coffee-first v1 looked like a glossy glass V60, made the bottom
  actuator ambiguous, and rendered the slurry/grounds relationship poorly.
  The exact-state reuse removes that equipment error without duplicating an
  already verified physical state.
- Cezve preparation v1 rendered a thick floating coffee cake, a wood-like
  handle, and an ambiguous open trivet. V2 uses thin granular coffee texture
  over cold liquid, a matte alloy handle, a solid off-heat pad, and ample
  headroom.
- Cezve heating v1 used glossy product-render materials, a visually excessive
  full burner ring, and a slurry/foam level too high for the first-ring cue.
  V2 uses matte illustrated planes, a low flame confined beneath the base, a
  calm low slurry, and a thin perimeter foam ring.
- Cezve lift-off v1 pasted a photorealistic hand over otherwise illustrated
  equipment. V2 uses a simplified editorial hand, a credible cool-end grip, a
  visible pot-to-grate gap, an extinguished burner, and controlled foam below
  both spouts.

### Source, mechanics, transparency, and phone QA

- The Cezve frames use the resolved STC-Pro-1-class one-cup geometry: a
  tin-lined copper body, secure long alloy handle, sufficient neck headroom,
  and low gas heat contained within the base. The canonical recipe remains the
  authority for 65 g water, 6 g coffee, cold start, observation-driven foam
  cue, and immediate removal before rolling boil.
- Every newly generated accepted bitmap passed full-resolution mechanics and
  artifact inspection, chroma-key removal with soft matte and despill, static
  1024 by 768 WebP validation, transparent corners, and separate light- and
  dark-theme phone review. Reused WebPs retain the previously accepted alpha
  and phone-QA results byte-for-byte.
- At phone size the preparation frame preserves visible headroom and granular
  coffee texture; the heating frame preserves stable support, confined flame,
  handle-away orientation, and first foam ring; the lift-off frame preserves
  a readable air gap, burner-off state, safe grip, and below-rim foam.


## Batch 06 retained candidates — 2026-08-03

Batch 06 closes the remaining three frames in the one-cup Cezve recipe. All
six stages of `cezve_turkish_single_rise_6_65` now have accepted exact-stage
candidates; this does not relax the separate runtime localization gate.

| Asset | Accepted | Generator lineage | WebP SHA-256 |
| --- | --- | --- | --- |
| `instruction_p1_cezve_turkish_single_rise_6_65_stage_02_instruction_default` | `v3` | `exec-a3e91816-b552-426b-8357-f22c36509bbb.png` → `exec-be116041-6c13-4b3b-a22d-c24111ecd2d5.png` → `exec-e333c082-198d-45be-9a5a-7d0026ba7a2c.png` | `afe4d116699994d0d4a22c53057ea054e6f85c01565061f2d906a073cbc3bd72` |
| `instruction_p1_cezve_turkish_single_rise_6_65_stage_05_instruction_default` | `v2` | `exec-ba1cb187-ad40-49b9-bbfe-79ae329d8aff.png` → `exec-d569ba88-075a-49b9-b3c6-9756c00d8b35.png` | `a733f367497ca2e42b3ca932a44e3a98e5bd8f9c8023b8b7d5452a81e02ee6a0` |
| `instruction_p1_cezve_turkish_single_rise_6_65_stage_06_instruction_default` | `v1` | `exec-7664fa82-d4fa-4deb-b929-a56867dc1e83.png` | `6083f7cde7264c434638c576f48ddf1f2233da1dd8c48b4c3005db8c9e38b53b` |

The first mixing render was rejected for portrait orientation, photographic
materials, and edge crowding. V2 restored landscape/editorial treatment but
showed a grounds clump, excess rim lobes, and noisy background ribbons. V3 has
exactly two spouts, a smooth evenly dispersed cold slurry, a partly submerged
spoon, a quiet single organic stage, and no heat. A reference-conditioned edit
failed in the Windows image sandbox before producing a payload and is not a
visual attempt.

The first serving render was rejected because the receiving cup was nearly
full while the stream remained active and the wrist crowded the edge. V2 keeps
one continuous spout-to-cup stream, gravity-correct liquid in the tilted pot,
a stable cup/saucer, half-to-two-thirds fill, and generous perimeter margin.
The settling frame uses a clean educational cup section: smooth coffee remains
above a thin dense granular bottom layer, the cup is undisturbed, and a single
faint wisp preserves the still-hot warning without visual noise.

Every retained frame passed raw mechanics, transparency, 1024 by 768 static
WebP validation, and separate light- and dark-theme phone review.


## Batch 07 retained candidates — 2026-08-03

Batch 07 closes all six stages of `cezve_bounded_repeated_rise_12_130`.

| Asset | Accepted | Generator / reuse lineage | WebP SHA-256 |
| --- | --- | --- | --- |
| `instruction_p1_cezve_bounded_repeated_rise_12_130_stage_01_instruction_default` | `v2` | `exec-01e07fce-c4e0-4b8d-a218-a488f6ed35c4.png` → `exec-2efede55-1e99-4631-a8d4-ab61c96727ca.png` | `85d046c63f4415180fe96f24b774cfbb31f529ce0d1f4eb67eac9acd061c5cc2` |
| `instruction_p1_cezve_bounded_repeated_rise_12_130_stage_02_instruction_default` | `v3` | `exec-44f3b0f5-5844-4d4f-a4d5-4c90c00e6eff.png` → `exec-bb944a5e-6068-4741-b2bc-4ef3768179c6.png` → `exec-61d0e14a-dd0f-462b-b744-f29787016352.png` | `26948823bc79e155808588fd39d37156638b760b1353b7abd5cc39f899d71f9c` |
| `instruction_p1_cezve_bounded_repeated_rise_12_130_stage_03_instruction_default` | `v1` | `exec-afc20dd6-e6dc-4546-858b-d8920e703f10.png` | `0d5bad4c856253f88ecabad676c5958b634b714d73c58dff8d3e4f188a426f03` |
| `instruction_p1_cezve_bounded_repeated_rise_12_130_stage_04_instruction_default` | `v1` | `exec-cf586cd8-d1d0-4f63-b545-884f04d64d92.png` | `55c236aa92828b02e5cd8dd145f344ad7b52b323f60b7583357d0c2bedd1a970` |
| `instruction_p1_cezve_bounded_repeated_rise_12_130_stage_05_instruction_default` | `v1` | exact-state byte reuse from stage 03 | `0d5bad4c856253f88ecabad676c5958b634b714d73c58dff8d3e4f188a426f03` |
| `instruction_p1_cezve_bounded_repeated_rise_12_130_stage_06_instruction_default` | `v1` | `exec-74b598cc-30f7-4262-a748-8bf1f33b743d.png` | `e150b21d3b49aeef551407c780c99b39c2c0db50587bc8c475448a544363fa75` |

Preparation v1 was rejected for three rim lips, an interior background ribbon,
and glossy rendering. V2 has one front/one rear spout, smooth side rim, low cold
mixture, and a quiet stage. Mixing v1 crowded the top edge; v2 over-shrank the
card gesture; v3 balances phone readability with a complete transparent
perimeter while keeping a uniform clump-free slurry.

The first and second bounded rises intentionally share a bitmap: in the same
pot, grate, low flame, handle orientation, and below-spout foam state, the
ordinal event is not visually distinguishable. Stage identity, timing,
completion semantics, and warnings remain separate. The off-heat frame uses a
heat-safe pad and residual foam ring. The final frame shows exactly two equal
stable servings with smooth coffee above equal settled granular layers.

Every retained frame passed raw mechanics, transparency, static 1024 by 768
WebP validation, and separate light- and dark-theme phone review.


## Batch 08 retained candidates — 2026-08-03

Batch 08 closes the three remaining stages of `v60_official_15_250`. All five
official V60 stages now have accepted exact-stage candidates. The exact prompts
used for every generated revision are retained beside the asset prompt records.

| Asset | Accepted | Generator lineage | WebP SHA-256 |
| --- | --- | --- | --- |
| `instruction_p1_v60_official_15_250_stage_02_instruction_default` | `v1` | `exec-3fad40cf-290e-44c1-a14e-6d9d3155e439.png` | `17396461cb744e82f86e08fb3a5959049cd546fd98741695e2e46e52f76fb066` |
| `instruction_p1_v60_official_15_250_stage_03_instruction_default` | `v2` | `exec-be9671c1-bd80-4ae3-97a6-31eb617a6d25.png` → `exec-1e4daba0-259b-4f2b-87a4-3a82136a8c2d.png` | `f0e55314cda6cd59b5e88c5d7985f50a33d1c9f76c41ca1780d03a0cccd0b16c` |
| `instruction_p1_v60_official_15_250_stage_05_instruction_default` | `v2` | `exec-eab76fb9-4043-4774-91f6-0983d2323fd4.png` → `exec-910ac44c-2db9-4e95-a95e-cca6c5e3211d.png` | `ea3528abdb57e4bcaca2a840d7e98db14870ba2e62bafb05fb45e5f20c8c3a36` |

The level-bed frame preserves a dry granular horizontal dose, correctly seated
paper, empty server, and blank scale. Bloom v1 was rejected because a minimal
wetting was paired with a cup-sized server volume; v2 restricts the beverage to
a thin floor puddle, keeps the entire bed wet with only a few late-bloom
bubbles, and aligns one quiet drop with the outlet. Finish v1 was rejected for
a misleading vertical chain of simultaneous drops; v2 contains the complete
lifting hand, keeps the upright dripper aligned above the server, and shows
exactly one isolated final drop.

Each accepted frame passed raw full-resolution inspection, chroma-key removal,
transparent-corner and static-WebP checks, and separate light- and dark-theme
phone review. The tracker, packaged drawables, and implementation matrix now
agree on 41 accepted exact-stage assets and 73 open assets.


## Batch 09 retained candidates — 2026-08-03

Batch 09 closes the five remaining stages of `v60_rao_20_330`, bringing the
tracker to 46 accepted exact-stage assets and 68 open assets.

| Asset | Accepted | Generator lineage | WebP SHA-256 |
| --- | --- | --- | --- |
| `instruction_p1_v60_rao_20_330_stage_02_instruction_default` | `v3` | `exec-70a89f7d-31c1-40c9-ab2a-b38332ecd83b.png` → `exec-922c12d0-4256-4106-82ee-11255d8ceaab.png` → `exec-aada9b83-651b-4442-9539-9ac34a3df4e3.png` | `3ff976a1fbcf289239c145fedc8b1a3de462bdd485d844f4f96cfd2431a47a36` |
| `instruction_p1_v60_rao_20_330_stage_03_instruction_default` | `v1` | `exec-c459aabd-0cb5-4ba7-9c87-4f088f70048c.png` | `e45c478b96d64b40e71f02a3b38babe80f994ca2fc66538f7ef334e265a245b9` |
| `instruction_p1_v60_rao_20_330_stage_04_instruction_default` | `v1` | `exec-ea1ce8d1-6823-4248-92c8-32818dcd4b63.png` | `b9b1b133953ffadf8cb045aa45769d76514f43c78d6cef8ebceb6d0512d2f4d2` |
| `instruction_p1_v60_rao_20_330_stage_05_instruction_default` | `v1` | `exec-9da5c928-9673-4bf5-90ed-dc8457ba5137.png` | `dc1b47bd2ecf8fa3abc7047b39b914980ec3701c788c040f788bbf8121bd5e3d` |
| `instruction_p1_v60_rao_20_330_stage_06_instruction_default` | `v1` | `exec-a0e826b3-2dbd-4202-8a67-938652d2edb1.png` | `da790e3a945b5709a2c43474c8b5c2162bf28a29582bec01373393dcd82ebb82` |

The official Hario UK Scott Rao recipe was rechecked for the plastic V60 02,
20 g dose, 60 mL aggressive bloom spin, 40-second wait, 200 mL first pour,
sub-second gentle settling spin, 70-percent-drained observation, low steady
330 mL pour, final gentle spin, and 4:00–4:30 completion range. The bitmaps do
not turn timing or scale values into visual claims.

Bloom-spin v1 was rejected for suspended coffee droplets inside the server.
V2 removed that noise but introduced a deep spiral whirlpool and simultaneous
outlet drops. V3 uses one broad diagonally banked wet bed, a few small bubbles,
a clean server, and no falling drops. The steady-pour frames preserve low,
nearly vertical kettle streams; the first and final settling states remain
visibly gentler than the bloom spin. The completion frame has a low generally
level drained bed and one isolated finishing drop.

All five retained frames passed full-resolution mechanics review, chroma-key
removal, static 1024 by 768 WebP and alpha checks, and individual light- and
dark-theme phone review. The generated fail-closed runtime catalog was then
refreshed to the same 46 tracker-accepted assets.


## Batch 10 retained candidate and open Kasuya iterations — 2026-08-03

The preparation frame for `v60_kasuya_4_6_20_300` is accepted as `v1`
(`exec-a80c062b-387e-4b08-8028-00bfbfbe01ca.png`, WebP SHA-256
`cf016da6744424a9b1067e0269631dc184e1c062bef7e0e8539025fe6c422e7b`).
It preserves the resolved KDC-02-B opaque black porcelain body, customized
ribs, smooth size-02 cone paper, level coarse dose, empty server, and dual-theme
phone readability.

Pour stages remain open rather than accepting misleading frames. Rejections
recorded in the tracker cover accordion/scalloped paper, visibly dry grounds
during active pours, cropped kettle-hand gestures, lost KDC rib geometry, a
noisy wet-coffee background, and an otherwise correct frame that became too
small at phone scale. Exact prompts and raw cache identifiers are retained for
every revision. The synchronized checkpoint is 47 accepted and 67 open.


## Batch 11 completed Kasuya sequence — 2026-08-03

Reference conditioning against the accepted preparation frame resolved the
remaining KDC-02-B consistency problems. All seven `v60_kasuya_4_6_20_300`
stages now have accepted exact-stage candidates.

| Stage | Accepted | Raw cache | WebP SHA-256 |
| --- | --- | --- | --- |
| 02 | `v5` | `exec-8d8431e7-aa99-4012-b535-50a65e7c2d47.png` | `8e19f64df2420db850ebf9db19f1ec5b93a9c5910c037fd83e906245f0fd7dc6` |
| 03 | `v5` | `exec-536fb79e-bd8c-43b7-8568-2c5eacf1c9ff.png` | `31ff35acfee213642f760c99205d185b960e7e83075ca3d81ca48816d2f4d862` |
| 04 | `v3` | `exec-738dd172-30fd-457e-b13a-9d7e4c7fd3cb.png` | `6b7cc9dbde2a8294df15ab99229159dd13da24d117520a136118f2d01846aec9` |
| 05 | `v1` | `exec-9bd15662-4d8c-4ff6-8986-cde43c15e89b.png` | `cccd50d46d326728eda0cdae80d6a6b976010d1c6b2aae9370399876eb378646` |
| 06 | `v1` | `exec-a93d0c2d-0aa3-40dd-b948-dee5431fd4a5.png` | `4050c06610762ddea24a1c2de129879bfcff800809d0e10c40e99000db2af087` |
| 07 | `v1` | `exec-9e287de3-5641-4942-a422-e16e6c2cc576.png` | `6e2a9b455fca0213573c0c2702f7224554dc1a8539ed26c52104423be01db842` |

Every retained frame preserves opaque black porcelain, customized ribs, one
smooth cone paper, granular grounds distinct from smooth liquid, a contained
organic stage and transparent exterior. Pulse frames show one continuous low
stream and ordered server progression without embedding quantities; the finish
frame shows one isolated aligned drop. All passed raw and dual-theme phone QA.
Tracker, matrix, drawables and generated runtime catalog now agree at 53
accepted and 61 open.

## Batch 12 completed Kurasu iced V60 sequence — 2026-08-03

The primary Kurasu iced-pour-over guide was rechecked before production. It
specifies the Hario V60 02 Tetsu Kasuya model, conical paper, 16 g coffee,
150 g water at 91 °C, 70 g brew ice in the server, three pours to 40 g,
100 g, and 150 g, approximately 2:10 total drawdown, complete melting of the
original brew ice, and separate fresh serving ice. The bitmaps keep quantities
out of the artwork while preserving those equipment and material states.

| Stage | Accepted | Raw cache | WebP SHA-256 |
| --- | --- | --- | --- |
| 01 | v4 | exec-ae383d47-8ab1-45c2-8077-2245eee40df4.png | 40aebbd158f68951e6cc1c7ad06b778a0e1ea80ae407c569f53382143d249d3b |
| 02 | v4 | exec-44721384-16d8-4dfb-999c-97849ed063fb.png | d2399d6bbea8b58ede9aac851fce0dfdfcb10e27d96cfff695bd5bd155183346 |
| 03 | v2 | exec-a8d94711-bb10-4dc8-9de4-3ed08f87f0cd.png | b65c344aa138a8403e66699963dd21cef0aad7c2724456d1af48953fedc38923 |
| 04 | v4 | exec-bb9b7ec2-658a-484c-ab70-0e37511d7a90.png | 7c46b8c437ad2f3702c439540a34b20f1e099fc976e0f239ec8b3c41ead7ef2f |
| 05 | v2 | exec-54bbc664-3fef-4bb6-8099-54bc07469661.png | c81697840a0a63a1b5172884459ea7bd16ce6389208f752aee80f316e1e91ce1 |
| 06 | v3 | exec-a11b2d50-1c56-4ba3-b1c4-9d568ac9f5e6.png | 1893d29ff6df4732c447da69b09e7697724d7e09ed5241ae15d7702cc8afeb40 |

The first complete pass was rejected for glossy CGI materials and cropped
forearms. Later tracked rejections cover portrait and 3:2 canvases, a mostly
dry bloom, phone-size ice that read as brown discs, a non-uniform green chroma
field, and an invented black server lid. Exact prompts and every raw cache ID
remain beside the asset records so each correction is reproducible.

The retained sequence uses one consistent flat-to-soft Material 3 Expressive
hardware family. It shows ice only in the prepared server, a thin first coffee
puddle, increasing beverage with visibly melting ice, no original ice after
stirring, and fresh ice only in the serving glass. All six frames passed raw
mechanics review, strict 4:3 and alpha processing, and individual light- and
dark-theme phone review. Tracker, matrix, drawables and generated runtime
catalog agree at 59 accepted and 55 open.

## Batch 13 completed Ozone Wave 185 sequence - 2026-08-04

The official Ozone Wave guide and Ozone's brewer specification were rechecked
before production. The retained sequence uses a stainless Kalita Wave 185,
one correctly seated and rinsed 20-wave paper, 25 g medium-ground coffee,
400 g water at 93 degrees C, a 50 g / 30-second bloom, successive cumulative
targets of 160 g, 220 g, 280 g, 340 g, and 400 g, and an observation-driven
drawdown ending at about 3:00. The artwork intentionally leaves quantities and
timing to the stage text while preserving equipment, material, flow, and
completion states.

| Stage | Accepted | Raw cache | WebP SHA-256 |
| --- | --- | --- | --- |
| 01 | v6 | exec-433e0584-ae4f-4597-8c13-ddeca9be28e7.png | 1bb269fe50b14e1db141918031a447588f2d5f981c86d58eb36679cda77ed9af |
| 02 | v2 | exec-7d2a3d8e-735d-4ec1-83c0-c7932a8980f7.png | ec25ed118e53d51df83b6036c0643b203aa9ccbb6caa572a88d77fe109695b77 |
| 03 | v3 | exec-04368bbc-8b01-48f8-9fab-87714ecae030.png | 50cd451f0424f650b424b6dd11ed1da1e95f540ff7194e1704f269d895b788c0 |
| 04 | v3 | exec-97f207af-7df5-40db-84cb-dc756f052f57.png | 80028cebec25f807f1a2d6c544579e88f24bd1da33a685d322395dd0a9202ae0 |
| 05 | v4 | exec-2a6f946b-56d8-45ee-b576-94f871d676e0.png | 778be8d51c26ba914820c0e5476f08fc9f0bf6caf4b131b6c13545b9df4d2d7b |
| 06 | v3 | exec-6535f889-0460-4889-8527-34577631de0f.png | 7984de0286d5b8642aa60992606d437ca103c16aa4efaafa2d45d29c7a6a75dd |
| 07 | v3 | exec-7e3e5a4b-9ce6-4d36-abe8-054ea8449624.png | a1335fd6b794f95233b8f1a6018ec3b5fad9a212685ef1afe8b130442f8c2525 |
| 08 | v2 | exec-1a4a1fc0-42b5-4cf0-99ae-7ca946b65b48.png | beac0c510fac51cc7c8414a7c3afaf6b456f50d059b7491314aae142c78c2559 |

QA reopened the earlier stage-01 acceptance after a complete visual reread
found an opaque grey lower-half rectangle in the raw generator output. That
source is quarantined as rejected; the next frame was also rejected for a
cropped forearm. V6 restores an intact stainless Wave 185, broad flange, loop
handle, open pleats, clear server, blank scale, complete hand, and generous
transparent perimeter.

The first full-sequence pass was rejected for photographic rendering,
background-room noise, cropped gestures, inconsistent hardware, or missing
scale state. The second pass exposed a more subtle instructional error in
theme previews: stages 03 and 04 showed coffee leaving the brewer without a
water stream entering the bed. Their replacements explicitly preserve two
separate continuous paths, pale water from spout to slurry and amber coffee
from outlet to server. Stages 05-07 were iterated until normalized phone cards
showed a strictly increasing server level; stage-05 v3 was specifically
rejected after it regressed below stage 04, and v4 now sits between accepted
stages 04 and 06.

Every retained frame passed full-resolution mechanics review, chroma and alpha
processing, 1024 by 768 static WebP validation, and individual light- and
dark-theme phone inspection. Exact prompts, raw cache IDs, rejected sources,
and per-attempt issues remain in the production tracker. Tracker, matrix,
drawables, and generated runtime catalog now agree at 66 accepted and 48 open.


## Batch 14 completed Melitta wedge sequence - 2026-08-04

The Voltage Coffee recipe was rechecked for a 23.5 g medium dose, 400 g water
at 195-205 degrees F, a 50 g / 40-second bloom, separated cumulative pulses to
100 g, 200 g, 300 g, and 400 g, mostly-drained pulse cues, and approximately
3:30-4:00 total completion. Current first-party Melitta product records bind
the illustration set to the bright-red, handleless 1-Cup plastic cone (product
64008/640820) with one folded white #2 wedge paper. The canonical profile ID
continues to preserve the broader source taxonomy; only this illustration
implementation is bounded to the exact current configuration.

Sources rechecked:

- https://voltagecoffee.com/melitta/
- https://shoponline.melitta.com/products/1-cup-pour-over-coffee-brew-cone-red
- https://shop.melitta.ca/products/plastic-pour-over%E2%84%A2-coffeemaker-1-cup
- https://cafec-jp.com/products/filterpaper/

| Stage | Accepted | Raw cache | WebP SHA-256 |
| --- | --- | --- | --- |
| 01 | v3 | exec-91d53649-2c6d-4f60-a805-338b16f1633a.png | 54c71922466274d90fb99142b3b4a400014832019ad0200b7a0f43734167c230 |
| 02 | v2 | exec-fc4c16b4-02f3-4767-b289-c0c3e6ccabe0.png | 267210636cc2a99113c037916b1cc67c3a5be7cc90006785f589c4364a08fffb |
| 03 | v1 | exec-bb485701-07de-4b28-aa11-07ef1e072cc4.png | e96202de3fa3bbff1c40b9accfc37067592dc1f5f100c403e40114b77b920af6 |
| 04 | starlit_tactile_v5 | exec-a0eecc75-146c-426c-ade0-4f9727c49761.png | 67edc9251dc02a2bdbc6a7a4a2919c6c40d5898c13d33022adae21a74a01bb87 |
| 05 | v1 | exec-8fedfdcf-77e7-4af2-92e5-3d5f215340c5.png | 60464c63363ace01bb7c86f04d127832968712d20e0a6a79d020f5eec9372ead |
| 06 | starlit_tactile_v5 | exec-cd10dd2a-72b2-466e-96a6-b1a9951854fd.png | 9557aca342f6a82fb36377f6870b864dd03a5323a8308658ecad1febb98bca40 |

Tracked rejections cover glossy CGI materials, a false central paper seam,
an impossible continuous outlet thread, cropped or photographic hands,
multiple-drop chains, an opaque rectangular panel, dry-looking grounds, and
redraws that invented a brewer handle or concealed the outlet air gap. Two v3
attempts were conservatively invalidated when an oversized base64 review
transfer appeared truncated; transfer-safe proxies later proved the source
pixels intact. They remain superseded by v5, and the review process now uses
size-bounded proxies before drawing any corruption conclusion.

Every retained frame was reread for correct wedge geometry, one side seam,
granular grounds, smooth liquid, continuous gravity, and ordered server-level
progression. All six also passed chroma removal, transparent-corner and static
WebP checks, plus separate light- and dark-theme phone inspection. Exact prompts,
raw cache IDs, and all rejection/regeneration todos remain in the tracker.
Tracker, matrix, drawables, and generated runtime catalog now agree at 72
accepted and 42 open.


## Batch 15 completed six-cup Chemex sequence - 2026-08-04

Current first-party Chemex guidance was rechecked before production. The
retained sequence uses the six-cup Classic carafe, a bonded filter with three
leaves over the pouring spout and one opposite, and a visibly open spout air
channel. Rinsing remains optional in the manufacturer guidance; this recipe's
rinse stage ends only after the rinse is discarded. Chemex's current guide
also corroborates medium-coarse grounds, a fully wet bloom, safe headroom, slow
controlled pouring, and filter removal after the desired brew is complete.

The 42 g / 700 g schedule, 100-125 g bloom, 400 g intermediate target,
94-96 degrees C starting range, and common 5:00-6:30 completion range remain
explicitly classified as expert synthesis rather than an official named
Chemex recipe. The artwork encodes only supported equipment and observable
physical states; quantities and timing remain in stage text.

Sources rechecked:

- https://chemexcoffeemaker.com/pages/faq
- https://chemexcoffeemaker.com/pages/filter-series-support-page
- https://chemexcoffeemaker.com/pages/classic-series-product-support
- https://chemexcoffeemaker.com/collections/classic-series-nav/products/six-cup-classic-chemex

| Stage | Accepted | Raw cache | WebP SHA-256 |
| --- | --- | --- | --- |
| 02 | starlit_tactile_v1 | exec-2659f34e-6419-492a-aeb9-6c72b7188049.png | 1d63a8df04253bcb7bdf480c3b1ba8f640b3587a477cde4adce87ac5505d5ea6 |
| 03 | starlit_tactile_v2 | exec-b495cabf-e76e-4ca0-92be-670bc288b4cd.png | ee6e2cf90feda0fa6d92c5d0789aac5054cdeb16bd4c66871377a44611290e3c |
| 04 | starlit_tactile_v1 | exec-70bff835-f948-4c29-b7a0-dbb57a29340f.png | 195e416467ea65e5294b97449ce6f24ee7ff38570f760a89ff37bb2fc2b6c32d |
| 05 | starlit_tactile_v2 | exec-b941be82-724a-440e-842d-4d2792321a01.png | d3dc2e0af6083266c0edd1ae2cb2e3e294c05db5d5d8b623b9647fc4a1257edf |
| 06 | starlit_tactile_v1 | exec-d1445594-a77d-42c0-b753-a1b2f7189608.png | e6e4c7666c016e3ab8d597db351d3f9143823091ab11833498dda22c42d9e729 |
| 07 | starlit_tactile_v2 | exec-3e1b6635-88d8-4051-bfea-dfd40552d558.png | 0fc6f4b8d42c4f6498661d761bfffda388c005332cc887e90911030a84ce8135 |

Bloom v1 was rejected because its sleeve was cut by the canvas and its outlet
coffee became multiple disconnected drops. V2 contains the complete gesture
and separate continuous water-in and coffee-out paths. Final-pour v1 was
rejected for a beverage level visibly below the finished frame; v2 restores
strict liquid progression. Swirl v1 read only as holding an upright carafe;
v2 uses a slight safe tilt and restrained banked liquid surface to communicate
gentle mixing without arrows, splashing, or clutter.

All retained frames passed full-resolution mechanics review, transparent-edge
processing, static 1024 by 768 WebP validation, and individual light- and
dark-theme phone inspection. The sequence reads at card size as empty rinsed
carafe, bloom puddle, middle level, high final-pour level, completed drawdown,
and filter-free gentle swirl. Exact prompts, raw lineage, and every rejected
attempt remain tracked. Tracker, matrix, drawables, and generated runtime
catalog now agree at 78 accepted and 36 open.


## Batch 16 completed generic-conical schematic sequence - 2026-08-04

The canonical evidence and the app's existing `BLOCK-GENERIC-CONE` resolution
were reread before production. The source recipe remains intentionally generic:
real 60-degree cones differ in ribs, outlets, paper fit, capacity, and bypass.
The illustration set therefore binds only to one explicit app-defined
schematic: an unbranded translucent smoke-blue handleless polymer cone, broad
circular flange, restrained straight support ribs, one circular apex outlet,
and one smooth white size-02-class paper. This closes illustration ambiguity
without claiming compatibility or equivalent flow for any real brewer.

The visual sequence preserves the source's 20 g dose, fully wet 50-60 g bloom,
low centered pour to about 200 g, observation-driven second pour to 320 g
before bed exposure, and geometry-dependent drawdown. Approximate quantities,
temperature, and time remain in text rather than being encoded as universal
visual claims.

Evidence retained:

- https://www.hario.co.uk/pages/brew-guides-v60-intermediate
- https://pmc.ncbi.nlm.nih.gov/articles/PMC10418593/
- https://coffeeadastra.com/2019/02/25/the-mechanism-behind-astringency-in-coffee/

| Stage | Accepted | Raw cache | WebP SHA-256 |
| --- | --- | --- | --- |
| 01 | starlit_tactile_v1 | exec-528aaf2d-1714-4ceb-828d-08b9c837a311.png | f73821d7819b1dcbe870d42fe4f126bca056d114e6bfe4136a7462c61a2a8acc |
| 02 | starlit_tactile_v2 | exec-e59131a5-cd17-48e5-a76a-08ca2d226ec1.png | 4f0013ec831c0555d0b21f76a195e9b2f16c03918254759bf7659f4d66aa80fb |
| 03 | starlit_tactile_v1 | exec-ffe6df14-7f30-4885-864b-6e5386a0ec88.png | 6b26f954a7eac51787a9e794887627ef6ee10373ebb08c9ae9d080b5bfec9ea1 |
| 04 | starlit_tactile_v1 | exec-9709f7ac-3809-4fd7-b687-7b5466a09f81.png | 5c698d638d6f50a4b49d26f6bb7ce7550b34ebeece43ce4a4fd715fd8677a291 |
| 05 | starlit_tactile_v1 | exec-fb4254e8-3cfa-474a-b2f2-4535a3062353.png | f2b2c67c9118fd836e7710f30523301e3518133798867c689fbc806a8dd1cb5c |

Bloom v1 was rejected because the outlet thread stopped above a separate drop
and puddle, visually teleporting coffee through the air gap. V2 makes the pale
inlet-water and amber outlet-coffee paths separately continuous. The retained
pours stay low and near the center rather than teaching hard wall pouring or
vigorous agitation. The server level rises strictly from bloom through the two
pours, while completion shows a damp granular bed, no standing slurry, and one
isolated final drop.

Every frame passed full-resolution mechanics review, chroma and alpha
processing, static 1024 by 768 WebP validation, and individual light- and
dark-theme phone inspection. Exact prompts, raw lineage, the rejected bloom,
and its resolved regeneration todo remain in the tracker. Tracker, matrix,
drawables, and generated runtime catalog now agree at 83 accepted and 31 open.


## Batch 17 completed Clever water-first and coffee-first sequences - 2026-08-04

The current Clever product instructions and the canonical James Hoffmann
water-first technique were rechecked before filling the remaining states. The
illustrations are bound to one translucent handled wedge-shaped immersion
dripper with folded white wedge/#4 paper, support feet, and a spring-loaded
bottom actuator. Off-server frames keep that actuator visibly unpressed and
the outlet dry. Placement on a broad stable server is the only state that
opens the valve; lifting the brewer closes it again.

The water-first sequence preserves 15 g coffee and 250 g water, water before
coffee, gentle mixing, a retained closed-valve steep, placement to release,
and vertical removal only after drawdown. The coffee-first sequence keeps the
same exact equipment but preserves its distinct order: rinsed paper and a
shallow 15 g dry bed, 250 g water, brief gentle mixing, and a quiet retained
steep. Quantities and approximate timing remain in text; the artwork encodes
equipment state, material differences, safe handling, and observable cues.

Sources rechecked:

- https://cleverbrewing.coffee/products/clever-dripper-black
- https://www.youtube.com/watch?v=RpOdennxP24

| Sequence / stage | Accepted | Raw cache | WebP SHA-256 |
| --- | --- | --- | --- |
| Water-first 03 | starlit_tactile_v1 | exec-7b872b54-36ff-4d71-9a42-f956ed437dce.png | 573f1fb25a2f4ce317d7ba95cd577d49178efc37b7163d302e3d82711b890b45 |
| Water-first 04 | starlit_tactile_v1 | exec-ae4f1679-a373-4800-9b16-b3c4562f83f2.png | 134214136b10c4373c7bd114ef82a5801db5230d91e84709d677b950776f7663 |
| Water-first 06 | starlit_tactile_v3 | exec-a18d2435-ecfa-4abd-949c-0ba624d68cfa.png | 90d6f17db8c3c1726fcfd8bcb775f4e7ce4680c47b89610bd7d6007eeb0e224a |
| Coffee-first 01 | starlit_tactile_v3 | exec-bb9bdb67-b3bd-48e5-8e39-c8931709fd55.png | 10a2579c643b6173a76f918cc4d4398d751bf3d869a5b650415c48d798384d37 |
| Coffee-first 02 | starlit_tactile_v1 | exec-26d7c8c2-8b12-4896-9760-8f3af8b904d3.png | becd2e413ced78ab0d76000218cf4bf560b66c43e16908a05ba858a07734d68f |
| Coffee-first 03 | starlit_tactile_v2 | exec-eaa7a191-86ca-4af6-87a2-3701aadec3f1.png | b1eda94bed092b24ed048a4c4e50469130ddc7f200656e94813273de592d9664 |

Two initial dry/finished-bed frames and their second revisions were rejected
because a 15 g dose appeared as a tall opaque plug occupying much of the
filter. The retained coffee-first steep was also reopened after its first
phone previews revealed granular texture throughout the full 250 g retained
volume, making liquid read as an impossible brewer full of grounds. The final
frames bound the dry and spent bed to a shallow realistic layer and separate
smooth translucent liquid from a thin textured surface raft.

All six new frames passed full-resolution mechanics review, chroma and alpha
processing, static 1024 by 768 WebP validation, and separate light- and
dark-theme phone inspection. Exact prompts, rejected raws, lineage, and every
resolved regeneration todo remain in the tracker. Tracker, matrix, drawables,
and generated runtime catalog now agree at 89 accepted and 25 open.

## Batch 18 completed Hario Switch official, hybrid, and gravity sequences - 2026-08-04

Hario and Kurasu guidance were rechecked before completing the three distinct
Switch workflows. The official baseline retains 20 g coffee and 240 g water
behind a seated steel ball for two minutes, then opens the valve. The Ole
Kristian Boen hybrid preserves a retained bloom, an open 100 g percolation
phase to 150 g cumulative, a separately retained final pour to 240 g, and a
final open release. Gravity mode keeps the same Switch 02 and V60 02 paper but
holds the valve open throughout, so bloom and main pour both show simultaneous
water-in and coffee-out paths rather than an immersion pool.

Sources rechecked:

- https://global.hario.com/pdf2019/2019HARIO_GC_COFFEE_EN.pdf
- https://kurasu.kyoto/blogs/kurasu-journal/switch
- https://global.hario.com/pdf2020/2020catalog.pdf

| Sequence / stage | Accepted | Raw cache or reuse | WebP SHA-256 |
| --- | --- | --- | --- |
| Official 02 | starlit_tactile_v3 | exec-4cd66972-cc04-4f6c-bfc9-20a588403927.png | 869c4dff62f9e28680f61537bff950988ac7e4080ab9d57b71667c55247aa76f |
| Official 03 | starlit_tactile_v1 | exec-7fe64912-4d70-4583-852c-0107710715df.png | 2b88996ddff36b7fc9b38c5e77a37cee9fc38e4af813139297f14830153fd6cf |
| Official 05 | starlit_tactile_v4 | Qualified reuse of gravity 04 | a41f247a9d6d070365ecb4a5de3bef24f3bbf520a6921195b90401ae08aeca77 |
| Hybrid 02 | starlit_tactile_v1 | exec-5f106445-45cb-4781-b710-91641b5a16d9.png | 2a8ede8c047d4415b8672ff0f219fc82a52b6b20c2de9a396e0627a34f3f54a1 |
| Hybrid 05 | starlit_tactile_v4 | Qualified reuse of gravity 04 | a41f247a9d6d070365ecb4a5de3bef24f3bbf520a6921195b90401ae08aeca77 |
| Gravity 02 | starlit_tactile_v1 | exec-9b282007-43b5-4cf9-b4e7-dddcce4694fb.png | 5eff219316ef166577c55ea5c6fff1b151e09ba9f32529d9bddceee3da3e515e |
| Gravity 03 | starlit_tactile_v5 | exec-2a20a7e8-86fa-4b5e-86aa-012dad8436c2.png | 6badafba4a95a2510d5418a78d32dcda1d8dd8c4b505d2696fc3a633d4fc25db |
| Gravity 04 | starlit_tactile_v2 | exec-fe36176c-dd80-4c8c-b31b-713193f33046.png | a41f247a9d6d070365ecb4a5de3bef24f3bbf520a6921195b90401ae08aeca77 |

The central ball-valve mechanics required repeated rejection. Failed frames
paired an open lever with a seated ball, showed coffee despite no visible
ball-seat gap, or fixed the gap while dropping the organic stage and cropping
kettle or hand gestures. Other rejections covered granular texture through a
full retained liquid volume, implausibly deep drained beds, and a stray green
shape inside a black full-canvas background. Gravity main-pour v5 is the first
candidate that preserves a contained complete gesture, separate continuous
inlet and outlet paths, and a visibly raised ball at once.

The official and hybrid completion cards reuse the accepted gravity-completion
art because their visible completion state is identical: the same Switch 02
hardware is open, the ball is raised, the bed is drained, and the finished
server is below. Recipe-specific dose, water, timing, and removal wording remain
in text; the shared visual makes no numeric claim. Every retained state passed
full-resolution mechanics review, transparent processing, static 1024 by 768
WebP validation, and separate light- and dark-theme phone inspection. Tracker,
matrix, drawables, and runtime catalog now agree at 97 accepted and 17 open.

## Batch 19 completed KBGV Select automatic-batch and Cup-One dose states - 2026-08-04

Current Moccamaster documentation was rechecked before resolving the earlier
generic automatic-brewer blocker. Both batch recipes are now explicitly bound
to one Moccamaster KBGV Select 1.25 L configuration: glass carafe and hotplate,
automatic drip-stop, black cone basket, one #4 paper, nine-hole outlet arm, and
the half-carafe selector for 500 g or full-carafe selector for 1,000 g. This
does not transfer the artwork to flat baskets, thermal carafes, permanent
filters, or different brewer geometry. The Cup-One dose frame remains bound to
its compact single-cup body, #1 paper, one removable basket, and the fixed
circular-opening basket bracket.

Sources rechecked:

- https://support.moccamaster.com/hc/en-us/article_attachments/1500014339002
- https://us.moccamaster.com/blogs/blog/brewing-with-your-moccamaster-coffee-brewer
- https://www.moccamaster.eu/faq
- https://support.moccamaster.com/hc/en-us/article_attachments/1500014620701
- https://us.moccamaster.com/products/bracket-for-cup-one-brew-basket
- https://sca.coffee/s/2017-SCA-CHB-Program-Requirements-ba6g.pdf

| Sequence / stage | Accepted | Raw cache | WebP SHA-256 |
| --- | --- | --- | --- |
| Half batch 01 | starlit_tactile_v4 | exec-72b49ac4-5555-4767-99c8-b5ebf69997c1.png | 114794db6d910723b91f1240c5b100c09a329128f8ceed287df47bbec4e9d18f |
| Half batch 02 | starlit_tactile_v6 | exec-eeff6abf-1a68-4c4d-b6ca-be6e3d3586d6.png | 4468ca2464ba23b320fcfad0d031247d1d5ad552d8924b8c18cdd78b094925be |
| Half batch 03 | starlit_tactile_v4 | exec-d4761d7d-ac9f-4a1d-bfa3-9c4b9308c0f0.png | c2b482532cc98f3c39480912cf7f1595a68c521437aff1fd1a888b2fce0574e3 |
| Half batch 04 | starlit_tactile_v1 | exec-0119099f-df67-4f8b-8135-4fb06ab83dd5.png | 68534f7e98da28ab531d1ef24ab0eb443ea27c1fefc15738ecc1c7d67a7bdd74 |
| Half batch 05 | starlit_tactile_v5 | exec-3856cc51-cb2c-4edc-8d67-c1b2eb437900.png | 39d58c976a19fe495cf2fb0f879d6facd94ff88a4067cd7fed4144ea3b9c47b8 |
| Full batch 01 | starlit_tactile_v1 | exec-802206bf-ba8d-4f73-a112-9f7de511bf19.png | 3ec5da28044ae8c3fdad881a6b3348b1fcc9261235343f0340efe0f472e85e97 |
| Full batch 02 | starlit_tactile_v3 | exec-027dbf1b-da32-4ac8-9cef-1057d8502873.png | 2e3bf8e61a9c267539e322edf9b46206875bc9aa8dd8822d0c75476f08e54d17 |
| Full batch 03 | starlit_tactile_v3 | exec-aeab23e5-bf5f-4ad6-bdde-aa7dd328ead6.png | e21ed28da230cfec60bbdc67e6081b9e3cf91adba66baaa378191d3df85bba45 |
| Full batch 04 | starlit_tactile_v4 | exec-ccdeafaf-29d1-4a07-9294-1ae49759e92d.png | 57d134288e5ef85ddf5da45ce6eebb93828203fd34a51503dbeb7a796509cfaa |
| Cup-One 02 | starlit_tactile_v4 | exec-22123567-9129-447d-8940-215a521d7f41.png | b244800be02653abe61c78008cf31f9f5650b918ab153a057410ca815c7b7875 |

Twenty-five rejected attempts remain reproducibly recorded. They cover cropped
hands, portrait or 3:2 canvases, missing keyed perimeters, wrong batch volume,
impossible shower/basket topology, liquid entering the carafe during reservoir
fill, flow from a powered-off basket, and incomplete lift-and-swirl gestures.
Cup-One attempts were reopened until the artwork showed exactly one removable
basket and the manufacturer's fixed circular-opening support bracket rather
than an invented second receptacle.

All ten retained frames passed full-resolution mechanics review, exact 4:3
validation, chroma and alpha processing, static 1024 by 768 WebP validation,
and individual light- and dark-theme phone inspection. Exact prompts, raw
lineage, rejected candidates, and resolved regeneration todos remain in the
tracker. Tracker, matrix, drawables, and runtime catalog now agree at 107
accepted and 7 open.


## Batch 20 completed 4 oz loose-disc gravity-phin sequence - 2026-08-04

Nguyen Coffee Supply's current gravity-phin guidance was rechecked before the
remaining stages were illustrated. This sequence remains bound to a compact
4 oz stainless gravity phin, 14 g coffee, 118 g total water at 91-93 C, a loose
perforated press with a center tab, a 30 g / 45 second bloom, first drip before
two minutes, and an observation-driven last drop near five minutes. It does
not transfer to threaded screw-insert phins or imply pressure brewing.

Sources rechecked:

- https://nguyencoffeesupply.com/blogs/vietnamese-coffee-brew-guide/traditional-vietnamese-drip-phin
- https://nguyencoffeesupply.com/blogs/news/how-to-make-vietnamese-coffee-use-the-phin-filter
- https://nguyencoffeesupply.com/blogs/news/dialing-in-your-phin-how-to-make-the-best-vietnamese-phin-coffee

| Sequence / stage | Accepted | Raw cache | WebP SHA-256 |
| --- | --- | --- | --- |
| Gravity phin 03 | starlit_tactile_v4 | exec-f275e993-1647-4598-9694-ca179f9a14d1.png | 85b5c2af7ea58d42aad6fd6ef8ac04b6d88a151526776ac3ef0057078c8ad149 |
| Gravity phin 04 | starlit_tactile_v3 | exec-482a1ec3-f7c6-4876-a1cf-0be2e6ddd3df.png | 74f86b896524e9e234a709fea00aa65eb4dff6c8ffa42100376e422d1410dd33 |
| Gravity phin 05 | starlit_tactile_v1 | exec-67011f15-9dd9-480d-b2df-01b725636013.png | 8cee0e6d97357cd2947775d08ad95ae1b1118eddaabeb7a208e5ccc0f43d8a32 |
| Gravity phin 06 | starlit_tactile_v1 | exec-7f15b233-dd7b-4bd4-9937-c14e43a5d4cc.png | 7c95ea55631193a440280ba6ced1d0a03a2b387607bc39c2625df3c09eb95af4 |

Five rejected attempts remain in the tracker with resolved regeneration todos.
They cover cropped kettles and hands, an already-full receiving cup during the
bloom, a physically impossible kettle charging base under a hand-held pour,
opaque upper-reservoir liquid, and inverted chroma/background surfaces. The
accepted fill frame uses a clean explanatory cutaway to keep the clear
below-rim water level, loose disc, granular bed, continuous outlet, and nearly
empty receiving cup legible at phone size.

All four retained frames passed full-resolution mechanics review, exact 4:3
validation, chroma and alpha processing, static 1024 by 768 WebP validation,
and individual light- and dark-theme phone inspection. Exact prompts, raw
lineage, rejected candidates, and resolved regeneration todos remain in the
tracker. Tracker, matrix, drawables, and runtime catalog now agree at 111
accepted and 3 open.


## Batch 21 completed 120-150 ml screw-insert phin sequence - 2026-08-04

The remaining screw-phin states were kept mechanically separate from the
loose-disc gravity profile. The canonical card remains bound to an 18 g dose,
120 g total water, a lightly engaged threaded insert, a 25 g pre-wet for
30-45 seconds, and observation-driven slow drainage. The fixed stud is hidden
inside the engaged center collar; no accepted frame depicts a long exposed
shaft, a sealed pressure vessel, or forceful compression.

Sources rechecked:

- https://trung-nguyen-coffee.co.uk/page_brewing.php
- https://www.gourmetkava.cz/en/blog/making-coffee/preparation-of--vietnamese-coffee
- https://nguyencoffeesupply.com/blogs/news/dialing-in-your-phin-how-to-make-the-best-vietnamese-phin-coffee

| Sequence / stage | Accepted | Raw cache | WebP SHA-256 |
| --- | --- | --- | --- |
| Screw phin 03 | starlit_tactile_v8 | exec-f735b78a-dcde-45cc-ae29-50703e0c9de8.png | 6594bb2ea8ef42447d23df5d593398eda4b325c33225f9cc2c6cc6187e1998a8 |
| Screw phin 04 | starlit_tactile_v5 | exec-1687257e-33ee-4250-8537-a3f5261faa3b.png | e084442183ef02c2defa3f8a473c76aea118c40089d5e6395102fa9dec4ea556 |
| Screw phin 06 | starlit_tactile_v3 | exec-b49b5366-c325-4966-9aab-295c2a286d99.png | 23576971cfd9ce7ecbd439ed2a45600df390fcd0513c9324fd32143708c0d1cf |

Thirteen rejected attempts remain reproducibly recorded with resolved todos.
They cover disconnected or exaggerated screw rods, an invented outlet spout,
grounds above the press, false side handles, missing protected hands, cropped
wrists, an extra bowl, a cut-away receiving cup, a floating filled brewer,
missing or gradient chroma stages, and an undersized press disc. The accepted
bloom uses a complete standalone tactile hand glyph so the pour remains
physically readable without any anatomy crossing an image edge.

All three retained frames passed full-resolution mechanics review, exact 4:3
validation, chroma and alpha processing, static 1024 by 768 WebP validation,
and individual light- and dark-theme phone inspection. Exact prompts, raw
lineage, rejected candidates, and every resolved regeneration todo remain in
the tracker. Tracker, matrix, drawables, and runtime catalog now agree at 114
accepted and 0 open.
