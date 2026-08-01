# Reproducible brewing-illustration workflow

This is the operational record for the five current flat 2D review candidates.
It complements the visual specification in
[illustration-style-system.md](illustration-style-system.md), rather than
replacing its brewing-mechanics research or review gates.

## What is reproducible

- The exact raw source PNG for every retained candidate is committed under
  docs/brewing/illustration-raw-masters.
- The exact generator prompt is committed in
  [flat-instruction-candidates-2026-08-01.md](../../prompts/brewing/flat-instruction-candidates-2026-08-01.md).
- The candidate WebP can be rebuilt byte-for-byte from its raw master using
  Pillow 12.1.1 and the per-asset conversion settings in
  [illustration-generation-manifest.json](illustration-generation-manifest.json).
- tools/rebuild_instruction_illustration_candidates.py performs that rebuild
  or checks every hash, dimension, and encoder setting without relying on the
  temporary Codex cache.

The image generator itself is stochastic. The built-in tool did not expose a
model/build identifier or seed, so re-running a prompt cannot be expected to
produce the same raw bitmap. That limitation is recorded explicitly in the
manifest. The committed raw master is the durable source for an exact rebuild
of the current candidate and the reference image for a future edit.

## Generation contract

1. Select the exact recipe, brewer profile, filter stack, and stage from
   p1_exact_guidance_2026_07_27.json and the exact-stage matrix. Do not
   generalize across mechanically different brewers.
2. Write one versioned prompt using the shared flat 2D editorial contract:
   warm-cream artboard, one physical state or action, clear mechanism,
   text-free, no props, no photographic/3D rendering, and explicit negative
   constraints.
3. Generate with Codex's built-in tools.image_gen__imagegen surface. Use a new
   generation unless an edit truly needs the previous image; record
   num_last_images_to_include and the parent limitation when it is used.
4. Preserve the raw PNG before processing it. It is a 1448 x 1086 opaque RGB
   4:3 source in this batch.
5. Produce only a versioned review candidate:
   docs/brewing/illustration-candidates/<stable-asset-id>/vector_vN.webp.
   Do not place it in drawable-nodpi yet.
6. Review the original and a 384 x 288 phone-scale view for brewer identity,
   filter form, state, safe handling, flow/valve/actuator clarity, text/noise,
   and image-above-copy readability.
7. Keep it PENDING_REVIEW until product, brewer-mechanics, accessibility, and
   placement review pass. Promotion is a separate, approved change.

## Exact PNG-to-WebP conversion

Every retained candidate was encoded with this operation:

~~~python
image.convert("RGB").resize((1024, 768), Image.Resampling.LANCZOS).save(
    output,
    "WEBP",
    quality=quality,  # use the exact 94 or 95 value from the manifest
    method=6,
)
~~~

Use Pillow 12.1.1 for a byte-identical rebuild. A different Pillow/libwebp
version can remain visually correct but change the encoded bytes and hash.

## Commands

~~~powershell
# Read-only provenance, raw-master, candidate, and byte-rebuild verification
python tools\rebuild_instruction_illustration_candidates.py --check

# Rebuild one existing candidate from its committed raw master
python tools\rebuild_instruction_illustration_candidates.py --write --id instruction_p1_chemex_42_700_stage_01_instruction_default

# Check shipping assets separately; this intentionally excludes review candidates
python tools\verify_instruction_assets.py
~~~

## Creating an adjusted or new illustration

Do not overwrite an accepted review candidate. Create vector_v(N+1).webp,
save its raw PNG under illustration-raw-masters, add the verbatim prompt and
request options, add a manifest entry and hashes, then run the visual audit
again. For an edit, provide the currently retained raw master as the reference
image so the next version starts from a durable input rather than an ephemeral
tool-context image.

The manifest and prompt record are mandatory before a new asset can be called
reproducible.
