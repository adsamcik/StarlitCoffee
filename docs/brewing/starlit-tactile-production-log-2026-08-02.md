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
