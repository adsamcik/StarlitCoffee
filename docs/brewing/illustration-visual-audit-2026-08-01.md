# Brewing illustration visual audit — 2026-08-01

## Decision

Every generated illustration was opened and inspected one by one. The older,
realistic or semi-realistic renders are provenance only and must not be placed
in the app. Five text-free flat editorial candidates remain for formal review;
they are still outside `drawable-nodpi` and none is registered or counted as
exact-stage coverage.

## Review method

- Reviewed the 79 raw generator outputs in
  `C:\Users\adam-\.codex\generated_images\019fa48e-1d5d-7560-b0de-f39fa2e8b914`:
  51 `call_*.png` files and 28 `exec-*.png` files.
- Opened each image separately at a 384 × 288 audit thumbnail, and checked the
  project-bound WebP candidates individually at their source size.
- Used exact-stage records only where a raw filename is recorded in
  `asset-production.md`; visually similar but unrecorded files remain
  explicitly untraceable.
- Evaluated visible brewer identity, filter form, valve/actuator state, flow,
  safe handling, mobile-size clarity, and the required text-free flat 2D
  in-app direction.

## Project-bound candidate results

| Exact stage | Variants visually reviewed | Result |
| --- | --- | --- |
| Chemex `chemex_42_700` stage 01 — place the three-layer filter over the spout | v1–v2 | Reject v1. Keep v2 as review candidate: the three-layer side, thinner opposite side, and unsealed spout channel read together. |
| Hario Switch `switch_official_20_240` stage 04 — open the valve | v1–v4 | Reject v1–v3. Keep v4 as review candidate: paper, raised ball, open seat, lever linkage, and one drawdown stream are legible. |
| Gravity Phin `phin_gravity_14_118` stage 01 — stabilize and add level coffee | v1–v5 | Reject v1–v4. Keep v5 as review candidate: compact open chamber, dry level bed, full flange support, and no false press, lid, water, or perforation claim. |
| Clever `clever_water_first_15_250` stage 05 — place on the server to release | v1–v4 | Reject v1–v3. Keep v4 as review candidate: a #4 wedge paper, stable server contact, bottom actuator, and a single initial stream are visible. |
| Screw-insert Phin `phin_screw_18_120` stage 02 — engage the screw insert lightly | v1–v6 | Reject v1–v5. Keep v6 as review candidate: it shows the finished light-engagement state rather than a hand pressing down. |

## Raw `exec_*.png` audit — 28 reviewed

