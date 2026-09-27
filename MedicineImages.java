import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/** Small, cached local-image thumbnails with a generated medicine-pack fallback. */
public final class MedicineImages {
    public static final int SIZE = 80;

    private static final Color BACKGROUND = new Color(245, 247, 249);
    private static final Map<List<String>, ImageIcon> CACHE = new ConcurrentHashMap<>();

    private MedicineImages() {
    }

    public static ImageIcon load(Medicine medicine) {
        Objects.requireNonNull(medicine, "medicine");
        String path = medicine.getImagePath();
        String normalizedPath = path == null ? "" : path.trim();
        String category = String.valueOf(medicine.getCategory());
        List<String> key = Arrays.asList(medicine.getId(), medicine.getName(),
                category, normalizedPath);
        return CACHE.computeIfAbsent(key, unused -> createIcon(medicine.getName(),
                category, normalizedPath));
    }

    private static ImageIcon createIcon(String name, String category, String path) {
        BufferedImage source = readLocalImage(path);
        BufferedImage thumbnail = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            configure(graphics);
            graphics.setColor(BACKGROUND);
            graphics.fillRoundRect(0, 0, SIZE, SIZE, 8, 8);
            if (source == null) {
                drawMedicinePack(graphics, name, accentFor(category));
            } else {
                double scale = Math.min((SIZE - 8.0) / source.getWidth(),
                        (SIZE - 8.0) / source.getHeight());
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                graphics.drawImage(source, (SIZE - width) / 2, (SIZE - height) / 2,
                        width, height, null);
            }
        } finally {
            graphics.dispose();
        }
        ImageIcon icon = new ImageIcon(thumbnail);
        icon.setDescription(name);
        return icon;
    }

    private static BufferedImage readLocalImage(String path) {
        if (path.isEmpty()) {
            return null;
        }
        try {
            File file = new File(path);
            if (!file.isFile() || !file.canRead()) {
                return null;
            }
            // ImageIO returns null when no installed reader supports the file.
            return ImageIO.read(file);
        } catch (IOException | SecurityException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static Color accentFor(String category) {
        String normalized = category.toLowerCase(Locale.ROOT);
        if (normalized.contains("heart") || normalized.contains("cardio")) {
            return new Color(182, 73, 91);
        }
        if (normalized.contains("lung") || normalized.contains("respir")) {
            return new Color(26, 129, 126);
        }
        if (normalized.contains("ortho")) {
            return new Color(166, 119, 39);
        }
        return new Color(76, 109, 161);
    }

    private static void drawMedicinePack(Graphics2D graphics, String name, Color accent) {
        graphics.setColor(new Color(222, 227, 232));
        graphics.fillRoundRect(23, 13, 40, 57, 7, 7);
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(19, 10, 40, 57, 7, 7);
        graphics.setColor(new Color(212, 219, 225));
        graphics.drawRoundRect(19, 10, 40, 57, 7, 7);
        graphics.setColor(accent);
        graphics.fillRect(20, 19, 39, 6);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        String initials = initials(name);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(initials, 39 - metrics.stringWidth(initials) / 2, 45);
        graphics.setColor(new Color(214, 220, 225));
        graphics.fillRoundRect(28, 51, 22, 3, 3, 3);

        AffineTransform transform = graphics.getTransform();
        graphics.rotate(-Math.PI / 5, 54, 62);
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(39, 56, 31, 12, 12, 12);
        graphics.setColor(accent);
        graphics.fillRoundRect(39, 56, 18, 12, 12, 12);
        graphics.fillRect(50, 56, 5, 12);
        graphics.setColor(new Color(188, 198, 207));
        graphics.setStroke(new BasicStroke(1.0f));
        graphics.drawRoundRect(39, 56, 31, 12, 12, 12);
        graphics.drawLine(55, 57, 55, 67);
        graphics.setTransform(transform);
    }

    private static String initials(String name) {
        String[] words = name.trim().split("\\s+");
        String initials = words.length > 1
                ? words[0].substring(0, 1) + words[1].substring(0, 1)
                : words[0].substring(0, Math.min(2, words[0].length()));
        return initials.toUpperCase(Locale.ROOT);
    }

    public static Image applicationIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            configure(graphics);
            graphics.setColor(new Color(26, 129, 126));
            graphics.fillRoundRect(2, 2, 60, 60, 12, 12);
            graphics.setColor(Color.WHITE);
            graphics.fillRoundRect(25, 13, 14, 38, 4, 4);
            graphics.fillRoundRect(13, 25, 38, 14, 4, 4);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
    }
}
