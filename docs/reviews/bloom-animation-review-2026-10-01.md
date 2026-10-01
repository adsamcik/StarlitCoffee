# Bloom animation review

Review date: 1 October 2026. Reviewers: three GPT-6 Astra agents, with source and renderer checks by the coordinating agent.

All 44 animations received individual visual feedback. The largest opportunity is coherent growth: preserve the same plant and its parts, replace weak middle poses with readable unfolding, and let completion finish a development the viewer has already followed. Several assets need missing or incorrectly sliced content repaired before aesthetic polish. Strong final concepts and palettes should be retained.

The review assigns 12 animations to sequence or damaged-content reconstruction, 31 to targeted refinement, and one, Sunflower, to light polish. These are review judgments about the current 25-frame sequence, not a recommendation to discard all final artwork.

## Evidence and limits

Each reviewer inspected every assigned original 1280 by 1280 PNG and both derived 148 pixel light and dark contact sheets. Together these cover all 1100 source frames and 88 theme contact sheets. Frame numbers run left to right, then top to bottom, from F1 to F25.

The derived previews approximate the current anchor translations and two silhouette halo passes. They use the custom Starlit Coffee palettes and Pillow bicubic filtering. Actual Compose filtering, device density, system dynamic colors, clipping by parent layouts and timing were not observed. The browser viewer provides uniform-progress comparison; it does not reproduce timer callback lag, pause behavior or the final-second snap. Temporal descriptions such as a stalled middle are inferred from the ordered drawings and current frame selection.

The existing grid check passes for all 44 sheets. There are no separate final still resources. All 1056 adjacent RGBA pairs differ at the pixel level; references to near-holds or redundant frames describe perceptual similarity, not literal copies. Correct dimensions and different pixels do not establish meaningful growth or intact frame content.

Current source is recorded by SHA256 in the companion JSON. No application code, shipped artwork, generation prompts or settings were changed by this review.

## Recommended priorities

1. Repair visibly broken frame content first. Morning glory spiral and Magnolia dawn contain duplicate or split plants and horizontal missing bands. Fuchsia ballerina, Iris origami, King protea sunrise, Water lily at twilight and Stained-glass iris have extensive missing petal or bract surfaces. More local surface defects also appear in Foxglove, Cherry blossom, Aurora anemone and Constellation blossom. Inspect pre-processing sources and alpha masks before deciding whether regeneration is needed; this review establishes the visible defect, not which pipeline stage caused it.
2. Establish persistent anatomy. Bean size, stem attachment, leaf junctions, petal identity and fruit nodes should survive adjacent frames. Fix reversals such as Latte bloom F10 to F11, Cherry tomato F20 to F21, and disappearing or replaced mature flowers in Chocolate cosmos and Snowdrop.
3. Redistribute useful growth. Some animations reach their final structure too early; others hold a bud until almost the last row. Use the spare drawings to show identifiable separation, rotation and reveal of existing parts. Avoid forcing every variant into the same late-opening schedule.
4. Tune small-size clarity and the finish after continuity is sound. Use broader readable surfaces, negative space and restrained highlights. Additional particles, glow, texture or settings would not resolve the observed problems.

## What makes a frame valuable

A useful frame either advances a readable structure, prepares a coming change through visible tension, or completes a motion already underway. It should answer a concrete visual question: has the seam opened, has the leaf uncurled, which petal is moving, or which berry is ripening? A held or very small change can support anticipation, but several full progress intervals spent repainting the same bud weaken the sense of growth.

Keep one persistent scaffold and a fixed authored base. Compare adjacent drawings at 132, 148 and 172 pixels, where the app places the animation. These pixel previews are useful comparisons, while native validation must use those sizes in dp on the target device. Inspect row-boundary transitions F5 to F6, F10 to F11, F15 to F16 and F20 to F21 exactly like every other adjacent pair.

As a proposed authoring direction, use early frames for bean opening and attached growth, middle frames for variant-specific development, and late frames for the remaining unfolding and a restrained finish. The individual milestone suggestions below are proposals, not changes already applied. They intentionally allow visible petal release before F21. This would require revising the current common prompt contract that insists on a compact pre-bloom pose at F20 and first recognizable final silhouette at F23. Keep full completion reserved for the finish without withholding all rewarding development until then.

## Shared renderer findings

`BloomSpritesheetAnimation.kt` animates a progress value for one second, then chooses a whole drawing with `round(progress * 24)`. This smooths when frame boundaries are reached; it does not interpolate petal geometry or blend the drawings. Internal drawings therefore last roughly duration divided by 24: 1.25 seconds at a 30 second bloom, 1.875 seconds at 45 seconds, and 2.5 seconds at 60 seconds. The first and last bins and countdown-driven snap behavior differ. Weak anatomy transitions will still switch abruptly, and nearly unchanged drawings can remain visible for a long interval.

The animation uses a bottom-band alpha-weighted anchor, translating toward the sequence median with corrections limited to 18 source pixels per axis. Changing bean shape or low leaves can move this estimate; measured anchor ranges include genuine anatomy changes and are not pure camera drift. Translation cannot repair resizing or replaced parts. Check safe margins after the correction and halo. The derived previews suggest top-detail loss in several sheets, which needs native confirmation before changing the renderer.

The settings final preview draws F25 with zero correction, while the animation applies its computed correction. This creates a source-level risk of different registration between the preview and the played finish. It should be checked at native size alongside the artwork work. The countdown reaching zero also switches to `snap()`, so any revised ending needs replay at actual durations, including pause and resume. Neither observation justifies adding a user-facing control.

## Feedback on each animation

Priority meanings: **Rebuild** means reconstruct substantial sequence or damaged frame content while preserving recoverable artwork; **Targeted** means retain the design and most good content while repairing named transitions or surfaces; **Polish** means minor refinement.

