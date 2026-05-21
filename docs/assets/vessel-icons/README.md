# Vessel Icons

Full-color PNG coffee vessel icons for cup presets.

The app uses PNG resources directly because they are smaller and more faithful
than the traced VectorDrawable set for this artwork. The optimized app assets
live in:

```text
app/src/main/res/drawable-nodpi/vessel_icon_<name>.png
```

The `drawable-nodpi` folder is intentional: these icons are already rendered as
256 x 256 transparent PNGs, and the Compose UI scales them into the requested
button or display size.

## Source Assets

- `vessel_icon_contact_sheet.png`: generic/base vessels.
- `vessel_icon_specialty_contact_sheet.png`: double-wall glasses, specialty servers, carafes, and tasting vessels.
- `png/`: processed base PNG sources.
- `specialty-png/`: processed specialty PNG sources.
- `vessel_icon_processed_sheet.png`: preview sheet for base processed PNGs.
- `vessel_icon_specialty_processed_sheet.png`: preview sheet for specialty processed PNGs.

## App Copy

When replacing the processed PNGs, copy each `vessel_icon_<name>_source.png`
into `app/src/main/res/drawable-nodpi/` as `vessel_icon_<name>.png`.

The app wiring is centralized in:

```text
app/src/main/java/com/adsamcik/starlitcoffee/ui/util/PresetIcon.kt
```

`PresetIcon` renders these assets with Compose `Image` and `ContentScale.Fit`,
so the PNGs keep their full-color appearance without accidental icon tinting.
