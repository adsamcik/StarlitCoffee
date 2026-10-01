# Native-alpha Bloom Prompts

Use the built-in image generator with a transparent background. Each animation
is a 25-frame, 5 × 5 atlas. The prepared app asset is 1280 × 1280 RGBA. Keep the
existing variant's final-flower identity, palette, fixed bean base and front view.

## Common direction

```text
Create one square transparent PNG spritesheet with exactly five columns and
five rows, containing 25 complete whole-object poses in row-major order.
No grid lines, labels, borders, background paint, shadows, gutters or scenery.
Give every object generous empty padding and keep it fully inside its cell.
Lock the camera, light, bean identity, bean size, base and attachment points.
Grow by extending, unfolding, opening and revealing parts, not by camera zoom.
Every introduced leaf, petal, branch, fruit, bell or decorative structure persists
through later poses. State the complete existing anatomy for each frame where
counts or attachment points might otherwise change.
Each adjacent pose makes one readable named advance. The middle of the sequence
must visibly develop the object. Start the recognizable opening before the last
row; use the final row to expand and finish progressively, then settle.
Do not reclose an open flower, reverse ripening, rotate the subject, hide a part,
reach the finished pose early, or fill the final row with duplicate poses.
Keep colored petal surfaces intact and readable on cream and dark backgrounds.
Use true alpha; do not chroma-key cyan or magenta from the artwork.
```

Use a cropped final sprite as the style reference when an old full atlas biases
the result toward its old chronology. Native output is reviewed rather than
assumed to obey the prompt. Save repair prompts and the source/frame provenance
of any whole-pose curation. The preparation tool changes geometry and matte
noise only; it does not supply new anatomy or intermediate drawings.

The per-variant generation and repair prompts from the October 1 revision follow.

## Per-variant prompt archive

These are the exact saved native generation and edit prompts. The revision manifest records accepted source selection and whole-pose provenance; a first attempt is not automatically the selected artwork.


### Coffee flower

- [coffee_flower.txt](bloom-revision-2026-10-01/coffee_flower.txt)

### Starlit coffee

- [coffee_starlit-initial.txt](bloom-revision-2026-10-01/coffee_starlit-initial.txt)
- [coffee_starlit-retry1.txt](bloom-revision-2026-10-01/coffee_starlit-retry1.txt)
- [coffee_starlit-retry2.txt](bloom-revision-2026-10-01/coffee_starlit-retry2.txt)
- [coffee_starlit.txt](bloom-revision-2026-10-01/coffee_starlit.txt)

### Latte bloom

The current [latte-art heart redesign](../docs/reviews/latte-heart-redesign-2026-10-01.md)
supersedes the rosetta artwork below. Its exact prompts are
[initial generation](bloom-latte-heart-2026-10-01/initial.txt),
[first repair](bloom-latte-heart-2026-10-01/repair-01.txt), and
[selected regeneration](bloom-latte-heart-2026-10-01/regenerate-02.txt).

- [coffee_latte-discarded-edge-repair.txt](bloom-revision-2026-10-01/coffee_latte-discarded-edge-repair.txt)
- [coffee_latte-initial.txt](bloom-revision-2026-10-01/coffee_latte-initial.txt)
- [coffee_latte-retry1.txt](bloom-revision-2026-10-01/coffee_latte-retry1.txt)
- [coffee_latte.txt](bloom-revision-2026-10-01/coffee_latte.txt)

### Coffee plant

The current [coffee bush redesign](../docs/reviews/coffee-plant-redesign-2026-10-01.md)
supersedes the seedling artwork below. Its exact prompt set is
[initial generation](coffee-plant-redesign-2026-10-01/initial.txt),
[regeneration](coffee-plant-redesign-2026-10-01/regenerate-01.txt),
[square atlas repair](coffee-plant-redesign-2026-10-01/repair-02.txt),
[unsuccessful atlas fruit repair](coffee-plant-redesign-2026-10-01/repair-03.txt),
and [selected isolated pose repair](coffee-plant-redesign-2026-10-01/frame12-repair.txt).

