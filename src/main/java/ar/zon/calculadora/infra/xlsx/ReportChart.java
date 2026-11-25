package ar.zon.calculadora.infra.xlsx;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/** Genera un gráfico de barras para el resumen del informe. */
final class ReportChart {
    static final int WIDTH = 640;
    private static final int ROW_HEIGHT = 32;

    private ReportChart() {
    }

    static int height(int rows) {
        return 84 + ROW_HEIGHT * Math.max(1, rows);
    }

    static byte[] bars(String title, Map<String, BigDecimal> values, int decimals) throws IOException {
        int width = WIDTH;
        int height = height(values.size());
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            var green = new Color(0x164B43);
            graphics.setColor(new Color(0xBCD4CD));
            graphics.drawRoundRect(1, 1, width - 3, height - 3, 16, 16);
            graphics.setColor(new Color(0xF0F7F4));
            graphics.fillRoundRect(12, 12, width - 24, 47, 12, 12);
            graphics.setColor(green);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
            graphics.drawString(title, 24, 37);
            int titleWidth = graphics.getFontMetrics().stringWidth(title);
            graphics.setColor(new Color(0x358574));
            graphics.fillRoundRect(24, 43, titleWidth, 2, 2, 2);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            var format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"));
            format.setMaximumFractionDigits(decimals);
            double maximum = values.values().stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
            int y = 82;
            for (var entry : values.entrySet()) {
                graphics.setColor(green);
                graphics.drawString(entry.getKey(), 24, y);
                graphics.setColor(new Color(0xEDF5F2));
                graphics.fillRoundRect(232, y - 13, 280, 16, 8, 8);
                graphics.setColor(new Color(0x358574));
                int length = maximum == 0 ? 0 : (int) Math.round(280 * (entry.getValue().doubleValue() / maximum));
                graphics.fillRoundRect(232, y - 13, length, 16, 8, 8);
                graphics.setColor(green);
                String label = format.format(entry.getValue());
                graphics.drawString(label, width - 24 - graphics.getFontMetrics().stringWidth(label), y);
                y += ROW_HEIGHT;
            }
            if (values.isEmpty()) {
                graphics.setColor(green);
                graphics.drawString("Sin emisiones calculadas en este lote", 24, y);
            }
        } finally {
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
