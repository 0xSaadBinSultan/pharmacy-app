import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.table.AbstractTableModel;

/** Cart data and totals; Swing callers mutate this model on the event dispatch thread. */
public final class CartModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"Item", "Dosage", "Qty", "Unit Price", "Total"};
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.05");
    @SuppressWarnings("serial") // This in-memory table model is not stored with Java serialization.
    private final List<Line> lines = new ArrayList<>();

    private static final class Line {
        final Medicine medicine;
        final String dosage;
        int quantity;

        Line(Medicine medicine, String dosage, int quantity) {
            this.medicine = medicine;
            this.dosage = dosage;
            this.quantity = quantity;
        }

        BigDecimal total() {
            return medicine.getPrice().multiply(BigDecimal.valueOf(quantity));
        }
    }

    @Override public int getRowCount() { return lines.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }

    @Override public Class<?> getColumnClass(int column) {
        switch (column) {
            case 0:
            case 1: return String.class;
            case 2: return Integer.class;
            case 3:
            case 4: return BigDecimal.class;
            default: throw new IndexOutOfBoundsException("Invalid cart column: " + column);
        }
    }

    @Override public boolean isCellEditable(int row, int column) { return false; }

    @Override public Object getValueAt(int row, int column) {
        Line line = lines.get(row);
        switch (column) {
            case 0: return line.medicine.getName();
            case 1: return line.dosage;
            case 2: return line.quantity;
            case 3: return line.medicine.getPrice();
            case 4: return line.total();
            default: throw new IndexOutOfBoundsException("Invalid cart column: " + column);
        }
    }

    public void add(Medicine medicine, String dosage, int quantity) {
        if (medicine == null) {
            throw new IllegalArgumentException("Select a medicine.");
        }
        if (!medicine.getDosageList().contains(dosage)) {
            throw new IllegalArgumentException("Select a valid dosage for this medicine.");
        }
        if (quantity < 1 || quantity > 100) {
            throw new IllegalArgumentException("Quantity must be between 1 and 100.");
        }
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.medicine.getId().equals(medicine.getId()) && line.dosage.equals(dosage)) {
                if (line.quantity + quantity > 100) {
                    throw new IllegalArgumentException("Maximum 100 units per medicine and dosage. "
                            + "This line already contains " + line.quantity + ".");
                }
                requireStock(line.medicine, dosage, line.quantity + quantity);
                line.quantity += quantity;
                fireTableRowsUpdated(i, i);
                return;
            }
        }
        requireStock(medicine, dosage, quantity);
        lines.add(new Line(medicine, dosage, quantity));
        int last = lines.size() - 1;
        fireTableRowsInserted(last, last);
    }

    public void remove(int modelRow) {
        lines.remove(modelRow);
        fireTableRowsDeleted(modelRow, modelRow);
    }

    public void clear() {
        int size = lines.size();
        if (size > 0) {
            lines.clear();
            fireTableRowsDeleted(0, size - 1);
        }
    }

    public boolean isEmpty() { return lines.isEmpty(); }

    public int itemCount() {
        int total = 0;
        for (Line line : lines) {
            total += line.quantity;
        }
        return total;
    }

    public BigDecimal getSubtotal() {
        BigDecimal subtotal = ZERO;
        for (Line line : lines) {
            subtotal = subtotal.add(line.total());
        }
        return subtotal;
    }

    public BigDecimal getTax() {
        return getSubtotal().multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotal() { return getSubtotal().add(getTax()); }

    public static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(value);
    }

    private static void requireStock(Medicine medicine, String dosage, int quantity) {
        int remaining = medicine.getStock(dosage);
        if (quantity > remaining) {
            throw new IllegalArgumentException("Insufficient stock for " + medicine.getName()
                    + " (" + dosage + "). Remaining: " + remaining
                    + "; requested in cart: " + quantity + ".");
        }
    }

    /** Confirm one sale: validate everything before deducting stock, then clear the cart. */
    public Receipt checkout(String customer, String payment) {
        Receipt receipt = createReceipt(customer, payment);
        for (Line line : lines) {
            requireStock(line.medicine, line.dosage, line.quantity);
        }
        for (Line line : lines) {
            line.medicine.reduceStock(line.dosage, line.quantity);
        }
        clear();
        return receipt;
    }

    /** Create a preview snapshot without changing stock or cart contents. */
    public Receipt createReceipt(String customer, String payment) {
        if (isEmpty()) {
            throw new IllegalArgumentException("Add at least one medicine before checkout.");
        }
        if (customer == null || customer.strip().isEmpty()) {
            throw new IllegalArgumentException("Enter the customer name.");
        }
        if (customer.codePoints().anyMatch(c -> Character.isISOControl(c)
                || c == 0x2028 || c == 0x2029)) {
            throw new IllegalArgumentException("Customer name must be a single line without control characters.");
        }
        String trimmedName = customer.strip();
        if (trimmedName.length() > 80) {
            throw new IllegalArgumentException("Customer name must be 80 characters or fewer.");
        }
        if (!"Cash".equals(payment) && !"Card".equals(payment)) {
            throw new IllegalArgumentException("Select Cash or Card as the payment method.");
        }
        List<Receipt.Item> items = new ArrayList<>();
        for (Line line : lines) {
            items.add(new Receipt.Item(line.medicine.getName(), line.dosage,
                    line.quantity, line.medicine.getPrice(), line.total()));
        }
        return new Receipt(trimmedName, payment, items, getSubtotal(), getTax(), getTotal());
    }
}
