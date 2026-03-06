# Test Data

This directory contains test assets for instrumented tests. The actual image files
are **not committed** — each developer symlinks to their local copy.

## Setup

### 1. Create the symlink

The `coffee-bags/` folder should point to your local coffee bag images directory.

**Windows** (requires admin or Developer Mode):
```cmd
mklink /D testdata\coffee-bags "C:\path\to\your\Coffee bags"
```

**Linux/macOS:**
```bash
ln -s /path/to/your/coffee-bags testdata/coffee-bags
```

### 2. Image naming convention

Images follow the pattern: `roaster_coffee_name_{front|back}.jpg`

| File | Description |
|------|-------------|
| `beansmiths_ethiopia_gedeb_front.jpg` | Front label |
| `beansmiths_ethiopia_gedeb_back.jpg` | Back label |
| `muntasha_ethiopia_natural_front.jpg` | Front only (sample bag) |

Front/back pairs are grouped by shared prefix (everything before `_front` / `_back`).

### 3. Push images to emulator

```bash
./gradlew pushTestImages
```

This pushes all images from `testdata/coffee-bags/` to `/data/local/tmp/coffee-bags/`
on the connected device or emulator.

### 4. Run instrumented tests

```bash
./gradlew connectedDebugAndroidTest
```
