# Bloom Spritesheet Frame Prompts

Use one prompt per generated source atlas. Paste the common prompt first, then paste one variation-specific frame plan. For completed-bloom still images, use the final still prompt contract at the end of this file and lock the still to the matching `R5C5 Frame 25` source line.

These prompts are written for a 25-frame sheet: 5 columns by 5 rows. They deliberately hold the artwork compressed until frame 20, then reserve frames 21-25 for the final visible bloom. Every frame line names a concrete visual difference from the previous frame.

Punk theme prompts follow the same 25-frame contract, but their final objects may be mechanical, elemental, textile, symbolic, botanical, or hybrid. They must still remain one centered coffee-bloom object growing from one fixed roasted coffee bean base. Genre cues belong on the object itself, never in scenery.

## Common Prompt

```text
Create one single raster spritesheet source atlas for a coffee bean bloom animation.

Highest priority: this is a technical slicing atlas first and artwork second. If any style request conflicts with the grid, slicing, alignment, or frame-continuity rules, obey the grid, slicing, alignment, and frame-continuity rules.

Grid contract:
Create exactly one atlas that is exactly 5 columns left-to-right by exactly 5 rows top-to-bottom, making exactly 25 equal square cells in one square 1:1 atlas.
The canvas itself is the atlas: no margins, gutters, title strip, caption area, empty band, extra panel, partial cell, merged cell, inset sheet, or decorative border. The outer magenta border touches the canvas edges.
Draw one axis-aligned slicing grid only: exactly 6 vertical #FF00FF lines and exactly 6 horizontal #FF00FF lines, including the outer border. Lines are straight, continuous, uniform thickness, and shared between adjacent cells. No doubled borders, rounded corners, crop marks, diagonal guides, labels, or extra internal guide lines.

Background contract:
Every non-art, non-grid pixel must be pure flat #00FFFF. No gradients, texture, paper grain, transparency, vignette, lighting, cast shadows, reflections, glow spill, or background objects.
Do not use cyan #00FFFF or magenta #FF00FF anywhere in the sprite artwork.
This is not a scene and has no floor plane. Do not draw contact shadows, drop shadows, shadow ellipses, ambient occlusion blobs, reflected light, or soft teal grounding under the sprite. The sprite must look like a clean cutout floating on the flat chroma-key background.
The lowest colored pixels in each cell must belong to the coffee bean shell or plant itself. Do not draw any oval, smear, glow, teal outline, cyan halo, dark base mark, or grounding stroke underneath the bean.

Text ban:
Render zero text. Do not draw letters, digits, frame numbers, row labels, column labels, captions, filenames, arrows, UI marks, signatures, watermarks, or icon-like symbols anywhere in the image.
The R1C1 / Frame 01 labels below are private instruction labels only. They describe which cell to draw; they must not appear visually in the image. Never copy any prompt label into any cell.

Animation order:
Use row-major order only as a cell address map: row 1 contains frames 01-05, row 2 contains frames 06-10, row 3 contains frames 11-15, row 4 contains frames 16-20, and row 5 contains frames 21-25.
Rows are layout only. Do not treat rows as story stages, timing bands, or progress groups. Do not infer a new phase from the start of a row.
The variation-specific frame plan is the only story authority. Each individual frame line describes exactly what that cell should contain. Draw the line literally.
For each cell, preserve every visible part introduced in earlier frames unless the frame line says it changes by opening, unfolding, lengthening, lifting, widening, rounding, curling, uncurling, separating, or settling.

Frame-difference contract:
Every adjacent frame must be visibly different from the previous frame by the specific named anatomy change in its own frame line. Do not copy, duplicate, mirror, hold, or redraw the same pose across multiple cells.
If a named change would be too subtle at sprite size, exaggerate only that named change until the frame is clearly different. Keep the anchor, scale, baseline, subject identity, and all other parts locked.
Do not use style drift, lighting drift, color noise, texture noise, camera changes, or different rendering quality as the frame difference.
Frame 25 is the first finished sprite. No earlier frame may look finished. Frame 24 still has exactly one unfinished silhouette or detail. Frame 25 changes only that final planned visible detail; it introduces no new anatomy, new petals, new leaves, new sparkles, new stems, cleanup redraw, scale change, detached final icon, or new pose.

Acceleration and final-bloom contract:
The animation must feel like it accelerates. Frames 01-10 are small setup changes, frames 11-20 are medium compression changes, and frames 21-25 are the largest final-bloom changes.
Frames 01-15 must not resemble the finished object. Frames 16-20 may show all major parts only as folded, clamped, curled, short, dim, or compressed pieces.
Frame 20 is the loaded pre-bloom pose: closed pressure, visible tension, and all final energy still compact.
Frames 21, 22, 23, 24, and 25 are the final bloom sequence. Frames 21-23 contain the largest silhouette changes. Frame 24 is near-final. Frame 25 resolves one remaining visible unfinished detail only.
Frame 21 starts the release from the exact frame-20 pose. It must keep the same anchor, scale, baseline, and parts, but it opens or expands visibly.
Frame 22 opens wider than frame 21. Frame 23 creates the first recognizable final silhouette. Frame 24 is almost final with exactly one unfinished outer silhouette or major visible detail. Frame 25 completes only that detail with a small but readable final change.
Do not let frames 21-25 look like five copies of the finished sprite. Do not reach the recognizable final open bloom before frame 23.

Wrap-edge continuity contract:
The wrap pairs 05->06, 10->11, 15->16, and 20->21 are ordinary adjacent animation steps. The first cell of a new row is not a restart and not a new stage.
For frames 06, 11, and 16, keep the same pose as the previous frame and continue with only the small named change.
For frame 21, keep the same pose identity as frame 20, but begin the intentionally larger final-bloom release. No repositioning, no new scale, no restart, and no jump to a finished flower.

Individual-frame guard:
If a frame says tiny, small, hint, point, nub, faint, slightly, barely visible, closed, cupped, compact, compressed, clamped, curled, folded, or unfinished, draw the smallest readable version of that feature. Do not substitute a larger later-frame form.
Do not draw a leaf when the frame says tip, a stem when it says point, a flower when it says bud, a flat blossom when it says cup, a full plant when it says sprout, or a finished bloom before frame 25.
Do not use completion percentages, rows, or broad phases to decide what a cell should contain. Use only the individual frame description for that exact frame.

Geometry and anchor contract:
Use normalized cell coordinates. The root anchor is fixed at x=50%, y=82% in every cell. The lowest visible pixel of the sprite sits on the same invisible baseline near y=88% in every cell, leaving empty cyan below it.
The coffee bean shell center and size stay constant: about 40% of cell width and 24% of cell height. The stem, plume, or bloom centerline remains x=50% plus or minus 2%. The final bloom center is fixed near x=50%, y=38%. Final sprite bounds stay inside x=18%-82% and y=10%-88%.
Growth happens by opening, unfolding, extending, curling, uncurling, and revealing named parts. Never use camera zoom, pan, recentering, whole-sprite resizing, squash/stretch, whole-sprite tilt, perspective tilt, top-down view, side view, or rotation changes.
Keep at least 12% empty cyan padding on all sides of every cell. No petal, leaf, stem, steam wisp, sparkle, glow, bean half, antialiasing, or shadow may touch or cross a magenta grid line.

Coffee bean base contract:
Use one fixed roasted coffee bean base in every frame: a warm dark-brown oval coffee bean with a vertical center seam, exactly two hinged halves, and visible roasted texture.
When the bean opens, only the left and right halves hinge outward symmetrically from the seam while staying anchored to the same root point. Do not create cracks, loose fragments, extra beans, a cup, saucer, bowl, seed pod, dirt mound, coconut shell, planter, or separate shell.
The bean halves may gradually open through the animation, but they must not close again or change identity.
By frame 20, the bean halves are already settled at their final open base angle. Frames 21-25 keep the same already-open bean base while only the bloom, plant, foam, or steam finishes.

Art contract:
Use a locked front-facing orthographic 2D mobile-game item view. Keep one soft warm key light from upper left, same highlight logic, same outline thickness, same rendering style, same scale, same baseline, and same bean identity in all 25 frames.
Exactly one central growth path and exactly one final bloom or plant. New parts may appear only when the frame plan first names them; once visible, they persist and mature gradually.
Any glow, sparkle, steam highlight, or star-shaped highlight named by a frame plan must be an opaque painted part of the sprite artwork, contained inside the sprite bounds. No aura, haze, transparency, or color spill outside the sprite silhouette.
No duplicate buds, side flowers, extra stems, floating props, unplanned particles, atmospheric haze, background decoration, cast shadows, drop shadows, or color spill onto the cyan background.
Style applies only to the sprite artwork: polished 2D painterly mobile-game sprite, crisp edges, readable at small size, warm roasted coffee texture. The atlas remains a flat technical slicing sheet.
```

## Coffee Flower

Target file: `bloom_coffee_flower_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one real white coffee blossom.
Final object contract: exactly one white coffee blossom with exactly five rounded white petals, one small yellow center, one straight green stem, exactly two simple green leaves, and the fixed opened roasted coffee bean base. No blossom clusters, no side buds, no six-petal flower, no extra stems, no second bean.
Final-bloom lock: frames 01-20 must stay closed, sprouting, or tightly cupped. Frames 21-25 are the only frames where the blossom unfolds into the final five-petal flower.

R1C1 Frame 01: closed roasted coffee bean, upright oval, vertical seam dark, no glow, no sprout.
R1C2 Frame 02: same closed bean; a thin amber highlight appears only inside the center seam.
R1C3 Frame 03: seam opens into a hairline slit at the top third; bean outline and size unchanged.
R1C4 Frame 04: bean halves hinge outward one small step from the seam, making a narrow warm slit.
R1C5 Frame 05: a pinhead green sprout point becomes barely visible inside the upper slit; nothing rises above the bean.
R2C1 Frame 06: same pose as frame 05; only the green point rises one tiny step above the seam.
R2C2 Frame 07: the point lengthens into a very short centered sprout, still no leaves.
R2C3 Frame 08: the sprout straightens and grows taller; one tiny leaf nub appears on the left side.
R2C4 Frame 09: a matching tiny leaf nub appears on the right side; stem remains thin and centered.
R2C5 Frame 10: both leaf nubs open into two small folded leaves, and a pale closed bud point appears at the stem top.
R3C1 Frame 11: same pose as frame 10; only the bud point grows into a small green-white oval.
R3C2 Frame 12: the two leaves lengthen downward slightly while the closed bud rises higher on the stem.
R3C3 Frame 13: bud turns cream-white and gains faint vertical petal seam lines; still closed.
R3C4 Frame 14: bud swells wider at the middle, top still pinched shut.
R3C5 Frame 15: five tiny petal tips become readable around the bud top, all still folded inward.
R4C1 Frame 16: same pose as frame 15; only the folded petal tips loosen one small step, forming a tiny closed cup.
R4C2 Frame 17: the cup grows taller and more rounded, with the five petal tips pressed together.
R4C3 Frame 18: the cup opens enough to show petal edges, but the yellow center remains hidden.
R4C4 Frame 19: the cup widens slightly; a thin yellow glint is visible deep inside.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and a tight upright five-tip flower cup presses outward with tension, yellow center mostly hidden.
R5C1 Frame 21: continuing the exact frame-20 pose; the top petal flips upward and the cup opens visibly, side petals still upright.
R5C2 Frame 22: left and right petals swing outward into a wider bowl, exposing half of the yellow center.
R5C3 Frame 23: recognizable five-petal coffee flower silhouette appears; lower petals unfold but remain slightly curled inward.
R5C4 Frame 24: almost-final blossom; five rounded petals are broad and balanced, with exactly one lower outer petal curl still tucked enough to change the silhouette.
R5C5 Frame 25: final sprite: same flower as frame 24; only the last lower outer petal curl uncurls outward, completing one fully open white coffee flower with five petals, yellow center, two leaves, and the same already-open bean base.
```

## Jasmine

