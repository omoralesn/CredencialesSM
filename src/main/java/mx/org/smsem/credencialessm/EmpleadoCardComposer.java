package mx.org.smsem.credencialessm;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * Credencial vertical de personal. El frente y el reverso son los bocetos;
 * encima van la fotografía, la adscripción y la firma.
 */
public final class EmpleadoCardComposer {

    /** CR80 vertical a 300 dpi: 2.125" × 3.375". */
    static final int ANCHO = 638;
    static final int ALTO = 1012;
    private static final int SEPARACION = 24;

    private static final int FOTO_X = 300;
    private static final int FOTO_Y = 615;
    private static final int FOTO_W = 443;
    private static final int FOTO_H = 491;

    private static final int ADS_X = 100;
    private static final int ADS_Y = 305;
    private static final int ADS_W = 844;
    private static final int ADS_H = 55;

    private static final int FIRMA_X = 130;
    private static final int FIRMA_Y = 390;
    private static final int FIRMA_W = 780;
    private static final int FIRMA_H = 330;

    private static final int QR_TAMANO = 400;
    private static final int QR_Y = 1165;

    private static final int BOCETO_ANCHO = 1044;
    private static final int BOCETO_ALTO = 1674;

    public BufferedImage compose(EmpleadoSmsem empleado, byte[] foto, byte[] firma) throws Exception {
        BufferedImage frente = cara(empleado, foto, firma, true);
        BufferedImage reverso = cara(empleado, foto, firma, false);
        BufferedImage hoja = new BufferedImage(ANCHO, ALTO * 2 + SEPARACION, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = hoja.createGraphics();
        try {
            g.setColor(new Color(0xE6, 0xEA, 0xEF));
            g.fillRect(0, 0, hoja.getWidth(), hoja.getHeight());
            g.drawImage(frente, 0, 0, null);
            g.drawImage(reverso, 0, ALTO + SEPARACION, null);
        } finally {
            g.dispose();
        }
        return hoja;
    }

    private static BufferedImage cara(EmpleadoSmsem empleado, byte[] foto, byte[] firma, boolean frente) throws Exception {
        BufferedImage lienzo = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ANCHO, ALTO);
            BufferedImage fondo = fondo(frente ? "/credencialessm/frente-personal.png" : "/credencialessm/reverso-personal.png");
            g.drawImage(fondo, 0, 0, ANCHO, ALTO, null);
            if (frente) {
                dibujarCubriendo(g, foto, escala(FOTO_X, true), escala(FOTO_Y, false), escala(FOTO_W, true), escala(FOTO_H, false));
            } else {
                dibujarAdscripcion(g, empleado == null ? "" : empleado.adscripcion);
                dibujarContenido(g, firma,
                        escala(FIRMA_X, true), escala(FIRMA_Y, false),
                        escala(FIRMA_W, true), escala(FIRMA_H, false));
                BufferedImage codigo = qr(empleado == null ? "" : empleado.cadenaQr, escala(QR_TAMANO, true));
                int qrX = (ANCHO - codigo.getWidth()) / 2;
                g.drawImage(codigo, qrX, escala(QR_Y, false), null);
            }
        } finally {
            g.dispose();
        }
        return lienzo;
    }

    private static void dibujarAdscripcion(Graphics2D g, String adscripcion) {
        String texto = adscripcion == null ? "" : adscripcion.trim();
        int x = escala(ADS_X, true);
        int y = escala(ADS_Y, false);
        int w = escala(ADS_W, true);
        int h = escala(ADS_H, false);
        int tamano = 22;
        Font fuente = new Font(Font.SANS_SERIF, Font.BOLD, tamano);
        g.setFont(fuente);
        FontMetrics medidas = g.getFontMetrics();
        while (tamano > 12 && medidas.stringWidth(texto) > w - 16) {
            tamano--;
            fuente = new Font(Font.SANS_SERIF, Font.BOLD, tamano);
            g.setFont(fuente);
            medidas = g.getFontMetrics();
        }
        g.setColor(new Color(0x1A, 0x27, 0x44));
        int textoX = x + Math.max(8, (w - medidas.stringWidth(texto)) / 2);
        int textoY = y + (h + medidas.getAscent() - medidas.getDescent()) / 2;
        g.drawString(texto, textoX, textoY);
    }

    private static void dibujarCubriendo(Graphics2D g, byte[] bytes, int x, int y, int w, int h) throws Exception {
        BufferedImage imagen = leer(bytes);
        if (imagen == null) {
            return;
        }
        double escala = Math.max(w / (double) imagen.getWidth(), h / (double) imagen.getHeight());
        int ancho = (int) Math.round(imagen.getWidth() * escala);
        int alto = (int) Math.round(imagen.getHeight() * escala);
        int origenX = x + (w - ancho) / 2;
        int origenY = y + (h - alto) / 2;
        java.awt.Shape recorte = g.getClip();
        g.setClip(x, y, w, h);
        g.drawImage(imagen, origenX, origenY, ancho, alto, null);
        g.setClip(recorte);
    }

    private static void dibujarContenido(Graphics2D g, byte[] bytes, int x, int y, int w, int h) throws Exception {
        BufferedImage imagen = leer(bytes);
        if (imagen == null) {
            return;
        }
        double escala = Math.min(w / (double) imagen.getWidth(), h / (double) imagen.getHeight());
        int ancho = (int) Math.round(imagen.getWidth() * escala);
        int alto = (int) Math.round(imagen.getHeight() * escala);
        int origenX = x + (w - ancho) / 2;
        int origenY = y + (h - alto) / 2;
        g.drawImage(imagen, origenX, origenY, ancho, alto, null);
    }

    private static BufferedImage qr(String cadena, int tamano) throws Exception {
        String payload = cadena == null || cadena.isBlank() ? "SMSEM" : cadena;
        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, tamano, tamano,
                Map.of(EncodeHintType.MARGIN, 1));
        BufferedImage imagen = new BufferedImage(tamano, tamano, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < tamano; y++) {
            for (int x = 0; x < tamano; x++) {
                imagen.setRGB(x, y, matrix.get(x, y) ? 0xFF1A2744 : 0xFFFFFFFF);
            }
        }
        return imagen;
    }

    private static BufferedImage leer(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static BufferedImage fondo(String recurso) throws Exception {
        try (InputStream in = EmpleadoCardComposer.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("No está el boceto " + recurso);
            }
            BufferedImage imagen = ImageIO.read(in);
            if (imagen == null) {
                throw new IllegalStateException("No se pudo leer el boceto " + recurso);
            }
            return imagen;
        }
    }

    private static int escala(int valor, boolean horizontal) {
        return horizontal
                ? valor * ANCHO / BOCETO_ANCHO
                : valor * ALTO / BOCETO_ALTO;
    }
}
