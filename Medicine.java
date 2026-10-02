import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Catalog entry with in-memory stock per dosage; Swing callers use the event dispatch thread. */
public final class Medicine {
    private final String id;
    private final String name;
    private final String category;
    private final List<String> dosageList;
    private final double unitPrice;
    private final BigDecimal price;
    private final String imagePath;
    private final Map<String, Integer> stock = new LinkedHashMap<>();

    public Medicine(String id, String name, String category, List<String> dosageList,
                    double unitPrice, String imagePath) {
        this(id, name, category, dosageList, unitPrice, imagePath, 100);
    }

    /** initialStock is the starting number of units for each dosage. */
    public Medicine(String id, String name, String category, List<String> dosageList,
                    double unitPrice, String imagePath, int initialStock) {
        if (initialStock < 0) {
            throw new IllegalArgumentException("Initial stock cannot be negative.");
        }
        this.id = requireText(id, "Medicine ID");
        this.name = requireText(name, "Medicine name");
        this.category = requireText(category, "Category");
        if (dosageList == null || dosageList.isEmpty()) {
            throw new IllegalArgumentException("At least one dosage is required.");
        }
        List<String> strengths = new ArrayList<>();
        for (String dosage : dosageList) {
            String value = requireText(dosage, "Dosage");
            if (strengths.contains(value)) {
                throw new IllegalArgumentException("Dosages must be unique.");
            }
            strengths.add(value);
            stock.put(value, initialStock);
        }
        this.dosageList = Collections.unmodifiableList(strengths);
        if (!Double.isFinite(unitPrice) || unitPrice < 0) {
            throw new IllegalArgumentException("Unit price must be a finite, nonnegative number.");
        }
        this.price = BigDecimal.valueOf(unitPrice).setScale(2, RoundingMode.HALF_UP);
        this.unitPrice = this.price.doubleValue();
        this.imagePath = imagePath == null ? "" : imagePath;
    }

    private static String requireText(String text, String label) {
        if (text == null || text.strip().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String result = text.strip();
        if (result.codePoints().anyMatch(c -> Character.isISOControl(c)
                || c == 0x2028 || c == 0x2029)) {
            throw new IllegalArgumentException(label + " must be a single line.");
        }
        return result;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public List<String> getDosageList() { return dosageList; }
    public double getUnitPrice() { return unitPrice; }
    public BigDecimal getPrice() { return price; }
    public String getImagePath() { return imagePath; }

    public int getStock(String dosage) {
        Integer remaining = stock.get(dosage);
        if (remaining == null) {
            throw new IllegalArgumentException("Select a valid dosage for this medicine.");
        }
        return remaining;
    }

    public void reduceStock(String dosage, int quantity) {
        int remaining = getStock(dosage);
        if (quantity < 1 || quantity > remaining) {
            throw new IllegalArgumentException("Insufficient stock for " + name + " (" + dosage
                    + "). Remaining: " + remaining + ".");
        }
        stock.put(dosage, remaining - quantity);
    }

    public static List<Medicine> sampleMedicines() {
        return Collections.unmodifiableList(Arrays.asList(
                new Medicine("MED-001", "Atorvastatin", "Cardiovascular / Heart",
                        Arrays.asList("5 mg", "10 mg", "20 mg"), 3.50,
                        "images/atorvastatin.png"),
                new Medicine("MED-002", "Amlodipine", "Cardiovascular / Heart",
                        Arrays.asList("2.5 mg", "5 mg", "10 mg"), 2.25,
                        "images/amlodipine.png"),
                new Medicine("MED-003", "Losartan", "Cardiovascular / Heart",
                        Arrays.asList("25 mg", "50 mg", "100 mg"), 4.10,
                        "images/losartan.png"),
                new Medicine("MED-004", "Salbutamol", "Respiratory / Lung",
                        Arrays.asList("2 mg", "4 mg"), 2.80,
                        "images/salbutamol.png"),
                new Medicine("MED-005", "Montelukast", "Respiratory / Lung",
                        Arrays.asList("4 mg", "5 mg", "10 mg"), 5.40,
                        "images/montelukast.png"),
                new Medicine("MED-006", "Budesonide", "Respiratory / Lung",
                        Arrays.asList("100 mcg", "200 mcg", "400 mcg"), 12.00,
                        "images/budesonide.png"),
                new Medicine("MED-007", "Celecoxib", "Orthopedic",
                        Arrays.asList("100 mg", "200 mg"), 6.25,
                        "images/celecoxib.png"),
                new Medicine("MED-008", "Naproxen", "Orthopedic",
                        Arrays.asList("250 mg", "500 mg"), 3.75,
                        "images/naproxen.png"),
                new Medicine("MED-009", "Diclofenac", "Orthopedic",
                        Arrays.asList("25 mg", "50 mg", "75 mg"), 3.20,
                        "images/diclofenac.png")
        ));
    }
}
