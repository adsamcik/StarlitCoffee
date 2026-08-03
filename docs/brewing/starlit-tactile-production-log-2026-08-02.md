# Starlit Tactile production log

Date: 2026-08-02

Status: batch 01 retained as transparent review candidates; promotion into
`drawable-nodpi` remains a separate integration step.

## Reproducible generation contract

- Generator: Codex built-in `image_gen` tool. The model/build identifier and
  seed are not exposed, so prompt replay is stochastic.
- Exact prompts are committed under `prompts/brewing/assets/<asset-id>`.
- Every accepted raw 1448 by 1086 chroma PNG is committed. When an accepted
  image came from an edit, every raw parent in that accepted lineage is also
  committed.
- Exact reference-image payloads supplied to the generator are committed; a
  higher-resolution ancestor is not treated as an equivalent input.
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
