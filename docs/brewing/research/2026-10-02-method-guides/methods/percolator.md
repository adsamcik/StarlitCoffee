# Percolator: a first pot with the Presto 02822

Editorially reviewed, 2026-10-02. English research content; no physical brew test.

A percolator sends hot liquid up a central tube, through a basket of grounds, and back into the pot repeatedly. This guide covers the **Presto 02822 six-cup electric percolator**; the stovetop variant uses different controls. Do not apply this procedure to a moka pot. [Presto product information](https://www.gopresto.com/product/presto-6-cup-stainless-steel-coffee-maker-02822)

## Equipment and starting recipe

Use the 02822 with its matching cord, perk tube/washer, basket, basket lid and cover; compatible wraparound paper; a measuring tablespoon; coarse percolator-ground coffee; and cold water. It requires **120 V AC**, indoors, on a stable counter away from a stove. [Current Presto manual, Form 72-875J](https://www.gopresto.com/uploads/02822_72_875J_SCM_Eng_Web_Instructions.pdf)

Start with **cold water to the internal 4 mark and 4 level measuring tablespoons of coffee**. This dose is app-authored, within Presto's published 3–7 tablespoon range for that mark. Adjust it to taste. The **2–6 marks** bound this model's batches; the manual pairs 2 with 2–3 tablespoons and 6 with 5–8. Re-check the matching dose when changing fill.

Presto's nominal cup is about **5 US fluid ounces of brewed coffee**, not a mug. Four nominal cups describe about 20 fluid ounces of coffee; they do **not** establish an exact input-water measurement or collected yield. Coffee grams, water grams/milliliters and mass ratio remain unknown. A tablespoon's coffee mass depends on the beans and grind. [Presto manual](https://www.gopresto.com/uploads/02822_72_875J_SCM_Eng_Web_Instructions.pdf)

## Brew the pot

1. **Prepare while unplugged and cool.** Before first use, wash and rinse the interior, cover and basket assembly. Keep the base/socket dry. The tube's bottom washer must move freely.
2. **Add cold water to 4.** Remove the assembly before filling. Use the mark inside the pot, not four household cups. Starting cold matters: Presto warns that a warm appliance can produce weak coffee on consecutive batches.
3. **Load the basket.** Fit the paper's center hole over its stem. Add four level measuring tablespoons, blocking the stem opening with a finger so grounds cannot enter it. Fold the wraparound paper over the coffee and tuck its excess inside. Coarse percolator grind is our choice; the manual also permits regular grind. Equal Exchange explains why its percolator coffee is coarser than its drip coffee. [Equal Exchange's percolator guide](https://equalexchange.coop/sites/default/files/brewingtips.pdf)
4. **Seat and close.** Put the basket lid on. Seat the tube in the bottom perk well, hold it there, and slip the filled basket onto it. Press the pot cover firmly into place. A misplaced tube or stuck washer can prevent circulation.
5. **Connect and observe.** Attach the cord to the appliance, then the wall outlet. Keep the cover closed. The **ready light** signals completion; temperature is machine-controlled. The advertised approximate cup-a-minute speed is not a deadline.
6. **Disconnect, then serve.** Disconnect at the wall first, then the appliance, before lifting the pot. Hold its handle and keep the cover secured while pouring. Optional keep-warm operates while the pot remains stationary and plugged in: readiness does not mean power is off. For longer service, transfer coffee to a thermal carafe. [Presto manual](https://www.gopresto.com/uploads/02822_72_875J_SCM_Eng_Web_Instructions.pdf), [Equal Exchange](https://equalexchange.coop/sites/default/files/brewingtips.pdf)

## Correct the next pot

For weak coffee, first check the measuring spoon, cold start, clean perk well, moving washer and seated tube. Then adjust only dose within the four-mark range. For sediment, check paper fit and grounds entering the stem before changing grind. Avoid fine powder; try coarser coffee next time if needed. For harsh coffee, serve sooner and check cleanliness before reducing dose. Do not open the hot pot or restart its cycle to repair flavor. [Presto manual](https://www.gopresto.com/uploads/02822_72_875J_SCM_Eng_Web_Instructions.pdf), [Equal Exchange](https://equalexchange.coop/sites/default/files/brewingtips.pdf)

## Stovetop variant and cleanup

The **COLETTI Bozeman** uses external heat, not the Presto's light. Its maker specifies low-to-medium heat, water no higher than the pouring holes, coarse coffee and a tablespoon per nominal cup. Visible pulses in the glass knob start its **4–7 minute** perking window, separate from heating time. Regulate heat for steady perking rather than aggressive boiling, then remove from heat at the chosen stopping point. Adjust that point between batches; neither seven minutes nor a particular temperature is universal. Check the exact model's hob compatibility: COLETTI distinguishes its induction model. [COLETTI support](https://coletticoffee.com/pages/percolator-support), [COLETTI FAQ](https://coletticoffee.com/pages/faq)

For the Presto, **never energize an empty pot**, put it on a burner, or remove its lid during brewing. Unplug and let it cool before dismantling. Wash its interior and removable parts; only the tube, basket and basket lid are identified as dishwasher-compatible. Never immerse the appliance, cord or plugs, or wet its base/socket. Follow its manual for periodic residue removal; stovetop cleaning advice does not replace electric-appliance precautions. [Presto manual](https://www.gopresto.com/uploads/02822_72_875J_SCM_Eng_Web_Instructions.pdf)

## App-audit appendix

At baseline `490fc2d4`, `BrewMethod.kt`, `domain/brewing/BrewingCatalog.kt`, exact recipes, typed plans and guidance contain no Percolator entry. This is proposed support, not an existing-guide defect.

`Recipe.kt` requires coffee mass; `Equipment.kt` stores gram capacities. Native marks/tablespoons need unit support or measured calibration. Keep output unknown using `UserMeasuredOutput`. Existing `StagePlan.kt` actions and observed/manual completion cover the tasks; the exact-recipe completion enum needs a ready-indicator meaning. Grinder “percolation” notes describe extraction generally. No decaf shortcut is evidenced.

## Unresolved evidence

The indexed older Presto 72-875H link returned 404; the maker's manual finder supplied verified 72-875J. Equal Exchange uses six-ounce coffee servings, so its batch table cannot replace Presto's five-ounce marks. No exact grinder setting, measured cycle temperature, collected yield, or physical brew test is available. This is English research content; no physical brewing or app execution was tested.