Target file: `bloom_jasmine_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one compact white jasmine flower.
Final object contract: exactly one compact jasmine flower with exactly five soft white rounded petals in a gentle pinwheel, one tiny pale center, one green stem, exactly two leaves, and the fixed opened bean base. No long star spikes, no flower cluster, no extra vines, no second stem.
Final-bloom lock: the jasmine must stay as a curled bud through frame 20. The pinwheel flower form appears only during frames 21-25.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark vertical seam, no green visible.
R1C2 Frame 02: same closed bean; a small golden highlight appears in the seam.
R1C3 Frame 03: seam opens into a thin slit near the top; bean silhouette unchanged.
R1C4 Frame 04: bean halves hinge outward one small step, revealing a warm narrow opening.
R1C5 Frame 05: a tiny curled green jasmine shoot tip appears inside the slit, below the bean rim.
R2C1 Frame 06: same pose as frame 05; only the curled shoot rises just above the seam.
R2C2 Frame 07: the shoot grows into a short soft S curve, still no leaves.
R2C3 Frame 08: the shoot straightens slightly and one tiny leaf nub appears on the right side.
R2C4 Frame 09: a second tiny leaf nub appears on the left side; shoot remains centered.
R2C5 Frame 10: both leaves unfold into two small glossy leaves, and a tiny pale bud point forms at the top.
R3C1 Frame 11: same pose as frame 10; only the pale bud point grows into a small closed oval with a green base.
R3C2 Frame 12: closed bud grows taller and creamier while the stem extends one small step.
R3C3 Frame 13: faint spiral petal seams appear on the bud, showing it will become a pinwheel.
R3C4 Frame 14: bud swells slightly asymmetrically while the stem and bud centerline remain vertical and centered; top remains shut.
R3C5 Frame 15: one white petal tip separates as a curled lip, but the bud remains closed.
R4C1 Frame 16: same pose as frame 15; only the curled lip loosens one small step.
R4C2 Frame 17: three petal tips become visible, all wrapped inward around the hidden center.
R4C3 Frame 18: five petal tips can be counted as a tight spiral cap, not a flower yet.
R4C4 Frame 19: the spiral cap widens into a compact cup, center still hidden.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and five curled jasmine petals are clamped into a tight pinwheel bud.
R5C1 Frame 21: continuing the exact frame-20 pose; the top curled petal unwinds outward, making the bud visibly wider.
R5C2 Frame 22: two side petals rotate outward in opposite directions, creating a clear pinwheel twist.
R5C3 Frame 23: recognizable five-petal jasmine pinwheel appears; pale center becomes visible, lower petals still cupped.
R5C4 Frame 24: almost-final jasmine; five rounded petals are balanced with exactly one outer petal tip still curled inward enough to affect the silhouette.
R5C5 Frame 25: final sprite: same jasmine as frame 24; only the last outer petal tip uncurls outward, completing one compact five-petal jasmine bloom on the same already-open bean base.
```

## Lotus

Target file: `bloom_lotus_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one small cream-white lotus flower.
Final object contract: exactly one small cream-white lotus, front-facing and slightly cupped, with exactly five outer petals and three inner upright petals around one warm center, plus the fixed opened bean base. No lily pad, water, mandala, side-view lotus, extra bloom, or second stem.
Final-bloom lock: frames 01-20 must show only a compressed lotus bud or tight lotus cup. The layered lotus silhouette is created in frames 21-25.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark seam, no light.
R1C2 Frame 02: same closed bean; seam gains a soft amber glow.
R1C3 Frame 03: seam opens slightly at the top, bean outline unchanged.
R1C4 Frame 04: bean halves hinge outward one small step, warm slit visible.
R1C5 Frame 05: a tiny pale-green lotus point appears inside the opening.
R2C1 Frame 06: same pose as frame 05; only the lotus point rises one tiny step above the bean.
R2C2 Frame 07: the point thickens into a small centered teardrop bud.
R2C3 Frame 08: a green base cup appears under the teardrop, still no petals.
R2C4 Frame 09: bud grows upright and taller while staying narrow.
R2C5 Frame 10: closed bud turns cream-white with a green lower base.
R3C1 Frame 11: same pose as frame 10; only faint vertical petal seams appear on the closed bud.
R3C2 Frame 12: the bud grows one small step taller; top remains pointed.
R3C3 Frame 13: two outer petal tips loosen at the top but stay pressed to the bud.
R3C4 Frame 14: three more outer petal tips become visible as seam lines, still closed.
R3C5 Frame 15: bud opens into a very narrow cup; inner petals remain hidden.
R4C1 Frame 16: same pose as frame 15; only the cup widens one small step.
R4C2 Frame 17: five outer petal seam lines can be counted on the tight cup, but the petals remain pressed together.
R4C3 Frame 18: one compressed central inner spike appears inside the cup, not three separate inner petals.
R4C4 Frame 19: outer petal edges lift under tension; the single inner spike remains clamped shut.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, five outer petals are upright and tight, and one central inner spike is compressed with the warm center mostly hidden.
R5C1 Frame 21: continuing the exact frame-20 pose; five outer petals drop outward a visible step while the central inner spike stays upright.
R5C2 Frame 22: left and right outer petals spread wider, and the central spike opens at the tip but does not yet split into three petals.
R5C3 Frame 23: recognizable layered lotus silhouette appears; the central spike separates into three inner petals and the warm center shows.
R5C4 Frame 24: almost-final lotus; all petals are balanced with exactly one front outer petal still curled upward.
R5C5 Frame 25: final sprite: same lotus as frame 24; only the front outer petal drops and uncurls outward, completing the small cream-white lotus on the same already-open bean base.
```

## Orchid

Target file: `bloom_orchid_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one simplified front-facing white orchid.
Final object contract: exactly one simplified white orchid with five rounded lobes: top, left, right, lower-left, lower-right, plus one small warm center detail, one green stem, exactly two leaves, and the fixed opened bean base. No antennae, tendrils, dangling roots, speckles, second flower, or malformed top.
Final-bloom lock: keep the orchid as a folded five-lobe bud through frame 20. Do not allow the wide moth-like orchid silhouette before frame 23.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark seam.
R1C2 Frame 02: same closed bean; seam gains a small warm shine.
R1C3 Frame 03: seam opens into a thin centered line.
R1C4 Frame 04: bean halves hinge outward one small step.
R1C5 Frame 05: a tiny green orchid shoot point appears inside the slit.
R2C1 Frame 06: same pose as frame 05; only the shoot point rises one tiny step above the seam.
R2C2 Frame 07: shoot grows taller with a graceful shallow bend, no leaves yet.
R2C3 Frame 08: first glossy leaf nub appears near the base on the left.
R2C4 Frame 09: second glossy leaf nub appears opposite on the right.
R2C5 Frame 10: two broad leaves begin unfolding, and a small cream bud node appears above them.
R3C1 Frame 11: same pose as frame 10; only the cream bud node grows into a small closed oval.
R3C2 Frame 12: bud elongates upward on the centered stem; leaves remain small.
R3C3 Frame 13: faint five-lobe seam hints appear on the bud surface.
R3C4 Frame 14: bud swells wider at the top, still sealed.
R3C5 Frame 15: the upper lobe edge separates as a tiny fold, no flat petals.
R4C1 Frame 16: same pose as frame 15; only the upper folded lobe becomes slightly larger.
R4C2 Frame 17: left and right lobe folds appear but stay tucked against the bud.
R4C3 Frame 18: lower-left and lower-right lobe folds appear below the center, all still cupped.
R4C4 Frame 19: all five orchid lobes are readable as folded pieces, warm center still hidden.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and five lobes press inward around the hidden center like a tight folded orchid.
R5C1 Frame 21: continuing the exact frame-20 pose; left and right lobes spread outward a visible step.
R5C2 Frame 22: lower-left and lower-right lobes round outward and separate, widening the flower below.
R5C3 Frame 23: recognizable five-lobe orchid silhouette appears; warm center detail becomes visible, top lobe still curled.
R5C4 Frame 24: almost-final orchid; five lobes are balanced with exactly one top-lobe outer edge still visibly curled.
R5C5 Frame 25: final sprite: same orchid as frame 24; only the top-lobe outer edge relaxes upward and outward, completing the simplified white orchid on the same already-open bean base.
```

## Rose

Target file: `bloom_rose_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one compact red rose.
Final object contract: exactly one compact red rose head with a tight spiral center, exactly ten visible rounded petals, one short green stem, exactly two leaves, and the fixed opened bean base. No rose bush, thorns, long stem, side bud, extra flower, or second bean.
Final-bloom lock: frames 01-20 must show only a closed rosebud or compressed rose cup. The rounded rose head opens only in frames 21-25.

R1C1 Frame 01: closed roasted coffee bean, upright oval, calm dark seam.
R1C2 Frame 02: same closed bean; seam gains a tiny red-gold glint.
R1C3 Frame 03: seam opens slightly at the top.
R1C4 Frame 04: bean halves hinge outward one small step, warm slit visible.
R1C5 Frame 05: a tiny green rose shoot point appears inside the slit.
R2C1 Frame 06: same pose as frame 05; only the shoot point rises one tiny step above the seam.
R2C2 Frame 07: shoot straightens into a short centered stem.
R2C3 Frame 08: first leaf nub appears on the left.
R2C4 Frame 09: second leaf nub appears opposite on the right.
R2C5 Frame 10: two leaves unfold slightly, and a tiny red rosebud point appears at the stem top.
R3C1 Frame 11: same pose as frame 10; only the rosebud point grows into a small red teardrop.
R3C2 Frame 12: green sepals wrap the base of the red teardrop.
R3C3 Frame 13: bud deepens red and grows rounder, top still closed.
R3C4 Frame 14: a small spiral hint appears at the sealed bud tip.
R3C5 Frame 15: first outer petal edge curls outward as a narrow red lip.
R4C1 Frame 16: same pose as frame 15; only the outer lip opens one small step into a compact cup.
R4C2 Frame 17: second outer petal edge appears opposite, cup still narrow.
R4C3 Frame 18: inner spiral becomes clearer while side petals remain tight.
R4C4 Frame 19: rose cup widens slightly and shows a compressed ring of petals.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and the compact red rose cup has outer petals tense and central spiral tightly wound.
R5C1 Frame 21: continuing the exact frame-20 pose; the outer petal ring spreads outward a visible step, making the rose head wider.
R5C2 Frame 22: second petal ring unfurls around the spiral, adding clear rounded side petals.
R5C3 Frame 23: recognizable compact rose head appears; exactly ten rounded petals form a fuller circular silhouette.
R5C4 Frame 24: almost-final rose; ten petals are balanced with exactly one outer silhouette petal tip still curled inward.
R5C5 Frame 25: final sprite: same rose as frame 24; only the last outer petal tip uncurls outward, completing the compact red rose on the same already-open bean base.
```

## Sunflower

Target file: `bloom_sunflower_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one cheerful miniature sunflower.
Final object contract: exactly one miniature front-facing sunflower head with exactly twelve yellow petals around one dark warm circular center, one green stem, exactly two broad leaves, and the fixed opened bean base. No duplicate head, seed scatter, extra petal rings, oversized flower, or second stem.
Final-bloom lock: frames 01-20 may show only a tight green-yellow disk with tiny folded petal tabs hugging the rim, not a readable sunflower ring. The full twelve-petal sunflower circle appears only across frames 21-25.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark seam.
R1C2 Frame 02: same closed bean; seam catches a small yellow glow.
R1C3 Frame 03: seam opens slightly near the top.
R1C4 Frame 04: bean halves hinge outward one small step with a golden slit.
R1C5 Frame 05: a tiny green sprout point appears in the slit.
R2C1 Frame 06: same pose as frame 05; only the sprout rises one tiny step above the seam.
R2C2 Frame 07: sprout becomes a short sturdy centered stem.
R2C3 Frame 08: first broad leaf nub appears on the left.
R2C4 Frame 09: second broad leaf nub appears opposite on the right.
R2C5 Frame 10: two broad leaves begin unfolding, and a tiny round green bud appears at stem top.
R3C1 Frame 11: same pose as frame 10; only the round bud grows slightly larger.
R3C2 Frame 12: dark warm circular center begins forming inside the bud.
R3C3 Frame 13: four tiny yellow petal points appear at top, bottom, left, and right of the rim.
R3C4 Frame 14: four more tiny diagonal petal points appear, all still very short.
R3C5 Frame 15: the round head widens slightly; short petal points remain folded tight.
R4C1 Frame 16: same pose as frame 15; only the petal points lengthen one small step.
R4C2 Frame 17: the dark center becomes rounder and clearer while petals stay short.
R4C3 Frame 18: all twelve petal positions are hinted as tiny folded yellow tabs hugging the rim, still not readable as full petals.
R4C4 Frame 19: petal tabs curl outward slightly but remain uneven and incomplete.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, round center is clear, and twelve yellow tabs are short, folded, and under tension.
R5C1 Frame 21: continuing the exact frame-20 pose; the four cardinal petals extend outward a visible step, changing the silhouette strongly.
R5C2 Frame 22: the four diagonal petals extend outward, making the head nearly circular.
R5C3 Frame 23: recognizable twelve-petal sunflower appears; remaining small gap petals fill and center texture sharpens.
R5C4 Frame 24: almost-final sunflower; exactly twelve petals are present with one petal tip still curled inward.
R5C5 Frame 25: final sprite: same sunflower as frame 24; only the final curled petal tip straightens outward, completing the miniature sunflower on the same already-open bean base.
```

## Starlit Coffee