| Animation | Priority | Main focus |
| --- | --- | --- |
| [Coffee flower](#coffee-flower) | Targeted | Bridge the abrupt final petal layout change. |
| [Starlit coffee](#starlit-coffee) | Rebuild | Grow persistent loops and stars through the late frames. |
| [Latte bloom](#latte-bloom) | Rebuild | Repair the steam reset and finished-rosetta plateau. |
| [Coffee plant](#coffee-plant) | Targeted | Keep leaf size and the growing tip continuous. |
| [Cherry tomato](#cherry-tomato) | Targeted | Prevent reverse ripening and lock fruit nodes. |
| [Strawberry](#strawberry) | Targeted | Keep each flower and berry at the same node. |
| [Raspberry](#raspberry) | Targeted | Preserve the cane and final berry arrangement. |
| [Blueberry](#blueberry) | Targeted | Grow fixed clusters before staggered ripening. |
| [Blackberry](#blackberry) | Targeted | Track berries and clarify their dark silhouettes. |
| [Coffee brew](#coffee-brew) | Rebuild | Build the steam crown throughout the sequence. |
| [Rose](#rose) | Targeted | Spread the opening across more middle frames. |
| [Lotus](#lotus) | Targeted | Unfold persistent petal tiers without a late jump. |
| [Sunflower](#sunflower) | Polish | Smooth disk reveal and final ray extension. |
| [Orchid](#orchid) | Targeted | Track the final side petals and descending lip. |
| [Jasmine](#jasmine) | Targeted | Track five petals and check stray pixels. |
| [Bleeding heart](#bleeding-heart) | Targeted | Keep foliage and raceme structure continuous. |
| [Passionflower](#passionflower) | Targeted | Reveal the corona and center over more frames. |
| [Bee orchid](#bee-orchid) | Targeted | Release petals and patterned lip in stages. |
| [Jade vine](#jade-vine) | Rebuild | Repair cropping and open a stable raceme. |
| [Bird of paradise](#bird-of-paradise) | Targeted | Open the sheath and lift segments gradually. |
| [Blue Himalayan poppy](#blue-himalayan-poppy) | Targeted | Smooth neck bending and petal spread. |
| [Rafflesia](#rafflesia) | Targeted | Show dome seams separating before lobes open. |
| [Chocolate cosmos](#chocolate-cosmos) | Rebuild | Keep already-open flowers on persistent stems. |
| [Flame lily](#flame-lily) | Targeted | Separate tips and fold petals backward in stages. |
| [Queen of the Night](#queen-of-the-night) | Targeted | Keep the cactus and late bud layout stable. |
| [Snowdrop](#snowdrop) | Targeted | Open fixed stalks without replacing flowers. |
| [Myosotis sylvatica](#myosotis-sylvatica) | Targeted | Track bud endpoints and simplify the small flowers. |
| [Fuchsia ballerina](#fuchsia-ballerina) | Rebuild | Repair missing petal surfaces before motion. |
| [Magnolia dawn](#magnolia-dawn) | Rebuild | Repair duplicate plants and missing horizontal bands. |
| [Iris origami](#iris-origami) | Rebuild | Repair petal surfaces and stage the unfolding. |
| [King protea sunrise](#king-protea-sunrise) | Rebuild | Repair bract surfaces and stabilize the cream crown. |
| [Dahlia kaleidoscope](#dahlia-kaleidoscope) | Targeted | Unfold tracked rings and reveal the center gradually. |
| [Pincushion firework](#pincushion-firework) | Targeted | Release persistent filaments and stabilize the center. |
| [Morning glory spiral](#morning-glory-spiral) | Rebuild | Repair duplicate plants and sliced frame content. |
| [Foxglove chimes](#foxglove-chimes) | Targeted | Keep opened bells and intact petal walls. |
| [Cherry blossom wish](#cherry-blossom-wish) | Targeted | Repair petals and stagger continuous bud openings. |
| [Himalayan lantern](#himalayan-lantern) | Targeted | Open persistent shell seams without late repainting. |
| [Black bat flower](#black-bat-flower) | Targeted | Stage bracts and whiskers; clarify dark planes. |
| [Water lily at twilight](#water-lily-at-twilight) | Rebuild | Repair petals and keep the revealed center visible. |
| [Moonphase magnolia](#moonphase-magnolia) | Targeted | Unfold persistent moon segments without reclosure. |
| [Crema chrysanthemum](#crema-chrysanthemum) | Targeted | Uncurl tracked ribbons through the middle frames. |
| [Stained-glass iris](#stained-glass-iris) | Rebuild | Repair glass panels and retain the front lower petal. |
| [Aurora anemone](#aurora-anemone) | Targeted | Repair petals and distribute cup opening. |
| [Constellation blossom](#constellation-blossom) | Targeted | Repair petals and prevent late reclosure. |

### Coffee flower

**Targeted.** The ivory flower against the glossy coffee bean is immediately readable; the broad five-petal finish is a strong, restrained reward.

Observed issues:

- F1-4 mostly changes the bean seam brightness; the silhouette offers little progress before F5.
- F10-16 grows the bud clearly, but F17-19 spends three frames on nearly the same cupped shape.
- F23→24 replaces the cupped flower with a front-facing five-petal layout in one step; the large lower petals arrive abruptly. F24-25 then adds very little.

At small size: Clear in both themes at 148px. Light-theme outlines preserve the ivory petals; the final five-petal silhouette reads more strongly than the earlier cup. The F23→24 change remains conspicuous at this scale.

Proposed refinement: Keep the final flower and bean. Redistribute two near-hold frames into intermediate outward/downward petal rotations before F24; track the same five petals from closed bud to finish. Make seam separation, rather than brightness alone, carry the early progress.

Suggested progression: F1 bean; F4 visible opening; F8 two leaves; F12 closed five-petal bud; F16 first petal separation; F20 partly unfolded star; F24 full star; F25 settle.

### Starlit coffee

**Rebuild.** The luminous loops and surrounding stars create a recognizable magical identity with excellent large-shape contrast on dark backgrounds.

Observed issues:

- F4→5 jumps rapidly from an opening bean to a nearly full-height plume.
- F5-9 largely redraws the plume; stars appear at F8 and disappear at F9 rather than establishing a growing constellation.
- F15-18 already has the three-loop flower and four surrounding stars. F19-25 mostly removes the remaining top wisp and shuffles small details; completion has little new payoff.
- Thin loop interiors and tiny sparks compete with the central motif rather than contributing equally useful progress.

At small size: Excellent dark-theme readability. In light theme the loops/stars become fine outlined shapes and lose luminous impact, though still identifiable. A slightly stronger warm-gold edge on the major loops would help more than extra sparks.

Proposed refinement: Rebuild the progression around a persistent three-loop constellation while retaining the final palette and bean. Let a short plume trace the left, right, then top loop over distinct intervals; reveal stars only as their matching loop completes. Save closure of the top loop and one coherent final light expansion for F23-25.

Suggested progression: F1 bean; F5 short plume; F9 left arc; F13 two open arcs; F17 two closed loops; F21 top loop forming; F24 constellation complete; F25 settle.

### Latte bloom

**Rebuild.** The cream rosetta has an elegant, clean silhouette and a strong coffee-specific association; it needs very little extra decoration.

Observed issues:

- F5-9 repeats a tall steam shape without a clear transition toward the rosetta.
- F10 has a broad cream surface and tall plume, then F11 collapses to a short sideways curl: a clear visual regression at the row boundary.
- F13-15 adds most of the rosetta lobes quickly. F16-25 is essentially the finished leaf, with tip and contour repainting instead of meaningful growth.

At small size: The rosetta remains readable in both themes, with stronger impact on dark. F16-25 feels particularly static at 148px. F19-20 also shows faint horizontal stray marks above the main art in the supplied composites; verify and clear source-cell residue.

Proposed refinement: Rebuild the middle and late progression using one continuous rosetta stem. Eliminate the F10→11 reset, add alternating cream lobes one pair at a time, and reserve the top lobe and final broadening for the last quarter. Preserve the restrained final artwork.

Suggested progression: F1 bean; F5 cream emerges; F9 lower lobe pair; F13 second pair; F17 third pair; F21 upper pair; F24 top heart/tip completes; F25 settle.

### Coffee plant

**Targeted.** The four broad glossy leaves read clearly, and the bean-to-sprout story is direct without requiring flower details.

Observed issues:

- F6→7 makes a large stem-height step while F7-9 changes little except tiny leaves.
- F11→12 shrinks the established leaf pair; F13 enlarges it again, producing a growth reversal.
- F15-18 shows a small paired shoot at the top, but F19 replaces it with a flat, cut-looking stem tip.
- F19-24 offers almost the same two-tier plant, and F25 simply enlarges leaves. The final tip remains visually unfinished.

At small size: Leaf tiers read well in both themes. The F11→12 shrink and lost top leaves at F19 are obvious at 148px. A faint isolated horizontal mark is visible above F7; verify/clear source-cell residue.

Proposed refinement: Repair the leaf-size reversal and carry the top shoot continuously into a small third leaf pair. Keep the two mature tiers stable while the new pair separates during F20-24; finish with a naturally tapered growing tip instead of a flat stump.

Suggested progression: F1 bean; F5 sprout; F9 first leaves unfold; F13 first pair mature; F17 second pair opens; F21 third pair separates; F25 compact three-tier plant.

### Cherry tomato

**Targeted.** The combination of yellow flowers, green fruit, and bright red tomatoes gives excellent narrative variety and a visually generous final reward.

Observed issues:

- F1→2 abruptly rotates the bean from diagonal to upright; this reads as an object replacement without an intermediate turn.
- F5→6 loses the developing branched tip and returns to a simpler single sprout.
- F12→13 adds several substantial branches/leaves at once; F15→16 changes branch geometry while introducing the first flower and fruit.
- F20→21 turns the upper ripe red tomato back into green and rearranges the lower clusters.
- F24-25 has a compelling full harvest, but late fruits appear in new positions instead of consistently growing from established flowers.

At small size: The final red fruit is clear in both themes. Mid-sequence green fruit competes with tiny leaves, so fewer stable clusters with clean gaps would communicate progress better than added fine detail.

Proposed refinement: Lock the bean orientation and main branch skeleton. Keep every fruit attached to the same node through green enlargement and red ripening; repair the F20→21 reverse-ripening first. Stage the two major lower clusters over the final six frames rather than adding a new harvest arrangement at the end.

Suggested progression: F1 stable bean; F5 sprout; F10 established branches; F14 flower buds; F17 flowers and tiny green fruit; F20 first red cluster; F23 remaining fruit ripens; F25 full harvest.

### Strawberry

**Targeted.** The four large red berries and surviving white flower form one of the clearest final compositions; the triangular berry shape remains distinctive.

Observed issues:

- F1→2 changes bean orientation abruptly. F5→6 shortens the sprout after the first leaf pair has appeared.
- F11→12 adds a large flower and substantial lower foliage in one step; the flower also moves toward the center in F13.
- F15→16 removes/rearranges several flowers while introducing sizeable green berries, obscuring flower-to-fruit continuity.
- F21→22 introduces a new lower-right green berry without an earlier visible flower at that node; the final four-fruit layout is only established late.

At small size: One of the strongest final silhouettes at 148px: four red berries remain separately recognizable in both themes. Seed detail remains a texture rather than a required cue; preserve the large berry shapes and spacing.

Proposed refinement: Keep the strong final layout but establish its four fruit nodes before flowering. Show each white flower closing into a small green berry at the same location, then enlarge and redden the berries on a staggered schedule. Repair the early sprout reset and stabilize the bean.

Suggested progression: F1 bean; F5 sprout; F10 compact foliage; F14 four flower nodes; F17 small green berries; F20 first two berries red; F23 third berry red; F25 four ripe berries.

### Raspberry

**Targeted.** The red compound berries contrast strongly with the leaves; the fruiting cane gives a richer endpoint than a simple single flower.

Observed issues:

- F1→2 changes bean orientation abruptly; the soil ring and bean texture continue to shift later.
- F7→8 adds a large amount of foliage in one step; F10→11 introduces a prominent white flower while reshaping the canopy.
- F12-17 already has the right fruiting branch but spends several frames moving small green berries around instead of clearly enlarging fixed berries.
- F19→20 suddenly adds multiple large red berries, including a left berry. F23→24 substantially rearranges the fruit clusters, and F25 adds more fruit again.

At small size: Red berries and their compound texture remain recognizable in both themes, but tiny green berries/branchlets merge with leaves. Stable cluster locations and clear berry-to-leaf gaps matter more than fine thorns or vein detail.

Proposed refinement: Retain the recognizable raspberry texture but lock one cane and two fruiting branches. Establish all final berry nodes as buds by mid-sequence, grow them in place, and reserve ripening for the last third. Rework F19-25 as one coherent staggered harvest instead of several alternate final compositions.

Suggested progression: F1 bean; F5 cane; F10 stable branches; F14 flowers and bud clusters; F18 green compound berries; F21 first red cluster; F24 all intended fruit ripe; F25 settle.

### Blueberry

**Targeted.** The bell-shaped flowers and powdery blue fruit give this animation a distinctive identity; the simple oval leaves avoid some of the visual clutter of other fruit plants.

Observed issues:

- F1→2 changes bean orientation and F5→6 shortens the seedling.
- F10→11 adds several developed flowers abruptly; later flowers move between branches instead of consistently becoming fruit.
- F18→19 turns several right berries blue in one step; F20→21 suddenly introduces a larger multi-berry arrangement and fruit on the left.
- F22-25 mostly repaints/repositions already mature berries, with little remaining growth and some changing overlap.

At small size: Blue fruit remains distinct from green leaves in both themes; the pale bell flowers read especially well on dark. Final overlapping berries are understandable as a cluster, but successive redraws produce little useful change at 148px.

Proposed refinement: Preserve the airy leaves and bell flowers. Establish the final two berry clusters earlier, transition each flower through a small green sphere, and stagger the blue color change. Spend F22-25 on the last berries reaching size/color while already ripe berries remain fixed.

Suggested progression: F1 bean; F5 seedling; F10 branch skeleton; F14 bell flowers; F17 small green berries; F20 first blue berries; F23 second cluster ripens; F25 final fullness.

### Blackberry

**Targeted.** The green-to-red-to-purple ripening story is strong, and the pale blossom provides a helpful contrasting focal point above the dark fruit.

Observed issues:

- F1→2 rotates the bean; F5→6 offers little additional silhouette development.
- F10→11 swaps a leafy tip for an open flower without a clear bud-opening bridge.
- F17→18 jumps from green to substantially larger red berries; F19 adds a large left fruit that was not established as a visible earlier bud.
- F20→21 makes nearly the whole crop dark at once and relocates the upper cluster. F21-25 mainly shifts the finished berries and foliage.
- F21-25 dark fruit is visually quieter than the red stage. It remains recognizable in the 148px dark composite, but stronger fruit-to-leaf gaps would preserve the final harvest emphasis.

At small size: The dark fruit is still recognizable with the existing highlights/halo in the dark composite, but visually quieter than the red ripening stage. Keep clear gaps around fruit and broad highlights; do not brighten the whole asset or add more detail.

Proposed refinement: Track four persistent fruit nodes from bud to green, red, and purple. Use the final five frames to ripen those nodes sequentially, preserve berry positions, and give the dark berries a slightly broader cool highlight and clear negative space from the bean/leaves.

Suggested progression: F1 bean; F5 cane; F10 flower buds; F14 flowers/green fruit; F18 red fruit; F21 first purple fruit; F24 last fruit darkens; F25 settle.

### Coffee brew

**Rebuild.** The warm steam/flame and opened bean are bold and readable, with a compact symmetric silhouette suited to a small brewing display.

Observed issues:

- F4→5 suddenly establishes a tall plume; F5-7 then changes mainly its contour.
- F10→11 reduces the size/height of the steam and side curls at the row boundary.
- F13-15 reaches the complete three-pronged steam motif. F16-25 largely repaints that same state and tiny sparks.
- The final shape is visually close to coffee_starlit, reducing the distinction between the two choices; its finish has little new event.

At small size: Main curls remain readable, especially on dark. Light theme reduces them to pale thin contours, weakening visual impact; strengthen selected warm-gold boundaries rather than expanding the halo or adding sparks.

Proposed refinement: Rebuild the timing around a continuous brewing plume while preserving the bean and colors. Let the side curls form gradually and rise/relax into a single coherent steam crown only near completion. Avoid star-like additions here so starlit owns the constellation finish; reserve the final frames for a readable final expansion and settle.

Suggested progression: F1 bean; F5 short steam; F9 rising main plume; F13 side curls emerge; F17 curls expand; F21 steam crown; F24 broad final plume; F25 settle.

### Rose

**Targeted.** The saturated red, layered bloom is immediately recognizable and creates a strong focal point without relying on particles.

Observed issues:

- F2-4 uses a bright red/orange seam flare that disappears when the sprout arrives at F5, adding an isolated effect rather than continuous growth.
- F10→11 is nearly a hold, then F11→12 enlarges the red bud substantially in one step.
- F17→18 changes a narrow upright bud into a broad, more front-facing rose abruptly.
- F20-25 is already fully open and mostly redraws the petal spiral, with little distinguishable progress.

At small size: Strong in both themes at 148px. Red and the broad whorled silhouette carry the identity; late internal petal redraws add little at this size. No extra particles or ornament are needed.

Proposed refinement: Keep the final rose. Redistribute late near-holds into intermediate opening poses between F12-20, tracking the same outer petal edges as they roll outward. Save the last outer-petal layer and gentle final widening for F23-25, and soften the isolated early flare.

Suggested progression: F1 bean; F5 sprout; F10 small red bud; F14 swollen bud; F18 outer petals curl; F21 middle whorl opens; F24 full rose; F25 settle.

### Lotus

**Targeted.** The broad layered ivory flower gives a calm, luminous finish, and the low bloom positioned near the bean feels balanced.

Observed issues:

- F9→10 changes a small green bud into a substantially larger white bud in one step.
- F10-13 repeats a similar closed bud with only minor contour changes.
- F15-18 spends four frames in a similar cup shape; F19→20 then adds a visibly new lower petal tier abruptly.
- F21-25 has nearly the same fully opened silhouette, with petal rearrangement rather than a distinct final opening.

At small size: The broad layered silhouette is clear in both themes. Light theme relies on gold outlines for inner-tier separation; preserve those boundaries. Most F21-25 differences become negligible at this scale.

Proposed refinement: Preserve the layered finish but distinguish the tiers from the bud stage onward. Open the outer tier first, then the inner tier, with a persistent petal count/layout. Use the spare closed-bud and final-hold frames to bridge F19→20 and reveal the center only near completion.

Suggested progression: F1 bean; F5 sprout; F9 green bud; F13 layered ivory bud; F17 outer tier opens; F21 inner tier opens; F24 center reveal; F25 settle.

### Sunflower

**Polish.** One of the clearest sequences in this set: a round bud opens into a dark disk with bright yellow rays, and the radial silhouette stays recognizable at a glance.

Observed issues:

- F10→11 and F16→17 contribute little visible progress relative to adjacent steps.
- F12→13 exposes much of the disk at once; a partial sepal-opening bridge would help.
- F20-25 is effectively the mature flower, with slight petal redraws/scale changes rather than a purposeful final phase.
- The bean seam flare F2-4 is more visually active than some later growth steps.

At small size: Best immediate small-size recognition in this group: dark disk, yellow rays, and green leaves are distinct in both themes. Final-frame redraws are barely meaningful, so continuity and timing are the priority.

Proposed refinement: Polish the existing sequence rather than redesign it. Move one near-hold into the first disk reveal, keep the disk/petal arrangement fixed, and distribute the final ray-petal extension across F20-24 before a stable F25. Reduce incidental texture changes.

Suggested progression: F1 bean; F5 sprout; F10 round bud; F13 partial disk reveal; F17 short ray petals; F21 rays extending; F24 full sunflower; F25 settle.

### Orchid

**Targeted.** The mature white orchid is elegant and distinctive, with a broad silhouette and colored lip that provide a clear visual reward.

Observed issues:

- F10-16 reads as a generic upright cup-shaped flower rather than an emerging orchid; most identity arrives very late.
- F18→19 replaces the cup center with a detailed orchid lip and changes the side petals/front-facing geometry in one jump.
- F19-25 is largely the completed orchid, with scale and small lip/petal redraws consuming the final quarter.
- The subtle pale petal veins and small colored center will contribute less than the broad silhouette at app size.

At small size: The final broad side petals identify the flower in both themes; the small colored lip remains visible but subtle. F18→19 is still a conspicuous design change at 148px. Preserve broad petal separation rather than increasing vein detail.

Proposed refinement: Keep the mature orchid but rebuild F12-21 around its actual final petal arrangement: two broad side petals gradually separate and the lip unfurls downward from a persistent center. Spread that reveal over several frames and reserve final side-petal expansion for F22-24.

Suggested progression: F1 bean; F5 sprout; F10 small bud; F14 orchid-specific folded bud; F18 side petals separating; F21 lip unfurls; F24 broad orchid; F25 settle.

### Jasmine

**Targeted.** The final five-petal white star is simple, bright, and readable; the restrained palette works well with the coffee bean.

Observed issues:

- F12-14 has thin stray horizontal colored pixels visible above the bud in the original sheet; inspect and clear the affected cell margins.
- F12-18 closely resembles the coffee_flower cup sequence, delaying jasmine's own narrow/star-shaped identity.
- F19-22 moves through a bent, roughly four-lobed layout; F23 introduces a five-petal layout and F25 rearranges it again. The flower reads as changing designs rather than the same petals unfolding.
- F20→21 mainly holds the earlier asymmetric form, leaving too few frames to resolve the final star continuously.

At small size: The final star is readable in both themes, but the preceding four-lobed/asymmetric forms make late progression feel like a changing flower design. F12-14 faint horizontal residue is also visible in the contact sheets.

Proposed refinement: Clean the stray pixels and establish five persistent narrow petals in the closed bud. Unfold those same petals outward in a stable order from F15 onward, keeping the flower center and leaf positions fixed. Preserve the final star but bridge its lower petals over several frames.

Suggested progression: F1 bean; F5 sprout; F10 small bud; F14 five-petal folded bud; F18 top/side petals open; F21 lower petals separating; F24 five-petal star; F25 settle.

### Bleeding heart

**Targeted.** The arched pink heart raceme and white hanging tips are distinctive; F12–20 visibly develop the flower rather than just increasing scale.

Observed issues:

- F1→2 rotates a flat bean into an upright split seed without an intermediate; F5→6 removes the paired seed leaves and shortens the plant.
- F10→11 substantially enlarges and repositions the lower foliage; F11→12 replaces the right leafy branch with a flowering raceme instead of retaining a clear branch-to-bud transition.
- F15→16 shifts the plant left and changes the bean size; F18→19 introduces an already flowering left raceme. These read as composition changes rather than continuous growth.
- F21–25 are nearly the same finished arrangement with small repaint/pose differences, while the hanging hearts crowd the horizontal bounds. Extra flower count is less valuable than readable heart silhouettes at 148px.

At small size: At 148px the pink hearts remain readable in both themes, but the final cluster becomes crowded and the approximate corrected preview cuts the highest foliage at F21–25. Preserve gaps between hearts and leave post-correction top margin.

Proposed refinement: Keep the final heart design; rebuild only the structural transitions using fixed bean and branch landmarks. Carry seed leaves through F5–7, establish the left raceme as tiny buds before F18, and use the last five frames for sequential heart swelling and white-tip emergence on existing stalks.

Suggested progression: F1–5 crack and rooted paired leaves; F6–10 continuous arch and permanent foliage; F11–15 visible buds on both racemes; F16–20 hearts inflate and tips release; F21–25 finish distal hearts without adding or moving branches.

### Passionflower

**Targeted.** The final white petals, purple radial corona and raised green center form an excellent distinctive payoff; the curled tendrils provide a recognizable vine accent.

Observed issues:

- F1–3 mostly alter the seed crack; F5→6 reduces the visible sprout, losing early progress.
- F10→11 jumps to a taller plant with a definite terminal bud; F12–18 repeatedly repaint an almost unchanged closed bud. F15→16 adds little visible progress.
- F20→21 begins the actual opening very late. F23→24 suddenly reveals a fully formed raised center while the bean visibly shrinks and the composition changes.
- F24→25 is predominantly enlargement; the most interesting corona separation is compressed into F21–24.

At small size: At 148px the final corona/center is still readable on both themes. The near-held F12–18 buds are especially apparent, and the approximate corrected preview clips the top of the F12–15 buds.

Proposed refinement: Preserve the final flower and allocate more frames to its opening: begin sepal separation around F14, reveal the corona progressively around F17–21, and grow the center through F22–25. Hold the bean, leaf junctions and flower attachment fixed while opening.

Suggested progression: F1–5 crack and continuous sprout; F6–10 leaves, tendrils and bud stalk; F11–15 bud swelling and first sepal separation; F16–20 petal fan and corona expansion; F21–25 center rises and flower settles.

### Bee orchid

**Targeted.** The pink three-part silhouette and dark patterned lip give a clear, charming identity once open.

Observed issues:

- F1→2 changes the seed orientation; F5→6 lowers the sprout/base composition.
- F8→9 introduces both large leaves in one step; F12→13 jumps from a small green tip to a large pink bud.
- F13–18 hold nearly the same closed bud, including the F15→16 row transition; most differences are contour and paint changes.
- F20→21 exposes most of the dark lip in one step; F21→22 suddenly spreads both lateral petals. F23–25 increasingly enlarge the flower while shrinking the bean, creating a zoom-like finish instead of further opening.

At small size: At 148px the final lip and pink petals remain clear in both themes. The long bud hold is more obvious than its fine color changes; the approximate corrected preview clips F13–15 bud tips.

Proposed refinement: Retain the finished orchid; replace redundant bud frames with leaf unfurling and staged lateral-petal/lip release. Fix bean scale and flower center, then finish by rounding the patterned lip and settling the three petals.

Suggested progression: F1–5 seed crack and sprout; F6–10 two leaves gradually unfurl; F11–15 pink bud grows and seams separate; F16–20 lip emerges and side petals lift; F21–25 petals spread and lip rounds at fixed scale.

### Jade vine

**Rebuild.** Turquoise claw-shaped flowers and a hanging raceme create one of the most unusual and visually rewarding silhouettes in the set.

Observed issues:

- F5→6 removes the leaves and shortens the vine, an unmistakable reversal.
- F9→10 makes a large foliage/stem jump. From about F10 onward the upper stem is visibly cut by the top cell edge, making the plant feel cropped instead of complete.
- F10→11 adds a long dangling bud chain; F15→16 jumps from mostly closed narrow buds to many broad hooked flowers at once.
- F18–25 add density faster than readable structure. F20→21 shifts/recomposes the bean and raceme, while F23–25 are almost finished-state repetitions with flowers crowded along the right and bottom edges.

At small size: At 148px the turquoise color reads well, but the tendrils, leaves and many claws compete for attention. Top clipping persists in both themes; fewer, more separated claws would be clearer.

Proposed refinement: Rebuild the composition with a complete curved vine inside a consistent safe margin. Preserve the claw design and turquoise color, reduce competing tendril detail, and open a fixed set of raceme buds in a clear top-to-bottom wave.

Suggested progression: F1–5 stable seed and leafy vine tip; F6–10 continuous arch within bounds; F11–15 fixed hanging bud chain develops; F16–20 top-to-bottom claw opening; F21–25 lower claws finish with clear gaps.

### Bird of paradise

**Targeted.** The final orange fan against a blue spear has strong color contrast and an immediately readable directional silhouette.

Observed issues:

- F1→2 flips the seed orientation; F5→6 lowers the sprout. F8→9 produces the paired leaves abruptly.
- F11→13 transforms the upright tip into the large horizontal sheath over very few frames.
- F13–18 largely hold the horizontal green sheath, with only tiny tip-color changes; F15→16 actually appears to reduce the orange opening.
- F20→21 adds much of the orange crest; F21→22 changes the supporting leaves and stem bend. F23–25 mostly enlarge/spread the completed flower as the bean shrinks.

At small size: At 148px the orange crest has good impact in both themes; the first orange traces around F15–18 are too small to communicate much progress. Keep the blue spear broad enough to separate from the sheath.

Proposed refinement: Keep the final orange/blue silhouette; spend F13–20 opening the sheath and lifting individual orange segments with the blue spear emerging between them. Lock the stem bend, leaves and seed scale through the finish.

Suggested progression: F1–5 sprout from fixed bean; F6–10 leaf unfolding and stem elongation; F11–15 sheath bends and visibly separates; F16–20 orange segments and blue spear emerge; F21–25 fan spreads and settles.

### Blue Himalayan poppy

**Targeted.** The drooping neck, soft blue petals and yellow center give a graceful reveal; F22–25 have a clear increasing floral payoff.

Observed issues:

- F5→6 resets the sprout lower; F7→8 inserts two substantial leaves in one frame.
- F10→11 abruptly bends the upright stalk into a hook while shrinking the leaves. F11→12 contributes almost no new silhouette before the bud appears at F13.
- F15→16 reduces and repositions the bud/plant; F16–19 linger on similar half-covered blue buds.
- F20→21 shifts the plant and reduces the bean/foliage; F23–25 enlarge and turn the flower toward the viewer rapidly, with F25 close to the right boundary.

At small size: At 148px the blue/yellow final bloom is strong in both themes and the petals retain soft volume. F16–19's small seam changes contribute little; protect the right edge during the final expansion.

Proposed refinement: Preserve the nodding blue bloom but distribute neck bending across F9–12 and keep the seed/leaf scaffold fixed. Move a few redundant bud frames into gradual cap peeling and a slower forward-facing petal spread, retaining space to the right.

Suggested progression: F1–5 sprout; F6–10 leaves and gradual neck curvature; F11–15 bud hangs and cap separates; F16–20 blue petals peel free; F21–25 bowl opens and yellow center becomes readable.

### Rafflesia

**Targeted.** The five large fleshy lobes and dark central cup remain legible without a delicate stem; growth is expressed through a strong broadening silhouette.

Observed issues:

- F2→3 loses the bright inner crack and changes seed size; F4→5 visibly shrinks the emerging red point.
- F11–13 are nearly the same segmented dome, then F14→15 jumps from a closed globe to five folded-out petals.
- F15→16 and F20→21 continue reasonably, but F16–20 spend several frames mostly resizing the opening; F21–24 repeat a similar final lobe arrangement.
- F25 mainly enlarges the final flower and adds central detail. The dense spots and glossy ridges compete with the central opening at small size; the bloom nearly fills the available width.

At small size: At 148px the broad five-lobed flower is highly readable in both themes. Spot texture is busy but does not destroy recognition; prioritize the abrupt F14→15 unfolding and near-held final poses over radical restyling.

Proposed refinement: Preserve the broad five-lobed composition and use intermediate dome frames to show the seams pulling apart before each lobe folds outward. Keep the brown base stable, simplify spot contrast, and make the final change a readable opening/deepening of the central cup rather than overall zoom.

Suggested progression: F1–5 seed opens around rising red bud; F6–10 five-lobed dome inflates; F11–15 seams separate and lobes hinge outward; F16–20 lobes flatten around opening cup; F21–25 central structure reveals at fixed flower width.

### Chocolate cosmos

**Rebuild.** Deep burgundy flowers against fine green foliage create a restrained palette and a useful multi-flower progression.

Observed issues:

- F5→6 lowers and narrows the seedling; F7→8 swaps broad seed leaves for a branching feathery scaffold abruptly.
- F10→11 replaces the top foliage with a bud-bearing stem. F12–15 repeatedly change bud positions rather than maintaining an obvious growing scaffold.
- F15→16 instantly opens the main flower and loses the second bud; F17→18 reintroduces a side flower. This breaks flower identity.
- F20→21 relocates the main flowers; F21–25 rearrange bloom/bud count and positions, including open side blooms apparently reverting to buds. The dark petals may be difficult to separate on dark surfaces.

At small size: At 148px the burgundy flowers are visibly weaker against the dark theme than the green foliage and glossy bean, even with the preview halo. Lift the petals' broad planes and center contrast while retaining the dark chocolate character.

Proposed refinement: Rebuild the frame sequence around three or four persistent stems, each with a tracked bud. Preserve the burgundy flower artwork, introduce each bud before opening, and stagger their openings without moving or closing an established bloom. Add enough petal-edge light to distinguish the flower on dark UI.

Suggested progression: F1–5 seedling; F6–10 stable feathery foliage and main stem; F11–15 fixed secondary stems and buds; F16–20 main then side blooms open; F21–25 remaining buds open in place.

### Flame lily

**Targeted.** The red/yellow recurved petals are vivid, and the long stamens create a striking celebratory final silhouette.

Observed issues:

- F2→3 removes the luminous seed crack; F5→6 shifts the growing tip down.
- F10→11 changes the stem to a curl; F12→13 introduces a large hanging bud with little buildup.
- F15–18 are similar closed red-striped buds, then F18→19 jumps to the full upright flame shape with exposed stamens.
- F20→21 shifts the main flower left. F21–25 largely enlarge and spread the finished flower while changing/removing the curled stem tip, rather than showing the petals progressively recurving.

At small size: At 148px the final red/yellow flower is vivid in both themes; thin stamens are secondary. Approximate corrected previews clip the upper curl around F12–15 and top petal tips in F24–25; keep the whole final silhouette inside a safe margin.

Proposed refinement: Preserve the flame silhouette, replace repeated closed-bud frames with visible petal-tip separation, and show the petals folding backward in stages. Keep the hook and seed landmarks coherent; reveal stamens progressively after the first petals open.

Suggested progression: F1–5 continuous sprout; F6–10 leaves and hook growth; F11–15 hanging bud forms and reddens; F16–20 tips split and petals recurve; F21–25 stamens extend and petals finish curling.

### Queen of the Night

**Targeted.** The cactus-like foliage and large layered white flower provide a strong identity, with meaningful opening changes through F16–21.

Observed issues:

- F5→6 slightly resets the shoot; F9→10 abruptly changes the number and arrangement of cactus segments.
- F10→11 replaces the multi-segment canopy with a different scaffold and a fully hanging bud. F11–15 bud growth is legible, but the supporting segments repaint/change identities.
- F15→16 shifts the plant left; F20→21 again shifts the composition and enlarges the flower, while the top foliage and right petals approach the frame bounds.
- F21–22 are almost the same finished flower; F22→23 adds a mature side bud and F24→25 moves/adds a bud on the opposite side. The final frames dilute the main bloom with unexplained new growth.

At small size: At 148px the large white bloom reads in both themes, though the light preview gives it a somewhat outlined appearance. Approximate correction crops the highest segment in F11–15 and the late right bud reaches/crosses the side bound.

Proposed refinement: Keep the layered white opening; use one stable cactus scaffold and introduce the flower stalk gradually. Remove the late unprepared side buds or seed them well before F20. Finish with the existing flower's inner petals opening, using a wider safe margin.

Suggested progression: F1–5 cactus shoot; F6–10 fixed segment scaffold and tiny bud stalk; F11–15 hanging bud grows and separates; F16–20 outer then inner petals open; F21–25 center reveals and flower settles without new branches.

### Snowdrop

**Targeted.** The downward white bells and green inner markings are clear, gentle and appropriate to a calm brewing flow.

Observed issues:

- F5 shows a distinct pale bud inside the sprout, but F5→6 removes it; the next bud does not reappear until F12.
- F10→11 changes the foliage proportions; F11→12 adds a nearly formed drooping bud and stalk in a single step.
- F15→16 is a useful opening step, but F18→19 suddenly adds a second right bud; F19→20 moves that bud to the left.
- F21–23 largely repaint/rearrange the same two bells. F23→24 changes a previously open left flower into a closed bud while adding the right bloom, so the final cluster lacks persistent flower identity.

At small size: At 148px the white bells and green inner markings remain clear on both themes. Existing contrast is adequate; preserving each bell's identity and position is the main refinement.

Proposed refinement: Keep the first bell opening and establish three persistent stalks before their buds enlarge. Open them sequentially from F16 onward, never moving an existing flower between sides or reverting it to a bud. Use the last frames for the third bell and inner green markings.

Suggested progression: F1–5 leaf shoot without premature flower; F6–10 continuous foliage and first arch; F11–15 three stalks with staggered buds; F16–20 first and second bells open in place; F21–25 third bell opens and green markings reveal.

### Myosotis sylvatica

**Targeted.** The airy blue spray with yellow centers is pleasant, and the final cluster remains visually lighter than the dense exotic flowers.

Observed issues:

- F5→6 lowers the growing tip; F10→11 mostly repaints the same plant.
- F11→12 replaces the upper leaves with a bud spray without a clear emerging flower stalk; F13–16 mostly multiply/reposition small buds.
- F16→17 opens multiple tiny flowers at once. F19→20 and F20→21 alter branch and flower positions; the final cluster repeatedly changes which bloom is at which endpoint.
- F21–25 differ mostly by small rearrangements and flower count. Individual flowers and their fine branching are small relative to the bean and may lose clarity at 148px.

At small size: At 148px blue faces are recognizable, but their thin rims and tiny centers merge into decorative dots, especially in dark mode. Approximate corrected previews cut the top buds in F14–15. Slightly larger, fewer flowers and fixed endpoints would improve progress readability.

Proposed refinement: Retain the delicate spray but choose a stable, slightly simplified branching structure with fewer larger readable flower faces. Track each bud endpoint and open one local group at a time; make F21–25 finish the existing outer buds rather than reshuffling the cluster.

Suggested progression: F1–5 sprout; F6–10 permanent leaf pair and branch stalk; F11–15 stable bud spray; F16–20 inner flower groups open; F21–25 outer buds finish with larger clear blue faces.

### Fuchsia ballerina

**Rebuild.** The arched stalk and contrasting pink sepals/purple hanging skirt could create a memorable, elegant downward bloom.

Observed issues:

- F1–3 provide little visible progress; early plants sit low in much empty space.
- F9→10 replaces a leafy tip with a fully arched hanging bud while removing the right leaf.
- F12–15 develop large background-colored holes in the bud; F16–20 are predominantly scratchy magenta outlines around a transparent interior. Both light/dark composites confirm missing surfaces.
- F20→21 opens the upper sepals abruptly while the hanging center remains hollow. F23–25 show a purple skirt with extensive missing-looking interior detail; F24–25 are nearly the same final pose. These defects prevent a polished small-size reading.

At small size: Both 148px theme composites confirm that the missing petal interiors take on the background color. The final purple skirt nearly disappears in dark mode and looks washed out/hollow in light mode; this is a material alpha/surface defect, not merely subtle shading.

Proposed refinement: First repair or regenerate the source alpha/matte and validate opaque colored petal interiors on both light and dark backgrounds. Rebuild the bud-to-skirt opening with solid connected surfaces, a fixed bean/stalk, and gradual sepal lift; do not try to hide the defect with glow.

Suggested progression: F1–5 clear seed-to-leaf progression; F6–10 arch and attached solid bud; F11–15 solid pink bud elongates; F16–20 sepals lift and purple skirt emerges; F21–25 skirt unfurls and stamens descend.

### Magnolia dawn

**Rebuild.** The pale pink-to-ivory magnolia and broad leaves have a warm, refined final color palette worth retaining.

Observed issues:

- Every third cell visibly contains two narrow plant renderings (F3, F8, F13, F18, F23), violating the single-subject frame composition.
- F11–15 have a horizontal missing-looking band across the bud; F16–20 have very large empty horizontal bands separating bud tips from their lower halves.
- The apparent row packing does not respect clean isolated 256px cells: upper bud tips around F11–15 intrude toward/above the row edge, and F16–20 are severely fragmented.
- F20→21 transitions from fragmented closed bud to a complete flower; F21–25 opening cannot read smoothly because F23 again contains two narrow flowers, while F24–25 jump to broad frontal blooms.

At small size: Both 148px theme composites confirm duplicated plants in every third cell, disconnected bud fragments around F6–15, and broad empty bands through the middle phase. F25's ivory petals are legible in both themes; the layout defects are the blocker.

Proposed refinement: Rebuild the sheet from 25 individually validated square frames. Repair layout/alpha first, ensuring one complete plant per cell with no missing bands or duplication; then preserve the final palette in a coherent magnolia cup-to-open-petal sequence.

Suggested progression: F1–5 one intact seed and sprout per frame; F6–10 paired leaves and terminal bud; F11–15 continuous intact bud enlargement; F16–20 petal seams separate into a cup; F21–25 outer petals unfold to final ivory flower.

### Iris origami

**Rebuild.** The vertical sword leaves and purple/yellow iris silhouette offer a strong shape distinct from round-petaled flowers.

Observed issues:

- F1→2 moves the tiny bean downward; the small base and sparse early sprout occupy little of the frame.
- F6–20 purple bud interiors are scratched away/transparent, leaving disconnected highlights and edges instead of a solid folded surface; both light/dark composites confirm the background shows through.
- F10→11, F15→16 and much of F11–18 are near-holds with slight repainting/scale changes; there is little readable anatomical progress through the long closed-bud phase.
- F20→21 splits the bud; F22→23 introduces the large drooping side petals and yellow beards abruptly. F23–25 are near-duplicate final poses, still with damaged-looking purple interiors.

At small size: Both 148px theme composites confirm background-colored holes through the purple bud/petals. They look white and scratched in light mode and almost disappear in dark mode. F23–25's yellow beards read, but cannot rescue the broken upper petals.

Proposed refinement: Repair/regenerate solid purple petal surfaces before tuning movement. Then replace the long bud hold with three clear unfolding stages: upper standards separate, lower falls descend, yellow beards reveal. Preserve the sword-leaf identity and use a consistent bean anchor.

Suggested progression: F1–5 sprout from stable visible bean; F6–10 sword leaves and intact purple bud; F11–15 bud swells and seams separate; F16–20 upper petals lift and lower petals descend; F21–25 yellow beards reveal and folds settle.

### King protea sunrise

**Rebuild.** Distinctive sculptural pink crown, broad green base and cream center give the last row a strong, recognizable silhouette.

Observed issues:

- F1-2 are visually near-identical; F7-17 mostly enlarge the same pointed bud rather than progressively releasing bracts.
- F7-25 contain conspicuous jagged background-revealing gaps inside pink bracts, worst at F14-22; these read as damaged petals rather than purposeful openings.
- F17-18 begins useful bract separation, but much of the width arrives at F20-21. F21-25 repeatedly redesign the cream center's height and crown while the silhouette is already essentially complete.
- F5-6 and F10-11 remain readable growth steps; the more consequential transition is F20-21. F15-16 adds foliage without a comparably clear bloom milestone.

At small size: At 148px the crown is recognizable on both themes, but petal holes remain prominent on dark and masquerade as highlights on light. Gray halo edges do not repair the missing material.

Proposed refinement: Retain the distinctive final composition but rebuild the affected petal imagery with intact surfaces and consistent individual bracts. Move initial bract release to the middle third, then open tracked layers gradually; keep center geometry continuous through the last five frames.

Suggested progression: F1-4 bean split and shoot; F5-9 pink bud and paired leaves; F10-15 outer bracts peel outward; F16-20 inner bracts separate and cream core rises; F21-25 crown broadens slightly and settles with a stable center.

### Dahlia kaleidoscope

**Targeted.** Clean coral petals, readable green stem and a large layered final flower; one of this group's most coherent intact assets.

Observed issues:

- F6-10 mostly swell one small bud; F12-18 retain nearly the same closed rose-like silhouette, so several middle frames add little at small size.
- F19-20 abruptly switches from a few opening petals to a broad, many-layered flower. F20-21 remains a noticeable shape change across the row boundary.
- F22-24 largely repeat the finished flower; F25 introduces a bright yellow center almost at once instead of gradually uncovering it.
- F5-6 and F10-11 are understandable growth changes; F15-16 adds little distinct motion.

At small size: Coral layering and overall silhouette remain clear on both 148px composites. Middle buds are so similar that texture changes contribute little; the F25 yellow center is legible but arrives abruptly.

Proposed refinement: Keep the palette and final flower. Replace redundant middle frames with tracked outer-petal unfolding, bridge F19-20 with intermediate layers, and reveal the yellow center over F22-25.

Suggested progression: F1-5 sprout and bud; F6-10 bud swells and separates at seams; F11-15 outer petal ring unfurls; F16-20 successive rings expand; F21-25 center is uncovered and petals settle.

### Pincushion firework

**Targeted.** Bright orange styling and the outward-curving tipped filaments make an excellent distinctive final reward.

Observed issues:

- F7-16 mainly grow a tightly packed orange oval; F17-20 show only small filament changes and underuse the characteristic firework motion.
- F20-21 releases several long filaments abruptly. The expansion is then crowded into F21-25.
- F23-24 raises a large central cone, which becomes a lower round cushion again at F25; this looks like anatomy changing rather than finishing.
- Thin stray blue/purple marks are visible near some source cell bottoms, especially F2-5. F1-2 also changes the bean's scale/shape abruptly.

At small size: Orange tips and arcs remain readable on both 148px themes. The few fine boundary specks are secondary, while the F24 central cone and F25 collapse remain obvious.

Proposed refinement: Preserve the orange cushion and tipped filaments. Begin distinguishable filament release around F12, lengthen the same filaments incrementally, and keep the center's height and topology continuous through F23-25. Clean isolated boundary pixels.

Suggested progression: F1-5 stable bean opens and sprouts; F6-10 cushion forms; F11-15 first tipped filaments separate; F16-20 filaments arc outward sequentially; F21-25 remaining arcs extend around a stable central cushion.

### Morning glory spiral

**Rebuild.** The curled vine and large blue trumpet could deliver a particularly legible, graceful growth story.

Observed issues:

- F3/8/13/18/23 visibly contain two narrow plants or split duplicated imagery within what must be one 256px cell; the apparent grid does not match the renderer's five-column framing.
- Broad horizontal missing bands cut across the flowers in F11-25; parts of later flowers are separated from their stems or truncated.
- Blue petals also contain jagged missing patches. These defects dominate any judgment of smooth growth.
- F5-6/10-11/15-16/20-21 cross states with changing crop or image layout; scale and anatomy cannot remain continuous with this sheet.

At small size: Both 148px themes plainly show doubled/split plants, detached flower fragments and horizontal cutouts. No size or halo adjustment can make the current cells valid single-flower states.

Proposed refinement: Rebuild the sheet from individually approved, consistently registered single-plant frames. Keep the spiral vine and blue trumpet concept; establish exact 256px cells and inspect every cropped cell before evaluating motion. Repairing timing cannot fix these source images.

Suggested progression: F1-5 one bean and one curling shoot; F6-10 paired leaves and a closed twisted trumpet; F11-15 a continuous twist begins loosening; F16-20 trumpet rim widens without crop changes; F21-25 uninterrupted blue face opens and settles.

### Foxglove chimes

**Targeted.** A tall stalk and staggered bells give this design a distinct vertical rhythm and a charming final silhouette.

Observed issues:

- F7-9 holds a single top bud; two side buds appear together at F10. F11-20 mostly recolor/swell these closed buds rather than visibly unroll bells.
- At F21 a bottom bell opens; F22 removes that bottom bell and opens the middle one instead. F23 then restores three open bells, so the plant visibly loses and regains anatomy.
- F16-25 pink petals have jagged background-revealing missing areas, especially the late bell walls; intended spots and unintended missing material need separation.
- F20-21 is a large opening event after a long plateau; F23-25 mainly vary bell direction/shape instead of delivering additional growth.

At small size: At 148px the tall final three-bell silhouette is clear. Dark reveals missing petal-wall regions that light largely conceals; the disappearing lower bell at F22 remains obvious in both.

Proposed refinement: Keep the staggered stalk. Preserve every bud and bell once it appears, unfurl from the bottom upward over more frames, and keep the spotted throats inside intact petal walls. Reuse the same stalk geometry throughout the opening.

Suggested progression: F1-5 sprout; F6-10 stalk and three persistent buds form; F11-15 bottom bell lengthens and opens; F16-20 middle bell opens as top bud bends; F21-25 top bell opens; all three settle without disappearing.

### Cherry blossom wish

**Targeted.** Three simple pink flowers on a spare branching stem create an immediately understandable final composition.

Observed issues:

- F6-7 is nearly a hold; F11-20 keeps the same three closed buds with slight repainting, using ten frames without a major visible milestone.
- F6-21 has jagged background-revealing holes in the pink buds; F24 also shows a damaged patch on the top flower.
- F20-21 abruptly opens the left bloom, F21-22 opens the right, and F22-23 opens the top. The sequence is sensible but each individual opening lacks intermediate petal poses.
- F23-25 are mostly finished-flower variations; F15-16 provides almost no meaningful progress.

At small size: The final three flowers are readable and attractive at 148px. Dark-theme intermediate buds look hollow or damaged; light conceals the same missing regions. The fine stem is sufficiently visible in these composites.

Proposed refinement: Preserve the clean three-branch silhouette and staggered opening order. Start opening the left bloom in the middle frames, show each bud's five petals separating over multiple steps, then overlap the right and top openings; repair missing petal pixels and hold a stable final flower.

Suggested progression: F1-5 first shoot; F6-10 branches and three buds form; F11-15 left flower unfurls; F16-20 right flower unfurls while top loosens; F21-25 top flower finishes and all three settle.

### Himalayan lantern

**Targeted.** The vivid orange lantern revealing a warm gold flower is an unusually strong and readable reveal concept.

Observed issues:

- F6-11 rapidly enlarges the lantern, but F11-20 mostly keeps swelling/repainting an already large closed shape; the reveal has almost no anticipation.
- F21-22 finally separates a seam; F22-23 suddenly reveals an almost complete gold flower.
- F23-24 changes the opening asymmetrically and the inner flower's orientation before F25 spreads into a new star-like shell shape; tracked folds would read more clearly.
- Small blue/purple boundary specks appear near several cell bottoms. F10-11 has a size step, while F15-16 adds little structural progress.

At small size: The orange lantern and gold reveal are legible on both 148px themes. The middle plateau and F23-24 uneven reclosing are visible; source-edge specks are a lower priority at this size.

Proposed refinement: Keep the lantern-and-flower payoff. Use middle frames to define and widen a stable set of seams, let a gold glint precede the full flower, and open the same shell panels continuously through the final row. Remove stray edge pixels.

Suggested progression: F1-5 shoot and small lantern; F6-10 lantern inflates; F11-15 seams brighten and separate; F16-20 panels peel apart with a growing gold glimpse; F21-25 flower opens as the same shell panels finish spreading.

### Black bat flower

**Targeted.** The bilateral dark bracts and long whiskers give this flower a striking, genuinely different outline. F17-20 already has a useful opening sequence.

Observed issues:

- F1-2 is nearly identical; F6-16 spends eleven frames on a slender closed bud with small scale/color changes.
- F21-23 mostly repeat open wings and short whiskers; F24-25 suddenly adds most of the whisker length, making the final frame feel like a replacement illustration.
- The near-black bud and bracts lose internal separation on the approximate 148px dark composite; the silhouette survives, but the center and thin whiskers are much less clear than on light.
- F20-21 is coherent and comparatively restrained; the weak part is F10-11/F15-16 with little progress, followed by the outsized final whisker change.

At small size: The 148px dark composite retains the outline but loses much of the center and bract detail. Pale halo outlines partially rescue edges; a few deliberate violet structural highlights would improve depth. Final whiskers are visible on both themes but fine.

Proposed refinement: Preserve the bat silhouette and asymmetrical first bract release. Start that release earlier, expose the central cluster gradually, and spread whisker growth across F18-25. Lift a few structural violet highlights rather than adding decoration.

Suggested progression: F1-5 sprout; F6-10 bud enlarges and outlines bract folds; F11-15 one bract then the other releases; F16-20 center opens and short whiskers emerge; F21-25 existing whiskers lengthen smoothly to final arcs.

### Water lily at twilight

**Rebuild.** A lavender radial bloom with a gold center provides a clear, attractive final target.

Observed issues:

- F6-25 has extensive jagged background-revealing gaps through petal surfaces, including much of the tall bud and late flower; the visible effect is shredded material.
- F11-20 mostly scales/repaints a closed upright bud. The radial opening starts only at F21.
- F22-23 changes sharply into the open flower; the gold center visible at F23 is largely obscured at F24 and reappears at F25.
- F24-25 source edges include stray vertical-colored marks; F20-21 adds a flat outer ring abruptly after the long bud plateau.

At small size: At 148px the flower looks pale and roughly intact on light, but large petal areas become background on dark and the final flower loses substantial body. This is a major theme-consistency defect.

Proposed refinement: Rebuild damaged petal frames around the existing lavender-and-gold final concept. Track petal layers and the center consistently, opening outer petals through the middle frames and inner petals later; keep the center increasingly visible once revealed.

Suggested progression: F1-5 shoot and bud; F6-10 intact bud lengthens and seams separate; F11-15 outer petals lower; F16-20 inner petal layers spread; F21-25 gold center is progressively revealed and radial flower settles.

### Moonphase magnolia

**Targeted.** The luminous moon-like bud turning into an ivory flower is visually distinctive, and broad petals remain simple enough to read at small size.

Observed issues:

- F10-20 is largely an enlarging moon sphere with changing crater texture; many frames communicate neither a new phase nor a new petal action.
- F20-21 suddenly peels two tall side petals away, F21-22 reveals more, and F22-23 becomes a flat open flower; most anatomical change occurs in three steps.
- F23-24 partly raises/closes the lower-left/front petal before F25 opens again, creating a small reversal at completion.
- Star accents appear abruptly at F22/F23, and their placement feels like decoration being switched on rather than a consequence of opening.

At small size: The moon sphere, ivory flower and tiny gold stars remain legible on both 148px themes. The halo produces a noticeable pale outline but is less damaging than the long motion plateau and F24 reclosure.

Proposed refinement: Retain the moon-textured bud and ivory flower. Start curved petal seam separation earlier, preserve recognizable moon segments as they unfold, and make the last frames a monotonic opening with stable stars revealed by the petals.

Suggested progression: F1-5 sprout; F6-10 rounded moon bud; F11-15 crescent-shaped petal seams release; F16-20 broad petals uncup and reveal star accents; F21-25 front petals lower to a stable open face.

### Crema chrysanthemum

**Targeted.** Warm cream ribbons and a dark coffee-colored center suit the coffee theme particularly well; the final bloom is rich and intact.

Observed issues:

- F7-8 makes little progress; F9-10 abruptly reorganizes an upright bud into a spiral rosette rather than showing that twist developing.
- F11-18 repeats a tightly curled sphere with small changes. F19-21 begins useful ribbon release, but F21-22 instantly replaces it with an almost fully opened chrysanthemum.
- F22-24 mainly alter spacing/size of the finished blossom. F25 adds more curled petal tips rather than resolving a clearly prepared motion.
- Fine blue/purple marks are visible along the top of some last-row source cells; these look unrelated to the flower.

At small size: Cream curls and coffee center read well on both 148px themes, including the final layered form. Tiny source-edge marks are barely visible here; the F21-22 topology jump is the substantive issue.

Proposed refinement: Preserve the cream-ribbon material and coffee center. Develop the spiral continuously from the bud, uncurl recognizable ribbons across F12-21, and reveal the center through a widening aperture before the final ring spreads. Clean cell-edge specks.

Suggested progression: F1-5 shoot and cream bud; F6-10 ribbons start a continuous spiral; F11-15 outer ribbons unfurl; F16-20 inner curls open and center appears; F21-25 layered curled tips spread and settle.

### Stained-glass iris

**Rebuild.** The glass-like color panels and classic upright-and-drooping iris silhouette have strong identity in the final frames.

Observed issues:

- F8-20 stays a narrow closed lance; color-panel changes substitute for meaningful unfolding across much of the sequence.
- F11-25 has numerous jagged background-revealing interruptions through colored petals, so the glass surfaces look broken or missing rather than translucent.
- F21-22 leaps from split spear to recognizable iris; F22-23 adds a large frontal drooping petal. That petal disappears or relocates to the side at F24, then returns front-and-center at F25.
- Fine multicolored lines become busy texture at 148px, while the overall iris survives. F15-16 has little silhouette change and F20-21 starts the entire reveal late.

At small size: At 148px light exposes pale missing regions among purple panels; dark makes much of the bloom merge into its background. The overall iris is recognizable, but fine leading becomes busy texture and the moving/disappearing front petal is obvious.

Proposed refinement: Rebuild affected petal imagery using a stable set of three upright and three descending structures, with intact, clearly bounded glass panels. Open these structures over the middle and late frames while preserving the front fall petal's identity. Simplify internal leading at displayed size.

Suggested progression: F1-5 shoot; F6-10 glass bud emerges; F11-15 upright petals separate; F16-20 three fall petals bend downward consistently; F21-25 panels catch light as the stable iris reaches full spread.

### Aurora anemone

**Targeted.** A simple purple cup becoming a round blue-violet flower with a dark center offers an appealing, readable color contrast.

Observed issues:

- F6-10 forms a bud well, but F11-21 keeps nearly the same closed oval; F16-20 is especially low-value repainting.
- F22 finally exposes the center and F23 abruptly becomes a flat open flower; F23-25 largely repeat this final pose.
- F22-25 has conspicuous background-revealing missing bands and patches near petal bases and upper petals; the cyan edges make these look torn rather than luminous.
- F5-6 introduces paired leaves and a purple bud at once; F10-11 and F15-16 are minor swelling steps, while F20-21 adds little before the late snap.

At small size: At 148px light makes the transparent band look like a white petal base; dark makes it a wide missing band around the center. This changes the apparent flower design between themes, beyond a normal contrast variation.

Proposed refinement: Keep the broad purple petals and dark center. Repair petal surfaces and distribute cup opening across F13-23, preserving petal count and allowing the cyan-to-purple gradient to follow opening surfaces. Finish with a small final spread rather than repeated redraws.

Suggested progression: F1-5 bean and shoot; F6-10 purple bud and leaves; F11-15 petal tips separate; F16-20 cup widens and center becomes visible; F21-25 petals lower to the open face and settle.

### Constellation blossom

**Targeted.** Five white petals and sparse gold points give a clean, easily understood flower with a restrained celestial motif.

Observed issues:

- F9-10 changes a pointed bud into a shorter star-capped shape abruptly; F11-20 then holds a rounded closed bud with minimal new action.
- F21 opens two side petals, F22 adds more, and F23 is fully open. F24 raises/recloses front petals before F25 returns to the open five-point silhouette.
- F21/F22/F24 show background-revealing holes in white petals, especially the left/top surfaces; cell-boundary specks also appear above the last row.
- At 148px the gold nodes remain visible but fine constellation marks are subtle. F15-16 contributes little beyond swelling.

At small size: At 148px the five-petal shape and gold nodes read in both themes; connecting marks are subtle. The missing white regions in F21/F22/F24 are much more conspicuous on dark. Preserve a sparse gold motif.

Proposed refinement: Keep the five-petal silhouette and restrained gold nodes. Preserve five continuous petal identities, begin separating them earlier, and remove the F24 reclosure. Use a consistent sparse constellation pattern that is uncovered as the petals open, and repair missing white areas.

Suggested progression: F1-5 shoot; F6-10 five-seamed bud; F11-15 first petal pair unfolds; F16-20 remaining petals separate and reveal gold nodes; F21-25 all five petals flatten continuously and settle.

## Smallest next implementation

First recover or rebuild the broken frame layouts and surfaces. Then prototype Coffee flower and Latte bloom: one botanical unfolding and one coffee-specific plume, both preserving the existing final style. These provide different shapes against which to assess continuity and growth before revising all variants. This implementation is proposed and has not been performed.

The value is clearer visible progress during a common waiting phase, with the existing animation appearing in its current location and requiring no extra tap, choice or setting. The main cost is asset authoring and review across 25 frames per variant; changing timing before content is fixed would obscure the remaining causes. Keep the current timer behavior until a coherent revised sequence is available for native replay. Failure should remain a valid, readable static frame without obscuring the timer.

Acceptance should include intact individual cells, stable persistent anatomy, no accidental growth reversal, meaningful changes at the intended display size, clean surfaces in both themes, sufficient margins after correction and halo, and a final drawing that clearly resolves the growth. Then check native replay at actual bloom durations, pause and resume, reduced motion behavior and a smaller screen. The existing geometry check remains necessary but is not sufficient. If small decorative details are unreadable at those sizes, simplify them rather than increasing complexity.

## Review artifacts and source references

- Full structured feedback and source hashes: `bloom-animation-review-2026-10-01.json` beside this document.
- Local frame viewer: `../../.qa-screens/bloom-review-2026-10-01/index.html`.
- Derived evidence and all theme contacts: `../../.qa-screens/bloom-review-2026-10-01/` (local QA artifacts, ignored by Git).
- Renderer: `app/src/main/java/com/adsamcik/starlitcoffee/ui/component/BloomSpritesheetAnimation.kt`.
- Display contexts: `BloomTimerScreen.kt` and `BrewTimerScreen.kt` in `app/src/main/java/com/adsamcik/starlitcoffee/ui/screen/`.
- Current authoring rules: `docs/bloom-spritesheet-splicing.md` and `prompts/bloom-spritesheet-frame-prompts.md`.
- Geometry check run successfully: `tools/verify_bloom_modern_grid.py`.

This is a source and visual design review, not Android execution, runtime performance, accessibility or release validation.
