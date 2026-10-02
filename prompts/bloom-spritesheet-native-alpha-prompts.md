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

2026-10-01 visual-interest restoration: [final-cleanup-01.txt](bloom-interest-restoration-2026-10-01/coffee_starlit/final-cleanup-01.txt), [growth-01.txt](bloom-interest-restoration-2026-10-01/coffee_starlit/growth-01.txt), [loop-bank-01.txt](bloom-interest-restoration-2026-10-01/coffee_starlit/loop-bank-01.txt), [early-bank-01.txt](bloom-interest-restoration-2026-10-01/coffee_starlit/early-bank-01.txt), [late-bank-01.txt](bloom-interest-restoration-2026-10-01/coffee_starlit/late-bank-01.txt), [early-star-fix-01.txt](bloom-interest-restoration-2026-10-01/coffee_starlit/early-star-fix-01.txt), [early-bank-02.txt](bloom-interest-restoration-2026-10-01/coffee_starlit/early-bank-02.txt).
Retained source: [`bloom-interest-coffee_starlit-native-source.png`](../docs/assets/bloom-interest-coffee_starlit-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


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

<!-- berry-family-redesign-2026-10-01:strawberry:start -->
Richer original-finish follow-up ([review](../docs/reviews/berry-family-redesign-2026-10-01.md)):

- [initial.txt](berry-family-redesign-2026-10-01/strawberry/initial.txt)
- [late-bank.txt](berry-family-redesign-2026-10-01/strawberry/late-bank.txt)
<!-- berry-family-redesign-2026-10-01:strawberry:end -->


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

<!-- berry-family-redesign-2026-10-01:blueberry:start -->
Richer original-finish follow-up ([review](../docs/reviews/berry-family-redesign-2026-10-01.md)):

- [initial.txt](berry-family-redesign-2026-10-01/blueberry/initial.txt)
- [fruiting-bank.txt](berry-family-redesign-2026-10-01/blueberry/fruiting-bank.txt)
- [fruiting-bank-repair.txt](berry-family-redesign-2026-10-01/blueberry/fruiting-bank-repair.txt)
- [early-bank.txt](berry-family-redesign-2026-10-01/blueberry/early-bank.txt)
- [early-bank-repair.txt](berry-family-redesign-2026-10-01/blueberry/early-bank-repair.txt)
- [tone-repair.txt](berry-family-redesign-2026-10-01/blueberry/tone-repair.txt)
<!-- berry-family-redesign-2026-10-01:blueberry:end -->


- [blueberry-initial.txt](bloom-revision-2026-10-01/blueberry-initial.txt)
- [blueberry-retry1.txt](bloom-revision-2026-10-01/blueberry-retry1.txt)
- [blueberry.txt](bloom-revision-2026-10-01/blueberry.txt)

### Blackberry

<!-- berry-family-redesign-2026-10-01:blackberry:start -->
Richer original-finish follow-up ([review](../docs/reviews/berry-family-redesign-2026-10-01.md)):

- [initial.txt](berry-family-redesign-2026-10-01/blackberry/initial.txt)
- [repair-01.txt](berry-family-redesign-2026-10-01/blackberry/repair-01.txt)
- [early-bridge.txt](berry-family-redesign-2026-10-01/blackberry/early-bridge.txt)
- [late-ripening.txt](berry-family-redesign-2026-10-01/blackberry/late-ripening.txt)
<!-- berry-family-redesign-2026-10-01:blackberry:end -->


- [blackberry-initial.txt](bloom-revision-2026-10-01/blackberry-initial.txt)
- [blackberry-retry1.txt](bloom-revision-2026-10-01/blackberry-retry1.txt)
- [blackberry.txt](bloom-revision-2026-10-01/blackberry.txt)

### Coffee brew

2026-10-01 visual-interest restoration: [final-01.txt](bloom-interest-restoration-2026-10-01/coffee_brew/final-01.txt), [growth-01.txt](bloom-interest-restoration-2026-10-01/coffee_brew/growth-01.txt), [coil-bank-01.txt](bloom-interest-restoration-2026-10-01/coffee_brew/coil-bank-01.txt).
Retained source: [`bloom-interest-coffee_brew-native-source.png`](../docs/assets/bloom-interest-coffee_brew-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


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

2026-10-01 visual-interest restoration: [final-design.txt](bloom-interest-restoration-2026-10-01/bleeding_heart/final-design.txt), [flowering-bank.txt](bloom-interest-restoration-2026-10-01/bleeding_heart/flowering-bank.txt), [early-bank.txt](bloom-interest-restoration-2026-10-01/bleeding_heart/early-bank.txt), [continuity-repair.txt](bloom-interest-restoration-2026-10-01/bleeding_heart/continuity-repair.txt), [f20-repair.txt](bloom-interest-restoration-2026-10-01/bleeding_heart/f20-repair.txt), [join-repair.txt](bloom-interest-restoration-2026-10-01/bleeding_heart/join-repair.txt).
Retained source: [`bloom-interest-bleeding_heart-native-source.png`](../docs/assets/bloom-interest-bleeding_heart-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


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

2026-10-01 visual-interest restoration: [final-target.txt](bloom-interest-restoration-2026-10-01/jade_vine/final-target.txt), [growth-01.txt](bloom-interest-restoration-2026-10-01/jade_vine/growth-01.txt), [pendant-bank.txt](bloom-interest-restoration-2026-10-01/jade_vine/pendant-bank.txt), [early-bridge.txt](bloom-interest-restoration-2026-10-01/jade_vine/early-bridge.txt), [early-bridge-repair.txt](bloom-interest-restoration-2026-10-01/jade_vine/early-bridge-repair.txt).
Retained source: [`bloom-interest-jade_vine-native-source.png`](../docs/assets/bloom-interest-jade_vine-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


- [jade_vine.txt](bloom-revision-2026-10-01/jade_vine.txt)
- [jade_vine_repair.txt](bloom-revision-2026-10-01/jade_vine_repair.txt)

### Bird of paradise

- [bird_of_paradise.txt](bloom-revision-2026-10-01/bird_of_paradise.txt)
- [bird_of_paradise_from_final.txt](bloom-revision-2026-10-01/bird_of_paradise_from_final.txt)

### Blue Himalayan poppy

2026-10-01 visual-interest restoration: [final-01.txt](bloom-interest-restoration-2026-10-01/blue_himalayan_poppy/final-01.txt), [final-02.txt](bloom-interest-restoration-2026-10-01/blue_himalayan_poppy/final-02.txt), [growth-01.txt](bloom-interest-restoration-2026-10-01/blue_himalayan_poppy/growth-01.txt), [growth-02.txt](bloom-interest-restoration-2026-10-01/blue_himalayan_poppy/growth-02.txt), [growth-03.txt](bloom-interest-restoration-2026-10-01/blue_himalayan_poppy/growth-03.txt), [late-01.txt](bloom-interest-restoration-2026-10-01/blue_himalayan_poppy/late-01.txt).
Retained source: [`bloom-interest-blue_himalayan_poppy-native-source.png`](../docs/assets/bloom-interest-blue_himalayan_poppy-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


- [blue_himalayan_poppy.txt](bloom-revision-2026-10-01/blue_himalayan_poppy.txt)
- [blue_himalayan_poppy_from_final.txt](bloom-revision-2026-10-01/blue_himalayan_poppy_from_final.txt)

### Rafflesia

- [rafflesia.txt](bloom-revision-2026-10-01/rafflesia.txt)
- [rafflesia_from_final.txt](bloom-revision-2026-10-01/rafflesia_from_final.txt)

### Chocolate cosmos

2026-10-01 visual-interest restoration: [reverse-bank.txt](bloom-interest-restoration-2026-10-01/chocolate_cosmos/reverse-bank.txt), [early-bank.txt](bloom-interest-restoration-2026-10-01/chocolate_cosmos/early-bank.txt), [early-refinement.txt](bloom-interest-restoration-2026-10-01/chocolate_cosmos/early-refinement.txt).
Retained source: [`bloom-interest-chocolate_cosmos-native-source.png`](../docs/assets/bloom-interest-chocolate_cosmos-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


- [chocolate_cosmos.txt](bloom-revision-2026-10-01/chocolate_cosmos.txt)
- [chocolate_cosmos_repair.txt](bloom-revision-2026-10-01/chocolate_cosmos_repair.txt)

### Flame lily

- [flame_lily.txt](bloom-revision-2026-10-01/flame_lily.txt)
- [flame_lily_from_final.txt](bloom-revision-2026-10-01/flame_lily_from_final.txt)

### Queen of the Night

2026-10-01 visual-interest restoration: [final-target.txt](bloom-interest-restoration-2026-10-01/queen_of_the_night/final-target.txt), [final-target-reframe.txt](bloom-interest-restoration-2026-10-01/queen_of_the_night/final-target-reframe.txt), [growth-01.txt](bloom-interest-restoration-2026-10-01/queen_of_the_night/growth-01.txt), [early-bank.txt](bloom-interest-restoration-2026-10-01/queen_of_the_night/early-bank.txt), [opening-bank.txt](bloom-interest-restoration-2026-10-01/queen_of_the_night/opening-bank.txt), [late-refinement.txt](bloom-interest-restoration-2026-10-01/queen_of_the_night/late-refinement.txt).
Retained source: [`bloom-interest-queen_of_the_night-native-source.png`](../docs/assets/bloom-interest-queen_of_the_night-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


- [queen_of_the_night.txt](bloom-revision-2026-10-01/queen_of_the_night.txt)
- [queen_of_the_night_continuity.txt](bloom-revision-2026-10-01/queen_of_the_night_continuity.txt)
- [queen_of_the_night_from_final.txt](bloom-revision-2026-10-01/queen_of_the_night_from_final.txt)

### Snowdrop

2026-10-01 visual-interest restoration: [final-design.txt](bloom-interest-restoration-2026-10-01/snowdrop/final-design.txt), [flowering-bank.txt](bloom-interest-restoration-2026-10-01/snowdrop/flowering-bank.txt), [early-bank.txt](bloom-interest-restoration-2026-10-01/snowdrop/early-bank.txt), [late-four-bank.txt](bloom-interest-restoration-2026-10-01/snowdrop/late-four-bank.txt).
Retained source: [`bloom-interest-snowdrop-native-source.png`](../docs/assets/bloom-interest-snowdrop-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


- [snowdrop.txt](bloom-revision-2026-10-01/snowdrop.txt)
- [snowdrop_continuity.txt](bloom-revision-2026-10-01/snowdrop_continuity.txt)
- [snowdrop_from_final.txt](bloom-revision-2026-10-01/snowdrop_from_final.txt)

### Myosotis sylvatica

2026-10-01 visual-interest restoration: [final-design.txt](bloom-interest-restoration-2026-10-01/myosotis_sylvatica/final-design.txt), [flowering-bank.txt](bloom-interest-restoration-2026-10-01/myosotis_sylvatica/flowering-bank.txt), [late-bank-repair.txt](bloom-interest-restoration-2026-10-01/myosotis_sylvatica/late-bank-repair.txt), [final-four-bank.txt](bloom-interest-restoration-2026-10-01/myosotis_sylvatica/final-four-bank.txt), [early-bank.txt](bloom-interest-restoration-2026-10-01/myosotis_sylvatica/early-bank.txt), [middle-four-bank.txt](bloom-interest-restoration-2026-10-01/myosotis_sylvatica/middle-four-bank.txt), [first-four-bank.txt](bloom-interest-restoration-2026-10-01/myosotis_sylvatica/first-four-bank.txt), [second-four-bank.txt](bloom-interest-restoration-2026-10-01/myosotis_sylvatica/second-four-bank.txt), [second-four-bud-repair.txt](bloom-interest-restoration-2026-10-01/myosotis_sylvatica/second-four-bud-repair.txt), [early-bridge.txt](bloom-interest-restoration-2026-10-01/myosotis_sylvatica/early-bridge.txt).
Retained source: [`bloom-interest-myosotis_sylvatica-native-source.png`](../docs/assets/bloom-interest-myosotis_sylvatica-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


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

2026-10-01 visual-interest restoration: [reverse-bank.txt](bloom-interest-restoration-2026-10-01/dahlia_kaleidoscope/reverse-bank.txt), [early-bank.txt](bloom-interest-restoration-2026-10-01/dahlia_kaleidoscope/early-bank.txt).
Retained source: [`bloom-interest-dahlia_kaleidoscope-native-source.png`](../docs/assets/bloom-interest-dahlia_kaleidoscope-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


- [dahlia_kaleidoscope-retry1.txt](bloom-revision-2026-10-01/dahlia_kaleidoscope-retry1.txt)
- [dahlia_kaleidoscope.txt](bloom-revision-2026-10-01/dahlia_kaleidoscope.txt)

### Pincushion firework

2026-10-01 visual-interest restoration: [early-bank.txt](bloom-interest-restoration-2026-10-01/pincushion_firework/early-bank.txt), [opening-bank.txt](bloom-interest-restoration-2026-10-01/pincushion_firework/opening-bank.txt), [reverse-bank.txt](bloom-interest-restoration-2026-10-01/pincushion_firework/reverse-bank.txt), [early-compact-bank.txt](bloom-interest-restoration-2026-10-01/pincushion_firework/early-compact-bank.txt).
Retained source: [`bloom-interest-pincushion_firework-native-source.png`](../docs/assets/bloom-interest-pincushion_firework-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


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

2026-10-01 visual-interest restoration: [final-01.txt](bloom-interest-restoration-2026-10-01/crema_chrysanthemum/final-01.txt), [growth-01.txt](bloom-interest-restoration-2026-10-01/crema_chrysanthemum/growth-01.txt), [opening-bank-01.txt](bloom-interest-restoration-2026-10-01/crema_chrysanthemum/opening-bank-01.txt), [late-bank-01.txt](bloom-interest-restoration-2026-10-01/crema_chrysanthemum/late-bank-01.txt), [late-bank-02.txt](bloom-interest-restoration-2026-10-01/crema_chrysanthemum/late-bank-02.txt), [opening-bank-02.txt](bloom-interest-restoration-2026-10-01/crema_chrysanthemum/opening-bank-02.txt).
Retained source: [`bloom-interest-crema_chrysanthemum-native-source.png`](../docs/assets/bloom-interest-crema_chrysanthemum-native-source.png). Built-in image generation; separate Astra endpoint and sequence reviews are recorded in the [restoration manifest](../docs/reviews/bloom-interest-restoration-2026-10-01.json).


- [crema_chrysanthemum.txt](bloom-revision-2026-10-01/crema_chrysanthemum.txt)

### Stained-glass iris

- [stained_glass_iris-retry1.txt](bloom-revision-2026-10-01/stained_glass_iris-retry1.txt)
- [stained_glass_iris.txt](bloom-revision-2026-10-01/stained_glass_iris.txt)

### Aurora anemone

- [aurora_anemone-retry1.txt](bloom-revision-2026-10-01/aurora_anemone-retry1.txt)
- [aurora_anemone.txt](bloom-revision-2026-10-01/aurora_anemone.txt)

### Constellation blossom

- [constellation_blossom.txt](bloom-revision-2026-10-01/constellation_blossom.txt)
