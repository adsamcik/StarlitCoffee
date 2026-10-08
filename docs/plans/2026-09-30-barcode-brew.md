# Scan to brew

## Product decision

Scan to brew connects the existing barcode camera to the Brew calculator. It
matches saved inventory locally and continues into the existing preparation flow
when a valid amount has been entered. With no amount, it selects the coffee and
asks the user to set an amount before pressing Brew. Selecting alone does not
open a sealed bag; entering preparation uses the existing bag-opening behavior.

- **User value:** removes searching through the bean inventory when the physical
  bag is already at hand. A unique match takes one scan action after setting the
  desired amount.
- **Target scenario:** recurring preparation using coffee already tracked in the
  app, especially when several coffees are in stock.
- **Default behavior:** show the scan action only when an in-stock bag has a saved
  barcode. Match open or sealed bags with positive or unknown remaining weight.
  Finished and empty bags cannot be selected through this action.
- **Discoverability:** a labeled Scan to brew chip beside the calculator's coffee
  selection, using the established barcode icon.
- **Core-flow impact:** the amount calculator and Brew button keep their usual
  behavior. The contextual chip adds no required steps or settings.
- **Configuration decision:** no toggle, scan mode, or barcode lookup service.
  Reuse the existing scanner and navigation result channel.
- **Failure behavior:** a missing match explains how to check the saved barcode
  and links to Your Beans. Multiple matches ask which physical bag is in use,
  showing status, weight and roast date where known. Canceling has no selection
  effect. Camera permission uses the existing scanner's behavior.
- **Accessibility impact:** a labeled action and standard dialogs and buttons;
  duplicate choices include text details instead of relying on an image or color.
- **Technical cost:** a small matching policy, contextual entry component,
  navigation wiring, and four prompts across all 23 supported languages. No
  database migration, network lookup, or new camera lifecycle is introduced.
- **Removal criteria:** consolidate the entry into the coffee selector if that
  selector becomes the calculator's primary coffee action. Reconsider visibility
  if scanning is rarely used or makes small-screen preparation harder.

## Matching and state

Numeric UPC/EAN/GTIN forms use leading-zero padding for the same product code.
Formatted numeric codes are accepted; custom identifiers require a full exact
match. Barcode prefixes and digits embedded in a QR URL never imply a match.
Every matching physical bag remains available for an explicit choice.

Results return to the calculator's own navigation entry and are consumed once.
Pending choices retain their barcode through screen recreation and wait until
inventory loading finishes. A bag's restored method is applied to the calculator
before snapshotting an amount, including an estimated in-cup target.
If the restored method cannot support that target and the calculator changes the
quantity being entered, automatic continuation stops so the user can review the
amount and press Brew.

## Verification boundary

Validated against the intentionally upgraded dependency catalog on 2026-09-30:

- Debug, release and Android test Kotlin compilation passed.
- The full JVM suite passed: 1,380 tests passed and four were skipped. This
  includes barcode matching, resource parity and static logging-template checks.
- Debug APK assembly and Detekt passed.

A live camera scan, permission denial, rotation while choosing a bag and a real
brew remain device checks. Local builds and unit tests do not prove those
interactions.
