package mx.org.smsem.credencialessm.dispositivos;

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesSpecification;

import java.io.Closeable;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pad Topaz por la API HID de Windows (VID 06A8). En Linux se sigue usando hidraw.
 */
final class TopazWindowsHid {

    private TopazWindowsHid() {
    }

    static Optional<TopazHidPad.DeviceInfo> find() {
        HidServices services = null;
        try {
            services = arrancar();
            for (HidDevice device : services.getAttachedHidDevices()) {
                TopazHidPad.DeviceInfo info = infoDe(device);
                if (info != null) {
                    return Optional.of(info);
                }
            }
            return Optional.empty();
        } finally {
            detener(services);
        }
    }

    static List<String> diagnose() {
        List<String> lines = new ArrayList<>();
        HidServices services = null;
        try {
            services = arrancar();
            TopazHidPad.DeviceInfo info = null;
            HidDevice abierto = null;
            for (HidDevice device : services.getAttachedHidDevices()) {
                TopazHidPad.DeviceInfo candidato = infoDe(device);
                if (candidato != null) {
                    info = candidato;
                    abierto = device;
                    break;
                }
            }
            if (info == null) {
                lines.add("No se encontró un pad Topaz (USB 06A8).");
                return lines;
            }
            lines.add("HID: " + info.name());
            lines.add(String.format("USB %04x:%04x modelo=%s",
                    info.vendorId(), info.productId(), info.model().id()));
            if (abierto.open()) {
                abierto.close();
                lines.add("Permiso de apertura OK.");
            } else {
                lines.add("Windows no dejó abrir el pad. Cierre el programa que lo tenga tomado.");
            }
            return lines;
        } finally {
            detener(services);
        }
    }

    static Session open(String devicePath) {
        HidServices services = arrancar();
        try {
            HidDevice elegido = null;
            for (HidDevice device : services.getAttachedHidDevices()) {
                if (devicePath != null && devicePath.equals(device.getPath())) {
                    elegido = device;
                    break;
                }
            }
            if (elegido == null) {
                for (HidDevice device : services.getAttachedHidDevices()) {
                    if (infoDe(device) != null) {
                        elegido = device;
                        break;
                    }
                }
            }
            if (elegido == null || !elegido.open()) {
                detener(services);
                return null;
            }
            return new Session(services, elegido);
        } catch (RuntimeException ex) {
            detener(services);
            throw ex;
        }
    }

    private static TopazHidPad.DeviceInfo infoDe(HidDevice device) {
        int vid = device.getVendorId() & 0xFFFF;
        int pid = device.getProductId() & 0xFFFF;
        String producto = device.getProduct() == null ? "" : device.getProduct();
        boolean topaz = vid == TopazModel.VENDOR_TOPAZ
                || producto.toUpperCase(Locale.ROOT).contains("TOPAZ");
        if (!topaz) {
            return null;
        }
        String path = device.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        TopazModel model = TopazModel.forUsbProduct(pid);
        String etiqueta = model.displayName() + (producto.isBlank() ? "" : " (" + producto + ")");
        Path nodo;
        try {
            nodo = Path.of(path);
        } catch (InvalidPathException ex) {
            nodo = Path.of("topaz-hid-windows");
        }
        return new TopazHidPad.DeviceInfo(nodo, etiqueta, vid, pid, model);
    }

    private static HidServices arrancar() {
        HidServicesSpecification spec = new HidServicesSpecification();
        spec.setAutoStart(false);
        HidServices services = HidManager.getHidServices(spec);
        services.start();
        return services;
    }

    private static void detener(HidServices services) {
        if (services == null) {
            return;
        }
        try {
            services.shutdown();
        } catch (Exception ignored) {
        }
    }

    static final class Session implements Closeable {
        private final HidServices services;
        private final HidDevice device;
        private boolean closed;

        Session(HidServices services, HidDevice device) {
            this.services = services;
            this.device = device;
        }

        void write(byte[] data) {
            int enviados = device.write(data, data.length, (byte) 0);
            if (enviados < 0) {
                throw new IllegalStateException("No se pudo escribir al pad Topaz");
            }
        }

        int read(byte[] buf) {
            return device.read(buf, 80);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                device.close();
            } catch (Exception ignored) {
            }
            detener(services);
        }
    }
}
