package mx.org.smsem.credencialessm;

import com.google.zxing.BarcodeFormat;
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

/**
 * Credencial vertical de personal. El frente y el reverso son los bocetos;
 * encima van la fotografía, el nombre, la adscripción y la firma.
 */
public final class EmpleadoCardComposer {

    /** CR80 vertical a 300 dpi: 2.125" × 3.375". */
    static final int ANCHO = 638;
    static final int ALTO = 1012;
    private static final int SEPARACION = 24;

    /** Interior del marco de foto del boceto frente (638×1024). */
    private static final int FOTO_X = 184;
    private static final int FOTO_Y = 377;
    private static final int FOTO_W = 270;
    private static final int FOTO_H = 298;

    /** Entre la foto y la franja ADMINISTRATIVO. */
    private static final int NOMBRE_X = 48;
    private static final int NOMBRE_Y = 688;
    private static final int NOMBRE_W = 542;
    private static final int NOMBRE_H = 200;

    /** Adscripción en negro sobre el recuadro blanco del reverso. */
    private static final Color ADSCRIPCION_COLOR = Color.BLACK;

    /** Recuadro blanco bajo la etiqueta Adscripción. */
    private static final int ADS_X = 56;
    private static final int ADS_Y = 182;
    private static final int ADS_W = 526;
    private static final int ADS_H = 90;

    /** Firma del pad: entre el recuadro de adscripción y el texto “Firma del trabajador”. */
    private static final int FIRMA_X = 110;
    private static final int FIRMA_Y = 310;
    private static final int FIRMA_W = 420;
    private static final int FIRMA_H = 150;