- [coffee_plant-initial.txt](bloom-revision-2026-10-01/coffee_plant-initial.txt)
- [coffee_plant-retry1.txt](bloom-revision-2026-10-01/coffee_plant-retry1.txt)
- [coffee_plant.txt](bloom-revision-2026-10-01/coffee_plant.txt)

### Cherry tomato

The current [tomato vine reconstruction](../docs/reviews/tomato-redesign-2026-10-01.md)
works backward from the richer original finish. Its exact prompt set is
[initial generation](tomato-redesign-2026-10-01/initial.txt),
[atlas repair](tomato-redesign-2026-10-01/repair-01.txt),
and [selected two-pose leaf repair](tomato-redesign-2026-10-01/leaf-repair.txt).

- [cherry_tomato-initial.txt](bloom-revision-2026-10-01/cherry_tomato-initial.txt)
- [cherry_tomato-retry1.txt](bloom-revision-2026-10-01/cherry_tomato-retry1.txt)
- [cherry_tomato-retry2.txt](bloom-revision-2026-10-01/cherry_tomato-retry2.txt)
- [cherry_tomato-retry3.txt](bloom-revision-2026-10-01/cherry_tomato-retry3.txt)
- [cherry_tomato.txt](bloom-revision-2026-10-01/cherry_tomato.txt)

### Strawberry

- [strawberry-initial.txt](bloom-revision-2026-10-01/strawberry-initial.txt)
- [strawberry-retry1.txt](bloom-revision-2026-10-01/strawberry-retry1.txt)
- [strawberry.txt](bloom-revision-2026-10-01/strawberry.txt)

### Raspberry

<!-- raspberry-redesign-2026-10-01:start -->
Richer original-finish follow-up ([review](../docs/reviews/raspberry-redesign-2026-10-01.md)):

- [flower-repair.txt](raspberry-redesign-2026-10-01/flower-repair.txt)
- [fruiting-bank.txt](raspberry-redesign-2026-10-01/fruiting-bank.txt)
- [growth-bridge.txt](raspberry-redesign-2026-10-01/growth-bridge.txt)
- [initial.txt](raspberry-redesign-2026-10-01/initial.txt)
- [repair-01.txt](raspberry-redesign-2026-10-01/repair-01.txt)

Earlier 44-animation checkpoint prompts remain below.
<!-- raspberry-redesign-2026-10-01:end -->

- [raspberry-initial.txt](bloom-revision-2026-10-01/raspberry-initial.txt)
- [raspberry-retry1.txt](bloom-revision-2026-10-01/raspberry-retry1.txt)
- [raspberry.txt](bloom-revision-2026-10-01/raspberry.txt)

### Blueberry

- [blueberry-initial.txt](bloom-revision-2026-10-01/blueberry-initial.txt)
- [blueberry-retry1.txt](bloom-revision-2026-10-01/blueberry-retry1.txt)
- [blueberry.txt](bloom-revision-2026-10-01/blueberry.txt)

### Blackberry

- [blackberry-initial.txt](bloom-revision-2026-10-01/blackberry-initial.txt)
- [blackberry-retry1.txt](bloom-revision-2026-10-01/blackberry-retry1.txt)
- [blackberry.txt](bloom-revision-2026-10-01/blackberry.txt)

### Coffee brew

- [coffee_brew-initial.txt](bloom-revision-2026-10-01/coffee_brew-initial.txt)
- [coffee_brew-retry1.txt](bloom-revision-2026-10-01/coffee_brew-retry1.txt)
- [coffee_brew.txt](bloom-revision-2026-10-01/coffee_brew.txt)

### Rose

- [rose-initial.txt](bloom-revision-2026-10-01/rose-initial.txt)
- [rose-retry1.txt](bloom-revision-2026-10-01/rose-retry1.txt)
- [rose.txt](bloom-revision-2026-10-01/rose.txt)

### Lotus

- [lotus-initial.txt](bloom-revision-2026-10-01/lotus-initial.txt)
- [lotus-retry1.txt](bloom-revision-2026-10-01/lotus-retry1.txt)
- [lotus.txt](bloom-revision-2026-10-01/lotus.txt)

