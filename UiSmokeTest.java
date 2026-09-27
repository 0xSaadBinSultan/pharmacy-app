import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Standalone Swing workflow checks. Run with a display or xvfb-run. */
public final class UiSmokeTest {
    private static PharmacyApp app;
    private static int assertions;

    private UiSmokeTest() { }

    public static void main(String[] args) throws Exception {
        try {
            edt(() -> {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                app = new PharmacyApp();
                app.setSize(1420, 880);
                app.setVisible(true);
                return null;
            });
            waitFor(() -> component(app, "catalogGrid", JPanel.class).getWidth() > 0);
            check(edt(() -> cardCount()) == 9, "Nine sample medicines");
            check(!edt(() -> button(app, "checkoutButton").isEnabled()), "Empty checkout disabled");
            check(!edt(() -> button(app, "clearCartButton").isEnabled()), "Empty clear disabled");
            check(!edt(() -> button(app, "removeButton").isEnabled()), "Unselected remove disabled");
            validateLayout();
            capture(app, "dashboard-large.png");
            resize(1040, 660);
            validateLayout();
            capture(app, "dashboard-compact.png");
            resize(1420, 880);

            filter("aToRvAsTaTiN", "All categories");
            check(edt(() -> cardCount()) == 1, "Case-insensitive name search");
            filter("lung", "All categories");
            check(edt(() -> cardCount()) == 3, "Category text search");
            filter("lung", "Heart");
            check(edt(() -> cardCount()) == 0, "Search and category intersection");
            check(edt(() -> component(app, "catalogCount", JLabel.class).getText()).equals("0 of 9"),
                    "No-match count");
            filter("", "Orthopedic");
            check(edt(() -> cardCount()) == 3, "Category filter");
            filter("", "All categories");

            add("MED-001", "10 mg", "3");
            check(cell(0, 1).equals("10 mg"), "Selected dosage retained");
            check(cell(0, 2).equals(3), "Typed spinner quantity committed");
            add("MED-001", "10 mg", "2");
            check(rows() == 1 && cell(0, 2).equals(5), "Same dosage merged");
            add("MED-001", "20 mg", "1");
            check(rows() == 2, "Different dosage stays separate");
            add("MED-004", "4 mg", "2");
            check(rows() == 3, "Different medicine added");
            edt(() -> {
                component(app, "cartTable", JTable.class).setRowSelectionInterval(1, 1);
                button(app, "removeButton").doClick();
                return null;
            });
            check(rows() == 2 && cell(1, 0).equals("Salbutamol"), "Selected line removed");
            check(edt(() -> component(app, "grandTotal", JLabel.class).getText()).equals("$24.26"),
                    "Grand total includes rounded tax");
            capture(app, "cart-large.png");
            resize(1040, 660);
            validateLayout();
            capture(app, "cart-compact.png");
            resize(1420, 880);

            for (String invalid : new String[] {"invalid", "1.5", "101"}) {
                edt(() -> {
                    JSpinner spinner = component(app, "quantity-MED-001", JSpinner.class);
                    ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(invalid);
                    return null;
                });
                clickAsync(app, "add-MED-001");
                JDialog error = waitForDialogTitle("Order could not be updated");
                check(rows() == 2, "Invalid spinner cannot mutate cart: " + invalid);
                edt(() -> { error.dispose(); return null; });
                waitFor(() -> !error.isShowing());
            }
            edt(() -> {
                component(app, "quantity-MED-001", JSpinner.class).setValue(1);
                return null;
            });

            JDialog checkout = checkout();
            check(edt(() -> component(checkout, "orderPreview", JTextArea.class).getText())
                    .contains("$24.26"), "Itemized preview has current total");
            clickAsync(checkout, "cancelCheckout");
            waitFor(() -> !checkout.isDisplayable());
            check(rows() == 2, "Checkout cancellation preserves cart");

            JDialog cashCheckout = checkout();
            clickAsync(cashCheckout, "confirmPayment");
            waitFor(() -> !component(cashCheckout, "checkoutValidation", JLabel.class)
                    .getText().isBlank());
            check(rows() == 2 && edt(cashCheckout::isShowing), "Blank customer rejected without loss");
            edt(() -> {
                component(cashCheckout, "customerName", JTextField.class).setText("Sam Rivera");
                component(cashCheckout, "paymentMethod", JComboBox.class).setSelectedItem("Cash");
                return null;
            });
            capture(cashCheckout, "checkout.png");
            clickAsync(cashCheckout, "confirmPayment");
            JDialog cashReceipt = waitForDialog("receiptDialog");
            String receipt = edt(() -> component(cashReceipt, "receiptText", JTextArea.class).getText());
            check(receipt.contains("Sam Rivera") && receipt.contains("Payment:  Cash")
                    && receipt.contains("Atorvastatin") && receipt.contains("Salbutamol")
                    && receipt.contains("$24.26"), "Cash receipt snapshot complete");
            check(rows() == 0, "Payment clears cart");
            check(!edt(() -> button(app, "checkoutButton").isEnabled()), "Paid cart checkout disabled");
            capture(cashReceipt, "receipt.png");
            clickAsync(cashReceipt, "receiptDone");
            waitFor(() -> !cashReceipt.isDisplayable());

            add("MED-007", "200 mg", "2");
            JDialog cardCheckout = checkout();
            edt(() -> {
                component(cardCheckout, "customerName", JTextField.class).setText("Alex Chen");
                component(cardCheckout, "paymentMethod", JComboBox.class).setSelectedItem("Card");
                return null;
            });
            clickAsync(cardCheckout, "confirmPayment");
            JDialog cardReceipt = waitForDialog("receiptDialog");
            String cardText = edt(() -> component(cardReceipt, "receiptText", JTextArea.class).getText());
            check(cardText.contains("Payment:  Card") && cardText.contains("Alex Chen")
                    && cardText.contains("$13.13"), "Card checkout receipt correct");
            check(rows() == 0, "Second checkout resets cart");
            clickAsync(cardReceipt, "receiptDone");
            waitFor(() -> !cardReceipt.isDisplayable());

            add("MED-002", "5 mg", "1");
            clickAsync(app, "clearCartButton");
            JDialog clear = waitForDialogTitle("Clear cart");
            clickTextAsync(clear, "No");
            waitFor(() -> !clear.isShowing());
            check(rows() == 1, "Declined clear preserves cart");
            clickAsync(app, "clearCartButton");
            JDialog clearAgain = waitForDialogTitle("Clear cart");
            clickTextAsync(clearAgain, "Yes");
            waitFor(() -> !clearAgain.isShowing());
            check(rows() == 0, "Confirmed clear empties cart");
            System.out.println("UiSmokeTest passed: " + assertions + " assertions; screenshots in artifacts/");
        } finally {
            edt(() -> {
                for (Window window : Window.getWindows()) {
                    window.dispose();
                }
                return null;
            });
        }
    }

