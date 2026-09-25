package mx.org.smsem.credencialessm;

import mx.gob.sidi.desk.devices.CredentialPrintJob;
import mx.gob.sidi.desk.devices.PrintResult;
import mx.gob.sidi.desk.devices.ZxpSeries7Printer;

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
        BufferedImage face = composer.compose(empleado, foto, firma);
        String safe = empleado.claveInterna == null ? "job" : empleado.claveInterna.replaceAll("[^a-zA-Z0-9_-]", "");
        Path out = dataDir.resolve("print-preview").resolve(
                "empleado-" + FILE_TS.format(Instant.now()) + "-" + safe + ".png");
        Files.createDirectories(out.getParent());
        ImageIO.write(face, "png", out.toFile());
        if (aArchivo) {
            return PrintResult.ok("Credencial guardada en archivo", out);
        }
        String modo = System.getenv().getOrDefault("SIDI_PRINTER", "zxp7").trim().toLowerCase(Locale.ROOT);
        if ("preview".equals(modo) || "mock".equals(modo)) {
            return PrintResult.ok("Vista previa generada (sin impresora física)", out);
        }
        CredentialPrintJob job = new CredentialPrintJob(
                safe, safe, safe, "", empleado.clave, empleado.nombreCompleto, "",
                "EMPLEADO", null, null);
        return new ZxpSeries7Printer(dataDir).printPreparedFace(face, job);
    }
}
