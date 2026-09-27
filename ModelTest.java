import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.event.TableModelEvent;

/** Dependency-free model regression checks: java -ea ModelTest */
public final class ModelTest {
    private static int checks;

    public static void main(String[] args) {
        testCatalog();
        testRounding();
        testCartLinesAndEvents();
        testCheckout();
        System.out.println("PASS: " + checks + " model checks");
    }

    private static void testCatalog() {
        List<Medicine> catalog = Medicine.sampleMedicines();
        check(catalog.size() >= 8, "At least eight sample medicines");
        check(catalog.stream().map(Medicine::getCategory).distinct().count() == 3,
                "All three categories represented");
        List<String> source = new ArrayList<>(Arrays.asList("5 mg", "10 mg"));
        Medicine medicine = new Medicine("T", "Test", "Orthopedic", source, 1.005, null);
        source.clear();
        check(medicine.getDosageList().size() == 2, "Dosages copied on construction");
        expect(UnsupportedOperationException.class, () -> medicine.getDosageList().add("20 mg"));
        equalMoney("1.01", medicine.getPrice(), "Unit price rounds to cents");
        check(medicine.getImagePath().isEmpty(), "Null image paths normalize safely");
        expect(IllegalArgumentException.class,
                () -> new Medicine("T", "Test", "Orthopedic", source, 1, ""));
        expect(IllegalArgumentException.class,
                () -> new Medicine("T", "Test", "Orthopedic", Arrays.asList("5 mg"), Double.NaN, ""));
        expect(IllegalArgumentException.class,
                () -> new Medicine("T", "Test", "Orthopedic", Arrays.asList("5 mg"), -1, ""));
    }

    private static void testRounding() {
        CartModel cart = new CartModel();
        equalMoney("0.00", cart.getTotal(), "Empty cart total");
        Medicine medicine = new Medicine("CENT", "Cent Test", "Orthopedic",
                Arrays.asList("5 mg"), 0.10, "");
        cart.add(medicine, "5 mg", 1);
        equalMoney("0.10", cart.getSubtotal(), "Exact decimal subtotal");
        equalMoney("0.01", cart.getTax(), "Half-cent tax rounds up");
        equalMoney("0.11", cart.getTotal(), "Exact decimal total");
        cart.add(medicine, "5 mg", 2);
        equalMoney("0.30", cart.getSubtotal(), "Repeated adds have no floating point drift");
        equalMoney("0.02", cart.getTax(), "Tax rounds once on subtotal");
        equalMoney("0.32", cart.getTotal(), "Rounded tax added to subtotal");
        check("$0.32".equals(CartModel.money(cart.getTotal())), "Currency formatting");
    }