### Sunflower

- [sunflower-initial.txt](bloom-revision-2026-10-01/sunflower-initial.txt)
- [sunflower-retry1.txt](bloom-revision-2026-10-01/sunflower-retry1.txt)
- [sunflower.txt](bloom-revision-2026-10-01/sunflower.txt)

### Orchid

- [orchid-initial.txt](bloom-revision-2026-10-01/orchid-initial.txt)
- [orchid-retry1.txt](bloom-revision-2026-10-01/orchid-retry1.txt)
- [orchid-retry2.txt](bloom-revision-2026-10-01/orchid-retry2.txt)
- [orchid.txt](bloom-revision-2026-10-01/orchid.txt)

### Jasmine

- [jasmine-initial.txt](bloom-revision-2026-10-01/jasmine-initial.txt)
- [jasmine-retry1.txt](bloom-revision-2026-10-01/jasmine-retry1.txt)
- [jasmine.txt](bloom-revision-2026-10-01/jasmine.txt)

### Bleeding heart

- [bleeding_heart.txt](bloom-revision-2026-10-01/bleeding_heart.txt)
- [bleeding_heart_continuity.txt](bloom-revision-2026-10-01/bleeding_heart_continuity.txt)
- [bleeding_heart_count_repair.txt](bloom-revision-2026-10-01/bleeding_heart_count_repair.txt)
- [bleeding_heart_from_final.txt](bloom-revision-2026-10-01/bleeding_heart_from_final.txt)
- [bleeding_heart_middle_repair.txt](bloom-revision-2026-10-01/bleeding_heart_middle_repair.txt)
- [bleeding_heart_remove_left.txt](bloom-revision-2026-10-01/bleeding_heart_remove_left.txt)
- [bleeding_heart_spacing.txt](bloom-revision-2026-10-01/bleeding_heart_spacing.txt)

### Passionflower

- [passionflower.txt](bloom-revision-2026-10-01/passionflower.txt)

### Bee orchid

- [bee_orchid.txt](bloom-revision-2026-10-01/bee_orchid.txt)
- [bee_orchid_from_final.txt](bloom-revision-2026-10-01/bee_orchid_from_final.txt)

### Jade vine

- [jade_vine.txt](bloom-revision-2026-10-01/jade_vine.txt)
- [jade_vine_repair.txt](bloom-revision-2026-10-01/jade_vine_repair.txt)

### Bird of paradise

- [bird_of_paradise.txt](bloom-revision-2026-10-01/bird_of_paradise.txt)
- [bird_of_paradise_from_final.txt](bloom-revision-2026-10-01/bird_of_paradise_from_final.txt)

### Blue Himalayan poppy

- [blue_himalayan_poppy.txt](bloom-revision-2026-10-01/blue_himalayan_poppy.txt)
- [blue_himalayan_poppy_from_final.txt](bloom-revision-2026-10-01/blue_himalayan_poppy_from_final.txt)

### Rafflesia

- [rafflesia.txt](bloom-revision-2026-10-01/rafflesia.txt)
- [rafflesia_from_final.txt](bloom-revision-2026-10-01/rafflesia_from_final.txt)

### Chocolate cosmos

- [chocolate_cosmos.txt](bloom-revision-2026-10-01/chocolate_cosmos.txt)
- [chocolate_cosmos_repair.txt](bloom-revision-2026-10-01/chocolate_cosmos_repair.txt)

### Flame lily

- [flame_lily.txt](bloom-revision-2026-10-01/flame_lily.txt)
- [flame_lily_from_final.txt](bloom-revision-2026-10-01/flame_lily_from_final.txt)

### Queen of the Night

- [queen_of_the_night.txt](bloom-revision-2026-10-01/queen_of_the_night.txt)
- [queen_of_the_night_continuity.txt](bloom-revision-2026-10-01/queen_of_the_night_continuity.txt)
- [queen_of_the_night_from_final.txt](bloom-revision-2026-10-01/queen_of_the_night_from_final.txt)

### Snowdrop