| Raw output | Visible result | Disposition |
| --- | --- | --- |
| `exec-f44f6f30-8fd0-4902-bb74-ecc7b99a00be.png` | Chemex filter held by a hand; soft rendered style. | Reject as a raw draft. |
| `exec-4ef225dc-18ae-46bf-ab40-eddcd0565078.png` | Early Chemex filter setup; layer/channel reading remains weak. | Reject as a raw draft. |
| `exec-aa7b58db-bbed-483b-b3cf-f542b6d60632.png` | Chemex v1 source. | Reject / superseded. |
| `exec-d25f9a7c-8f81-48aa-88fe-0040113ce011.png` | Chemex v2 source. | Keep only through the v2 WebP review candidate. |
| `exec-5a2d4805-9b15-4849-874d-fd4262dc50d8.png` | Switch-like dripper with a generic black base. | Reject. |
| `exec-9d5c9ca5-3297-46be-b1fc-ea034e37a888.png` | Switch-like draft with unclear paper and valve. | Reject. |
| `exec-9af226c9-1da9-4e3d-b02b-47ce46635516.png` | Switch-like draft with a front-tap reading. | Reject. |
| `exec-0a60e500-248d-4e41-8562-b7aa00a7f40b.png` | Small generic Switch-like drawdown. | Reject. |
| `exec-9663ac7c-bb1d-4556-a9cb-8aec733f5b1a.png` | Switch draft showing a hand beside hot glass. | Reject. |
| `exec-68c5c747-f141-4e38-9e89-7415e5f09f2a.png` | Switch draft with an ambiguous control. | Reject. |
| `exec-4d7dadb4-ba45-48bb-acbc-c5f76d0fcf0e.png` | Switch draft showing a hand beside hot glass. | Reject. |
| `exec-59746fbe-2dfc-4274-a0bd-8ab559b12998.png` | Switch v4 source; the retained candidate's paper, raised ball, linkage, and drawdown are reviewed through its WebP derivative. | Keep only through the v4 WebP review candidate. |
| `exec-7787a0e2-5119-4786-b366-cd17eaaaa025.png` | Early steep-and-release draft; paper and actuator are unclear. | Reject. |
| `exec-7f7bf2f7-d7c2-4ef2-b3c9-2bc6046038b8.png` | Clever v1 source. | Reject / superseded. |
| `exec-bdcf5093-1497-4128-8f12-45e99ad1cb99.png` | Clever v2 source. | Reject / superseded. |
| `exec-fa9285cf-d7bf-481f-9359-7848fc37bb19.png` | Clever v3 source. | Reject / superseded. |
| `exec-8613e35b-a953-45f2-82ca-3263f8ffcc85.png` | Clever v4 source. | Keep only through the v4 WebP review candidate. |
| `exec-5e81970e-cb8f-4ded-8ab2-70261577d582.png` | Gravity Phin v1 source, noisy external-hole treatment. | Reject / superseded. |
| `exec-98f9fbeb-875e-425a-b16c-9cbe79e5105f.png` | Gravity Phin v2 source, invented bridge-like geometry. | Reject / superseded. |
| `exec-c6a1dead-44d0-4228-9767-5635fa90b443.png` | Gravity Phin v3 source, too generic. | Reject / superseded. |
| `exec-43ee4842-9a2b-4d86-8e9f-8e1d39a1c7b7.png` | Gravity Phin v4 source, more rendered and less compact. | Reject / superseded. |
| `exec-137f8942-d4c2-44d9-aae2-01044953de16.png` | Gravity Phin v5 source. | Keep only through the v5 WebP review candidate. |
| `exec-a89e0058-820f-4f20-aa2c-325994590d24.png` | Screw-insert Phin v1, prominent pressing hand. | Reject / superseded. |
| `exec-dc3a0d5e-f302-4f45-b444-19cb1b9c36c8.png` | Screw-insert Phin v2, hand does not engage the control. | Reject / superseded. |
| `exec-ccc2af57-6114-4e2e-a7d5-57b5e28913a9.png` | Screw-insert Phin v3, reads as downward pressure. | Reject / superseded. |
| `exec-2115713c-c2e0-410b-a2ef-45cf397f5493.png` | Screw-insert Phin v4, oversized hand and pressure cue. | Reject / superseded. |
| `exec-5def4682-6716-4065-a45d-e1a15c2573db.png` | Screw-insert Phin v5, isolated finger reads as a poke. | Reject / superseded. |
| `exec-d0fe2020-1883-4c6b-9a2e-0fbff2db7412.png` | Screw-insert Phin v6 source. | Keep only through the v6 WebP review candidate. |

## Raw `call_*.png` audit — 51 reviewed

All 51 are older realistic or semi-realistic source renders. They are
text-free, but none meets the required clean flat 2D in-app language. The
following table records each file’s direct source mapping when one exists; an
unmapped file has no authority for recipe use.

