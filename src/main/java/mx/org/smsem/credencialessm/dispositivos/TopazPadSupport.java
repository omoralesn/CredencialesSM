package mx.org.smsem.credencialessm.dispositivos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Detección unificada de pads Topaz (HID 06A8 + serial FTDI/BSB).
 */
public final class TopazPadSupport {

    public sealed interface Handle permits HidHandle, SerialHandle {
        TopazModel model();

        String label();

        void addListener(Consumer<TopazPadPoint> listener);

        void start();

        void close();

        long pointCount();
    }

    public record HidHandle(TopazHidPad pad, TopazHidPad.DeviceInfo info) implements Handle {
        @Override
        public TopazModel model() {
            return info.model();
        }

        @Override
        public String label() {
            return info.name();
        }

        @Override
        public void addListener(Consumer<TopazPadPoint> listener) {
            pad.addListener(listener);
        }

        @Override
        public void start() {
            pad.start();
        }

        @Override
        public void close() {
            pad.close();
        }

        @Override
        public long pointCount() {
            return pad.getPointCount();
        }
    }

    public record SerialHandle(TopazSerialPad pad, TopazSerialPad.DeviceInfo info) implements Handle {
        @Override
        public TopazModel model() {
            return info.model();
        }

        @Override
        public String label() {
            return info.name();
        }

        @Override
        public void addListener(Consumer<TopazPadPoint> listener) {
            pad.addListener(listener);
        }

        @Override
        public void start() {
            pad.start();
        }

        @Override
        public void close() {
            pad.close();
        }

        @Override
        public long pointCount() {
            return pad.getPointCount();
        }
    }

    private TopazPadSupport() {
    }

    /** Prefiere HID Topaz nativo; si no, serial FTDI/Topaz. */
    public static Optional<Handle> openBest() {
        Optional<TopazHidPad.DeviceInfo> hid = TopazHidPad.findTopazHid();
        if (hid.isPresent()) {
            TopazHidPad.DeviceInfo d = hid.get();
            if (TopazHidPad.canOpen(d)) {
                TopazHidPad pad = new TopazHidPad(d.hidraw(), d.model());
                return Optional.of(new HidHandle(pad, d));
            }
        }
        Optional<TopazSerialPad.DeviceInfo> ser = TopazSerialPad.findFirst();
        if (ser.isPresent()) {
            TopazSerialPad.DeviceInfo d = ser.get();
            TopazSerialPad pad = new TopazSerialPad(d.portPath(), d.model());
            return Optional.of(new SerialHandle(pad, d));
        }
        return Optional.empty();
    }

    public static List<String> diagnose() {
        List<String> lines = new ArrayList<>();
        lines.addAll(TopazHidPad.diagnose());
        List<TopazSerialPad.DeviceInfo> serial = TopazSerialPad.findAll();
        if (serial.isEmpty()) {
            lines.add("Serial FTDI/Topaz: ninguno detectado.");
        } else {
            for (TopazSerialPad.DeviceInfo d : serial) {
                lines.add("Serial: " + d.name() + " modelo=" + d.model().id());
            }
        }
        if (!windows()) {
            lines.add("Setup en cualquier PC: ./scripts/setup-topaz.sh");
        }
        return lines;
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