    private static void testCartLinesAndEvents() {
        CartModel cart = new CartModel();
        Medicine medicine = Medicine.sampleMedicines().get(0);
        List<TableModelEvent> events = new ArrayList<>();
        cart.addTableModelListener(events::add);
        cart.add(medicine, "5 mg", 2);
        check(events.get(0).getType() == TableModelEvent.INSERT, "Insert table event");
        cart.add(medicine, "5 mg", 3);
        check(cart.getRowCount() == 1 && cart.itemCount() == 5, "Matching dosage merges");
        check(events.get(1).getType() == TableModelEvent.UPDATE, "Merge table event");
        cart.add(medicine, "10 mg", 1);
        check(cart.getRowCount() == 2 && cart.itemCount() == 6, "Different dosages remain separate");
        check(cart.getColumnCount() == 5, "Five cart columns");
        check(cart.getColumnClass(2) == Integer.class && cart.getValueAt(0, 2) instanceof Integer,
                "Numeric quantity column");
        check(cart.getColumnClass(3) == BigDecimal.class && cart.getValueAt(0, 4) instanceof BigDecimal,
                "Numeric money columns");
        check(!cart.isCellEditable(0, 2), "Cart has no unvalidated inline edits");
        cart.add(medicine, "5 mg", 95);
        check((Integer) cart.getValueAt(0, 2) == 100, "Maximum line quantity accepted");
        int eventCount = events.size();
        expect(IllegalArgumentException.class, () -> cart.add(medicine, "5 mg", 1));
        check((Integer) cart.getValueAt(0, 2) == 100 && eventCount == events.size(),
                "Rejected merge leaves cart and events unchanged");
        expect(IllegalArgumentException.class, () -> cart.add(medicine, "10 mg", 0));
        expect(IllegalArgumentException.class, () -> cart.add(medicine, "10 mg", 101));
        expect(IllegalArgumentException.class, () -> cart.add(medicine, "unknown", 1));
        expect(IllegalArgumentException.class, () -> cart.add(null, "10 mg", 1));
        cart.remove(0);
        check(cart.getRowCount() == 1 && cart.itemCount() == 1, "Remove selected model row");
        check(events.get(events.size() - 1).getType() == TableModelEvent.DELETE, "Remove table event");
        cart.clear();
        check(cart.isEmpty() && cart.itemCount() == 0, "Clear resets cart");
        equalMoney("0.00", cart.getTotal(), "Clear resets totals");
        int clearedEventCount = events.size();
        cart.clear();
        check(events.size() == clearedEventCount, "Repeated clear is harmless");
    }

    private static void testCheckout() {
        CartModel cart = new CartModel();
        expect(IllegalArgumentException.class, () -> cart.createReceipt("Customer", "Cash"));
        cart.add(Medicine.sampleMedicines().get(0), "5 mg", 2);
        expect(IllegalArgumentException.class, () -> cart.createReceipt("   ", "Cash"));
        expect(IllegalArgumentException.class, () -> cart.createReceipt(null, "Cash"));
        expect(IllegalArgumentException.class, () -> cart.createReceipt("Customer\nOther", "Cash"));
        expect(IllegalArgumentException.class, () -> cart.createReceipt("\nCustomer", "Cash"));
        expect(IllegalArgumentException.class, () -> cart.createReceipt("X".repeat(81), "Cash"));
        expect(IllegalArgumentException.class, () -> cart.createReceipt("Customer", "Transfer"));
        check(cart.itemCount() == 2, "Failed checkout preserves cart");
        Receipt receipt = cart.createReceipt("  Alex Customer  ", "Cash");
        Receipt secondReceipt = cart.createReceipt("Alex Customer", "Card");
        check(!receipt.getId().equals(secondReceipt.getId()), "Receipt identifiers unique");
        check(cart.itemCount() == 2, "Receipt creation waits for caller to clear cart");
        check(receipt.getText().contains("Customer: Alex Customer\n"), "Customer name trimmed");
        check(receipt.getText().contains("Atorvastatin") && receipt.getText().contains("5 mg")
                && receipt.getText().contains("$3.50") && receipt.getText().contains("$7.00"),
                "Receipt itemized with dosage and prices");
        check(receipt.getText().contains("Payment:  Cash") && secondReceipt.getText().contains("Payment:  Card"),
                "Both payment methods supported");
        equalMoney("7.35", receipt.getTotal(), "Receipt total");
        String snapshot = receipt.getText();
        cart.clear();
        cart.add(Medicine.sampleMedicines().get(1), "2.5 mg", 8);
        check(snapshot.equals(receipt.getText()), "Receipt remains unchanged after cart reset and reuse");
        equalMoney("7.35", receipt.getTotal(), "Receipt total independent of live cart");
        check(!receipt.getText().contains("Amlodipine"), "Receipt has no later cart entries");
    }

    private static void equalMoney(String expected, BigDecimal actual, String message) {
        check(new BigDecimal(expected).equals(actual), message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        checks++;
        try {
            action.run();
        } catch (Throwable ex) {
            if (type.isInstance(ex)) {
                return;
            }
            throw new AssertionError("Expected " + type.getSimpleName() + ", got " + ex, ex);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
