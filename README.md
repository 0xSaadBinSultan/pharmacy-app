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
- Selected-row removal, confirmed cart clearing, subtotal, 5% tax, and grand total.
- Checkout with required customer name, Cash/Card selection, and itemized order preview. Cancel preserves the cart.
- Payment confirmation produces an immutable receipt and clears the order. Receipts can be printed, saved as UTF-8 text, and reopened using Last receipt.
- Responsive card columns, resizable split panes, keyboard-accessible native controls, and Ctrl+F to focus search.

## Source Files

- `PharmacyApp.java`: dashboard, catalog filtering, checkout dialogs, and receipt printing/saving.
- `Medicine.java`: validated immutable medicine data and sample catalog.
- `MedicineImages.java`: cached local images with aspect-preserving scaling and generated fallback artwork.
- `CartModel.java`: cart table, quantity validation, decimal monetary calculations, and receipt creation.
- `Receipt.java`: immutable transaction snapshot and plain-text receipt formatting.
- `ModelTest.java`: executable regression checks with no test framework dependency.
- `UiSmokeTest.java`: desktop integration checks and screenshots for catalog and checkout flows.

## Images and Sample Data

Edit `Medicine.sampleMedicines()` to change the catalog. Each medicine has an `imagePath` accepting a local file path; relative paths resolve from the working directory. Missing, unreadable, or unsupported images receive a generated AWT medicine icon. No image downloads or UI libraries are required.

Prices use USD. Financial calculations use `BigDecimal` and round tax once on the order subtotal to two decimal places using `HALF_UP`. Sample prices are per unit and shared across strengths of the same medicine. Sample medicines, strengths, and prices are demonstration data.

This application stores its catalog, cart, and latest receipt in memory. Restarting resets them; export receipts to retain them. Cash/Card records a simulated payment choice; no payment gateway is contacted or charged. No inventory database, prescription validation, or clinical decision support is included.

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
