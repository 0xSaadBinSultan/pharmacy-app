# Pharmacy Management System

A complete Java Swing desktop demo with a searchable medicine catalog, dosage-specific cart, 5% tax, and customer checkout. Uses only Java Swing, AWT, and the standard library. Requires JDK 11 or newer and a graphical desktop.

## Run

From this directory:

```sh
javac --release 11 -d out *.java
java -cp out PharmacyApp
```

Or use the included runnable JAR:

```sh
java -jar PharmacyManagement.jar
```

## Features

- Nine sample medicines across Heart, Lung, and Orthopedic categories.
- Instant, case-insensitive search by name or category, combined with category filtering.
- Medicine cards with 80 x 80 thumbnails, dosage selection, quantities from 1 to 100, prices, and add-to-cart actions.
- Cart lines merge only when medicine ID and dosage match. Each line allows at most 100 units.
- Remaining stock is shown on each medicine card for the selected dosage. Each sample dosage starts with 100 units; out-of-stock dosages cannot be added.
- Selected-row removal, confirmed cart clearing, subtotal, 5% tax, and grand total.
- Checkout with required customer name, Cash/Card selection, and itemized order preview. Cancel preserves the cart.
- Payment confirmation produces an immutable receipt and clears the order. Receipts can be printed, saved as UTF-8 text, and reopened using Last receipt.
- Confirmed checkout deducts the purchased quantity from stock and refreshes the catalog. Adding, removing, clearing, cancelling, and rejected checkout leave stock unchanged. Cart additions and checkout both validate stock to prevent overselling.
- Responsive card columns, resizable split panes, keyboard-accessible native controls, and Ctrl+F to focus search.

## Source Files

- `PharmacyApp.java`: dashboard, catalog filtering, checkout dialogs, and receipt printing/saving.
- `Medicine.java`: validated medicine data, stock per dosage, and sample catalog.
- `MedicineImages.java`: cached local images with aspect-preserving scaling and generated fallback artwork.
- `CartModel.java`: cart table, quantity/stock validation, decimal monetary calculations, receipt creation, and checkout stock deduction.
- `Receipt.java`: immutable transaction snapshot and plain-text receipt formatting.
- `ModelTest.java`: executable regression checks with no test framework dependency.
- `UiSmokeTest.java`: desktop integration checks and screenshots for catalog and checkout flows.

## Images and Sample Data

Edit `Medicine.sampleMedicines()` to change the catalog. Each medicine has an `imagePath` accepting a local file path; relative paths resolve from the working directory. Missing, unreadable, or unsupported images receive a generated AWT medicine icon. No image downloads or UI libraries are required.

To change starting stock, use the `Medicine` constructor's optional final integer argument, for example `new Medicine("MED-001", "Atorvastatin", "Cardiovascular / Heart", Arrays.asList("5 mg", "10 mg", "20 mg"), 3.50, "images/atorvastatin.png", 150)`. It sets 150 units independently for each dosage. Stock is deducted only through `CartModel.checkout(...)`; `createReceipt(...)` creates a snapshot without changing inventory.

Prices use USD. Financial calculations use `BigDecimal` and round tax once on the order subtotal to two decimal places using `HALF_UP`. Sample prices are per unit and shared across strengths of the same medicine. Sample medicines, strengths, and prices are demonstration data.

This application stores its catalog, stock, cart, and latest receipt in memory. Restarting resets them; export receipts to retain them. Cash/Card records a simulated payment choice; no payment gateway is contacted or charged. No inventory database, prescription validation, or clinical decision support is included.

## Check

```sh
javac --release 11 -Xlint:all -d out *.java
java -Djava.awt.headless=true -cp out ModelTest
```

On Linux with Xvfb installed, run the desktop checks in an isolated display:

```sh
xvfb-run -a -s '-screen 0 1600x1000x24' java -cp out UiSmokeTest
```

On a normal graphical desktop, `java -cp out UiSmokeTest` also works. Screenshots are written to `artifacts/`.

## Rebuild the JAR

```sh
jar --create --file PharmacyManagement.jar --main-class PharmacyApp -C out .
```