    private static final int QR_TAMANO = 220;
    private static final int QR_Y = 770;

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
            Margen margen = margenBlanco(fondo);
            g.drawImage(fondo,
                    0, 0, ANCHO, ALTO,
                    margen.x, margen.y, margen.x + margen.ancho, margen.y + margen.alto,
                    null);
            if (frente) {
                dibujarCubriendo(g, foto, escalaX(FOTO_X, margen), escalaY(FOTO_Y, margen),
                        medidaX(FOTO_W, margen), medidaY(FOTO_H, margen));
                dibujarNombre(g, empleado == null ? "" : empleado.nombreCompleto, margen);
            } else {
                dibujarAdscripcion(g, empleado == null ? "" : empleado.adscripcion, margen);
                dibujarContenido(g, firma,
                        escalaX(FIRMA_X, margen), escalaY(FIRMA_Y, margen),
                        medidaX(FIRMA_W, margen), medidaY(FIRMA_H, margen));
                int ladoQr = Math.min(medidaX(QR_TAMANO, margen), medidaY(QR_TAMANO, margen));
                BufferedImage codigo = qr(empleado == null ? "" : empleado.cadenaQr, ladoQr);
                int qrX = (ANCHO - codigo.getWidth()) / 2;
                g.drawImage(codigo, qrX, escalaY(QR_Y, margen), null);
            }
        } finally {
            g.dispose();
        }
        return lienzo;
    }

    /**
     * Nombre del empleado, entre la fotografía y la franja ADMINISTRATIVO.
     * Si no cabe en un renglón, sigue en las líneas que hagan falta.
     */
    private static void dibujarNombre(Graphics2D g, String nombre, Margen margen) {
        String texto = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
        if (texto.isEmpty()) {
            return;
        }
        int x = escalaX(NOMBRE_X, margen);
        int y = escalaY(NOMBRE_Y, margen);
        int w = medidaX(NOMBRE_W, margen);
        int h = medidaY(NOMBRE_H, margen);
        int ancho = Math.max(1, w - 12);
        int tamano = 44;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, tamano));
        List<String> lineas = envolver(g.getFontMetrics(), texto, ancho);
        if (lineas.size() > 1) {
            tamano = 38;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, tamano));
            lineas = envolver(g.getFontMetrics(), texto, ancho);
        }
        FontMetrics medidas = g.getFontMetrics();
        while (tamano > 22 && lineas.size() * medidas.getHeight() > h) {
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
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.fill(forma);
        g.draw(forma);
        g.setStroke(anterior);
    }

    /**
     * La adscripción cabe en varias líneas dentro del recuadro blanco, en negro.
     * Un texto corto sigue en una sola, centrado; si no cabe, baja de tamaño
     * hasta que el bloque quepa en la franja.
     */
    private static void dibujarAdscripcion(Graphics2D g, String adscripcion, Margen margen) {
        String texto = adscripcion == null ? "" : adscripcion.trim().replaceAll("\\s+", " ");
        if (texto.isEmpty()) {
            return;
        }
        int x = escalaX(ADS_X, margen);
        int y = escalaY(ADS_Y, margen);
        int w = medidaX(ADS_W, margen);
        int h = medidaY(ADS_H, margen);
        int holgura = 8;
        int ancho = Math.max(1, w - holgura * 2);
        int tamano = 20;
        Font fuente = new Font(Font.SANS_SERIF, Font.BOLD, tamano);
        g.setFont(fuente);
        List<String> lineas = envolver(g.getFontMetrics(), texto, ancho);
        if (lineas.size() > 1) {
            tamano = 18;
            fuente = new Font(Font.SANS_SERIF, Font.BOLD, tamano);
            g.setFont(fuente);
            lineas = envolver(g.getFontMetrics(), texto, ancho);
        }
        FontMetrics medidas = g.getFontMetrics();
        while (tamano > 11 && lineas.size() * medidas.getHeight() > h) {
            tamano--;
            fuente = new Font(Font.SANS_SERIF, Font.BOLD, tamano);
            g.setFont(fuente);
            medidas = g.getFontMetrics();
            lineas = envolver(medidas, texto, ancho);
        }
        g.setColor(ADSCRIPCION_COLOR);
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

    /**
     * No usa {@code EncodeHintType.MARGIN}. El SDK de Zebra incluye zxing 1.7, que no
     * tiene ese campo: el hilo de impresión moría con NoSuchFieldError y el velo se quedaba.
     * Se pide la matriz natural y se deja un módulo de margen al dibujarla.
     */
    private static BufferedImage qr(String cadena, int tamano) throws Exception {
        String payload = cadena == null || cadena.isBlank() ? "SMSEM" : cadena;
        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0);
        int[] caja = cajaConMargen(matrix, 1);
        int origenX = caja[0];
        int origenY = caja[1];
        int lado = caja[2];
        BufferedImage imagen = new BufferedImage(tamano, tamano, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < tamano; y++) {
            int srcY = origenY + (y * lado) / tamano;
            for (int x = 0; x < tamano; x++) {
                int srcX = origenX + (x * lado) / tamano;
                imagen.setRGB(x, y, matrix.get(srcX, srcY) ? 0xFF1A2744 : 0xFFFFFFFF);
            }
        }
        return imagen;
    }

    /** x, y y lado del QR con el margen pedido, en píxeles de la matriz. */
    private static int[] cajaConMargen(BitMatrix matrix, int margenModulos) {
        int ancho = matrix.getWidth();
        int alto = matrix.getHeight();
        int minX = ancho;
        int minY = alto;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                if (matrix.get(x, y)) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (maxX < 0) {
            return new int[] {0, 0, Math.max(ancho, 1)};
        }
        int modulo = 1;
        while (minX + modulo <= maxX && matrix.get(minX + modulo, minY)) {
            modulo++;
        }
        int margen = Math.max(0, margenModulos) * modulo;
        int x0 = Math.max(0, minX - margen);
        int y0 = Math.max(0, minY - margen);
        int lado = Math.max(maxX - minX, maxY - minY) + 1 + margen * 2;
        if (x0 + lado > ancho) {
            lado = ancho - x0;
        }
        if (y0 + lado > alto) {
            lado = alto - y0;
        }
        return new int[] {x0, y0, Math.max(lado, 1)};
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

    /**
     * Recorta el marco del boceto (borde blanco o negro del mockup) para que el
     * diseño llegue al canto de la tarjeta al imprimir.
     */
    private static Margen margenBlanco(BufferedImage imagen) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        int arriba = 0;
        int abajo = alto - 1;
        int izquierda = 0;
        int derecha = ancho - 1;
        while (arriba < abajo && filaMarco(imagen, arriba)) {
            arriba++;
        }
        while (abajo > arriba && filaMarco(imagen, abajo)) {
            abajo--;
        }
        while (izquierda < derecha && columnaMarco(imagen, izquierda)) {
            izquierda++;
        }
        while (derecha > izquierda && columnaMarco(imagen, derecha)) {
            derecha--;
        }
        return new Margen(izquierda, arriba, derecha - izquierda + 1, abajo - arriba + 1);
    }

    private static boolean filaMarco(BufferedImage imagen, int y) {
        int marco = 0;
        int ancho = imagen.getWidth();
        for (int x = 0; x < ancho; x++) {
            if (esMarco(imagen.getRGB(x, y))) {
                marco++;
            }
        }
        return marco >= ancho * 0.85;
    }

    private static boolean columnaMarco(BufferedImage imagen, int x) {
        int marco = 0;
        int alto = imagen.getHeight();
        for (int y = 0; y < alto; y++) {
            if (esMarco(imagen.getRGB(x, y))) {
                marco++;
            }
        }
        return marco >= alto * 0.85;
    }

    /** Blanco de margen o negro del trazo del boceto. */
    private static boolean esMarco(int rgb) {
        int rojo = (rgb >> 16) & 0xFF;
        int verde = (rgb >> 8) & 0xFF;
        int azul = rgb & 0xFF;
        return (rojo >= 248 && verde >= 248 && azul >= 248)
                || (rojo <= 40 && verde <= 40 && azul <= 40);
    }

    /** Posición del boceto, ya sin el marco exterior. */
    private static int escalaX(int valor, Margen margen) {
        return (valor - margen.x) * ANCHO / margen.ancho;
    }

    private static int escalaY(int valor, Margen margen) {
        return (valor - margen.y) * ALTO / margen.alto;
    }

    /** Ancho o alto: no se resta el borde, solo se escala. */
    private static int medidaX(int valor, Margen margen) {
        return valor * ANCHO / margen.ancho;
    }

    private static int medidaY(int valor, Margen margen) {
        return valor * ALTO / margen.alto;
    }

    private record Margen(int x, int y, int ancho, int alto) {
    }
}