Target file: `bloom_coffee_starlit_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one magical starlit steam flower.
Final object contract: exactly one cream-gold steam flower made from exactly five smooth connected petal loops, one central glow, and exactly five tiny pale-gold star-shaped highlights attached close to the steam loops, plus the fixed opened bean base. No flame, explosion, galaxy cloud, chaotic sparkles, smoke plume, detached particles, text symbols, or extra flower.
Final-bloom lock: frames 01-20 must keep the steam loops narrow, dim, and compressed. The five-loop glowing flower becomes recognizable only in frames 21-25.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark seam.
R1C2 Frame 02: same closed bean; a tiny golden spark glows inside the seam.
R1C3 Frame 03: seam opens slightly with warm light inside.
R1C4 Frame 04: bean halves hinge outward one small step; soft cream-gold glow leaks from the slit.
R1C5 Frame 05: one tiny cream steam wisp appears just above the opening, connected to the seam.
R2C1 Frame 06: same pose as frame 05; only the wisp rises one tiny step into a smooth S curve.
R2C2 Frame 07: a second faint glow thread joins the same wisp, still a single narrow plume.
R2C3 Frame 08: one tiny star speck appears close to the plume, not far away.
R2C4 Frame 09: the plume grows taller and brighter while staying narrow and centered.
R2C5 Frame 10: the top of the plume bends into the first small loop, still compressed.
R3C1 Frame 11: same pose as frame 10; only a second small loop begins opposite the first.
R3C2 Frame 12: central glow brightens into a small warm core inside the plume.
R3C3 Frame 13: a third small loop appears above the core, all loops connected.
R3C4 Frame 14: fourth and fifth loop hints appear as dim curled lines, not closed petals.
R3C5 Frame 15: all five loop hints thicken slightly but remain narrow and uneven.
R4C1 Frame 16: same pose as frame 15; only the loop hints widen one small step.
R4C2 Frame 17: second tiny star speck appears near the left side of the compressed flower.
R4C3 Frame 18: third tiny star speck appears near the right side; loops still not flower-wide.
R4C4 Frame 19: fourth tiny star speck appears near the top; central glow rounder but contained.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, five connected steam loops are visible but tight, uneven, dim, and one upper loop remains open.
R5C1 Frame 21: continuing the exact frame-20 pose; the upper loop opens upward and the central glow expands visibly.
R5C2 Frame 22: left and right loops smooth outward, making the steam flower much wider.
R5C3 Frame 23: recognizable five-loop starlit flower appears; lower loops round out and all five tiny star-shaped highlights are visible close to the loops.
R5C4 Frame 24: almost-final steam flower; all five star-shaped highlights are already sharp, and exactly one upper loop gap remains open.
R5C5 Frame 25: final sprite: same steam flower as frame 24; only the upper loop gap closes, completing five connected loops, central glow, and five tiny attached star-shaped highlights on the same already-open bean base.
```

## Latte Bloom

Target file: `bloom_coffee_latte_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one creamy latte-art rosetta flower.
Final object contract: latte-art foam, not smoke: exactly five cream rosetta lobes plus one central pull-through line, rising from the fixed opened bean base. No cup, saucer, liquid puddle, random smoke, bubbles, botanical leaves made of foam, extra stem, or extra bloom.
Final-bloom lock: frames 01-20 must keep the foam as a compact poured knot with lobes folded inward. The clean five-lobe rosetta opens only in frames 21-25.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark seam.
R1C2 Frame 02: same closed bean; seam gains a tiny cream highlight.
R1C3 Frame 03: seam opens slightly at the top.
R1C4 Frame 04: bean halves hinge outward one small step; pale foam glow appears inside.
R1C5 Frame 05: first small cream foam wisp rises just above the opening.
R2C1 Frame 06: same pose as frame 05; only the foam wisp curls one tiny step upward.
R2C2 Frame 07: wisp widens into a smooth cream ribbon, still one ribbon.
R2C3 Frame 08: second cream ribbon appears beside the first, both connected to the opening.
R2C4 Frame 09: ribbons curl together into the beginning of a compact rosetta knot.
R2C5 Frame 10: a small oval foam pool forms above the bean opening, still tight.
R3C1 Frame 11: same pose as frame 10; only the first left-side rosetta lobe starts as a small curve.
R3C2 Frame 12: a small right-side lobe appears opposite the first, and a faint central groove begins between them.
R3C3 Frame 13: a third upper lobe forms above the center as a tiny folded cap.
R3C4 Frame 14: a fourth lower-left lobe appears, still tucked close to the foam pool.
R3C5 Frame 15: a fifth lower-right lobe appears; all five lobes are present only as compact nested curls.
R4C1 Frame 16: same pose as frame 15; only the left and right nested curls widen one small step.
R4C2 Frame 17: central pull-through groove lengthens through the compact foam, still faint.
R4C3 Frame 18: top lobe lifts into a small curled cap, still folded inward.
R4C4 Frame 19: lower lobes press outward slightly, making the compact knot broader but not final.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, five lobes are tight nested curls, top lobe is visibly folded inward, and pull-through remains faint.
R5C1 Frame 21: continuing the exact frame-20 pose; the top lobe rises and widens upward a visible step, making the foam taller.
R5C2 Frame 22: left and right side lobes sweep outward, making the rosetta much wider.
R5C3 Frame 23: recognizable five-lobe latte rosetta appears; lower lobes round outward and the central pull-through line becomes crisp.
R5C4 Frame 24: almost-final rosetta; all five lobes are clean with exactly one top outer curl still open.
R5C5 Frame 25: final sprite: same rosetta as frame 24; only the top outer curl uncurls into a smooth rounded lobe, completing the five-lobe latte-art bloom on the same already-open bean base.
```

## Coffee Plant

Target file: `bloom_coffee_plant_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one healthy young coffee plant through a final leaf flush.
Final object contract: exactly one young coffee seedling with one vertical stem and exactly three paired leaf levels, six glossy oval green leaves total, growing from the fixed opened bean base. No flowers, berries, roots, soil, side stems, duplicate plant, or second bean.
Final-bloom lock: frames 01-20 must keep the seedling compact with folded leaf pairs. Frames 21-25 are the final leaf flush where the plant becomes full and healthy.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark seam.
R1C2 Frame 02: same closed bean; seam gains a subtle green-gold highlight.
R1C3 Frame 03: seam opens slightly at the top.
R1C4 Frame 04: bean halves hinge outward one small step; tiny green point appears inside.
R1C5 Frame 05: green point presses upward through the seam but remains mostly inside the bean.
R2C1 Frame 06: same pose as frame 05; only the sprout tip rises one tiny step above the bean.
R2C2 Frame 07: sprout straightens into a short vertical stem.
R2C3 Frame 08: first lower leaf nub appears on the left.
R2C4 Frame 09: second lower leaf nub appears opposite on the right.
R2C5 Frame 10: first paired leaf level unfolds into two tiny oval leaves.
R3C1 Frame 11: same pose as frame 10; only the stem rises one small step and two tiny second-level nubs become visible close to the stem.
R3C2 Frame 12: second leaf-pair nubs separate slightly from the stem.
R3C3 Frame 13: lower leaf pair widens and becomes glossier, while second pair remains small.
R3C4 Frame 14: second leaf pair unfolds into two small oval leaves.
R3C5 Frame 15: top growth point lifts above the second pair as two tiny folded tips.
R4C1 Frame 16: same pose as frame 15; only the top folded tips open one small step.
R4C2 Frame 17: three paired leaf levels are visible, all compact and close to the stem.
R4C3 Frame 18: lower pair widens slightly and tilts outward, middle pair stays compact.
R4C4 Frame 19: middle pair widens slightly and the stem thickens while staying centered.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and all six leaves exist but are short, folded, and pressed near the stem.
R5C1 Frame 21: continuing the exact frame-20 pose; top leaf pair opens outward a visible step, changing the upper silhouette.
R5C2 Frame 22: middle leaf pair rotates outward and lengthens, making the plant much wider.
R5C3 Frame 23: recognizable full seedling silhouette appears; lower leaves reach near-final width and all six leaves are readable.
R5C4 Frame 24: almost-final seedling; six glossy leaves are full except the right-top leaf remains half-curled and visibly shorter than the left-top leaf.
R5C5 Frame 25: final sprite: same seedling as frame 24; only the right-top leaf unfurls outward and lengthens to match the left-top leaf, completing the healthy six-leaf coffee plant on the same already-open bean base.
```

## Coffee Brew

Target file: `bloom_coffee_brew_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one warm magical coffee-brew flower.
Final object contract: exactly one warm amber coffee center with exactly five controlled cream steam-petal curls arranged like a flower, rising from the fixed opened bean base. No flames, splash shapes, coffee cup, pouring liquid, chaotic smoke, extra plume, or second bloom.
Final-bloom lock: frames 01-20 must keep the brew energy as a tight amber core with narrow curled steam. The five-petal brew flower opens only in frames 21-25.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark seam.
R1C2 Frame 02: same closed bean; seam gains a tiny amber glow.
R1C3 Frame 03: seam opens slightly near the top.
R1C4 Frame 04: bean halves hinge outward one small step; dark amber coffee shimmer appears inside.
R1C5 Frame 05: first cream steam wisp rises just above the opening.
R2C1 Frame 06: same pose as frame 05; only the steam wisp curls one tiny step upward.
R2C2 Frame 07: amber core becomes a small round glow inside the opened bean.
R2C3 Frame 08: second steam curl appears close to the first, connected to the core.
R2C4 Frame 09: the two steam curls bend into petal-like arcs, still narrow.
R2C5 Frame 10: third controlled curl appears above the core, compact and connected.
R3C1 Frame 11: same pose as frame 10; only a fourth steam-petal curl begins as a small new curve.
R3C2 Frame 12: amber core grows from a small dot into a wider round center while staying contained inside the bean opening.
R3C3 Frame 13: fifth curl begins, all five connected to the same core.
R3C4 Frame 14: the tips of the five narrow curls hook outward slightly, creating clearer petal edges.
R3C5 Frame 15: five-curl flower shape is hinted, but curls are still short and compressed.
R4C1 Frame 16: same pose as frame 15; only the steam petals widen one small step around the amber core.
R4C2 Frame 17: amber core expands gently and becomes more circular.
R4C3 Frame 18: lower steam arcs curl outward slightly, no broad flower yet.
R4C4 Frame 19: top steam curls lift and lean outward, still tight.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and five steam curls are present but narrow, tense, and one top curl remains compressed against the core.
R5C1 Frame 21: continuing the exact frame-20 pose; the top curl rounds outward a visible step and the amber core becomes wider.
R5C2 Frame 22: left and right curls widen symmetrically, making the brew flower much broader.
R5C3 Frame 23: recognizable five-curl coffee-brew flower appears; lower curls settle into a clear flower-like arrangement.
R5C4 Frame 24: almost-final brew flower; steam petals are broad with exactly one final top curl still not fully open.
R5C5 Frame 25: final sprite: same brew flower as frame 24; only the final top curl completes the outer silhouette, producing one warm amber coffee-brew flower on the same already-open bean base.
```

## Cyberpunk

Target file: `bloom_cyberpunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one neon data-orchid aroma lantern.
Final object contract: exactly one centered cybernetic aroma emitter with angular dark casing, one electric violet and lime circuit seam system, one front sensor lens, exactly four opaque dark angular blade-petal plates with violet-and-lime circuit etch, one compact glowing aroma core, and the fixed opened roasted coffee bean base. No scenery, cityscape, rain, UI, text, labels, logos, extra props, duplicate beans, duplicate stems, characters, or multiple final objects.
Final-bloom lock: frames 01-20 must keep the cybernetic lantern folded, dim, clamped, or compressed. Frames 21-25 are the only final release; frame 23 is the first recognizable data-orchid lantern silhouette, frame 24 leaves one unresolved lens detail, and frame 25 resolves only that detail.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark seam, with one tiny violet pinprick glow centered inside the crease.
R1C2 Frame 02: same closed bean; the violet pinprick becomes a short lime circuit dash nested in the seam.
R1C3 Frame 03: seam opens into a hairline slit; a tiny angular dark casing tip barely lifts from the seam under the circuit dash.
R1C4 Frame 04: bean halves hinge outward one small step; the casing tip rises and forms a tiny triangular hatch lip.
R1C5 Frame 05: the triangular hatch lip splits into two small dark armor flanges hugging the seam.
R2C1 Frame 06: same pose as frame 05; only a dim violet micro-core appears between the two armor flanges.
R2C2 Frame 07: the micro-core elongates into a short vertical capsule while the flanges stay tight.
R2C3 Frame 08: one folded circuit-etched blade-petal plate peeks from the capsule's left side, still dark and narrow.
R2C4 Frame 09: a matching folded circuit-etched blade-petal plate peeks from the capsule's right side, both plates locked inward.
R2C5 Frame 10: the capsule gains a faint lime seam down its center and a tiny covered sensor bump at the front.
R3C1 Frame 11: same pose as frame 10; only the central dark casing grows into a compact bud with both blade-petal plates tucked against it.
R3C2 Frame 12: upper and lower folded blade-petal plates appear as dark stacked fins around the compact bud.
R3C3 Frame 13: violet-and-lime circuit etches trace along all four folded blade-petal plates but remain dim.
R3C4 Frame 14: the covered sensor bump becomes a small black lens socket, unlit and half-hidden by casing.
R3C5 Frame 15: angular side casing clamps close over the lens socket, making the object more compressed.
R4C1 Frame 16: same pose as frame 15; only the four folded blade-petal plates thicken into a tight data-orchid bud around the dark casing.
R4C2 Frame 17: lime circuit seams connect from the bean seam to the casing base like one short central conduit.
R4C3 Frame 18: subtle glitch-edged notches appear on the folded blade-petal tips without opening them.
R4C4 Frame 19: the aroma core glows faintly inside the closed bud, visible only through a narrow vertical slit.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and the casing, four blade-petals, lens socket, circuit seams, and hidden aroma core are folded, dim, and locked.
R5C1 Frame 21: continuing the exact frame-20 pose; the locking clamps release and the four folded blade-petals open outward into a clearly wider but still half-closed data-orchid bud.
R5C2 Frame 22: the blade-petals swing outward halfway, violet and lime seams brighten, and the aroma core becomes rounder.
R5C3 Frame 23: recognizable data-orchid lantern silhouette appears; four blade-petals frame one central glowing core in the dark angular casing.
R5C4 Frame 24: almost-final data-orchid lantern; casing, petals, seams, and core are resolved, but the one front sensor lens iris remains dark and unaligned.
R5C5 Frame 25: final sprite: same data-orchid lantern as frame 24; only the front sensor lens iris snaps into centered lit alignment, adding no new anatomy.
```

