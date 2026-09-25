package mx.org.smsem.credencialessm;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import mx.gob.sidi.desk.devices.CardLayout;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;

/**
 * Cara de credencial de empleado: nombre, clave editable, adscripción y QR de la cadena interna.
 */
public final class EmpleadoCardComposer {

    private final CardLayout layout = CardLayout.load();

    public BufferedImage compose(EmpleadoSmsem empleado, byte[] foto, byte[] firma) throws Exception {
        BufferedImage canvas = new BufferedImage(layout.widthPx, layout.heightPx, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0xF4, 0xF7, 0xFB));
            g.fillRect(0, 0, layout.widthPx, layout.heightPx);
            g.setColor(new Color(0x0B, 0x3D, 0x6E));
            g.fillRect(0, 0, layout.widthPx, 70);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, layout.fontTitle));
            g.drawString("SMSEM · Empleado", layout.titleX, layout.titleY);

            g.setColor(new Color(0x1A, 0x1A, 0x1A));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, layout.fontBody + 2));
            g.drawString(texto(empleado.nombreCompleto), layout.nameX, layout.nameY);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, layout.fontBody));
            g.drawString("Clave: " + texto(empleado.clave), layout.claveX, layout.claveY);
            g.drawString("Adscripción: " + texto(empleado.adscripcion), layout.folioX, layout.folioY);

            drawBytes(g, foto, layout.photoX, layout.photoY, layout.photoW, layout.photoH);
            drawBytes(g, firma, layout.signatureX, layout.signatureY, layout.signatureW, layout.signatureH);
            BufferedImage qr = qr(empleado.cadenaQr, 180);
            g.drawImage(qr, layout.widthPx - 200, layout.heightPx - 210, null);

            g.setColor(new Color(0x0B, 0x3D, 0x6E));
            g.drawRect(1, 1, layout.widthPx - 3, layout.heightPx - 3);
        } finally {
            g.dispose();
        }
        return canvas;
    }

    private static void drawBytes(Graphics2D g, byte[] bytes, int x, int y, int w, int h) throws Exception {
        g.setColor(new Color(0xD9, 0xE2, 0xEC));
        g.fillRect(x, y, w, h);
        if (bytes == null || bytes.length == 0) {
            return;
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image != null) {
            g.drawImage(image, x, y, w, h, null);
        }
    }

    private static BufferedImage qr(String cadena, int size) throws Exception {
        String payload = cadena == null || cadena.isBlank() ? "SMSEM" : cadena;
        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size,
                Map.of(EncodeHintType.MARGIN, 1));
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0xFF102A43 : 0xFFFFFFFF);
            }
        }
        return image;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }
}
