import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;

/** A dependency-free, in-memory pharmacy point-of-sale demonstration. */
public final class PharmacyApp extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final Color BACKGROUND = new Color(245, 247, 248);
    private static final Color INK = new Color(32, 43, 49);
    private static final Color MUTED = new Color(102, 114, 123);
    private static final Color BORDER = new Color(221, 227, 230);
    private static final Color ACCENT = new Color(19, 117, 105);
    private static final Color ERROR = new Color(177, 48, 56);

    @SuppressWarnings("serial") // The window is never persisted with Java serialization.
    private final List<Medicine> medicines = Medicine.sampleMedicines();
    private final CartModel cart = new CartModel();
    private final JTextField search = new JTextField();
    private final JComboBox<String> category = new JComboBox<>(new String[] {
        "All categories", "Heart", "Lung", "Orthopedic"
    });
    private final CatalogPanel catalog = new CatalogPanel();
    private final JLabel catalogCount = label("", 13, MUTED);
    private final JLabel cartCount = label("0 items", 13, MUTED);
    private final JLabel subtotal = label("", 14, INK);
    private final JLabel tax = label("", 14, INK);
    private final JLabel total = boldLabel("", 24, INK);
    private final JLabel status = label("Ready", 12, MUTED);
    private final JTable table = new JTable(cart);
    private final JButton remove = new JButton("Remove");
    private final JButton clear = new JButton("Clear cart");
    private final JButton checkout = new JButton("Checkout");
    private final JButton lastReceiptButton = new JButton("Last receipt");
    @SuppressWarnings("serial")
    private Receipt lastReceipt;

    public PharmacyApp() {
        super("Pharmacy Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(MedicineImages.applicationIcon());
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(1040, bounds.width), Math.min(660, bounds.height)));

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BACKGROUND);
        content.add(buildHeader(), BorderLayout.NORTH);

        JSplitPane workspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildCatalog(), buildCart());
        workspace.setResizeWeight(1.0);
        workspace.setDividerSize(6);
        workspace.setBorder(null);
        workspace.setContinuousLayout(true);
        JSplitPane navigation = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildNavigation(), workspace);
        navigation.setResizeWeight(0.0);
        navigation.setDividerLocation(202);
        navigation.setDividerSize(5);
        navigation.setBorder(null);
        navigation.setContinuousLayout(true);
        content.add(navigation, BorderLayout.CENTER);

        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(9, 18, 9, 18)));
        status.setName("status");
        content.add(status, BorderLayout.SOUTH);
        setContentPane(content);

        search.setName("searchField");
        category.setName("categoryFilter");
        catalog.setName("catalogGrid");
        catalogCount.setName("catalogCount");
        table.setName("cartTable");
        checkout.setName("checkoutButton");
        clear.setName("clearCartButton");
        remove.setName("removeButton");
        total.setName("grandTotal");

        search.getDocument().addDocumentListener(onDocumentChange(this::filterCatalog));
        category.addActionListener(event -> filterCatalog());
        cart.addTableModelListener(event -> updateCart());
        table.getSelectionModel().addListSelectionListener(event ->
                remove.setEnabled(table.getSelectedRow() >= 0));
        remove.addActionListener(event -> removeSelected());
        clear.addActionListener(event -> clearCart());
        checkout.addActionListener(event -> openCheckout());
        lastReceiptButton.addActionListener(event -> showReceipt(lastReceipt));
        lastReceiptButton.setEnabled(false);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "search");
        getRootPane().getActionMap().put("search", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                search.requestFocusInWindow();
                search.selectAll();
            }
        });
        filterCatalog();
        updateCart();

        setSize(Math.min(1420, bounds.width), Math.min(880, bounds.height));
        setLocationRelativeTo(null);
        workspace.setDividerLocation(Math.max(300, getWidth() - 202 - 478));
    }

    private JPanel buildHeader() {
        JPanel header = panel(new BorderLayout(16, 0), Color.WHITE, 18);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(18, 22, 18, 22)));
        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setOpaque(false);
        brand.add(new JLabel(new javax.swing.ImageIcon(
                MedicineImages.applicationIcon().getScaledInstance(38, 38,
                        java.awt.Image.SCALE_SMOOTH))), BorderLayout.WEST);
        JPanel titles = vertical();
        titles.add(boldLabel("Pharmacy", 25, INK));
        titles.add(label("Inventory & checkout", 12, MUTED));
        brand.add(titles, BorderLayout.CENTER);
        header.add(brand, BorderLayout.WEST);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 12, 8));
        actions.setOpaque(false);
        actions.add(label(LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.US)), 13, MUTED));
        actions.add(lastReceiptButton);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildNavigation() {
        JPanel navigation = panel(new BorderLayout(), Color.WHITE, 18);
        navigation.setMinimumSize(new Dimension(180, 0));
        JPanel filters = vertical();
        filters.add(boldLabel("Catalog", 17, INK));
        filters.add(Box.createVerticalStrut(27));
        filters.add(label("Search", 12, MUTED));
        filters.add(Box.createVerticalStrut(6));
        search.setToolTipText("Search medicine name or category");
        fixHeight(search, 34);
        filters.add(search);
        filters.add(Box.createVerticalStrut(22));
        filters.add(label("Category", 12, MUTED));
        filters.add(Box.createVerticalStrut(6));
        fixHeight(category, 34);
        filters.add(category);
        filters.add(Box.createVerticalStrut(14));
        JButton reset = new JButton("Reset filters");
        reset.setAlignmentX(Component.LEFT_ALIGNMENT);
        reset.addActionListener(event -> {
            category.setSelectedIndex(0);
            search.setText("");
            search.requestFocusInWindow();
        });
        filters.add(reset);
        navigation.add(filters, BorderLayout.NORTH);
        JPanel footer = vertical();
        footer.add(label("LOCAL REGISTER", 11, MUTED));
        footer.add(Box.createVerticalStrut(5));
        footer.add(label("USD  |  Tax 5%", 12, MUTED));
        navigation.add(footer, BorderLayout.SOUTH);
        return navigation;
    }

    private JPanel buildCatalog() {
        JPanel area = panel(new BorderLayout(0, 14), BACKGROUND, 18);
        area.setMinimumSize(new Dimension(296, 0));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(boldLabel("Medicines", 20, INK), BorderLayout.WEST);
        heading.add(catalogCount, BorderLayout.EAST);
        area.add(heading, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(catalog);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                catalog.updateColumns();
            }
        });
        area.add(scroll, BorderLayout.CENTER);
        return area;
    }

    private JPanel medicineCard(Medicine medicine) {
        JPanel card = panel(new BorderLayout(0, 10), Color.WHITE, 14);
        card.setName("medicine-" + medicine.getId());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));

        JPanel details = vertical();
        JPanel preview = new JPanel(new BorderLayout(8, 0));
        preview.setOpaque(false);
        preview.setAlignmentX(Component.LEFT_ALIGNMENT);
        preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        JLabel image = new JLabel(MedicineImages.load(medicine));
        image.setPreferredSize(new Dimension(80, 80));
        image.getAccessibleContext().setAccessibleName(medicine.getName() + " thumbnail");
        preview.add(image, BorderLayout.WEST);
        JPanel price = new JPanel(new GridBagLayout());
        price.setOpaque(false);
        GridBagConstraints pc = new GridBagConstraints();
        pc.gridx = 0;
        pc.gridy = 0;
        pc.anchor = GridBagConstraints.EAST;
        price.add(boldLabel(CartModel.money(medicine.getPrice()), 21, INK), pc);
        pc.gridy++;
        price.add(label("per unit", 11, MUTED), pc);
        preview.add(price, BorderLayout.EAST);
        details.add(preview);
        details.add(Box.createVerticalStrut(12));
        JLabel name = boldLabel(medicine.getName(), 17, INK);
        name.setToolTipText(medicine.getName());
        details.add(name);
        details.add(Box.createVerticalStrut(3));
        details.add(label(medicine.getCategory(), 12, MUTED));
        card.add(details, BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 5, 10);
        controls.add(label("Dosage", 11, MUTED), c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 5, 0);
        controls.add(label("Quantity", 11, MUTED), c);
        JComboBox<String> dosage = new JComboBox<>(medicine.getDosageList().toArray(new String[0]));
        dosage.setName("dosage-" + medicine.getId());
        dosage.getAccessibleContext().setAccessibleName(medicine.getName() + " dosage");
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 10);
        dosage.setPreferredSize(new Dimension(108, 32));
        controls.add(dosage, c);
        JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        JFormattedTextField quantityText = ((JSpinner.DefaultEditor) quantity.getEditor()).getTextField();
        quantityText.setFocusLostBehavior(JFormattedTextField.PERSIST);
        quantity.setName("quantity-" + medicine.getId());
        quantity.setPreferredSize(new Dimension(68, 32));
        quantity.getAccessibleContext().setAccessibleName(medicine.getName() + " quantity");
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 0);
        controls.add(quantity, c);
        JPanel bottom = new JPanel(new BorderLayout(0, 12));
        bottom.setOpaque(false);
        bottom.add(controls, BorderLayout.NORTH);
        JButton add = new JButton("Add to cart");
        add.setName("add-" + medicine.getId());
        add.setPreferredSize(new Dimension(0, 34));
        add.addActionListener(event -> {
            try {
                String enteredQuantity = quantityText.getText().strip();
                if (!enteredQuantity.matches("[0-9]{1,3}")) {
                    throw new ParseException("Quantity must be a whole number.", 0);
                }
                quantity.commitEdit();
                int units = (Integer) quantity.getValue();
                String strength = (String) dosage.getSelectedItem();
                cart.add(medicine, strength, units);
                status.setForeground(ACCENT);
                status.setText("Added " + medicine.getName() + " (" + strength + ") x " + units + ".");
                add.setText("Added");
                Timer feedback = new Timer(900, ignored -> add.setText("Add to cart"));
                feedback.setRepeats(false);
                feedback.start();
            } catch (ParseException exception) {
                showError("Enter a whole-number quantity from 1 to 100.");
            } catch (IllegalArgumentException exception) {
                showError(exception.getMessage());
            }
        });
        bottom.add(add, BorderLayout.SOUTH);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private void filterCatalog() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        String selected = (String) category.getSelectedItem();
        catalog.removeAll();
        int matches = 0;
        for (Medicine medicine : medicines) {
            String haystack = (medicine.getName() + " " + medicine.getCategory()).toLowerCase(Locale.ROOT);
            boolean categoryMatches = category.getSelectedIndex() == 0
                    || medicine.getCategory().contains(selected);
            if (categoryMatches && haystack.contains(query)) {
                catalog.add(medicineCard(medicine));
                matches++;
            }
        }
        catalogCount.setText(matches + " of " + medicines.size());
        if (matches == 0) {
            JPanel empty = new JPanel(new GridBagLayout());
            empty.setOpaque(false);
            empty.add(label("No matching medicines", 15, MUTED));
            catalog.add(empty);
        }
        catalog.updateColumns();
        catalog.revalidate();
        catalog.repaint();
    }

    private JPanel buildCart() {
        JPanel area = panel(new BorderLayout(0, 16), Color.WHITE, 18);
        area.setMinimumSize(new Dimension(420, 0));
        area.setPreferredSize(new Dimension(478, 0));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(boldLabel("Current order", 20, INK), BorderLayout.WEST);
        heading.add(cartCount, BorderLayout.EAST);
        area.add(heading, BorderLayout.NORTH);

        table.setRowHeight(42);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setGridColor(BORDER);
        table.setIntercellSpacing(new Dimension(8, 1));
        table.setFillsViewportHeight(true);
        table.setBackground(Color.WHITE);
        table.setSelectionBackground(new Color(222, 242, 236));
        table.setSelectionForeground(INK);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 32));
        table.setDefaultRenderer(BigDecimal.class, new MoneyRenderer());
        table.setDefaultRenderer(String.class, new TextRenderer());
        int[] widths = {122, 76, 40, 72, 86};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(0).setMinWidth(80);
        table.getColumnModel().getColumn(1).setMinWidth(55);
        table.getColumnModel().getColumn(2).setMinWidth(34);
        table.getColumnModel().getColumn(3).setMinWidth(60);
        table.getColumnModel().getColumn(4).setMinWidth(82);
        JScrollPane lines = new JScrollPane(table);
        lines.setBorder(BorderFactory.createLineBorder(BORDER));
        lines.getViewport().setBackground(Color.WHITE);
        area.add(lines, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 23));
        bottom.setOpaque(false);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(remove);
        actions.add(Box.createHorizontalStrut(8));
        actions.add(clear);
        bottom.add(actions, BorderLayout.NORTH);
        JPanel summary = vertical();
        summary.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
        summary.add(Box.createVerticalStrut(18));
        summary.add(summaryRow("Subtotal", subtotal));
        summary.add(Box.createVerticalStrut(12));
        summary.add(summaryRow("Tax (5%)", tax));
        summary.add(Box.createVerticalStrut(22));
        JPanel grand = summaryRow("Grand total", total);
        grand.setPreferredSize(new Dimension(0, 35));
        summary.add(grand);
        summary.add(Box.createVerticalStrut(20));
        primaryButton(checkout);
        fixHeight(checkout, 44);
        summary.add(checkout);
        bottom.add(summary, BorderLayout.CENTER);
        area.add(bottom, BorderLayout.SOUTH);
        return area;
    }

    private void updateCart() {
        cartCount.setText(cart.itemCount() + (cart.itemCount() == 1 ? " item" : " items"));
        subtotal.setText(CartModel.money(cart.getSubtotal()));
        tax.setText(CartModel.money(cart.getTax()));
        total.setText(CartModel.money(cart.getTotal()));
        checkout.setEnabled(!cart.isEmpty());
        clear.setEnabled(!cart.isEmpty());
        remove.setEnabled(table.getSelectedRow() >= 0 && !cart.isEmpty());
    }

    private void removeSelected() {
        int selected = table.getSelectedRow();
        if (selected >= 0) {
            cart.remove(table.convertRowIndexToModel(selected));
            status.setForeground(MUTED);
            status.setText("Item removed.");
        }
    }

    private void clearCart() {
        if (!cart.isEmpty() && JOptionPane.showConfirmDialog(this,
                "Remove all items from this order?", "Clear cart",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
            cart.clear();
            status.setForeground(MUTED);
            status.setText("Cart cleared.");
        }
    }

    private void openCheckout() {
        if (cart.isEmpty()) {
            return;
        }
        JDialog dialog = new JDialog(this, "Checkout", true);
        dialog.setName("checkoutDialog");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content = panel(new BorderLayout(0, 16), BACKGROUND, 22);
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 7, 16);
        form.add(label("Customer name", 13, INK), c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 7, 0);
        form.add(label("Payment method", 13, INK), c);
        JTextField customer = new JTextField(25);
        customer.setName("customerName");
        customer.getAccessibleContext().setAccessibleName("Customer name");
        JComboBox<String> payment = new JComboBox<>(new String[] {"Cash", "Card"});
        payment.setName("paymentMethod");
        payment.getAccessibleContext().setAccessibleName("Payment method");
        customer.setPreferredSize(new Dimension(300, 34));
        payment.setPreferredSize(new Dimension(135, 34));
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 16);
        form.add(customer, c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(payment, c);
        content.add(form, BorderLayout.NORTH);

        JTextArea preview = receiptArea(orderSummary());
        preview.setName("orderPreview");
        content.add(new JScrollPane(preview), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        JLabel validation = label(" ", 12, ERROR);
        validation.setName("checkoutValidation");
        customer.getDocument().addDocumentListener(onDocumentChange(() -> validation.setText(" ")));
        payment.addActionListener(event -> validation.setText(" "));
        bottom.add(validation, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.setName("cancelCheckout");
        cancel.addActionListener(event -> dialog.dispose());
        JButton confirm = new JButton("Confirm payment");
        confirm.setName("confirmPayment");
        primaryButton(confirm);
        confirm.addActionListener(event -> {
            try {
                Receipt receipt = cart.createReceipt(customer.getText(), (String) payment.getSelectedItem());
                confirm.setEnabled(false);
                lastReceipt = receipt;
                cart.clear();
                lastReceiptButton.setEnabled(true);
                status.setForeground(ACCENT);
                status.setText("Payment recorded: " + CartModel.money(receipt.getTotal())
                        + " | Receipt " + receipt.getId());
                dialog.dispose();
                showReceipt(receipt);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                validation.setText(exception.getMessage());
                customer.requestFocusInWindow();
            }
        });
        buttons.add(cancel);
        buttons.add(confirm);
        bottom.add(buttons, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(confirm);
        configureDialog(dialog, cancel, 690, 660);
        SwingUtilities.invokeLater(customer::requestFocusInWindow);
        dialog.setVisible(true);
    }

    private String orderSummary() {
        StringBuilder text = new StringBuilder("PHARMACY\nORDER SUMMARY\n");
        text.append("====================================================\n\n");
        for (int row = 0; row < cart.getRowCount(); row++) {
            text.append(cart.getValueAt(row, 0)).append(" | ").append(cart.getValueAt(row, 1)).append('\n');
            text.append("  ").append(cart.getValueAt(row, 2)).append(" x ")
                    .append(CartModel.money((BigDecimal) cart.getValueAt(row, 3)))
                    .append(" = ").append(CartModel.money((BigDecimal) cart.getValueAt(row, 4)))
                    .append("\n\n");
        }
        text.append("----------------------------------------------------\n");
        text.append(String.format("%-34s %16s%n", "Subtotal", CartModel.money(cart.getSubtotal())));
        text.append(String.format("%-34s %16s%n", "Tax (5%)", CartModel.money(cart.getTax())));
        text.append(String.format("%-34s %16s%n", "GRAND TOTAL", CartModel.money(cart.getTotal())));
        return text.toString();
    }

    private void showReceipt(Receipt receipt) {
        if (receipt == null) {
            return;
        }
        JDialog dialog = new JDialog(this, "Payment recorded - " + receipt.getId(), true);
        dialog.setName("receiptDialog");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content = panel(new BorderLayout(0, 16), BACKGROUND, 22);
        content.add(boldLabel("Payment recorded  " + CartModel.money(receipt.getTotal()), 21, ACCENT),
                BorderLayout.NORTH);
        JTextArea receiptText = receiptArea(receipt.getText());
        receiptText.setName("receiptText");
        content.add(new JScrollPane(receiptText), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton save = new JButton("Save receipt");
        save.addActionListener(event -> saveReceipt(dialog, receipt));
        JButton print = new JButton("Print");
        print.addActionListener(event -> {
            try {
                receiptText.print();
            } catch (PrinterException | SecurityException exception) {
                JOptionPane.showMessageDialog(dialog, "Could not print receipt: " + exception.getMessage(),
                        "Print failed", JOptionPane.ERROR_MESSAGE);
            }
        });
        JButton done = new JButton("Done");
        done.setName("receiptDone");
        primaryButton(done);
        done.addActionListener(event -> dialog.dispose());
        buttons.add(save);
        buttons.add(print);
        buttons.add(done);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(done);
        configureDialog(dialog, done, 740, 700);
        dialog.setVisible(true);
    }

    private void saveReceipt(JDialog parent, Receipt receipt) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save receipt");
        chooser.setFileFilter(new FileNameExtensionFilter("Text receipt (*.txt)", "txt"));
        chooser.setSelectedFile(new java.io.File("receipt-" + receipt.getId() + ".txt"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")) {
            file = file.resolveSibling(file.getFileName() + ".txt");
        }
        if (Files.exists(file) && JOptionPane.showConfirmDialog(parent,
                "Replace " + file.getFileName() + "?", "Replace receipt",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Files.writeString(file, receipt.getText(), StandardCharsets.UTF_8);
            status.setForeground(ACCENT);
            status.setText("Receipt saved: " + file.toAbsolutePath());
        } catch (IOException | SecurityException exception) {
            JOptionPane.showMessageDialog(parent, "Could not save receipt: " + exception.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void configureDialog(JDialog dialog, JButton close, int width, int height) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(Math.min(width, screen.width - 40), Math.min(height, screen.height - 40));
        dialog.setMinimumSize(new Dimension(Math.min(600, screen.width - 40),
                Math.min(420, screen.height - 40)));
        dialog.setLocationRelativeTo(this);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        dialog.getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                close.doClick();
            }
        });
    }

    private void showError(String message) {
        status.setForeground(ERROR);
        status.setText(message);
        JOptionPane.showMessageDialog(this, message, "Order could not be updated", JOptionPane.WARNING_MESSAGE);
    }

    private static JTextArea receiptArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setMargin(new Insets(18, 18, 18, 18));
        area.setBackground(Color.WHITE);
        area.setForeground(INK);
        area.setCaretPosition(0);
        return area;
    }

    private static JPanel panel(java.awt.LayoutManager layout, Color background, int padding) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(background);
        panel.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
        return panel;
    }

    private static JPanel vertical() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, (float) size));
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel boldLabel(String text, int size, Color color) {
        JLabel label = label(text, size, color);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static JPanel summaryRow(String title, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label(title, 14, INK), BorderLayout.WEST);
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private static void fixHeight(JComponent component, int height) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setPreferredSize(new Dimension(0, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private static void primaryButton(JButton button) {
        button.setBackground(ACCENT);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setMargin(new Insets(10, 16, 10, 16));
    }

    private static DocumentListener onDocumentChange(Runnable action) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { action.run(); }
            @Override public void removeUpdate(DocumentEvent event) { action.run(); }
            @Override public void changedUpdate(DocumentEvent event) { action.run(); }
        };
    }

    private static final class MoneyRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        MoneyRenderer() { setHorizontalAlignment(SwingConstants.RIGHT); }
        @Override protected void setValue(Object value) {
            setText(value instanceof BigDecimal ? CartModel.money((BigDecimal) value) : "");
            setToolTipText(getText());
        }
    }

    private static final class TextRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override protected void setValue(Object value) {
            super.setValue(value);
            setToolTipText(value == null ? null : value.toString());
        }
    }

    private static final class CatalogPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        private static final int CARD_WIDTH = 244;
        private static final int CARD_HEIGHT = 302;
        private static final int GAP = 14;
        private int columns = 1;

        CatalogPanel() {
            super(new GridLayout(0, 1, GAP, GAP));
            setBackground(BACKGROUND);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 2));
        }

        void updateColumns() {
            int width = getParent() == null ? CARD_WIDTH : getParent().getWidth();
            int next = Math.max(1, (width + GAP - 2) / (CARD_WIDTH + GAP));
            if (columns != next) {
                columns = next;
                ((GridLayout) getLayout()).setColumns(columns);
            }
            revalidate();
        }

        @Override public Dimension getPreferredSize() {
            int rows = (Math.max(1, getComponentCount()) + columns - 1) / columns;
            return new Dimension(columns * (CARD_WIDTH + GAP) - GAP + 2,
                    rows * (CARD_HEIGHT + GAP) - GAP + 2);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("PharmacyApp requires a graphical desktop. Run ModelTest for headless checks.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException exception) {
                System.err.println("Native look and feel unavailable; using Swing default.");
            }
            PharmacyApp app = new PharmacyApp();
            app.setVisible(true);
            app.search.requestFocusInWindow();
        });
    }
}