## Frostpunk

Target file: `bloom_frostpunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one frostpunk heat-core ice condenser.
Final object contract: exactly one centered frostpunk condenser with an insulated dark metal collar seated in the bean seam, riveted pressure bands, a pale blue-white ice crystal crown, one small amber coffee heat core, and the fixed opened roasted coffee bean base. No snowy village, blizzard, locomotive, furnace scene, background, loose snow clouds, text, UI, duplicate objects, extra blooms, or second bean.
Final-bloom lock: frames 01-20 must keep the condenser closed, compressed, clamped, or pressure-frozen. Frames 21-25 are the only final release; frame 23 is the first recognizable heat-core ice condenser silhouette, frame 24 leaves one unresolved rivet-band clasp, and frame 25 resolves only that clasp.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark seam, with a tiny warm amber pinprick fully contained inside the crack.
R1C2 Frame 02: same closed bean; the amber pinprick brightens into a short seam flash and lifts a hairline pale frost edge.
R1C3 Frame 03: microscopic ice facets plate along the seam lips, clamped tight to the bean with no readable object shape yet.
R1C4 Frame 04: a dark metal sliver emerges vertically from the seam, still compressed like a folded insulated collar tab.
R1C5 Frame 05: the metal sliver widens into a low crescent clamp with two tiny dark rivet dots visible on the clamp.
R2C1 Frame 06: same pose as frame 05; only pale blue-white facets crystallize upward behind the clamp as short pressure-frozen shards.
R2C2 Frame 07: the clamp plates thicken into a partial dark collar hugging the seam; amber glow remains a trapped ember.
R2C3 Frame 08: ice facets knit into a compact crown bud, angular and contained, with the collar still incomplete.
R2C4 Frame 09: a narrow pressure band arcs across the lower bud, pinning the ice growth into a squat condenser nub.
R2C5 Frame 10: the ember condenses into a tiny round heat-core socket, still half-hidden by overlapping metal plates.
R3C1 Frame 11: same pose as frame 10; only riveted band segments clamp tighter around the base while ice facets stack upward.
R3C2 Frame 12: the ice crown gains sharper triangular plates, with pale blue-white tips locked close around the warm center.
R3C3 Frame 13: the insulated dark metal collar becomes a nearly complete ring seated in the bean seam, with the crown still folded.
R3C4 Frame 14: side pressure plates slide into alignment, creating a compact reactor-like bud without opening outward.
R3C5 Frame 15: the amber heat core becomes clearer as a small glowing bead surrounded by frosted facet ribs and dark riveted bands.
R4C1 Frame 16: same pose as frame 15; only the condenser bud rises slightly taller while crystalline ribs interlock over the core.
R4C2 Frame 17: pressure bands pull taut across the ice crown, rivets sharpening as the collar compresses the bloom upward.
R4C3 Frame 18: blue-white facets crowd into a dense crown cap; the amber core glows hotter through a narrow central aperture.
R4C4 Frame 19: the single object strains vertically from the seam, plates flexing and facets pressed tight around the core.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and the compact dark collar, clenched riveted bands, and overpacked ice facets are ready to release.
R5C1 Frame 21: continuing the exact frame-20 pose; the ice crown springs upward from the collar, unfolding facets while staying one connected object.
R5C2 Frame 22: the condenser expands into a taller mechanical crystal form; pressure bands separate into readable riveted arcs.
R5C3 Frame 23: recognizable frostpunk heat-core ice condenser appears with dark insulated collar, amber core, and blue-white crown.
R5C4 Frame 24: almost-final condenser; crown, collar, core, and bands are resolved, but one front rivet-band clasp remains open and misaligned.
R5C5 Frame 25: final sprite: same condenser as frame 24; only the single open clasp snaps shut flush, leaving all crown facets, collar, heat core, and riveted bands unchanged.
```

## Dieselpunk

Target file: `bloom_dieselpunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one compact dieselpunk art-deco turbine aroma supercharger.
Final object contract: exactly one centered compact gunmetal aroma supercharger with one round turbine casing, exactly five chunky brass-edged fan blades, brass rivets, one small attached exhaust stack, exactly three short intake fins, one warm amber oil-coffee core, and the fixed opened roasted coffee bean base. No tanks, planes, war machines, roads, factories, smoke clouds, background scene, text, extra props, second object, loose parts, or second bean.
Final-bloom lock: frames 01-20 must keep every turbine piece compressed, folded, clamped, or under pressure. Frames 21-25 are the only frames where the mechanical fan blossom opens; frame 23 is the first recognizable dieselpunk supercharger silhouette.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark vertical seam, no metal visible.
R1C2 Frame 02: same closed bean; a tiny warm amber glint appears only inside the seam.
R1C3 Frame 03: seam opens into a thin slit near the top; bean outline and size unchanged.
R1C4 Frame 04: bean halves hinge outward one small step, revealing a narrow dark gunmetal edge inside.
R1C5 Frame 05: a tiny round amber core dot appears below the rim, mostly hidden inside the bean.
R2C1 Frame 06: same pose as frame 05; only the amber core rises one tiny step above the seam.
R2C2 Frame 07: a small gunmetal collar forms around the amber core, still low and compressed.
R2C3 Frame 08: one short folded brass-edged blade nub appears tucked against the collar.
R2C4 Frame 09: two more folded blade nubs appear, all clamped close to the center.
R2C5 Frame 10: five folded blade nubs are present as a tight star, not yet fan-shaped.
R3C1 Frame 11: same pose as frame 10; only a thin circular gunmetal casing lip appears around the folded blades.
R3C2 Frame 12: casing lip thickens into a small compressed ring, with the amber core still partly covered.
R3C3 Frame 13: blade nubs lengthen slightly but remain stacked and pressed inward inside the ring.
R3C4 Frame 14: the gunmetal ring gains two bold art-deco step facets at the top, still compact.
R3C5 Frame 15: brass rivet dots appear on the casing rim, small and evenly spaced.
R4C1 Frame 16: same pose as frame 15; only the folded blades loosen one small step while staying inside the casing.
R4C2 Frame 17: one small attached exhaust stack stub rises from the upper-right casing edge, capped and closed.
R4C3 Frame 18: three short intake fins appear as a compact folded fin cluster on the lower-left casing edge, flat against the ring.
R4C4 Frame 19: amber core brightens into a small circular oil-coffee window while the five blades remain clamped over it.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and the casing, five folded blades, rivets, capped stack, folded intake fins, and amber core all exist but are compressed under pressure.
R5C1 Frame 21: continuing the exact frame-20 pose; the five chunky blades push outward a visible step, widening the casing silhouette but staying partly overlapped.
R5C2 Frame 22: the turbine casing opens wider, the brass-edged blades separate into clearer fan spokes around the amber core, and two of the three intake fins unfold from the rim.
R5C3 Frame 23: recognizable dieselpunk aroma supercharger silhouette appears; five chunky blades form a compact mechanical blossom inside the round gunmetal casing.
R5C4 Frame 24: almost-final supercharger; casing, rivets, stack, amber core, and five blades are balanced, with exactly one lower-left intake fin still folded against the rim.
R5C5 Frame 25: final sprite: same supercharger as frame 24; only the lower-left intake fin unfolds outward to match the other fins, completing one compact dieselpunk turbine blossom on the same already-open bean base.
```

## Weatherpunk

Target file: `bloom_weatherpunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one weatherpunk storm-glass flower.
Final object contract: exactly one compact botanical weather bloom with the opened roasted coffee bean base acting as two sepals, five soft cloud-petal lobes arranged like a flower (one top, two side, two lower-side), one thin warm brass lightning stamen in the middle, tiny attached blue raindrop beads under the lower petals, and one tiny plain letterless brass wind-vane leaf at the top. Subtle brass ribs may outline the petals. No brass gadget, porthole, gauge, thick circular ring, dial, face, screws, machine cylinder, storm scene, sky background, cloud filling the frame, rain curtain, text, UI, loose particle clutter, duplicate bloom, or second bean.
Final-bloom lock: frames 01-20 must keep the weather flower folded, compressed, or tightly cupped above the bean. Frames 21-25 are the only final bloom frames; frame 23 is the first recognizable storm-glass flower, frame 24 leaves one unresolved top-leaf detail, and frame 25 resolves only that detail.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark vertical seam, no weather parts visible.
R1C2 Frame 02: same closed bean; a faint warm amber glow appears only inside the upper third of the seam.
R1C3 Frame 03: seam opens into a hairline slit at the top third; one tiny pale steam thread is visible inside but stays below the rim.
R1C4 Frame 04: bean halves hinge outward one small step, making a narrow amber slit with a pinhead brass glint deep inside.
R1C5 Frame 05: bean opens slightly more; a tiny rounded cream cloud bud presses upward, still mostly hidden.
R2C1 Frame 06: same pose as frame 05; the cloud bud rises one tiny step with a warm brass stamen dot in its center.
R2C2 Frame 07: bean halves open a little wider; the central stamen becomes a short thin brass filament, no ring.
R2C3 Frame 08: two tiny side cloud buds appear attached close to the filament, forming a compact floral bud.
R2C4 Frame 09: the cloud bud thickens into three small petal nubs hugging the center; still compact.
R2C5 Frame 10: two lower-side cloud nubs join, making five folded cloud-petal buds, no raindrops yet.
R3C1 Frame 11: the five folded cloud petals become clearer; a faint golden zig inside the stamen hints at lightning.
R3C2 Frame 12: tiny blue dew beads appear attached beneath the two lower cloud petals, connected and not falling.
R3C3 Frame 13: subtle thin brass rib lines trace the folded petal bases; still no circular ring or dial.
R3C4 Frame 14: a tiny letterless brass wind-vane leaf sprouts folded at the top of the bud.
R3C5 Frame 15: loaded compact bud, bean base open, five cloud petals folded tight around one brass lightning stamen.
R4C1 Frame 16: same loaded bud; cloud petals puff slightly outward but remain closed.
R4C2 Frame 17: lower cloud petals separate a little, keeping a clear flower silhouette.
R4C3 Frame 18: side cloud petals loosen one step; the thin central lightning stamen is visible.
R4C4 Frame 19: top cloud petal lifts; wind-vane leaf stays folded and tiny.
R4C5 Frame 20: final pre-bloom pose; bean base settled, five cloud petals folded but pressurized around center.
R5C1 Frame 21: first bloom release; top cloud petal opens upward while all parts remain attached.
R5C2 Frame 22: left and right cloud petals open outward symmetrically, still compact.
R5C3 Frame 23: recognizable weatherpunk storm-glass flower appears with five cloud petals, thin brass lightning stamen, attached dew beads, and open bean base.
R5C4 Frame 24: almost-final bloom; all five cloud petals are balanced, and one tiny top wind-vane leaf remains folded inward.
R5C5 Frame 25: final sprite: same storm-glass flower as frame 24; only the tiny top wind-vane leaf straightens, completing one compact weatherpunk storm-glass coffee flower on the same already-open bean base.
```

## Gothic Punk

