package mx.org.smsem.credencialessm;

import mx.gob.sidi.desk.app.ui.CameraCaptureDialog;
import mx.gob.sidi.desk.app.ui.SignatureCaptureDialog;
import mx.gob.sidi.desk.devices.TopazHidPad;
import mx.gob.sidi.desk.devices.TopazSerialPad;
import mx.gob.sidi.desk.devices.WebcamSupport;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Usa cámara y pad cuando están conectados. Si no, genera una imagen de prueba.
 */
public final class CapturaEmpleado {

    public record Resultado(byte[] bytes, boolean dispositivo) {
    }

    private CapturaEmpleado() {
    }

    public static Resultado foto(Stage stage) {
        if (camaraDisponible()) {
            return new Resultado(CameraCaptureDialog.show(stage).orElse(null), true);
        }
        return new Resultado(imagen("FOTO DE PRUEBA", 320, 400, false), false);
    }

    public static Resultado firma(Stage stage) {
        if (padDisponible()) {
            return new Resultado(SignatureCaptureDialog.show(stage).orElse(null), true);
        }
        return new Resultado(imagen("Firma de prueba", 480, 160, true), false);
    }

    private static boolean camaraDisponible() {
        try {
            return !WebcamSupport.listDevices(4).isEmpty();
        } catch (Exception | UnsatisfiedLinkError ex) {
            return false;
        }
    }

    private static boolean padDisponible() {
        try {
            return TopazHidPad.findTopazHid().isPresent() || TopazSerialPad.findFirst().isPresent();
        } catch (Exception | UnsatisfiedLinkError ex) {
            return false;
        }
    }

    private static byte[] imagen(String leyenda, int ancho, int alto, boolean trazo) {
        BufferedImage image = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ancho, alto);
            g.setColor(new Color(0x9F, 0xB3, 0xC8));
            g.setStroke(new BasicStroke(3f));
            g.drawRect(2, 2, ancho - 5, alto - 5);
            if (trazo) {
                g.setColor(new Color(0x10, 0x2A, 0x43));
                g.drawLine(40, alto - 50, 120, 40);
                g.drawLine(120, 40, 200, alto - 55);
                g.drawLine(200, alto - 55, ancho - 40, 55);
            } else {
                g.setColor(new Color(0xD9, 0xE2, 0xEC));
                g.fillOval(ancho / 2 - 70, 40, 140, 160);
                g.fillRoundRect(50, 210, ancho - 100, alto - 250, 40, 40);
            }
            g.setColor(new Color(0x48, 0x65, 0x81));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.drawString(leyenda, 24, alto - 24);
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar la imagen de prueba", ex);
        }
    }
}
