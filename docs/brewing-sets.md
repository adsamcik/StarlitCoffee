# Brewing sets

A brewing set is one equipment combination: method, filter (where relevant),
and grinder. It remembers its own ratio, calculator expression and selected
quantity. Two sets can use the same method and different equipment, or separate
espresso and filter equipment in the same place. Examples are Home filter,
Home espresso and Work.

The frequent task is switching equipment without re-entering the recipe or
accidentally carrying a filter ratio into espresso. Brew has one set picker
beside the existing ratio control. Equipment appears as a short summary. The
picker opens the same list managed in Settings. Adding a named set is optional;
the first unnamed set uses its method name. The save action on Brew creates a
set from the current equipment and calculator input. It also works before an
amount has been entered.

Onboarding chooses one first method and only relevant equipment. Each method
starts at its own default ratio; espresso starts at 1:2. Settings adds, edits,
selects and deletes sets. An equipment edit preserves the latest amount, ratio
and quantity. Changing a set's method clears its amount and starts at the new
method's default. Deleting the active set selects the first remaining set;
the last set cannot be deleted.

Existing enabled methods become unnamed sets, preserving the previous default
method, method-specific inputs and equipment. Named calculator favorites are
imported once. Existing saved recipes and history remain intact. Deleting an
imported set does not cause it to return on the next launch. Legacy preference
fields remain synchronized for preparation and stable brewer-profile consumers.

Equipment controls disappear when they are irrelevant or lack supported grinder
guidance. A persisted incompatible grinder falls back to no grinder and generic
texture guidance. All set strings exist in the app's 23 supported locales.
Rows expose selection semantics, edit/delete buttons have named accessible
labels, and the editor scrolls when space or text size requires it.

Writes are atomic and calculator updates are ordered. Equipment revisions reject
queued input from an older revision. A failed save keeps the editor open with its
draft; a failed remembered input or selection offers retry. Set restoration is
tested separately from recipe/history persistence, with UI coverage for switching,
editing, deletion, incompatible equipment and restart.

This replaces the global method/default/filter/grinder controls and the separate
calculator-favorites picker. It adds no mandatory naming or extra taps to a brew.
Maintenance cost is one versioned preferences list, a shared editor and ordered
calculator writes. If the list becomes difficult to scan, improve naming and
ordering in this surface before introducing another profile mode or workflow.