Target file: `bloom_gothic_punk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one gothic punk stained-glass iron rose-lantern.
Final object contract: exactly one centered heavy black wrought-iron pointed-arch rose-lantern with deep crimson stained-glass petal panels, thorn-like iron tips, one warm coffee-candle ember core, and the fixed opened roasted coffee bean base. No cathedral, graveyard, background scene, characters, readable symbols, crosses, bats, extra flowers, loose particles, or second bean.
Final-bloom lock: frames 01-20 must keep the rose-lantern folded, caged, clamped, or compressed. Frames 21-25 are the only final release; frame 23 is the first recognizable gothic stained-glass lantern silhouette, frame 24 leaves one unresolved candle-core detail, and frame 25 resolves only that detail.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark seam, with a tiny warm ember trapped inside the crack.
R1C2 Frame 02: same closed bean; seam opens a hair wider and two short black iron ribs press against the opening like a locked cage.
R1C3 Frame 03: tight thorn-like iron tips curl inward over the seam, and crimson glass hints appear as tiny dark red facets below the surface.
R1C4 Frame 04: bean halves hinge outward one small step; a compressed pointed-arch rib outline begins inside the seam, folded flat and caged.
R1C5 Frame 05: black tracery forms a compact bud-like lantern cage, with crimson stained-glass slivers squeezed between ribs.
R2C1 Frame 06: same pose as frame 05; the caged iron bud rises slightly from the seam, thorn tips locked together at the top.
R2C2 Frame 07: pointed-arch ribs stack closer and darker, while the compressed rose-lantern shape stays squat and trapped.
R2C3 Frame 08: iron ribs thicken into a heavy black cage; stained-glass petal panels remain folded inward.
R2C4 Frame 09: the seam pushes a compact iron cap upward; thorn tips interlock like a latch and crimson facets brighten.
R2C5 Frame 10: the caged lantern bud gains a small central coffee-candle ember, mostly obscured by closed tracery.
R3C1 Frame 11: same pose as frame 10; only the folded pointed arch becomes taller but pinched, with stained-glass panels packed tightly.
R3C2 Frame 12: iron ribs press outward then lock; thorn tips remain inward-facing and crimson glass stays under tension.
R3C3 Frame 13: the compact iron bud shows heavier arch shoulders, with coffee glow leaking through thin seams.
R3C4 Frame 14: the caged bloom rises another step, black tracery sharpens, and crimson panels remain folded like armored glass petals.
R3C5 Frame 15: the compressed rose-lantern mass reaches mid-height, with pointed-arch ribs visible but clamped shut around the candle core.
R4C1 Frame 16: same pose as frame 15; only the iron cage tightens into a narrow spearhead shape and thorn-like tips overlap at the crown.
R4C2 Frame 17: crimson stained-glass panels press against the iron frame, and warm coffee-candle light brightens inside the sealed cage.
R4C3 Frame 18: pointed-arch ribs begin to separate microscopically but remain bound, with the final silhouette still intentionally unclear.
R4C4 Frame 19: the caged bloom strains upward, black iron tracery bends under inner glow, and crimson panels stay mostly closed.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and a locked gothic iron bud holds folded stained-glass panels around a hidden coffee-candle core.
R5C1 Frame 21: continuing the exact frame-20 pose; the warm coffee glow pushes the iron ribs apart and the panels start unfolding.
R5C2 Frame 22: the wrought-iron pointed arch expands outward, crimson glass panels flare from the cage, and the candle core becomes visible.
R5C3 Frame 23: recognizable gothic punk stained-glass iron rose-lantern appears with heavy pointed arch, thorn tips, crimson glass petals, and warm coffee-candle center.
R5C4 Frame 24: almost-final rose-lantern; bold black iron arch, crimson glass panels, and warm ember glow are resolved, with only the central coffee-candle wick still bent sideways.
R5C5 Frame 25: final sprite: same rose-lantern as frame 24; only the central coffee-candle wick straightens into clean alignment, with no change to the iron arch, glass panels, or overall glow.
```

## Aetherpunk

Target file: `bloom_aetherpunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one raised aetherpunk aether still crystal bloom.
Final object contract: exactly one raised aether still bloom anchored to the bean seam by a central violet-gold thread, with elegant brass ring petals arranged around one violet-gold crystal core, exactly two connected violet-gold energy ribbons attached to the object, abstract non-readable arc segments on the brass rings, and the fixed opened roasted coffee bean base. No floating islands, sky armadas, cosmic backgrounds, excessive particles, readable runes, text, UI, second object, environment, detached ribbons, extra crystals, duplicate rings, or second bean.
Final-bloom lock: frames 01-20 must stay compact, folded, clamped, short, dim, and contained above the bean seam. Frames 21-25 are the only frames where the brass ring petals, crystal core, and attached energy ribbons release into the final readable aetherpunk bloom.

R1C1 Frame 01: closed roasted coffee bean, upright oval, with a hairline violet-gold glint inside the vertical seam.
R1C2 Frame 02: same closed bean; the seam glint thickens into one tiny luminous aether thread just peeking upward from the center seam.
R1C3 Frame 03: the tiny thread curls into a short vertical vapor hook, still lower than the bean top and centered on the seam.
R1C4 Frame 04: a pinhead violet crystal point appears at the top of the thread, mostly hidden between the bean halves.
R1C5 Frame 05: the crystal point lifts slightly above the seam and gains a tiny gold highlight, with the bean halves barely hinged open.
R2C1 Frame 06: same pose as frame 05; only the tiny crystal point rises one notch higher while the compact centered thread persists.
R2C2 Frame 07: a small brass crescent clasp forms around the crystal point, cupped tight like a closed mechanical bud.
R2C3 Frame 08: a second matching brass crescent appears opposite the first, making a tiny closed ring clasp around the crystal.
R2C4 Frame 09: two short attached violet energy ribbons sprout from the clasp sides and curl inward tightly against the bud.
R2C5 Frame 10: the crystal point lengthens into a small faceted teardrop, still mostly enclosed by the brass clasp.
R3C1 Frame 11: same pose as frame 10; only the brass clasp gains four tiny hinge nubs at diagonal positions, all folded inward.
R3C2 Frame 12: faint abstract arc segments appear on the clasp as separated curved marks, clearly non-letter shapes and not readable text.
R3C3 Frame 13: the attached ribbons brighten and thicken slightly, still curled tight against the central teardrop.
R3C4 Frame 14: four miniature brass ring-petal tips unfold from the hinge nubs, each still short and cupped toward the crystal.
R3C5 Frame 15: the teardrop crystal becomes a compact violet-gold hexagonal core, with all ring-petal tips clamped close around it.
R4C1 Frame 16: same pose as frame 15; only the ring-petal tips extend into short folded brass arcs, forming an incomplete tight oval.
R4C2 Frame 17: the folded brass arcs separate slightly from the core while staying short, like locked mechanical petals under tension.
R4C3 Frame 18: the two attached energy ribbons each uncurl into a small loop, making two loops connected to the brass arcs and still contained inside the tight oval.
R4C4 Frame 19: the crystal core rises along the same anchored centerline toward the final bloom position while the brass arcs remain compressed and inward-facing.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open angle, and the compact crystal core is clamped by folded brass ring petals with attached ribbons coiled tight.
R5C1 Frame 21: continuing the exact frame-20 pose; the folded brass ring petals spring outward into a wider oval cage while the crystal core stays centered.
R5C2 Frame 22: the brass ring petals open wider into elegant petal-like arcs and the attached violet-gold ribbons stretch between them without detaching.
R5C3 Frame 23: recognizable raised aether still bloom appears with a clear central crystal core, surrounding brass ring petals, and connected energy ribbons.
R5C4 Frame 24: almost-final aetherpunk bloom; polished brass ring petals, violet-gold crystal core, connected ribbons, and abstract arc segments are resolved, but the uppermost brass ring petal remains slightly folded inward.
R5C5 Frame 25: final sprite: same aetherpunk bloom as frame 24; only the uppermost brass ring petal opens fully to match the others, completing the single raised aether still bloom.
```

## Desertpunk

Target file: `bloom_desertpunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one desertpunk heat-scarab cactus condenser.
Final object contract: exactly one centered desertpunk heat-scarab cactus condenser with ochre ceramic scarab shell plates, tiny succulent fins and spines, one leather strap band, one sun-baked brass heat vent, one warm coffee ember core, and the fixed opened roasted coffee bean base. No desert landscape, dunes, caravans, sun background, dust storm, characters, animals, loose sand clouds, text, extra props, or multiple blooms.
Final-bloom lock: frames 01-20 must keep the condenser closed, strapped, compressed, or armor-clamped. Frames 21-25 are the only final release; frame 23 is the first recognizable heat-scarab cactus condenser silhouette, frame 24 leaves one unresolved brass heat vent detail, and frame 25 resolves only that detail.

R1C1 Frame 01: closed roasted coffee bean, upright oval, with a faint warm ember seam along the bean crease.
R1C2 Frame 02: same closed bean; the ember seam thickens into a tiny ochre ceramic nub pressed low against the bean.
R1C3 Frame 03: compact ceramic nub gains two hairline shell grooves, still compressed and seedlike.
R1C4 Frame 04: a small brass glint forms at the nub's crown, flush and unopened.
R1C5 Frame 05: the nub becomes a tight scarab-like bud with closed ochre plates hugging the bean.
R2C1 Frame 06: same pose as frame 05; the closed shell bud lifts slightly, with a warm coffee ember glow visible through one narrow central slit.
R2C2 Frame 07: two miniature succulent fins start to emboss beneath the shell edges, still tucked in.
R2C3 Frame 08: a leather strap band begins as a thin dark wrap around the compressed bud.
R2C4 Frame 09: a brass heat vent becomes a tiny capped ridge, not yet open.
R2C5 Frame 10: ceramic shell plates segment into scarab armor, all parts still compact and closed.
R3C1 Frame 11: same pose as frame 10; only the shell bud rises taller while succulent fins sharpen into tiny water-saving ribs.
R3C2 Frame 12: the ember core brightens behind the central slit, casting warm highlights on ochre plates.
R3C3 Frame 13: the leather strap band tightens visibly around the middle, holding the condenser closed.
R3C4 Frame 14: the brass vent shows small vertical slots, still capped and compressed.
R3C5 Frame 15: outer shell plates hinge a few degrees outward like a restrained scarab carapace.
R4C1 Frame 16: same pose as frame 15; only succulent fins unfold slightly from between plates, with spines tiny and orderly.
R4C2 Frame 17: the compact condenser silhouette widens but remains partly locked by the leather band.
R4C3 Frame 18: the brass vent lifts above the core, still angled inward and not fully venting.
R4C4 Frame 19: ochre plates separate into a clear armored bud, with the ember core mostly visible.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and all shell plates, fins, strap, vent, and ember core are packed tight.
R5C1 Frame 21: continuing the exact frame-20 pose; scarab shell plates open symmetrically around the glowing coffee ember core.
R5C2 Frame 22: succulent fins and spines fan into compact condenser ribs beneath the ochre plates.
R5C3 Frame 23: recognizable heat-scarab cactus condenser silhouette appears, with leather band, brass vent, shell plates, fins, and ember core readable as one relic.
R5C4 Frame 24: almost-final condenser; shell plates, fins, leather band, and ember core are resolved, but the brass heat vent remains slightly closed.
R5C5 Frame 25: final sprite: same condenser as frame 24; only the brass heat vent opens to its finished sun-baked slotted form while every other detail stays unchanged.
```

## Oceanpunk

Target file: `bloom_oceanpunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one oceanpunk abyssal coral-jelly condenser bloom.
Final object contract: exactly one oceanpunk abyssal coral-jelly condenser bloom with an abalone shell collar, exactly five connected teal and deep-blue bioluminescent tendril-petal lobes, thin pressure bands, one pearl-like coffee-cream core, exactly two attached crema-steam current loops rising from the bean as opaque painted ribbons connected at the bean seam and the bud, and the fixed opened roasted coffee bean base. No underwater scene, fish, reef background, bubbles filling cells, ship, treasure, text, extra props, second bloom, loose particle swarms, detached pearls, separate water effects, or second bean.
Final-bloom lock: frames 01-20 must keep all oceanpunk parts compact, cupped, clamped, folded, dim, or contained close to the bean. Frames 21-25 are the only final release; frame 23 is the first recognizable shell-base, five-lobed tendril bloom silhouette.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark vertical seam, no glow and no ocean cue.
R1C2 Frame 02: same closed bean; seam gains one tiny teal-blue glint inside the dark groove.
R1C3 Frame 03: seam opens into a hairline slit at the top third, with a faint coffee-cream shine deep inside.
R1C4 Frame 04: bean halves hinge outward one small step, revealing a narrow warm slit with a dim teal edge.
R1C5 Frame 05: a tiny abalone-shell nub appears inside the slit, still below the bean rim and fully contained.
R2C1 Frame 06: same pose as frame 05; only the abalone nub rises one tiny step above the seam.
R2C2 Frame 07: the nub widens into a very small iridescent shell collar cup, still closed and centered.
R2C3 Frame 08: one short deep-blue tendril point appears folded inside the shell collar, not rising above it.
R2C4 Frame 09: two matching folded tendril points become visible beside the first, all tucked inward.
R2C5 Frame 10: five tiny connected tendril points can be counted as a clamped crown inside the shell collar.
R3C1 Frame 11: same pose as frame 10; only a thin pressure band appears around the compact tendril crown.
R3C2 Frame 12: the shell collar opens one small step wider, showing abalone green-blue-pink facets attached to the base.
R3C3 Frame 13: the five tendril points lengthen into short curled lobes, still pressed together like a closed jelly bud.
R3C4 Frame 14: a small pearl-like coffee-cream core glints between the curled lobes but remains mostly covered.
R3C5 Frame 15: one attached crema-steam current loop rises as a tight oval loop from the bean seam into the closed bud.
R4C1 Frame 16: same pose as frame 15; only the current loop brightens and connects cleanly to the shell collar.
R4C2 Frame 17: a second attached current loop forms behind the first, both compact and wrapped close to the bud.
R4C3 Frame 18: the pressure band splits into two thin bands around the clamped tendril lobes, with no loose pieces.
R4C4 Frame 19: five curled tendril lobes swell outward under tension but remain short, dim, and folded over the core.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, abalone collar is tight, five connected tendril lobes are clamped around the hidden pearl core, pressure bands are taut, and attached current loops are compressed close to the bean.
R5C1 Frame 21: continuing the exact frame-20 pose; the five connected tendril lobes release outward a visible step while the pressure bands stretch but stay attached.
R5C2 Frame 22: the abalone shell collar flares wider and the attached crema-steam current loops rise taller as two solid painted arcs connected to the bloom.
R5C3 Frame 23: recognizable oceanpunk silhouette appears: shell base, five connected teal deep-blue tendril lobes, and a visible pearl-like coffee-cream core, with lobe tips still slightly curled.
R5C4 Frame 24: almost-final condenser bloom; five tendril lobes, shell collar, pressure bands, current loops, and central pearl core are balanced, with exactly one rightmost tendril lobe still curled inward enough to leave the silhouette unfinished.
R5C5 Frame 25: final sprite: same oceanpunk bloom as frame 24; only the rightmost tendril lobe uncurls outward, completing one abyssal coral-jelly condenser bloom on the same already-open bean base.
```

