package mx.org.smsem.credencialessm.dispositivos;

import com.fazecast.jSerialComm.SerialPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Pad Topaz por puerto serial USB (FTDI BSB, SigLite, etc.).
 * Parámetro crítico: paridad impar (topaz-dext / SigPlus).
 */
public final class TopazSerialPad implements AutoCloseable {

    private final String portPath;
    private final TopazModel model;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong pointCount = new AtomicLong();
    private Thread reader;
    private volatile SerialPort port;
    private final List<Consumer<TopazPadPoint>> listeners = new CopyOnWriteArrayList<>();

    public TopazSerialPad(String portPath, TopazModel model) {
        this.portPath = portPath;
        this.model = model != null ? model : TopazModel.SIGNATURE_GEM_1X5;
    }

    public String getPortPath() {
        return portPath;
    }

    public TopazModel getModel() {
        return model;
    }

    public long getPointCount() {
        return pointCount.get();
    }

    public void addListener(Consumer<TopazPadPoint> listener) {
        listeners.add(listener);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        reader = new Thread(this::loop, "topaz-serial-" + portPath);
        reader.setDaemon(true);
        reader.start();
    }

    public void stop() {
        running.set(false);
        SerialPort p = port;
        if (p != null) {
            try {
                p.closePort();
            } catch (Exception ignored) {
                // unblock
            }
        }
        if (reader != null) {
            reader.interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void loop() {
        SerialPort p = SerialPort.getCommPort(portPath);
        p.setComPortParameters(
                model.baud(),
                model.dataBits(),
                SerialPort.ONE_STOP_BIT,
                model.oddParity() ? SerialPort.ODD_PARITY : SerialPort.NO_PARITY);
        p.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 200, 0);
        if (!p.openPort()) {
            System.err.println("Topaz serial: no se pudo abrir " + portPath);
            running.set(false);
            return;
        }
        port = p;
        TopazSerialProtocol proto = new TopazSerialProtocol();
        byte[] buf = new byte[256];
        try {
            while (running.get()) {
                int n = p.readBytes(buf, buf.length);
                if (n <= 0) {
                    continue;
                }
                proto.acceptAll(buf, 0, n, (kind, rawX, rawY, penDown, penNear, status) -> {
                    if (!TopazSerialProtocol.isPenKind(kind)) {
                        return;
                    }
                    if (!model.rawInRange(rawX, rawY)) {
                        return;
                    }
                    if (!penDown && !penNear) {
                        return;
                    }
                    TopazPadPoint pt = new TopazPadPoint(
                            model.scaleX(rawX), model.scaleY(rawY), penDown, penNear);
                    pointCount.incrementAndGet();
                    for (Consumer<TopazPadPoint> l : listeners) {
                        l.accept(pt);
                    }
                });
            }
        } catch (Exception ex) {
            if (running.get()) {
                System.err.println("Topaz serial: " + ex.getMessage());
            }
        } finally {
            try {
                p.closePort();
            } catch (Exception ignored) {
            }
            port = null;
            running.set(false);
        }
    }

    public record DeviceInfo(String portPath, String name, TopazModel model) {
    }

    public static List<DeviceInfo> findAll() {
        List<DeviceInfo> out = new ArrayList<>();
        for (SerialPort sp : SerialPort.getCommPorts()) {
            String desc = safe(sp.getPortDescription()) + " " + safe(sp.getSystemPortName())
                    + " " + safe(sp.getDescriptivePortName());
            String sys = sp.getSystemPortPath();
            if (sys == null || sys.isBlank()) {
                sys = sp.getSystemPortName();
            }
            boolean topazName = TopazModel.looksLikeTopazSerialName(desc);
            boolean ftdi = desc.toUpperCase().contains("FTDI") || desc.toUpperCase().contains("FT232")
                    || desc.toUpperCase().contains("USB SERIAL");
            if (!topazName && !(ftdi && puertoSerialGenerico(sys))) {
                continue;
            }
            TopazModel model = topazName ? TopazModel.SIGNATURE_GEM_1X5 : TopazModel.SIGLITE_1X5;
            String label = (topazName ? "Topaz serial" : "FTDI serial (posible Topaz)") + " " + sys;
            out.add(new DeviceInfo(sys, label, model));
        }
        return out;
    }

    public static Optional<DeviceInfo> findFirst() {
        List<DeviceInfo> all = findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    private static boolean puertoSerialGenerico(String sys) {
        if (sys == null || sys.isBlank()) {
            return false;
        }
        String ruta = sys.toUpperCase(Locale.ROOT);
        return ruta.contains("TTYUSB") || ruta.contains("TTYACM") || ruta.matches(".*COM\\d+.*");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
