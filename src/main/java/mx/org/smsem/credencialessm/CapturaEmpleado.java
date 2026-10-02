package mx.org.smsem.credencialessm;

import mx.gob.sidi.desk.app.ui.CameraCaptureDialog;
import mx.gob.sidi.desk.app.ui.SignatureCaptureDialog;
import mx.gob.sidi.desk.devices.TopazHidPad;
import mx.gob.sidi.desk.devices.TopazSerialPad;
import mx.gob.sidi.desk.devices.WebcamSupport;
import javafx.stage.Stage;

import java.util.Locale;

/**
 * Fotografía de la cámara y firma del pad. Sin imagen de respaldo.
 */
public final class CapturaEmpleado {

    private CapturaEmpleado() {
    }

    public static byte[] foto(Stage stage) {
        if (!camaraDisponible()) {
            throw new IllegalStateException(mensajeSinCamara());
        }
        return CameraCaptureDialog.show(stage).orElse(null);
    }

    public static byte[] firma(Stage stage) {
        if (!padDisponible()) {
            throw new IllegalStateException("No hay pad de firma conectado.");
        }
        return SignatureCaptureDialog.show(stage).orElse(null);
    }

    private static boolean camaraDisponible() {
        try {
            return !WebcamSupport.listDevices(4).isEmpty();
        } catch (Exception | UnsatisfiedLinkError ex) {
            return false;
        }
    }

    private static String mensajeSinCamara() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "No hay cámara disponible. En Windows revise Privacidad y seguridad → Cámara "
                    + "y permita el acceso a las aplicaciones de escritorio.";
        }
        return "No hay cámara conectada.";
    }

    private static boolean padDisponible() {
        try {
            return TopazHidPad.findTopazHid().isPresent() || TopazSerialPad.findFirst().isPresent();
        } catch (Exception | UnsatisfiedLinkError ex) {
            return false;
        }
    }
}