## Piratepunk

Target file: `bloom_piratepunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one piratepunk compass-sail aroma charm.
Final object contract: exactly one centered piratepunk charm with one tiny mast-like spine, one weathered brass compass rose without letters, exactly three dark torn sail-petal panels, one attached rope ring, one coffee-gold core, and the fixed opened roasted coffee bean base. No ships, flags with symbols, maps, skulls, skeletons, cannons, deck scenes, treasure piles, text, characters, background, separate props, or second bean.
Final-bloom lock: frames 01-20 must keep the charm compact, lashed, folded, or restrained. Frames 21-25 are the only final release; frame 23 is the first recognizable compass-sail charm silhouette, frame 24 leaves one upper sail-petal unresolved, and frame 25 resolves only that petal.

R1C1 Frame 01: closed roasted coffee bean, upright oval, with a faint coffee-gold glow sealed in the crease.
R1C2 Frame 02: same closed bean; a tiny brass glint forms inside the bean crease while the outer bean remains whole.
R1C3 Frame 03: hair-thin rope fibers gather above the bean seam as the first turn of a small attached rope band, not wrapping around the bean body.
R1C4 Frame 04: the lashing tightens around the central charm above the seam, with a dim brass point tucked inside the opening.
R1C5 Frame 05: a short mast-like spine nub presses upward from the bean crease, still mostly hidden and compact.
R2C1 Frame 06: same pose as frame 05; only the spine nub lengthens slightly while coffee vapor stays trapped around it.
R2C2 Frame 07: weathered brass facets gather flat against the bean front, beginning a compass-rose geometry without letters.
R2C3 Frame 08: the brass facets sharpen into tiny petal-like points, still folded tight over the coffee-gold core.
R2C4 Frame 09: dark sail-like panels appear as torn folded slivers, pinned close to the mast-like spine.
R2C5 Frame 10: the rope band curls into an attached partial ring, hugging the emerging charm above the seam and keeping the bloom restrained.
R3C1 Frame 11: same pose as frame 10; only coffee aroma pushes outward in a small wind-like swell, lifting one dark sail-petal edge.
R3C2 Frame 12: the brass compass rose becomes clearer, compact and letterless, nested over the glowing bean core.
R3C3 Frame 13: the mast-like spine straightens at center, with all sail-petal panels still lashed inward.
R3C4 Frame 14: the attached rope ring rounds out behind the compact charm, visible as part of the single object.
R3C5 Frame 15: the bean core brightens coffee-gold, but the sail panels remain tightly folded and bundled.
R4C1 Frame 16: same pose as frame 15; only aroma pressure makes the rope ring taut and the brass rose slightly raised from the bean.
R4C2 Frame 17: dark torn sail-petal panels fan a few degrees outward, still compact and tied close to the spine.
R4C3 Frame 18: brass points align around the glowing core, suggesting a compass-sail charm but not yet a full silhouette.
R4C4 Frame 19: the lashing loosens visibly, with coffee vapor curling inside the attached rope ring.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and the core, spine, brass rose, rope ring, and folded sail-petals are all present but restrained.
R5C1 Frame 21: continuing the exact frame-20 pose; the rope ring opens to its final attached circle and the sail-petals lift with aroma-wind.
R5C2 Frame 22: the brass compass rose spreads wider over the coffee-gold core, while dark sail panels unfold unevenly.
R5C3 Frame 23: recognizable piratepunk compass-sail aroma charm appears with spine, rope ring, brass rose, and sail-petals clearly readable.
R5C4 Frame 24: almost-final charm; all parts are released, but one upper sail-petal remains slightly curled and unresolved.
R5C5 Frame 25: final sprite: same compass-sail charm as frame 24; only the last sail-petal flattens into place, with no other changes.
```

## Clockpunk

Target file: `bloom_clockpunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one precise clockpunk escapement orrery blossom.
Final object contract: exactly one polished brass clockwork blossom with exactly four nested attached gear-petals (two outer, two inner), one visible ruby jewel pivot, one tiny pendulum tongue, one mainspring spiral core, and the fixed opened roasted coffee bean base. All parts connect to the central object. No loose gears, gear avalanche, clock face, digits, readable numbers, numeral discs, steam, pipes, workshop background, second machine, extra bean, or second object.
Final-bloom lock: frames 01-20 must keep the clockwork wound, clamped, compact, and rising from the bean seam. Frames 21-25 are the only frames where the escapement orrery blossom opens into its final polished brass silhouette.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark vertical seam, no brass visible.
R1C2 Frame 02: same closed bean; a thin warm brass glint appears only inside the center seam.
R1C3 Frame 03: seam opens into a hairline slit near the top; bean outline and size unchanged.
R1C4 Frame 04: bean halves hinge outward one small step, revealing a narrow polished brass line in the slit.
R1C5 Frame 05: a pinhead brass pivot nub becomes barely visible inside the upper seam; nothing rises above the bean.
R2C1 Frame 06: same pose as frame 05; only the brass pivot nub rises one tiny step above the seam.
R2C2 Frame 07: the nub reveals a tiny wound mainspring curl at its center, still mostly hidden in the opening.
R2C3 Frame 08: the mainspring curl thickens into a small tight spiral disk, locked flat and centered.
R2C4 Frame 09: two minuscule folded brass tooth-petals appear tight against the spiral, both attached to the center.
R2C5 Frame 10: a tiny ruby jewel point appears at the spiral center while the tooth-petals remain clamped inward.
R3C1 Frame 11: same pose as frame 10; only a short central brass stem lifts the compact mechanism slightly higher.
R3C2 Frame 12: two more folded gear-petal tips appear behind the first pair, still nested tightly around the spiral.
R3C3 Frame 13: the nested brass tips gain crisp tooth edges, but the whole object remains a closed mechanical bud.
R3C4 Frame 14: a tiny pendulum tongue becomes visible as a short tucked brass tab below the jewel pivot.
R3C5 Frame 15: the mainspring spiral becomes clearer and more wound, pressing the folded petals outward without opening.
R4C1 Frame 16: same pose as frame 15; only the outer folded gear-petals loosen one small step while staying clamped.
R4C2 Frame 17: inner folded petals separate slightly from the spiral, still upright and compressed around the pivot.
R4C3 Frame 18: the ruby jewel pivot grows readable at the center; pendulum tongue remains tucked short.
R4C4 Frame 19: the nested gear-petals press outward into a compact brass cup, not yet a blossom silhouette.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, mainspring core is tightly wound, ruby jewel glints, gear-petals are fully present but clamped into a small compressed cup, and the pendulum tongue is tucked under tension.
R5C1 Frame 21: continuing the exact frame-20 pose; the outer gear-petal ring releases upward and outward in one large step while all petals remain attached to the pivot.
R5C2 Frame 22: inner gear-petals fan wider around the mainspring core, creating a broad polished brass mechanical flower cup.
R5C3 Frame 23: recognizable escapement orrery blossom silhouette appears; nested brass gear-petals form a balanced radial bloom around the visible ruby jewel and mainspring spiral.
R5C4 Frame 24: almost-final clockpunk blossom; all nested gear-petals are polished, open, and balanced, with exactly one tiny pendulum tongue still angled and not yet hanging straight.
R5C5 Frame 25: final sprite: same clockpunk blossom as frame 24; only the tiny pendulum tongue settles straight beneath the ruby jewel pivot, completing one polished brass escapement orrery blossom on the same already-open bean base.
```

## Bronzepunk

Target file: `bloom_bronzepunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one bronzepunk patina laurel amphora offering vessel.
Final object contract: exactly one centered ancient bronze amphora bloom rising from the fixed opened roasted coffee bean base, with exactly four hammered bronze petal-plates forming amphora curves, green patina streaks, exactly two laurel side fins, and one small warm coffee ember bowl integrated at the center. No precision gears, steam pipes, clock faces, temples, statues, weapons, background, text, extra props, second vessel, loose leaves, or multiple objects.
Final-bloom lock: frames 01-20 must stay compact, cast, folded, clamped, or closed, with all amphora, laurel, patina, and ember features compressed into the bean. Frames 21-25 are the only frames where the vessel releases into the final recognizable amphora bloom.

R1C1 Frame 01: closed roasted coffee bean, upright oval, with only a tiny dull bronze dot seated inside the vertical seam.
R1C2 Frame 02: same closed bean; the bronze dot lengthens into a short hammered bronze dash.
R1C3 Frame 03: the dash thickens into a tiny cast-bronze plug, still flush inside the seam.
R1C4 Frame 04: the plug gains one faint green patina hairline down its center, with no opening yet.
R1C5 Frame 05: the bean seam parts by a sliver and the bronze plug rises a few pixels as a compact rounded nub.
R2C1 Frame 06: same pose as frame 05; only the rounded nub gains a tiny pinched amphora-neck top while staying sealed and compact.
R2C2 Frame 07: the bean halves hinge slightly outward and the sealed bud shows two tiny folded side ridges.
R2C3 Frame 08: the folded side ridges sharpen into miniature laurel-fin tips pressed tight against the bud.
R2C4 Frame 09: the central bud grows taller into a short closed bronze urn shape, with hammered dimples visible.
R2C5 Frame 10: a tiny warm coffee ember point appears deep inside the still-closed top slit.
R3C1 Frame 11: same pose as frame 10; only the top slit opens a hair wider around the dim ember point.
R3C2 Frame 12: the bean halves hinge farther outward and the urn belly swells slightly while staying clamped.
R3C3 Frame 13: two folded hammered bronze petal-plates become visible on the front, overlapping tightly like armor.
R3C4 Frame 14: thin green patina streaks extend down the folded plates without changing the closed silhouette.
R3C5 Frame 15: the laurel-fin tips lengthen a little along both sides but remain curled inward against the urn.
R4C1 Frame 16: same pose as frame 15; only the bean halves settle to their final open base angle while the bronze vessel stays closed.
R4C2 Frame 17: the urn neck rises into a taller sealed amphora throat, with the ember still trapped as a narrow glow line.
R4C3 Frame 18: the front petal-plates press outward under tension, making a wider but still closed hammered bronze bud.
R4C4 Frame 19: both laurel side fins become fully present as short curled bronze-green fins clamped to the sides.
R4C5 Frame 20: loaded pre-bloom pose; an open bean base supports one compact sealed bronze amphora bud, all plates, fins, patina, and ember compressed tight.
R5C1 Frame 21: continuing the exact frame-20 pose; the sealed amphora bud releases upward, opening the top into a small cup-like mouth while folded plates start to flare.
R5C2 Frame 22: the hammered bronze plates flare wider and the laurel side fins uncurl halfway, revealing a brighter central coffee ember.
R5C3 Frame 23: recognizable ancient amphora-shaped bronze bloom appears with curved side fins and an open ember bowl, still not fully balanced.
R5C4 Frame 24: almost-final amphora bloom; hammered bronze plates, patina streaks, laurel fins, and center ember bowl are complete, but the right laurel fin remains visibly curled inward.
R5C5 Frame 25: final sprite: same amphora bloom as frame 24; only the right laurel fin uncurls to match the left, completing one bronzepunk patina laurel amphora bloom without adding new parts.
```

