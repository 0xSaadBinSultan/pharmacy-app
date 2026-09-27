import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** An immutable, printable record of a confirmed local sale. */
public final class Receipt {
    private static final String RULE = "----------------------------------------------------------";
    private final String id;
    private final String text;
    private final BigDecimal total;

    static final class Item {
        final String name;
        final String dosage;
        final int quantity;
        final BigDecimal unitPrice;
        final BigDecimal total;

        Item(String name, String dosage, int quantity, BigDecimal unitPrice, BigDecimal total) {
            this.name = name;
            this.dosage = dosage;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.total = total;
        }
    }

    Receipt(String customer, String payment, List<Item> items, BigDecimal subtotal,
            BigDecimal tax, BigDecimal total) {
        ZonedDateTime now = ZonedDateTime.now();
        this.id = "RX-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        this.total = total;
        List<Item> snapshot = Collections.unmodifiableList(new ArrayList<>(items));
        StringBuilder receipt = new StringBuilder();
        receipt.append("                  PHARMACY RECEIPT\n")
                .append("                  Local demo sale\n")
                .append(RULE).append('\n')
                .append("Receipt:  ").append(id).append('\n')
                .append("Date:     ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")))
                .append('\n');
        appendWrapped(receipt, "Customer: ", customer, RULE.length());
        receipt.append("Payment:  ").append(payment).append('\n')
                .append(RULE).append('\n')
                .append(String.format(Locale.US, "%-23s %5s %12s %14s%n", "Dosage", "Qty", "Unit price", "Line total"));
        for (Item item : snapshot) {
            appendWrapped(receipt, "", item.name, RULE.length());
            if (item.dosage.length() > 23) {
                appendWrapped(receipt, "", item.dosage, RULE.length());
                receipt.append(String.format(Locale.US, "%-23s %5d %12s %14s%n", "", item.quantity,
                        CartModel.money(item.unitPrice), CartModel.money(item.total)));
            } else {
                receipt.append(String.format(Locale.US, "%-23s %5d %12s %14s%n", item.dosage,
                        item.quantity, CartModel.money(item.unitPrice), CartModel.money(item.total)));
            }
            receipt.append('\n');
        }
        receipt.append(RULE).append('\n')
                .append(String.format(Locale.US, "%-42s %15s%n", "Subtotal", CartModel.money(subtotal)))
                .append(String.format(Locale.US, "%-42s %15s%n", "Tax (5%)", CartModel.money(tax)))
                .append(String.format(Locale.US, "%-42s %15s%n", "GRAND TOTAL", CartModel.money(total)))
                .append(RULE).append('\n')
                .append("Thank you for your purchase.\n")
                .append("Demo record only. No external payment was processed.\n");
        this.text = receipt.toString();
    }

    private static void appendWrapped(StringBuilder out, String prefix, String value, int width) {
        String indentation = String.join("", Collections.nCopies(prefix.length(), " "));
        String currentPrefix = prefix;
        int position = 0;
        while (position < value.length()) {
            int end = Math.min(value.length(), position + width - currentPrefix.length());
            out.append(currentPrefix).append(value, position, end).append('\n');
            position = end;
            currentPrefix = indentation;
        }
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public BigDecimal getTotal() { return total; }
}
