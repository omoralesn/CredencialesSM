package mx.org.smsem.credencialessm.dispositivos;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Pad Topaz por {@code /dev/hidraw*} (p.ej. SignatureGem 1X5-HID {@code 06a8:0043}).
 * <p>
 * El firmware encapsula el protocolo serial de 5 bytes en reportes de 8 bytes
 * ({@code status = 0x20|len}). Solo se dibujan paquetes de punta (kind 0/1/4);
 * los ecos de comando (kind 2/3) se ignoran — antes se interpretaban mal y
 * generaban cuadrados/triángulos.
 */
public final class TopazHidPad implements AutoCloseable {

    private static final byte CMD_DC1 = 0x11;
    private static final int REPORT_LEN = 8;
    /** Sondeo suave — inundar OUT puede silenciar el firmware HID. */
    private static final long POLL_INTERVAL_MS = 80L;
    private static final long SILENCE_BEFORE_POLL_MS = 120L;

    private final Path hidraw;
    private final TopazModel model;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong reportCount = new AtomicLong();
    private final AtomicLong pointCount = new AtomicLong();
    private Thread reader;
    private volatile Closeable streamCloser;
    private final List<Consumer<TopazPadPoint>> listeners = new CopyOnWriteArrayList<>();

    public TopazHidPad(Path hidraw) {
        this(hidraw, TopazModel.SIGNATURE_GEM_1X5_HID);
    }

    public TopazHidPad(Path hidraw, TopazModel model) {
        this.hidraw = hidraw;
        this.model = model != null ? model : TopazModel.SIGNATURE_GEM_1X5_HID;
    }

    public Path getHidraw() {
        return hidraw;
    }

    public TopazModel getModel() {
        return model;
    }

    public long getReportCount() {
        return reportCount.get();
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
        reader = new Thread(this::loop, "topaz-hid-" + hidraw.getFileName());
        reader.setDaemon(true);
        reader.start();
    }