## Nanopunk

Target file: `bloom_nanopunk_spritesheet.png`

```text
Subject: a roasted coffee bean crystallizes coffee aroma into one nanopunk self-assembling lattice bloom.
Final object contract: exactly one centered nanopunk self-assembling lattice bloom with exactly five chrome hex-cell petal plates, pale blue micro-circuit veins, one coffee-brown molecular core, crisp crystalline molecular symmetry, and the fixed opened roasted coffee bean base. The nanobots resolve into one coherent physical flower-object, not a loose swarm cloud. No lab scene, UI, projected text, extra particles, multiple objects, duplicate beans, or background.
Final-bloom lock: frames 01-20 must stay compressed, clamped, tessellating, or partially facet-locked. Frames 21-25 are the only final release frames; frame 23 is the first recognizable lattice-flower silhouette, frame 24 has exactly one unfinished hex-cell plate detail, and frame 25 resolves only that detail.

R1C1 Frame 01: closed roasted coffee bean, upright oval, with a tiny dark chrome molecular pin emerging from the center seam.
R1C2 Frame 02: the molecular pin becomes a short stacked nano-column, each segment tightly fused into one centered compressed spine.
R1C3 Frame 03: two minuscule chrome hex plates facet-lock against the lower nano-column, still narrower than the bean seam.
R1C4 Frame 04: pale blue micro-circuit veins appear as two tiny inset lines on the fused lower plates, with no glow spill.
R1C5 Frame 05: the lower plates tessellate into a compact three-cell wedge, all cells touching as one solid sprout.
R2C1 Frame 06: same pose as frame 05; only the compact wedge gains one central coffee-brown molecular bead locked inside it.
R2C2 Frame 07: the nano-column compresses upward into a squat hexagonal bud, with the brown bead centered and partially enclosed.
R2C3 Frame 08: two folded side plates clamp onto the bud like closed chrome petals, keeping the silhouette tight and vertical.
R2C4 Frame 09: the folded side plates gain faint pale blue circuit veins that run inward toward the brown core.
R2C5 Frame 10: a top cap of three tiny hex cells facet-locks over the core, forming one closed crystalline nano-bud.
R3C1 Frame 11: same pose as frame 10; only the closed nano-bud lengthens slightly as stacked hex cells interlock upward.
R3C2 Frame 12: the left folded plate thickens into a layered chrome petal shard, still clamped tight against the bud.
R3C3 Frame 13: the right folded plate thickens to match, creating a symmetrical closed chrome capsule around the core.
R3C4 Frame 14: pale blue micro-circuit veins branch across the capsule as engraved physical traces, not projected light lines.
R3C5 Frame 15: the capsule gains a small faceted crystalline point at the top, all plates still fused into one compressed object.
R4C1 Frame 16: same pose as frame 15; only the top point splits into two barely separated locked facets, suggesting stored pressure.
R4C2 Frame 17: side petal shards hinge outward a few degrees while remaining short, cupped, and tightly interlocked.
R4C3 Frame 18: a ring of small chrome hex cells becomes visible around the coffee-brown core, still clamped in a compact rosette.
R4C4 Frame 19: the rosette widens slightly as hex cells tessellate edge-to-edge, with all major petals folded and compressed.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and a tense closed nano-lattice bud sits above the bean with all chrome plates facet-locked around the brown core.
R5C1 Frame 21: continuing the exact frame-20 pose; the compressed nano-lattice releases upward and outward, unfolding top chrome plates into a wider cupped crystalline form.
R5C2 Frame 22: the cupped form opens wider as left and right hex-cell petals slide and lock into larger symmetrical chrome plates with brighter engraved blue veins.
R5C3 Frame 23: recognizable nanopunk lattice-flower silhouette appears, with chrome hex-cell petals arranged around the one coffee-brown molecular core.
R5C4 Frame 24: almost-final lattice bloom; chrome hex-cell petals, pale blue engraved veins, and coffee-brown molecular core are resolved, but one upper-right outer hex-cell plate remains folded inward and visibly unfinished.
R5C5 Frame 25: final sprite: same lattice bloom as frame 24; only the upper-right outer hex-cell plate facet-locks outward to complete the single crisp nanopunk lattice bloom.
```

## Silkpunk

Target file: `bloom_silkpunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one silkpunk bamboo-and-rice-paper aroma lantern.
Final object contract: exactly one centered bamboo-and-rice-paper aroma lantern bloom with exactly five lacquered bamboo ribs, cream rice-paper panels, one red silk cord knot, ink-brush-like dark edge strokes with no readable writing, steam folds integrated into the paper panels, and the unchanged fixed opened roasted coffee bean base. No full kites, banners, calligraphy text, landscape, characters, lantern strings, loose ribbons everywhere, background, multiple props, flowers, extra beans, or detached ornaments.
Final-bloom lock: frames 01-20 must keep every lantern part folded, lashed, closed, clamped, curled, or compressed. Frames 21-25 are the only final unfurl; frame 23 is the first recognizable lantern/fan-bloom silhouette, frame 24 has exactly one unresolved red knot detail, and frame 25 resolves only that knot.

R1C1 Frame 01: closed roasted coffee bean, upright oval, with a tiny warm coffee-gold glint along the vertical seam and no visible lantern parts.
R1C2 Frame 02: the bean seam parts by a hairline, revealing one tiny cream rice-paper sliver pinched vertically inside.
R1C3 Frame 03: the seam opens slightly wider and a short lacquered bamboo tip appears behind the cream sliver, still trapped between bean halves.
R1C4 Frame 04: bean halves hinge outward a few degrees and the cream sliver folds into a narrow closed pleat.
R1C5 Frame 05: a second tiny cream pleat appears directly beside the first, both bound tight by a minuscule red cord wrap.
R2C1 Frame 06: same pose as frame 05; only the bean opens a little more while the two cream pleats lengthen upward as a closed lashed bundle.
R2C2 Frame 07: a third short folded rice-paper pleat emerges in the center, all three pressed together with no fan shape yet.
R2C3 Frame 08: two dark ink-brush-like edge strokes appear along the outer folded pleats, abstract and not readable.
R2C4 Frame 09: the bamboo tips extend into three short lacquered ribs, still parallel and clamped shut.
R2C5 Frame 10: a faint steam curl rises from the bean seam and tucks into the folded paper bundle as a pale internal crease.
R3C1 Frame 11: same pose as frame 10; only the folded bundle rises taller without opening.
R3C2 Frame 12: the red cord wrap thickens into a small tight knot nub around the lower folded bundle.
R3C3 Frame 13: the closed rice-paper bundle gains two more compressed side pleats and two matching short bamboo ribs, making five total ribs still lashed into a narrow vertical packet.
R3C4 Frame 14: the lacquered bamboo ribs bow outward slightly under tension but remain tied together at the top.
R3C5 Frame 15: the cream panels show stronger steam-fold creases inside the closed packet, with no recognizable lantern silhouette.
R4C1 Frame 16: same pose as frame 15; only the bean halves reach a broad open cup angle while the folded lantern packet stays clamped upright.
R4C2 Frame 17: the bamboo rib fan inside the packet spreads only at the very top by a small crack, still visibly closed and lashed.
R4C3 Frame 18: the lower red cord knot nub slides into a centered cinch on the packet, holding all panels tightly gathered.
R4C4 Frame 19: the folded rice-paper panels swell into a compact teardrop-shaped closed lantern bud with dark edge strokes tucked along the sides.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are settled at the final open base angle, and the closed teardrop lantern packet is tense, lashed, and compressed above it.
R5C1 Frame 21: continuing the exact frame-20 pose; the lashing loosens and the upper rice-paper packet opens into a small half-fan while bamboo ribs begin separating.
R5C2 Frame 22: the bamboo ribs swing wider and pull the cream panels into a broad arched fan, still partly curled inward.
R5C3 Frame 23: recognizable silkpunk lantern/fan-bloom silhouette appears, with visible lacquered ribs radiating from the bean base.
R5C4 Frame 24: almost-final lantern bloom; all cream rice-paper panels and ink-like dark edge strokes are open and settled, but the red silk cord knot remains a loose unfinished half-loop.
R5C5 Frame 25: final sprite: same lantern bloom as frame 24; only the loose red half-loop tightens into one neat red silk cord knot.
```

## Solarpunk

Target file: `bloom_solarpunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one clean solarpunk photovoltaic leaf-disc bloom.
Final object contract: exactly one bold icon-readable photovoltaic leaf-disc bloom with exactly five rounded green leaf-panel petals edged in warm brass around one amber solar-glass center, one subtle vine stem, and the fixed opened roasted coffee bean base. No city, garden scene, buildings, eco architecture, background sun, text, extra props, duplicate objects, loose sparkles, fungus, reclaimed decay, or second bloom.
Final-bloom lock: frames 01-20 must keep the solarpunk object compact, closed, folded, dim, or clamped. Frames 21-25 are the only final release; frame 23 is the first recognizable five-panel leaf-disc silhouette.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark vertical seam, no green, no brass, no glass.
R1C2 Frame 02: same closed bean; a thin warm coffee-gold highlight appears only inside the seam.
R1C3 Frame 03: seam opens into a hairline slit near the top third; bean outline and size unchanged.
R1C4 Frame 04: bean halves hinge outward one small step from the seam, revealing a narrow amber slit.
R1C5 Frame 05: a tiny brass-green engineered point appears inside the upper slit, still below the bean rim.
R2C1 Frame 06: same pose as frame 05; only the brass-green point rises one tiny step above the seam.
R2C2 Frame 07: the point lengthens into a very short centered vine stem with a sealed amber bead at its top.
R2C3 Frame 08: the amber bead grows slightly and gains one tiny folded green panel nub on the left side.
R2C4 Frame 09: a matching folded green panel nub appears on the right side; stem remains subtle and centered.
R2C5 Frame 10: the two side nubs tuck closer around the amber bead while a tiny top panel tip appears, all closed.
R3C1 Frame 11: same pose as frame 10; only the top panel tip grows into a small rounded folded leaf-panel.
R3C2 Frame 12: two lower folded panel hints appear tight against the bead, making five total panel positions but still clamped.
R3C3 Frame 13: faint warm brass edging becomes visible on the folded panel tips; amber center remains mostly hidden.
R3C4 Frame 14: the closed five-panel bud swells into a compact oval cap, with panels overlapping like a sealed device.
R3C5 Frame 15: the amber solar-glass center shows as a narrow vertical glint between folded panels, not a disc.
R4C1 Frame 16: same pose as frame 15; only the folded panels loosen one small step, forming a tiny closed crown.
R4C2 Frame 17: the five panel edges become countable as brass-rimmed folds, but all leaf-panels remain upright and cupped.
R4C3 Frame 18: the amber center rounds slightly inside the tight crown, suggesting stored morning-coffee warmth.
R4C4 Frame 19: panel tips press outward under tension, still compact and not yet forming a readable leaf-disc.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and five brass-edged green leaf-panels clamp around a dim amber solar-glass core.
R5C1 Frame 21: continuing the exact frame-20 pose; the top leaf-panel lifts outward and the amber core brightens visibly.
R5C2 Frame 22: left and right leaf-panels hinge outward wider, creating a broader engineered crown while lower panels remain cupped.
R5C3 Frame 23: recognizable five-panel photovoltaic leaf-disc silhouette appears; amber solar-glass center is round, with the lower panel still folded inward.
R5C4 Frame 24: almost-final solarpunk bloom; five rounded green brass-edged panels are balanced around the amber center, with exactly one lower panel tip still tucked enough to affect the silhouette.
R5C5 Frame 25: final sprite: same bloom as frame 24; only the last lower panel tip uncurls outward, completing one clean five-panel solarpunk photovoltaic leaf-disc bloom on the same already-open bean base.
```

## Steampunk

