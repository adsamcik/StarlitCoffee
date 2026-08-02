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