    private static void filter(String query, String category) throws Exception {
        edt(() -> {
            component(app, "categoryFilter", JComboBox.class).setSelectedItem(category);
            component(app, "searchField", JTextField.class).setText(query);
            return null;
        });
    }

    private static void add(String id, String dosage, String quantity) throws Exception {
        edt(() -> {
            component(app, "dosage-" + id, JComboBox.class).setSelectedItem(dosage);
            JSpinner spinner = component(app, "quantity-" + id, JSpinner.class);
            ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(quantity);
            button(app, "add-" + id).doClick();
            return null;
        });
    }

    private static JDialog checkout() throws Exception {
        clickAsync(app, "checkoutButton");
        return waitForDialog("checkoutDialog");
    }

    private static int rows() throws Exception {
        return edt(() -> component(app, "cartTable", JTable.class).getRowCount());
    }

    private static Object cell(int row, int column) throws Exception {
        return edt(() -> component(app, "cartTable", JTable.class).getValueAt(row, column));
    }

    private static int cardCount() {
        int count = 0;
        for (Component child : component(app, "catalogGrid", JPanel.class).getComponents()) {
            if (child.getName() != null && child.getName().startsWith("medicine-")) {
                count++;
            }
        }
        return count;
    }

    private static void resize(int width, int height) throws Exception {
        edt(() -> { app.setSize(width, height); app.validate(); return null; });
        Thread.sleep(150);
        edt(() -> { app.validate(); return null; });
    }