Target file: `bloom_steampunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one steampunk pressure-valve coffee bloom.
Final object contract: exactly one centered steampunk pressure-valve coffee bloom with one vertical copper pipe core, one brass pressure collar, one leather gasket ring, one round glass gauge lens with no numbers, tick marks, or hands, exactly five controlled cream steam-petal curls, and the fixed opened roasted coffee bean base. No gears, gear avalanche, workshop background, vehicles, clocks, numerals, text, extra props, duplicate stems, heavy turbine parts, or multiple objects.
Final-bloom lock: frames 01-20 must stay compressed, clamped, pressurized, and not flower-like. Frames 21-25 are the only frames where espresso-like pressure opens the valve into the final five steam-petal bloom.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark vertical seam, no metal visible.
R1C2 Frame 02: same closed bean; a tiny copper highlight appears only inside the center seam.
R1C3 Frame 03: seam opens into a hairline slit at the top third; bean outline and size unchanged.
R1C4 Frame 04: bean halves hinge outward one small step, revealing a narrow warm copper line.
R1C5 Frame 05: a tiny rounded copper valve pin becomes barely visible inside the slit, below the bean rim.
R2C1 Frame 06: same pose as frame 05; only the copper valve pin rises one tiny step above the seam.
R2C2 Frame 07: the pin lengthens into a very short centered copper pipe nub, still capped and compressed.
R2C3 Frame 08: a thin brass crescent collar appears around the pipe nub, tucked between the bean halves.
R2C4 Frame 09: a dark leather gasket ring appears as a narrow band under the brass crescent, still tight.
R2C5 Frame 10: the pipe nub gains a tiny blank glass lens bead on top, with no markings or pointer.
R3C1 Frame 11: same pose as frame 10; only the blank glass lens bead grows into a small round lens.
R3C2 Frame 12: the brass collar closes into a tiny circular clamp around the pipe, hugging the gasket.
R3C3 Frame 13: two short copper elbow hints fold inward from the collar, attached to the same centered apparatus.
R3C4 Frame 14: the elbow hints thicken slightly but remain clamped close against the central pipe.
R3C5 Frame 15: a faint cream steam thread appears behind the blank lens, trapped as a tight curl.
R4C1 Frame 16: same pose as frame 15; only the trapped steam thread curls one small step tighter.
R4C2 Frame 17: four more faint cream steam tips become visible as tiny inward curls around the lens, not petals yet.
R4C3 Frame 18: the brass pressure collar widens slightly and the five steam tips press outward under tension.
R4C4 Frame 19: the copper elbow pipes lift a little from the collar while still folded inward and compact.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, the leather gasket and brass collar clamp a blank glass lens, copper pipes are pressurized close to the center, and five cream steam curls are tight and trapped.
R5C1 Frame 21: continuing the exact frame-20 pose; the valve opens a visible step and the top cream steam curl pushes upward from behind the blank lens.
R5C2 Frame 22: left and right steam curls unfurl outward from the collar, making the pressure bloom much wider while copper pipes stay centered.
R5C3 Frame 23: recognizable steampunk pressure-valve coffee bloom appears; five cream steam-petal curls frame the blank glass lens, with brass collar, leather gasket ring, and delicate copper pipes readable as one object.
R5C4 Frame 24: almost-final pressure-valve bloom; all five steam-petal curls are controlled and balanced, the blank glass lens has no markings, and exactly one lower-right copper pipe elbow remains slightly folded inward.
R5C5 Frame 25: final sprite: same pressure-valve bloom as frame 24; only the lower-right copper pipe elbow unfolds into its final delicate curve, completing one steampunk pressure-valve coffee bloom on the same already-open bean base.
```

## Ecopunk

Target file: `bloom_ecopunk_spritesheet.png`

```text
Subject: a roasted coffee bean blooms into one ecopunk reclaimed mycelium circuit bloom.
Final object contract: exactly one centered reclaimed mycelium circuit bloom with exactly five salvaged circuit-board petal plates, mossy green mycelium growth, subtle mushroom-gill underside hints, a rust-and-leaf palette, one warm compost-coffee core, and the fixed opened roasted coffee bean base. No forest background, trash pile, city ruin, insects, characters, loose debris, text, readable circuit labels, multiple mushrooms, environment, extra stems, or second bloom.
Final-bloom lock: frames 01-20 must keep all reclaimed, composted, and compressed parts folded, clamped, short, or partially buried in the bean opening. Frames 21-25 are the only final release; frame 23 is the first recognizable five-plate ecopunk bloom silhouette.

R1C1 Frame 01: closed roasted coffee bean, upright oval, dark vertical seam, no growth or salvage visible.
R1C2 Frame 02: same closed bean; a tiny moss-green compost glint appears only inside the center seam.
R1C3 Frame 03: seam opens into a hairline slit at the top third; bean outline and size unchanged.
R1C4 Frame 04: bean halves hinge outward one small step, revealing a narrow dark compost line inside.
R1C5 Frame 05: a tiny compressed mycelium nub appears within the slit, still below the bean rim.
R2C1 Frame 06: same pose as frame 05; only the mycelium nub rises one tiny step above the seam.
R2C2 Frame 07: the nub thickens into a short mossy stalk with a faint rust-brown speck embedded in it.
R2C3 Frame 08: a minuscule salvaged green circuit shard presses against the left side of the stalk, still clamped tight.
R2C4 Frame 09: a matching tiny rust-edged circuit shard presses against the right side, both shards folded inward.
R2C5 Frame 10: a small warm compost-coffee core dot appears at the top of the stalk, mostly covered by folded shards.
R3C1 Frame 11: same pose as frame 10; only the core dot grows into a small round amber-brown button.
R3C2 Frame 12: two additional compressed circuit slivers appear behind the core, all stacked vertically and not petal-like yet.
R3C3 Frame 13: mossy mycelium threads crawl over the folded slivers as short raised veins, staying attached to the single bloom.
R3C4 Frame 14: a fifth folded sliver appears at the top, making five countable reclaimed plates around the core, all still cupped tightly.
R3C5 Frame 15: faint mushroom-gill underside lines appear beneath the lower folded plates, visible only as small tan rib hints.
R4C1 Frame 16: same pose as frame 15; only the five clamped plates loosen one small step while remaining compressed.
R4C2 Frame 17: the warm compost-coffee core pushes upward, making the folded plate bundle taller and tenser.
R4C3 Frame 18: moss thickens along the plate edges and mycelium veins connect each plate back to the core.
R4C4 Frame 19: the lower plates curl outward slightly, showing more tan gill underside hints but no open bloom silhouette.
R4C5 Frame 20: loaded pre-bloom pose; bean halves are already settled at the final open base angle, and five reclaimed circuit plates are present but folded tight around the warm compost-coffee core like a compressed mycelium bud.
R5C1 Frame 21: continuing the exact frame-20 pose; the top circuit-board plate lifts upward and opens a visible step, exposing more mossy veins.
R5C2 Frame 22: left and right plates swing outward wider, making the bloom much broader while lower plates remain partly curled.
R5C3 Frame 23: recognizable ecopunk five-plate bloom silhouette appears; salvaged circuit-board petals radiate around the warm compost-coffee core with mossy mycelium veins and subtle gill underside hints.
R5C4 Frame 24: almost-final bloom; all five reclaimed plates are broad and balanced, with exactly one rightmost outer plate edge still folded inward and visibly interrupting the silhouette.
R5C5 Frame 25: final sprite: same bloom as frame 24; only the rightmost outer plate edge unfolds outward, completing one mossy reclaimed mycelium circuit bloom with five salvaged plates, warm compost-coffee core, and the same already-open bean base.
```

## Punk Final Still Prompt Contract

Use one final still per punk spritesheet. The preferred still is a direct extraction of `R5C5 Frame 25` from the processed and aligned spritesheet. If a final still must be generated separately, paste the common final still prompt first, then paste exactly one theme-specific source lock from the list below.

```text
Create one standalone final still image for a completed coffee bean bloom sprite.

This is not a new design. It must match the referenced R5C5 Frame 25 sprite exactly: same completed object, same already-open roasted coffee bean base, same silhouette, same scale, same front-facing orthographic view, same bottom-center anchor, same visible parts, same colors, and same final unresolved-detail resolution.

Draw exactly one completed sprite centered in one square 1:1 image. Use transparent background if supported; otherwise use a perfectly flat solid #00FFFF chroma-key background. Do not draw a grid, border, frame number, caption, label, watermark, UI, background scene, floor plane, shadow, glow spill, extra particle field, duplicate bean, duplicate stem, extra bloom, new petal, new leaf, new sparkle, new prop, or any new anatomy.

Keep the sprite bounds consistent with the spritesheet: lowest visible pixel on the same bottom-center baseline near y=88%, root anchor near x=50%, y=82%, final sprite inside x=18%-82% and y=10%-88%, with at least 12% empty padding on all sides. The finished still should be suitable for cropping or saving as a 256 x 256 RGBA app asset.

The only acceptable difference from the animation atlas is that this is a single final image instead of a 5x5 grid. It must look like the exact completed frame the user reached at the end of the bloom animation and can remain on screen for the rest of the brewing period.
```

### Punk Final Still Source Locks

#### Cyberpunk Final Still

Target file: `bloom_cyberpunk_final.png`

```text
Source lock: R5C5 Frame 25 from Cyberpunk - final sprite: same data-orchid lantern as frame 24; only the front sensor lens iris snaps into centered lit alignment, adding no new anatomy.
```

#### Frostpunk Final Still

Target file: `bloom_frostpunk_final.png`

```text
Source lock: R5C5 Frame 25 from Frostpunk - final sprite: same condenser as frame 24; only the single open clasp snaps shut flush, leaving all crown facets, collar, heat core, and riveted bands unchanged.
```

#### Dieselpunk Final Still

Target file: `bloom_dieselpunk_final.png`

```text
Source lock: R5C5 Frame 25 from Dieselpunk - final sprite: same supercharger as frame 24; only the lower-left intake fin unfolds outward to match the other fins, completing one compact dieselpunk turbine blossom on the same already-open bean base.
```

#### Weatherpunk Final Still

Target file: `bloom_weatherpunk_final.png`

```text
Source lock: R5C5 Frame 25 from Weatherpunk - final sprite: same storm-glass flower as frame 24; only the tiny top wind-vane leaf straightens, completing one compact weatherpunk storm-glass coffee flower on the same already-open bean base.
```

#### Gothic Punk Final Still

Target file: `bloom_gothic_punk_final.png`

```text
Source lock: R5C5 Frame 25 from Gothic Punk - final sprite: same rose-lantern as frame 24; only the central coffee-candle wick straightens into clean alignment, with no change to the iron arch, glass panels, or overall glow.
```

#### Aetherpunk Final Still

Target file: `bloom_aetherpunk_final.png`

```text
Source lock: R5C5 Frame 25 from Aetherpunk - final sprite: same aetherpunk bloom as frame 24; only the uppermost brass ring petal opens fully to match the others, completing the single raised aether still bloom.
```

#### Desertpunk Final Still

Target file: `bloom_desertpunk_final.png`

```text
Source lock: R5C5 Frame 25 from Desertpunk - final sprite: same condenser as frame 24; only the brass heat vent opens to its finished sun-baked slotted form while every other detail stays unchanged.
```

#### Oceanpunk Final Still

Target file: `bloom_oceanpunk_final.png`

```text
Source lock: R5C5 Frame 25 from Oceanpunk - final sprite: same oceanpunk bloom as frame 24; only the rightmost tendril lobe uncurls outward, completing one abyssal coral-jelly condenser bloom on the same already-open bean base.
```

#### Piratepunk Final Still

Target file: `bloom_piratepunk_final.png`

```text
Source lock: R5C5 Frame 25 from Piratepunk - final sprite: same compass-sail charm as frame 24; only the last sail-petal flattens into place, with no other changes.
```

#### Clockpunk Final Still

Target file: `bloom_clockpunk_final.png`

```text
Source lock: R5C5 Frame 25 from Clockpunk - final sprite: same clockpunk blossom as frame 24; only the tiny pendulum tongue settles straight beneath the ruby jewel pivot, completing one polished brass escapement orrery blossom on the same already-open bean base.
```

#### Bronzepunk Final Still

Target file: `bloom_bronzepunk_final.png`

```text
Source lock: R5C5 Frame 25 from Bronzepunk - final sprite: same amphora bloom as frame 24; only the right laurel fin uncurls to match the left, completing one bronzepunk patina laurel amphora bloom without adding new parts.
```

#### Nanopunk Final Still

Target file: `bloom_nanopunk_final.png`

```text
Source lock: R5C5 Frame 25 from Nanopunk - final sprite: same lattice bloom as frame 24; only the upper-right outer hex-cell plate facet-locks outward to complete the single crisp nanopunk lattice bloom.
```

#### Silkpunk Final Still

Target file: `bloom_silkpunk_final.png`

```text
Source lock: R5C5 Frame 25 from Silkpunk - final sprite: same lantern bloom as frame 24; only the loose red half-loop tightens into one neat red silk cord knot.
```

#### Solarpunk Final Still

Target file: `bloom_solarpunk_final.png`

```text
Source lock: R5C5 Frame 25 from Solarpunk - final sprite: same bloom as frame 24; only the last lower panel tip uncurls outward, completing one clean five-panel solarpunk photovoltaic leaf-disc bloom on the same already-open bean base.
```

#### Steampunk Final Still

Target file: `bloom_steampunk_final.png`

```text
Source lock: R5C5 Frame 25 from Steampunk - final sprite: same pressure-valve bloom as frame 24; only the lower-right copper pipe elbow unfolds into its final delicate curve, completing one steampunk pressure-valve coffee bloom on the same already-open bean base.
```

#### Ecopunk Final Still

Target file: `bloom_ecopunk_final.png`

```text
Source lock: R5C5 Frame 25 from Ecopunk - final sprite: same bloom as frame 24; only the rightmost outer plate edge unfolds outward, completing one mossy reclaimed mycelium circuit bloom with five salvaged plates, warm compost-coffee core, and the same already-open bean base.
```
