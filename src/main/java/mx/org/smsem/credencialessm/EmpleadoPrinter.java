package mx.org.smsem.credencialessm;

import mx.org.smsem.credencialessm.dispositivos.CredentialPrintJob;
import mx.org.smsem.credencialessm.dispositivos.PrintResult;
import mx.org.smsem.credencialessm.dispositivos.ZxpSeries7Printer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class EmpleadoPrinter {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private final Path dataDir;
    private final EmpleadoCardComposer composer = new EmpleadoCardComposer();

    public EmpleadoPrinter(Path dataDir) {
        this.dataDir = dataDir;
    }

    public PrintResult imprimir(EmpleadoSmsem empleado, byte[] foto, byte[] firma, boolean aArchivo) throws Exception {
        BufferedImage hoja = composer.compose(empleado, foto, firma);
        String safe = empleado.claveInterna == null ? "job" : empleado.claveInterna.replaceAll("[^a-zA-Z0-9_-]", "");
        Path out = dataDir.resolve(ConstantesEstacion.texto("impresora.carpeta")).resolve(
                "empleado-" + FILE_TS.format(Instant.now()) + "-" + safe + ".png");
        Files.createDirectories(out.getParent());
        ImageIO.write(hoja, "png", out.toFile());
        if (aArchivo) {
            return PrintResult.ok("Credencial guardada en archivo", out);
        }
        String modo = ConstantesEstacion.texto("impresora.modo").toLowerCase(Locale.ROOT);
        if ("preview".equals(modo)) {
            return PrintResult.ok("Vista previa generada (sin impresora física)", out);
        }
        BufferedImage frente = hoja.getSubimage(0, 0, EmpleadoCardComposer.ANCHO, EmpleadoCardComposer.ALTO);
        int yReverso = hoja.getHeight() - EmpleadoCardComposer.ALTO;
        BufferedImage reverso = hoja.getSubimage(0, yReverso, EmpleadoCardComposer.ANCHO, EmpleadoCardComposer.ALTO);
        ZxpSeries7Printer zxp = new ZxpSeries7Printer(dataDir);
        CredentialPrintJob trabajo = new CredentialPrintJob(
                safe, safe, safe, "", empleado.clave, empleado.nombreCompleto, "",
                "EMPLEADO", null, null);
        return zxp.printPreparedFaces(frente, reverso, trabajo);
    }

    /** True solo si va a impresora física (no checkbox archivo ni modo preview). */
    public static boolean esImpresionFisica(boolean aArchivo) {
        if (aArchivo) {
            return false;
        }
        String modo = ConstantesEstacion.texto("impresora.modo").toLowerCase(Locale.ROOT);
        return !"preview".equals(modo);
    }
}