    private static void validateLayout() throws Exception {
        edt(() -> {
            JPanel catalog = component(app, "catalogGrid", JPanel.class);
            for (Component card : catalog.getComponents()) {
                if (card.getName() == null || !card.getName().startsWith("medicine-")) {
                    continue;
                }
                List<Component> content = new ArrayList<>();
                collectContent(card, content);
                for (Component item : content) {
                    Rectangle bounds = SwingUtilities.convertRectangle(item.getParent(), item.getBounds(), card);
                    check(bounds.x >= 0 && bounds.y >= 0 && bounds.getMaxX() <= card.getWidth()
                            && bounds.getMaxY() <= card.getHeight(), "Card content contained: " + describe(item));
                    if (item instanceof JLabel) {
                        Dimension needed = item.getPreferredSize();
                        check(item.getWidth() >= needed.width && item.getHeight() >= needed.height,
                                "Card label unclipped: " + describe(item));
                    }
                    for (Component other : content) {
                        if (item == other) {
                            continue;
                        }
                        Rectangle otherBounds = SwingUtilities.convertRectangle(
                                other.getParent(), other.getBounds(), card);
                        check(!bounds.intersects(otherBounds), "Card content does not overlap: "
                                + describe(item) + " / " + describe(other));
                    }
                }
            }
            return null;
        });
    }

    private static void collectContent(Component component, List<Component> result) {
        if (component instanceof JLabel || component instanceof AbstractButton
                || component instanceof JComboBox || component instanceof JSpinner) {
            result.add(component);
        } else if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectContent(child, result);
            }
        }
    }

    private static String describe(Component component) {
        return component instanceof JLabel ? ((JLabel) component).getText() : component.getName();
    }

    private static void capture(Window window, String filename) throws Exception {
        BufferedImage image = edt(() -> {
            BufferedImage result = new BufferedImage(window.getWidth(), window.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = result.createGraphics();
            window.paint(graphics);
            graphics.dispose();
            return result;
        });
        Files.createDirectories(Path.of("artifacts"));
        ImageIO.write(image, "png", Path.of("artifacts", filename).toFile());
        int first = image.getRGB(0, 0);
        boolean differs = false;
        for (int y = 0; y < image.getHeight() && !differs; y += 7) {
            for (int x = 0; x < image.getWidth(); x += 7) {
                differs |= image.getRGB(x, y) != first;
            }
        }
        check(differs, "Screenshot nonblank: " + filename);
    }

    private static void clickAsync(Container root, String name) throws Exception {
        JButton target = edt(() -> button(root, name));
        SwingUtilities.invokeLater(target::doClick);
    }

    private static void clickTextAsync(Container root, String text) throws Exception {
        JButton target = edt(() -> {
            for (Component item : descendants(root)) {
                if (item instanceof JButton && text.equals(((JButton) item).getText())) {
                    return (JButton) item;
                }
            }
            throw new AssertionError("Missing button: " + text);
        });
        SwingUtilities.invokeLater(target::doClick);
    }

    private static JDialog waitForDialog(String name) throws Exception {
        waitFor(() -> findDialog(name, false) != null);
        return edt(() -> findDialog(name, false));
    }

    private static JDialog waitForDialogTitle(String title) throws Exception {
        waitFor(() -> findDialog(title, true) != null);
        return edt(() -> findDialog(title, true));
    }

    private static JDialog findDialog(String text, boolean title) {
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog && window.isShowing()
                    && text.equals(title ? ((JDialog) window).getTitle() : window.getName())) {
                return (JDialog) window;
            }
        }
        return null;
    }

    private static void waitFor(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (edt(condition)) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for Swing state");
    }

    private static JButton button(Container root, String name) {
        return component(root, name, JButton.class);
    }

    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        for (Component item : descendants(root)) {
            if (name.equals(item.getName())) {
                return type.cast(item);
            }
        }
        throw new AssertionError("Missing component: " + name);
    }

    private static List<Component> descendants(Container root) {
        List<Component> result = new ArrayList<>();
        for (Component item : root.getComponents()) {
            result.add(item);
            if (item instanceof Container) {
                result.addAll(descendants((Container) item));
            }
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
