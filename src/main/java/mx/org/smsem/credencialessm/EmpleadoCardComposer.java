package mx.org.smsem.credencialessm;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Credencial vertical de personal. El frente y el reverso son los bocetos;
 * encima van la fotografía, el nombre, la adscripción y la firma.
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

    /** Entre el marco de la foto y las letras PA. */
    private static final int NOMBRE_X = 200;
    private static final int NOMBRE_Y = 1154;
    private static final int NOMBRE_W = 620;
    private static final int NOMBRE_H = 142;

    /** Guinda #641C32, un poco más rojo para que se lea sobre el frente y la adscripción. */
    private static final Color NOMBRE_COLOR = new Color(0x7A, 0x22, 0x2C);

    /** Dentro de la franja blanca, a la derecha de la flecha azul. */
    private static final int ADS_X = 190;
    private static final int ADS_Y = 310;
    private static final int ADS_W = 740;
    private static final int ADS_H = 168;

    private static final int FIRMA_X = 190;
    private static final int FIRMA_Y = 512;
    private static final int FIRMA_W = 720;
    private static final int FIRMA_H = 208;

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
                dibujarNombre(g, empleado == null ? "" : empleado.nombreCompleto);
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

    /**
     * Nombre del empleado, entre la fotografía y el texto PA, en el azul del diseño.
     * Si no cabe en un renglón, sigue en las líneas que hagan falta.
     */
    private static void dibujarNombre(Graphics2D g, String nombre) {
        String texto = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
        if (texto.isEmpty()) {
            return;
        }
        int x = escala(NOMBRE_X, true);
        int y = escala(NOMBRE_Y, false);
        int w = escala(NOMBRE_W, true);
        int h = escala(NOMBRE_H, false);
        int ancho = Math.max(1, w - 12);
        int tamano = 36;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, tamano));
        List<String> lineas = envolver(g.getFontMetrics(), texto, ancho);
        if (lineas.size() > 1) {
            tamano = 32;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, tamano));
            lineas = envolver(g.getFontMetrics(), texto, ancho);
        }
        FontMetrics medidas = g.getFontMetrics();
        while (tamano > 20 && lineas.size() * medidas.getHeight() > h) {
            tamano--;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, tamano));
            medidas = g.getFontMetrics();
            lineas = envolver(medidas, texto, ancho);
        }
        int altoLinea = medidas.getHeight();
        int yLinea = y + Math.max(0, (h - lineas.size() * altoLinea) / 2) + medidas.getAscent();
        for (String linea : lineas) {
            int textoX = x + (w - medidas.stringWidth(linea)) / 2;
            dibujarGrueso(g, linea, textoX, yLinea);
            yLinea += altoLinea;
        }
    }

    /** Rellena y traza la letra para que la negrita se vea más gruesa. */
    private static void dibujarGrueso(Graphics2D g, String texto, int x, int y) {
        TextLayout layout = new TextLayout(texto, g.getFont(), g.getFontRenderContext());
        Shape forma = layout.getOutline(AffineTransform.getTranslateInstance(x, y));
        Stroke anterior = g.getStroke();
        g.setColor(NOMBRE_COLOR);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.fill(forma);
        g.draw(forma);
        g.setStroke(anterior);
    }

    /**
     * La adscripción cabe en varias líneas. Un nombre corto sigue en una sola,
     * centrado; si no cabe, baja de tamaño solo hasta que el bloque quepa en la franja.
     */
    private static void dibujarAdscripcion(Graphics2D g, String adscripcion) {
        String texto = adscripcion == null ? "" : adscripcion.trim().replaceAll("\\s+", " ");
        if (texto.isEmpty()) {
            return;
        }
        int x = escala(ADS_X, true);
        int y = escala(ADS_Y, false);
        int w = escala(ADS_W, true);
        int h = escala(ADS_H, false);
        int margen = 6;
        int ancho = Math.max(1, w - margen * 2);
        int tamano = 22;
        Font fuente = new Font(Font.SANS_SERIF, Font.BOLD, tamano);
        g.setFont(fuente);
        List<String> lineas = envolver(g.getFontMetrics(), texto, ancho);
        if (lineas.size() > 1) {
            tamano = 20;
            fuente = new Font(Font.SANS_SERIF, Font.BOLD, tamano);
            g.setFont(fuente);
            lineas = envolver(g.getFontMetrics(), texto, ancho);
        }
        FontMetrics medidas = g.getFontMetrics();
        while (tamano > 13 && lineas.size() * medidas.getHeight() > h) {
            tamano--;
            fuente = new Font(Font.SANS_SERIF, Font.BOLD, tamano);
            g.setFont(fuente);
            medidas = g.getFontMetrics();
            lineas = envolver(medidas, texto, ancho);
        }
        g.setColor(NOMBRE_COLOR);
        int altoLinea = medidas.getHeight();
        int altoBloque = lineas.size() * altoLinea;
        int yLinea = y + Math.max(0, (h - altoBloque) / 2) + medidas.getAscent();
        for (String linea : lineas) {
            int textoX = x + (w - medidas.stringWidth(linea)) / 2;
            g.drawString(linea, textoX, yLinea);
            yLinea += altoLinea;
        }
    }

    private static List<String> envolver(FontMetrics medidas, String texto, int ancho) {
        List<String> lineas = new ArrayList<>();
        StringBuilder linea = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            if (palabra.isEmpty()) {
                continue;
            }
            if (medidas.stringWidth(palabra) > ancho) {
                if (linea.length() > 0) {
                    lineas.add(linea.toString());
                    linea.setLength(0);
                }
                lineas.addAll(partirPalabra(medidas, palabra, ancho));
                continue;
            }
            String candidata = linea.length() == 0 ? palabra : linea + " " + palabra;
            if (medidas.stringWidth(candidata) <= ancho) {
                linea.setLength(0);
                linea.append(candidata);
            } else {
                lineas.add(linea.toString());
                linea.setLength(0);
                linea.append(palabra);
            }
        }
        if (linea.length() > 0) {
            lineas.add(linea.toString());
        }
        return lineas;
    }

    /** Parte una palabra más ancha que el renglón, de preferencia en un guion. */
    private static List<String> partirPalabra(FontMetrics medidas, String palabra, int ancho) {
        List<String> partes = new ArrayList<>();
        int inicio = 0;
        while (inicio < palabra.length()) {
            int fin = inicio + 1;
            int ultimoCorte = -1;
            while (fin <= palabra.length() && medidas.stringWidth(palabra.substring(inicio, fin)) <= ancho) {
                char c = palabra.charAt(fin - 1);
                if ((c == '-' || c == '/') && fin < palabra.length()) {
                    ultimoCorte = fin;
                }
                fin++;
            }
            if (fin > palabra.length()) {
                partes.add(palabra.substring(inicio));
                break;
            }
            int corte = ultimoCorte > inicio ? ultimoCorte : Math.max(inicio + 1, fin - 1);
            partes.add(palabra.substring(inicio, corte));
            inicio = corte;
        }
        return partes;
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
        imagen = firmaFondoTransparente(imagen);
        double escala = Math.min(w / (double) imagen.getWidth(), h / (double) imagen.getHeight());
        int ancho = (int) Math.round(imagen.getWidth() * escala);
        int alto = (int) Math.round(imagen.getHeight() * escala);
        int origenX = x + (w - ancho) / 2;
        int origenY = y + (h - alto) / 2;
        g.drawImage(imagen, origenX, origenY, ancho, alto, null);
    }

    /**
     * El pad entrega PNG con fondo blanco. Convierte el blanco (y casi blanco) a
     * alpha para que se vea el boceto debajo de la firma.
     */
    private static BufferedImage firmaFondoTransparente(BufferedImage origen) {
        int ancho = origen.getWidth();
        int alto = origen.getHeight();
        BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int argb = origen.getRGB(x, y);
                int alphaOrigen = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                int luma = (r * 30 + g * 59 + b * 11) / 100;
                int alpha = Math.min(alphaOrigen, 255 - luma);
                if (alpha < 12) {
                    salida.setRGB(x, y, 0);
                } else {
                    salida.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }
        return salida;
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
