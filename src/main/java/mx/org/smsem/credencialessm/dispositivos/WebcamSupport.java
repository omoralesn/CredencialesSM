package mx.org.smsem.credencialessm.dispositivos;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Acceso a cámara web multiplataforma vía OpenCV
 * (Linux V4L2, Windows MSMF/DirectShow, macOS AVFoundation).
 */
public final class WebcamSupport {

    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private WebcamSupport() {
    }

    public static void ensureLoaded() {
        if (LOADED.compareAndSet(false, true)) {
            nu.pattern.OpenCV.loadLocally();
        }
    }

    /**
     * Lista cámaras. Importante: el llamador debe liberar cualquier VideoCapture
     * abierto antes de invocar esto; si el dispositivo está ocupado, OpenCV no lo verá.
     */
    public static List<CameraDevice> listDevices(int maxProbe) {
        ensureLoaded();
        Set<Integer> indices = new LinkedHashSet<>();
        if (isLinux()) {
            File dev = new File("/dev");
            File[] nodes = dev.listFiles((d, name) -> name.matches("video\\d+"));
            if (nodes != null) {
                for (File node : nodes) {
                    try {
                        indices.add(Integer.parseInt(node.getName().substring(5)));
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                }
            }
        }
        int limit = Math.max(1, Math.min(maxProbe, 8));
        for (int i = 0; i < limit; i++) {
            indices.add(i);
        }

        List<CameraDevice> devices = new ArrayList<>();
        for (int i : indices) {
            VideoCapture capture = openCapture(i);
            if (capture != null && capture.isOpened()) {
                Mat probe = new Mat();
                boolean readable = capture.read(probe) && !probe.empty();
                probe.release();
                if (!readable) {
                    capture.release();
                    continue;
                }
                String name = "Cámara " + i;
                double w = capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
                double h = capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
                if (w > 0 && h > 0) {
                    name = name + " (" + (int) w + "×" + (int) h + ")";
                }
                devices.add(new CameraDevice(i, name));
                capture.release();
            }
        }
        return devices;
    }

    public static VideoCapture openCapture(int index) {
        ensureLoaded();
        int[] backends = backends();
        VideoCapture capture = null;
        for (int backend : backends) {
            VideoCapture intento = new VideoCapture(index, backend);
            if (intento.isOpened()) {
                capture = intento;
                break;
            }
            intento.release();
        }
        if (capture == null) {
            return null;
        }
        // Preferencia razonable; el driver puede ignorarla
        capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 1280);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 720);
        return capture;
    }

    private static int[] backends() {
        if (isLinux()) {
            return new int[] {Videoio.CAP_V4L2, Videoio.CAP_ANY};
        }
        if (isWindows()) {
            return new int[] {Videoio.CAP_MSMF, Videoio.CAP_DSHOW, Videoio.CAP_ANY};
        }
        return new int[] {Videoio.CAP_ANY};
    }

    private static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("linux");
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    public static BufferedImage toBufferedImage(Mat frame) {
        if (frame == null || frame.empty()) {
            return null;
        }
        Mat bgr = new Mat();
        try {
            if (frame.channels() == 1) {
                Imgproc.cvtColor(frame, bgr, Imgproc.COLOR_GRAY2BGR);
            } else if (frame.channels() == 4) {
                Imgproc.cvtColor(frame, bgr, Imgproc.COLOR_BGRA2BGR);
            } else if (frame.channels() == 3) {
                frame.copyTo(bgr);
            } else {
                return null;
            }
            int width = bgr.cols();
            int height = bgr.rows();
            byte[] data = new byte[width * height * 3];
            bgr.get(0, 0, data);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(data, 0, target, 0, data.length);
            return image;
        } finally {
            bgr.release();
        }
    }

    /** Recorte central vertical (retrato 3:4) para credencial. */
    public static Mat centerCropPortrait(Mat frame) {
        if (frame == null || frame.empty()) {
            throw new IllegalArgumentException("Frame vacío");
        }
        int fw = frame.cols();
        int fh = frame.rows();
        int cropW = (int) Math.round(fh * 3.0 / 4.0);
        int cropH = fh;
        if (cropW > fw) {
            cropW = fw;
            cropH = (int) Math.round(fw * 4.0 / 3.0);
            if (cropH > fh) {
                cropH = fh;
                cropW = (int) Math.round(fh * 3.0 / 4.0);
            }
        }
        cropW = Math.max(1, Math.min(cropW, fw));
        cropH = Math.max(1, Math.min(cropH, fh));
        int x = (fw - cropW) / 2;
        int y = (fh - cropH) / 2;
        return frame.submat(y, y + cropH, x, x + cropW).clone();
    }

    public static byte[] encodeJpeg(Mat frame, int quality) {
        MatOfByte buffer = new MatOfByte();
        MatOfInt compress = new MatOfInt(
                Imgcodecs.IMWRITE_JPEG_QUALITY, Math.max(50, Math.min(quality, 95)));
        if (!Imgcodecs.imencode(".jpg", frame, buffer, compress)) {
            throw new IllegalStateException("No se pudo codificar la fotografía JPEG");
        }
        return buffer.toArray();
    }

    public static String openCvVersion() {
        ensureLoaded();
        return Core.VERSION;
    }

    public record CameraDevice(int index, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