| Raw output | Exact record where available | Disposition |
| --- | --- | --- |
| `call_03Sz71oZM2TNizqklM08OkFw.png` | Cup-One stage 04 | Replace with flat art; retain only as a mechanics reference. |
| `call_0MHmskGnMsYhpIBl52eNlcxi.png` | Unmapped | Archive/reject; ambiguous Switch-like state. |
| `call_14AnBDhadHBe1GC6oPfcoKGh.png` | Unmapped | Archive/reject; no clear instructional action. |
| `call_4aH3DzA1KvmbdujlJJrPWxPn.png` | V60 official stage 04 | Replace with flat art; pour path is plausible. |
| `call_4IeUwQLJ1CINNXJQvWnk8YZy.png` | Cup-One stage 06 | Replace with flat art; outlet-cleaning action is too subtle. |
| `call_4zFPSw44cwFgOu4sYwzeIyJ7.png` | Screw Phin stage 02 | Reject; it teaches pressing, not light engagement. |
| `call_6Nkw4txTus0gMrlyP6HwyBXr.png` | Clever stage 01 | Replace with flat art; #4 paper and closed valve are unclear. |
| `call_a0webCpiilcHlmrSLEyJJsHI.png` | Gravity Phin stage 02 | Replace with flat art; gravity-disc state is too subtle. |
| `call_aIiIev8TxAoZ8M928DHVouID.png` | Unmapped | Reject; wrong-looking filter/outlet geometry. |
| `call_AjIWZRF6721moi7VoFiKR7OI.png` | Chemex stage 01 | Reject; layers and open channel are not legible. |
| `call_aL3aaxhxY4OEWi5X2BKCQMJF.png` | Cup-One stage 05 | Replace with flat art; residual-drip cue is too subtle. |
| `call_C8xSeYK6mZijSwLrddOox03w.png` | Hario Switch add-coffee stage | Reject; filter boundaries are unclear and style is wrong. |
| `call_CApUzEc7HdlYdOhzWciFzsDf.png` | Unmapped | Archive/reject; precursor with weak drip/support cue. |
| `call_cMFKfQOt8bkcbqq3wh1RxcJC.png` | Unmapped | Reject; Chemex paper crosses the channel. |
| `call_Co9IpysuaMR3G0IZBqDE5HOn.png` | V60 Rao stage 01 | Replace with flat art; shallow-nest cue is weak. |
| `call_CU5qxcjHCcQhvkJsnTVsmyWH.png` | Unmapped | Archive/reject; closed-valve/no-flow state is not visible. |
| `call_dgeeyHyF0pgvLINVzmWNCBFH.png` | Unmapped | Reject; cluttered precursor with unclear holder geometry. |
| `call_dX5efcnwcWEhLM18K5123aIr.png` | Unmapped | Reject; wrong V60-like brewer for Clever release. |
| `call_fMYjfQjFxI2YUgkIteyM7zj2.png` | Switch gravity stage 01 | Replace with flat art; open-valve state needs clearer mechanics. |
| `call_GAH0bA5EsGnAeRDtsZfnltGS.png` | Clever water-first stage 02 | Reject; V60-like form and missing #4 paper. |
| `call_Gww98DkmNdeSAijFZiLeL663.png` | Switch official stage 01 | Replace with flat art; wet paper, closed valve, and no-flow are unclear. |
| `call_iF8m72QHN1Dn8OiaLeVsrGKK.png` | Switch Ole Boen hybrid stage 03 | Replace with flat art; closed-valve state is too subtle. |
| `call_IVaYQbahuIN4Hh617nDU9C3N.png` | Cup-One stage 03 | Replace with flat art; fill-stop and support clearance are unclear. |
| `call_jTHuRAThIK0k1U0GEHNKVyJC.png` | Screw Phin stage 01 | Replace with flat art; retain its basic mechanics only. |
| `call_KyAej6ckJ3OQznpbWYBjYdIX.png` | Unmapped | Archive/reject; realistic, unrecorded Phin variation. |
| `call_kyMUvUElzoiU17pxOgsSf27F.png` | Screw Phin stage 05 | Replace with flat art; retain only the safe-monitoring concept. |
| `call_M5r23ofKArMvA0Sago97XS0r.png` | Unmapped | Archive/reject; plausible but no exact-stage authority. |
| `call_MUwtpKjWLJarU57AH8yLolOb.png` | Gravity Phin stage 07 | Replace with flat art; keep the cloth/safe-hand concept. |
| `call_n0ZTOUPWfwRdATBgx3YjuxGM.png` | Unmapped | Reject; incompatible sidewall-hole geometry. |
| `call_nTGDitajlPjg10hU1AFs6bHY.png` | Unmapped | Reject; Switch trigger/ball/seat relationship is ambiguous. |
| `call_odoQwDbXpF0OELrwPlAiZgXs.png` | Clever stage 05 | Reject; #4 filter and actuator are not self-explanatory. |
| `call_PVSSv7fepwMHE5DIHSSXtm3T.png` | Switch stage 04 | Reject; open-valve linkage is obscured at mobile size. |
| `call_q5h48fQffPtFe3MCFKkLa0cX.png` | Gravity Phin stage 01 | Replace with flat art; retain component separation only. |
| `call_Qqw21ESQ2Yt0tUjr3rFX4JNY.png` | Unmapped | Archive/reject; hidden critical floor and decorative noise. |
| `call_rxU5JcPdlPLK0YauJiXfdlK1.png` | Unmapped | Archive/reject; unsupported basket reads incorrectly. |
| `call_sSDtzk5dAbDG16kn4uXC2Cm1.png` | Unmapped | Reject; impossible cutaway-like cup depiction. |
| `call_SW3OnlhiiHWMXZlmYPMJrN6H.png` | Unmapped | Archive/reject; duplicate-like Cup-One scene without clear state. |
| `call_TD1v5gzxfWFh902wMOgXZTIt.png` | Wave 185 stage 01 | Replace with flat art; lower three-hole geometry is not legible. |
| `call_u8lWPcaWE9MsPA5n9un4adAh.png` | V60 official stage 01 | Replace with flat art; wet paper/seam is too subtle. |
| `call_uiSoy4DEsMOhH4cURuzYEnnp.png` | Unmapped | Archive/reject; Cup-One state is unclear. |
| `call_UPTnJ0M2FJ4TGgMgpgzP5hsG.png` | Unmapped | Reject; early Switch draft has an incompatible handle. |
| `call_UuGUUXQWn2hWJ6HIXEyPb8aD.png` | Cup-One stage 01 | Replace with flat art; mechanics plausible but model fit is uncertain. |
| `call_VcXy5oL60F3KxMqVqmrBF95M.png` | Unmapped | Archive/reject; unrecorded Switch-rinse variant. |
| `call_vftFyHqNlbJp7Fv93sxKR9hR.png` | Unmapped | Archive/reject; wedge paper and valve state are unclear. |
| `call_wdF1HkUay3FFx6fdHtzzj7Ib.png` | Unmapped | Reject; generic ribbed cone conflicts with a Clever identity. |
| `call_xEWHIklZj3WcSQAl13jhERk1.png` | Switch Ole Boen hybrid stage 01 | Replace with flat art; retained-bloom/closed-ball state is too subtle. |
| `call_xIw7RzM7nJdWTTW1C3J5VBix.png` | Unmapped | Reject; depicts the deep crater that the stage avoids. |
| `call_xmi1jUHmdkh6dxHJvRjNVvPl.png` | Unmapped | Reject; could imply probing or modifying the holder. |
| `call_y9SZv376TJ25KnBbXZFwQg5l.png` | Unmapped | Reject; Switch valve state is unclear. |
| `call_YDtevhAAIPg5aoEBHzntYAAz.png` | Unmapped | Reject; wrong-looking Cup-One filter geometry. |
| `call_ZPHb5K6ndhNe717UUEGR3CHx.png` | Unmapped | Archive/reject; no liquid or valve cue. |

## Guardrail

A raw render can preserve research provenance but cannot become an app asset by
default. A future illustration must be rebuilt in the flat 2D house style and
pass the same exact-stage, mobile-size, safety, accessibility, and placement
review before it is registered.