- [snowdrop.txt](bloom-revision-2026-10-01/snowdrop.txt)
- [snowdrop_continuity.txt](bloom-revision-2026-10-01/snowdrop_continuity.txt)
- [snowdrop_from_final.txt](bloom-revision-2026-10-01/snowdrop_from_final.txt)

### Myosotis sylvatica

- [myosotis_sylvatica.txt](bloom-revision-2026-10-01/myosotis_sylvatica.txt)
- [myosotis_sylvatica_continuity.txt](bloom-revision-2026-10-01/myosotis_sylvatica_continuity.txt)
- [myosotis_sylvatica_count_repair.txt](bloom-revision-2026-10-01/myosotis_sylvatica_count_repair.txt)
- [myosotis_sylvatica_from_final.txt](bloom-revision-2026-10-01/myosotis_sylvatica_from_final.txt)

### Fuchsia ballerina

- [fuchsia_ballerina.txt](bloom-revision-2026-10-01/fuchsia_ballerina.txt)

### Magnolia dawn

- [magnolia_dawn.txt](bloom-revision-2026-10-01/magnolia_dawn.txt)
- [magnolia_dawn_cleanup.txt](bloom-revision-2026-10-01/magnolia_dawn_cleanup.txt)

### Iris origami

- [iris_origami.txt](bloom-revision-2026-10-01/iris_origami.txt)

### King protea sunrise

- [king_protea_sunrise-retry1.txt](bloom-revision-2026-10-01/king_protea_sunrise-retry1.txt)
- [king_protea_sunrise.txt](bloom-revision-2026-10-01/king_protea_sunrise.txt)

### Dahlia kaleidoscope

- [dahlia_kaleidoscope-retry1.txt](bloom-revision-2026-10-01/dahlia_kaleidoscope-retry1.txt)
- [dahlia_kaleidoscope.txt](bloom-revision-2026-10-01/dahlia_kaleidoscope.txt)

### Pincushion firework

- [pincushion_firework-retry1.txt](bloom-revision-2026-10-01/pincushion_firework-retry1.txt)
- [pincushion_firework.txt](bloom-revision-2026-10-01/pincushion_firework.txt)

### Morning glory spiral

- [morning_glory_spiral-retry1.txt](bloom-revision-2026-10-01/morning_glory_spiral-retry1.txt)
- [morning_glory_spiral.txt](bloom-revision-2026-10-01/morning_glory_spiral.txt)

### Foxglove chimes

- [foxglove_chimes-retry1.txt](bloom-revision-2026-10-01/foxglove_chimes-retry1.txt)
- [foxglove_chimes.txt](bloom-revision-2026-10-01/foxglove_chimes.txt)

### Cherry blossom wish

- [cherry_blossom_wish.txt](bloom-revision-2026-10-01/cherry_blossom_wish.txt)

### Himalayan lantern

- [himalayan_lantern.txt](bloom-revision-2026-10-01/himalayan_lantern.txt)

### Black bat flower

- [black_bat_flower.txt](bloom-revision-2026-10-01/black_bat_flower.txt)

### Water lily at twilight

- [water_lily_twilight.txt](bloom-revision-2026-10-01/water_lily_twilight.txt)

### Moonphase magnolia

- [moonphase_magnolia-retry1.txt](bloom-revision-2026-10-01/moonphase_magnolia-retry1.txt)
- [moonphase_magnolia.txt](bloom-revision-2026-10-01/moonphase_magnolia.txt)

### Crema chrysanthemum

- [crema_chrysanthemum.txt](bloom-revision-2026-10-01/crema_chrysanthemum.txt)

### Stained-glass iris

- [stained_glass_iris-retry1.txt](bloom-revision-2026-10-01/stained_glass_iris-retry1.txt)
- [stained_glass_iris.txt](bloom-revision-2026-10-01/stained_glass_iris.txt)

### Aurora anemone

- [aurora_anemone-retry1.txt](bloom-revision-2026-10-01/aurora_anemone-retry1.txt)
- [aurora_anemone.txt](bloom-revision-2026-10-01/aurora_anemone.txt)

### Constellation blossom

- [constellation_blossom.txt](bloom-revision-2026-10-01/constellation_blossom.txt)
