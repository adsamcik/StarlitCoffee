# Research Prompt: Reliable Product Label Detection Without a Custom-Trained Model

## Context

We're building a zero-button coffee bag scanning feature — the app should automatically detect when a coffee bag is in the camera frame, without the user pressing a capture button. We want to avoid training and shipping a custom object detection model if possible.

Our current approach uses **OCR text geometry as a proxy for bag detection**: if ML Kit returns ≥5 text blocks clustered in a centered rectangle covering 25-65% of the frame, we assume there's a product label present. This is "free" because OCR already runs every 400ms.

An adversarial review with 10 agents identified **three critical failure modes** that challenge this approach:

### Failure 1: Minimalist/premium bags (CRITICAL)
Specialty coffee packaging trends toward minimalism. Scandinavian, Japanese, and ultra-premium roasters often have bags with only 2-3 text elements (logo + origin + weight). The ≥5 text block threshold would NEVER detect these bags — and these are exactly the bags whose owners are most likely to use a coffee scanning app.

### Failure 2: Dark/metallic bags (CRITICAL)
Premium coffee bags use matte black with foil-stamped gold text, dark green with embossed lettering, holographic/metallic surfaces. ML Kit's text detection degrades severely on low-contrast text and specular reflections. If text blocks aren't detected, the ENTIRE proxy approach goes blind — there's no geometry to analyze.

### Failure 3: Non-Latin scripts (CRITICAL for global)
ML Kit groups CJK (Chinese/Japanese/Korean) text into fewer, larger blocks than Latin text. A Japanese bag with equivalent content to a 10-block English bag might produce only 2-3 blocks. The threshold is calibrated to Latin text behavior.

### Additional concerns
- Books, cereal boxes, and menus also have 5+ text blocks in rectangles — false positives
- Coffee keyword check ("roast", "arabica") creates circular dependency: need OCR to detect bag, need bag detected to trust OCR
- First 0-2 seconds of camera open: no OCR results exist, system is completely blind

## Questions for Research

1. **Product packaging detection on mobile without custom models**: What approaches exist for detecting "a product package is in frame" using only standard Android APIs (ML Kit, MediaPipe, CameraX, OpenCV)? Specifically:
   - Can ML Kit Image Labeling reliably distinguish "product packaging" from "book" or "menu"? What labels does it actually return for pouches/bags?
   - Can contour detection (OpenCV) reliably identify the silhouette of a stand-up pouch? What about flexible/crinkled bags?
   - Can depth estimation (ToF sensor or monocular depth) segment a hand-held object from background?

2. **Text-region-based detection improvements**: If we keep the OCR proxy approach, how can we make it robust?
   - What preprocessing (CLAHE, edge enhancement) most improves ML Kit's text detection on dark/metallic/low-contrast surfaces?
   - Can we use text block AREA instead of COUNT to handle CJK text (one large block = equivalent to 5 small Latin blocks)?
   - Can we use text line ORIENTATION and SPACING patterns to distinguish product labels from books/menus/signs?
   - What's the minimum viable signal? Can 1-2 text blocks plus a barcode or distinctive aspect ratio be enough?

3. **Hybrid detection without training**: Is there a practical architecture that combines multiple weak signals (text geometry + barcode presence + aspect ratio + color histogram + edge density + depth) into a reliable detector without training a custom model? Something like a manually-tuned decision tree or scoring function? What's the state of the art for "zero-shot product detection" on mobile?

4. **When does custom training become unavoidable?** At what point does the problem complexity (supporting all bag types, all scripts, all materials) exceed what's achievable with heuristics? How small can a custom detector be (model size, training set size, latency) while still being useful? Is there a way to collect training data passively from the app's own usage (e.g., user confirms "this is a coffee bag" → future training example)?

5. **Cold start alternatives**: For the first 0-2 seconds before OCR returns results, what can the system do?
   - Show a guide overlay with bag silhouette?
   - Use a tiny ~500KB image classifier just for "is there a package in frame?"
   - Use camera autofocus distance/state as a signal (AF locked at ~30cm = something close)?

## Environment
- Samsung S24 Ultra, Snapdragon 8 Gen 3
- CameraX with ImageAnalysis (KEEP_ONLY_LATEST)
- ML Kit OCR, barcode scanning, image labeling available
- OpenCV 4.13 already in project
- Target: specialty coffee bags from worldwide roasters (diverse packaging, scripts, materials)
