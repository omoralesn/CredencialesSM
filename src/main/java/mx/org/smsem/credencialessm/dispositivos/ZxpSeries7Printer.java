package mx.org.smsem.credencialessm.dispositivos;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Envía frente y reverso a la ZXP Series 7 por USB.
 * Cada cara es un bitmap CR80 propio; no usa la cola de Windows.
 */
public final class ZxpSeries7Printer {

    private static final Logger LOG = Logger.getLogger(ZxpSeries7Printer.class.getName());
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());
    private static final String[] DLLS = {
            "ZebraNativeUsbAdapter_64.dll",
            "ZebraNativeUsbAdapter_32.dll"
    };

    private final Path previewDir;

    public ZxpSeries7Printer(Path dataDir) {
        this.previewDir = dataDir.resolve("print-preview");
    }

    /**
     * Frente y, si viene, reverso, en un solo trabajo USB.
     * Origen 0,0 y tamaño real de cada imagen (638×1012 vertical).
     */
    public PrintResult printPreparedFaces(BufferedImage frente, BufferedImage reverso, CredentialPrintJob job) {
        try {
            if (frente == null) {
                throw new IllegalStateException("No hay frente para imprimir.");
            }
            String safeId = job.getJobId() == null ? "job" : job.getJobId().replaceAll("[^a-zA-Z0-9_-]", "");
            Files.createDirectories(previewDir);
            Path audit = previewDir.resolve("zxp7-" + FILE_TS.format(Instant.now()) + "-" + safeId + ".png");
            ImageIO.write(frente, "png", audit.toFile());
            List<Cara> caras = new ArrayList<>();
            caras.add(new Cara("Front", frente));
            if (reverso != null) {
                caras.add(new Cara("Back", reverso));
                ImageIO.write(reverso, "png", previewDir.resolve(
                        "zxp7-" + FILE_TS.format(Instant.now()) + "-" + safeId + "-R.png").toFile());
            }
            String destino = enviarPorUsb(caras);
            LOG.info(() -> "ZXP7 PRINT OK jobId=" + job.getJobId() + " via=" + destino + " audit=" + audit);
            return PrintResult.ok("Enviado a " + destino, audit.toAbsolutePath());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Fallo impresión ZXP Series 7: " + ex.getMessage(), ex);
        }
    }

    public static boolean isSdkPresent() {
        String[] candidates = {
                "com.zebra.sdk.common.card.graphics.ZebraCardGraphics",
                "com.zebra.sdk.common.card.printer.ZebraCardPrinterFactory",
                "com.zebra.sdk.comm.UsbConnection"
        };
        for (String name : candidates) {
            try {
                Class.forName(name);
                return true;
            } catch (ClassNotFoundException ignored) {
                // try next
            }
        }
        return false;
    }

    private String enviarPorUsb(List<Cara> caras) throws Exception {
        if (!isSdkPresent()) {
            throw new IllegalStateException(
                    "No está el SDK de la Zebra en el programa. No se usa el driver de Windows.");
        }
        cargarUsbNativo();
        Class<?> descubridor = Class.forName("com.zebra.sdk.printer.discovery.UsbDiscoverer");
        Object[] usb = (Object[]) invocarEstatico(descubridor, "getZebraUsbPrinters");
        if (usb == null || usb.length == 0) {
            throw new IllegalStateException(
                    "USB no reportó una Zebra. Revise que la ZXP esté encendida y conectada.");
        }
        Object conexion = invocar(usb[0], "getConnection");
        invocar(conexion, "open");
        Object impresora = null;
        Object graficos = null;
        try {
            Class<?> fabrica = Class.forName("com.zebra.sdk.common.card.printer.ZebraCardPrinterFactory");
            Class<?> tipoConexion = Class.forName("com.zebra.sdk.comm.Connection");
            impresora = invocarEstatico(fabrica, "getInstance", new Class<?>[]{tipoConexion}, new Object[]{conexion});
            Class<?> tipoGrafico = Class.forName("com.zebra.sdk.common.card.graphics.ZebraCardGraphics");
            Class<?> tipoImpresora = Class.forName("com.zebra.sdk.common.card.printer.ZebraCardPrinter");
            graficos = tipoGrafico.getConstructor(tipoImpresora).newInstance(impresora);
            Class<?> orientacion = Class.forName("com.zebra.sdk.common.card.enumerations.OrientationType");
            Class<?> tipoImpresion = Class.forName("com.zebra.sdk.common.card.enumerations.PrintType");
            Class<?> rotacion = Class.forName("com.zebra.sdk.common.card.graphics.enumerations.RotationType");
            Class<?> lado = Class.forName("com.zebra.sdk.common.card.enumerations.CardSide");
            Class<?> tipoDibujo = Class.forName("com.zebra.sdk.common.card.enumerations.GraphicType");
            Object color = enumValor(tipoImpresion, "Color");
            Object sinGiro = enumValor(rotacion, "RotateNoneFlipNone");
            Object bmp = enumValor(tipoDibujo, "BMP");
            Class<?> infoTipo = Class.forName("com.zebra.sdk.common.card.containers.GraphicsInfo");
            List<Object> trabajos = new ArrayList<>();
            for (Cara cara : caras) {
                BufferedImage imagen = cara.imagen;
                int ancho = imagen.getWidth();
                int alto = imagen.getHeight();
                Object orient = enumValor(orientacion, alto >= ancho ? "Portrait" : "Landscape");
                tipoGrafico.getMethod("initialize", int.class, int.class, orientacion, tipoImpresion, Color.class)
                        .invoke(graficos, ancho, alto, orient, color, Color.WHITE);
                byte[] pixeles = (byte[]) tipoGrafico.getMethod("imageToImageData", BufferedImage.class)
                        .invoke(graficos, imagen);
                tipoGrafico.getMethod("drawImage", byte[].class, int.class, int.class, int.class, int.class, rotacion)
                        .invoke(graficos, pixeles, 0, 0, ancho, alto, sinGiro);
                Object dibujo = tipoGrafico.getMethod("createImage").invoke(graficos);
                Object info = infoTipo.getDeclaredConstructor().newInstance();
                infoTipo.getField("side").set(info, enumValor(lado, cara.lado));
                infoTipo.getField("printType").set(info, color);
                infoTipo.getField("graphicType").set(info, bmp);
                infoTipo.getField("graphicData").set(info, dibujo);
                infoTipo.getField("xOffset").setInt(info, 0);
                infoTipo.getField("yOffset").setInt(info, 0);
                infoTipo.getField("opacity").setInt(info, 100);
                trabajos.add(info);
                tipoGrafico.getMethod("clear").invoke(graficos);
            }
            invocar(impresora, "print", new Class<?>[]{int.class, List.class}, new Object[]{1, trabajos});
            String nombre = String.valueOf(invocar(conexion, "getSimpleConnectionName"));
            return "ZXP Series 7 USB (" + nombre + ")";
        } finally {
            cerrar(graficos, "close");
            cerrar(impresora, "destroy");
            cerrar(conexion, "close");
        }
    }

    private static void cargarUsbNativo() {
        for (Path dll : dllsCandidatos()) {
            if (!Files.isRegularFile(dll)) {
                continue;
            }
            try {
                System.load(dll.toAbsolutePath().toString());
                LOG.info(() -> "USB Zebra: " + dll);
                return;
            } catch (UnsatisfiedLinkError ex) {
                String texto = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                if (texto.contains("already loaded") || texto.contains("ya se ha cargado")) {
                    return;
                }
                LOG.log(Level.FINE, "No se cargó " + dll, ex);
            }
        }
    }

    private static List<Path> dllsCandidatos() {
        List<Path> bases = new ArrayList<>();
        bases.add(Path.of(System.getProperty("user.dir", ".")));
        try {
            var origen = ZxpSeries7Printer.class.getProtectionDomain().getCodeSource();
            if (origen != null && origen.getLocation() != null) {
                Path codigo = Path.of(origen.getLocation().toURI());
                bases.add(Files.isDirectory(codigo) ? codigo : codigo.getParent());
            }
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            // Sin carpeta del jar se prueba solo el directorio de trabajo.
        }
        List<Path> dlls = new ArrayList<>();
        for (Path base : bases) {
            if (base == null) {
                continue;
            }
            for (String nombre : DLLS) {
                dlls.add(base.resolve(nombre));
                dlls.add(base.resolve("natives").resolve(nombre));
            }
        }
        return dlls;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValor(Class<?> tipo, String nombre) {
        return Enum.valueOf((Class) tipo, nombre);
    }

    private static Object invocar(Object destino, String metodo) throws Exception {
        return invocar(destino, metodo, new Class<?>[0], new Object[0]);
    }

    private static Object invocar(Object destino, String metodo, Class<?>[] tipos, Object[] args) throws Exception {
        try {
            return destino.getClass().getMethod(metodo, tipos).invoke(destino, args);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception causa) {
                throw causa;
            }
            throw ex;
        }
    }

    private static Object invocarEstatico(Class<?> tipo, String metodo) throws Exception {
        return invocarEstatico(tipo, metodo, new Class<?>[0], new Object[0]);
    }

    private static Object invocarEstatico(Class<?> tipo, String metodo, Class<?>[] tipos, Object[] args) throws Exception {
        try {
            return tipo.getMethod(metodo, tipos).invoke(null, args);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception causa) {
                throw causa;
            }
            throw ex;
        }
    }

    private static void cerrar(Object destino, String metodo) {
        if (destino == null) {
            return;
        }
        try {
            destino.getClass().getMethod(metodo).invoke(destino);
        } catch (Exception ex) {
            LOG.log(Level.FINE, "No se pudo cerrar " + metodo, ex);
        }
    }

    private record Cara(String lado, BufferedImage imagen) {
    }
}