    public void stop() {
        running.set(false);
        Closeable c = streamCloser;
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
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
        if (isWindows()) {
            loopWindows();
            return;
        }
        byte[] outCmd = new byte[REPORT_LEN];
        outCmd[0] = CMD_DC1;
        byte[] buf = new byte[64];
        byte[] payload = new byte[64];
        TopazSerialProtocol proto = new TopazSerialProtocol();

        FileOutputStream out = null;
        FileInputStream in = null;
        try {
            out = new FileOutputStream(hidraw.toFile());
            in = new FileInputStream(hidraw.toFile());
            final FileOutputStream outF = out;
            final FileInputStream inF = in;
            streamCloser = () -> {
                try {
                    inF.close();
                } finally {
                    outF.close();
                }
            };

            out.write(outCmd);
            out.flush();
            Thread.sleep(80);

            final AtomicLong lastDataMs = new AtomicLong(System.currentTimeMillis());
            final AtomicBoolean triedReset = new AtomicBoolean(false);
            final long started = System.currentTimeMillis();
            Thread poller = new Thread(() -> {
                try {
                    while (running.get()) {
                        long idle = System.currentTimeMillis() - lastDataMs.get();
                        if (idle >= SILENCE_BEFORE_POLL_MS) {
                            outF.write(outCmd);
                            outF.flush();
                        }
                        if (!triedReset.get() && reportCount.get() == 0
                                && System.currentTimeMillis() - started > 1200L) {
                            if (triedReset.compareAndSet(false, true)) {
                                System.err.println("Topaz HID sin respuesta — intentando reset USB…");
                                tryUsbReset();
                                try {
                                    inF.close();
                                } catch (IOException ignored) {
                                }
                                return;
                            }
                        }
                        Thread.sleep(POLL_INTERVAL_MS);
                    }
                } catch (IOException | InterruptedException ignored) {
                    // cierre
                }
            }, "topaz-hid-poll-" + hidraw.getFileName());
            poller.setDaemon(true);
            poller.start();

            boolean reopened = false;
            while (running.get()) {
                int n;
                try {
                    n = in.read(buf);
                } catch (IOException ex) {
                    if (running.get() && triedReset.get() && !reopened) {
                        reopened = true;
                        try {
                            Thread.sleep(1200);
                            in = new FileInputStream(resolveHidraw().toFile());
                            out = new FileOutputStream(resolveHidraw().toFile());
                            final FileInputStream in2 = in;
                            final FileOutputStream out2 = out;
                            streamCloser = () -> {
                                try {
                                    in2.close();
                                } finally {
                                    out2.close();
                                }
                            };
                            out.write(outCmd);
                            out.flush();
                            lastDataMs.set(System.currentTimeMillis());
                            continue;
                        } catch (Exception reopenEx) {
                            System.err.println("Topaz HID reabrir tras reset: " + reopenEx.getMessage());
                            break;
                        }
                    }
                    if (running.get()) {
                        throw ex;
                    }
                    break;
                }
                if (n < 0) {
                    break;
                }
                if (n == 0) {
                    continue;
                }
                reportCount.incrementAndGet();
                lastDataMs.set(System.currentTimeMillis());

                if (!hasNonZero(buf, n)) {
                    continue;
                }

                int plen = TopazSerialProtocol.unwrapHidReport(buf, n, payload);
                if (plen <= 0) {
                    continue;
                }
                proto.acceptAll(payload, 0, plen, this::onSerialPacket);
            }
            poller.interrupt();
        } catch (IOException ex) {
            if (running.get()) {
                System.err.println("Topaz HID: " + ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            streamCloser = null;
            running.set(false);
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ignored) {
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void loopWindows() {
        TopazWindowsHid.Session session = null;
        try {
            session = TopazWindowsHid.open(hidraw.toString());
            if (session == null) {
                System.err.println("Topaz HID: Windows no dejó abrir " + hidraw);
                return;
            }
            final TopazWindowsHid.Session abierta = session;
            streamCloser = abierta;
            byte[] outCmd = new byte[REPORT_LEN];
            outCmd[0] = CMD_DC1;
            byte[] buf = new byte[64];
            byte[] payload = new byte[64];
            TopazSerialProtocol proto = new TopazSerialProtocol();
            abierta.write(outCmd);
            while (running.get()) {
                int n = abierta.read(buf);
                if (n <= 0) {
                    if (running.get()) {
                        abierta.write(outCmd);
                    }
                    continue;
                }
                reportCount.incrementAndGet();
                if (!hasNonZero(buf, n)) {
                    continue;
                }
                int usable = n;
                byte[] report = buf;
                if (n > 2 && buf[0] == 0 && (((buf[1] & 0xF0) == 0x20) || (buf[1] & 0x80) != 0)) {
                    report = new byte[n - 1];
                    System.arraycopy(buf, 1, report, 0, report.length);
                    usable = report.length;
                }
                int plen = TopazSerialProtocol.unwrapHidReport(report, usable, payload);
                if (plen <= 0) {
                    continue;
                }
                proto.acceptAll(payload, 0, plen, this::onSerialPacket);
            }
        } catch (Exception ex) {
            if (running.get()) {
                System.err.println("Topaz HID: " + ex.getMessage());
            }
        } finally {
            streamCloser = null;
            running.set(false);
            if (session != null) {
                session.close();
            }
        }
    }

    private void onSerialPacket(int kind, int rawX, int rawY, boolean penDown, boolean penNear, byte status) {
        // Solo tinta: kind 0/1/4. Ignorar info de dispositivo (2) y ecos de comando (3).
        if (!TopazSerialProtocol.isPenKind(kind)) {
            return;
        }
        if (!model.rawInRange(rawX, rawY)) {
            return;
        }
        // Requiere punta o proximidad real — no forzar penDown en ecos
        if (!penDown && !penNear) {
            return;
        }
        TopazPadPoint pt = new TopazPadPoint(model.scaleX(rawX), model.scaleY(rawY), penDown, penNear);
        pointCount.incrementAndGet();
        for (Consumer<TopazPadPoint> l : listeners) {
            l.accept(pt);
        }
    }

    private Path resolveHidraw() {
        return findTopazHid().map(DeviceInfo::hidraw).orElse(hidraw);
    }

    private static boolean hasNonZero(byte[] buf, int n) {
        for (int i = 0; i < n; i++) {
            if (buf[i] != 0) {
                return true;
            }
        }
        return false;
    }

    /** Reset USBDEVFS del dispositivo 06a8 si hay permiso en /dev/bus/usb/... */
    private static void tryUsbReset() {
        Path sys = Path.of("/sys/bus/usb/devices");
        if (!Files.isDirectory(sys)) {
            return;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sys)) {
            for (Path entry : ds) {
                Path vid = entry.resolve("idVendor");
                if (!Files.isRegularFile(vid)) {
                    continue;
                }
                if (!"06a8".equalsIgnoreCase(Files.readString(vid).trim())) {
                    continue;
                }
                String node = null;
                for (String line : Files.readString(entry.resolve("uevent")).split("\n")) {
                    if (line.startsWith("DEVNAME=")) {
                        node = "/dev/" + line.substring(8);
                    }
                }
                if (node == null || !Files.isWritable(Path.of(node))) {
                    return;
                }
                // ioctl USBDEVFS_RESET vía script portable (evita JNI)
                Path script = Path.of("scripts/reset-topaz-usb.sh");
                if (!Files.isExecutable(script)) {
                    script = Path.of("/home/ojmn/EspaciosTrabajo/Eclipse/SIDID/scripts/reset-topaz-usb.sh");
                }
                if (Files.isExecutable(script)) {
                    new ProcessBuilder(script.toString())
                            .redirectErrorStream(true)
                            .start()
                            .waitFor();
                    Thread.sleep(1500);
                }
                return;
            }
        } catch (Exception ex) {
            System.err.println("Topaz USB reset: " + ex.getMessage());
        }
    }

    public record DeviceInfo(Path hidraw, String name, int vendorId, int productId, TopazModel model) {
    }

    public static boolean canOpen(DeviceInfo info) {
        if (info == null || info.hidraw() == null) {
            return false;
        }
        if (isWindows()) {
            return true;
        }
        return Files.isReadable(info.hidraw()) && Files.isWritable(info.hidraw());
    }

    public static Optional<DeviceInfo> findTopazHid() {
        if (isWindows()) {
            try {
                return TopazWindowsHid.find();
            } catch (Exception | UnsatisfiedLinkError ex) {
                return Optional.empty();
            }
        }
        Path sys = Path.of("/sys/class/hidraw");
        if (!Files.isDirectory(sys)) {
            return Optional.empty();
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sys)) {
            for (Path entry : ds) {
                Path uevent = entry.resolve("device/uevent");
                if (!Files.isRegularFile(uevent)) {
                    continue;
                }
                String text = Files.readString(uevent);
                String upper = text.toUpperCase(Locale.ROOT);
                if (!upper.contains("06A8") && !upper.contains("TOPAZ")) {
                    continue;
                }
                String name = entry.getFileName().toString();
                Path node = Path.of("/dev", name);
                if (!Files.exists(node)) {
                    continue;
                }
                int vid = TopazModel.VENDOR_TOPAZ;
                int pid = 0x0043;
                for (String line : text.split("\n")) {
                    if (line.startsWith("HID_ID=")) {
                        String[] parts = line.substring(7).split(":");
                        if (parts.length >= 3) {
                            try {
                                vid = Integer.parseInt(parts[1], 16);
                                pid = Integer.parseInt(parts[2], 16);
                            } catch (NumberFormatException ignored) {
                                // defaults
                            }
                        }
                    }
                }
                TopazModel model = TopazModel.forUsbProduct(pid);
                String label = model.displayName() + " (" + name + ")";
                return Optional.of(new DeviceInfo(node, label, vid, pid, model));
            }
        } catch (IOException ex) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static List<String> diagnose() {
        if (isWindows()) {
            try {
                return TopazWindowsHid.diagnose();
            } catch (Exception | UnsatisfiedLinkError ex) {
                return List.of("No se pudo consultar el pad HID en Windows: " + ex.getMessage());
            }
        }
        List<String> lines = new ArrayList<>();
        Optional<DeviceInfo> info = findTopazHid();
        if (info.isEmpty()) {
            lines.add("No hay hidraw Topaz (VID 06A8).");
            return lines;
        }
        DeviceInfo d = info.get();
        lines.add("HID: " + d.name() + " → " + d.hidraw());
        lines.add(String.format("USB %04x:%04x modelo=%s", d.vendorId(), d.productId(), d.model().id()));
        boolean canRead = Files.isReadable(d.hidraw());
        boolean canWrite = Files.isWritable(d.hidraw());
        if (!canRead || !canWrite) {
            lines.add("Falta permiso RW → ./scripts/setup-topaz.sh");
        } else {
            lines.add("Permiso RW OK.");
        }
        return lines;
    }
}
