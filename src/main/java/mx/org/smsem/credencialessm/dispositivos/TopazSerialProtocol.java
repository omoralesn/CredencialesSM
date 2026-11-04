package mx.org.smsem.credencialessm.dispositivos;

/**
 * Parser del protocolo serial Topaz de 5 bytes (SigPlus / topaz-dext),
 * usado tanto en pads FTDI como encapsulado dentro de reportes HID.
 */
final class TopazSerialProtocol {

    private final byte[] scratch = new byte[5];
    private int state;
    private int index;
    private byte lastByte;

    @FunctionalInterface
    interface PacketSink {
        void onPacket(int kind, int rawX, int rawY, boolean penDown, boolean penNear, byte status);
    }

    void reset() {
        state = 0;
        index = 0;
        lastByte = 0;
    }

    void accept(byte thisByte, PacketSink sink) {
        if (state == 0) {
            if ((thisByte & 0x80) == 0) {
                return;
            }
            lastByte = thisByte;
            state = 1;
            return;
        }
        if (state == 1) {
            if ((thisByte & 0x80) != 0) {
                scratch[0] = thisByte;
                index = 1;
                state = 2;
            } else {
                scratch[0] = lastByte;
                scratch[1] = thisByte;
                index = 2;
                state = 2;
            }
            return;
        }
        if (index >= 5) {
            state = 0;
            return;
        }
        scratch[index++] = thisByte;
        if (index != 5) {
            return;
        }
        state = 0;
        if (isBadPacket(scratch)) {
            return;
        }
        int kind = (scratch[0] >> 2) & 7;
        int x = convertCoord(scratch[1], scratch[2]);
        int y = convertCoord(scratch[3], scratch[4]);
        int status = scratch[0] & 0xFF;
        boolean penDown = (status & 0x01) != 0;
        boolean penNear = (status & 0x40) != 0;
        sink.onPacket(kind, x, y, penDown, penNear, (byte) status);
    }

    /** Alimenta un buffer completo (p.ej. payload HID desencapsulado). */
    void acceptAll(byte[] data, int off, int len, PacketSink sink) {
        for (int i = 0; i < len; i++) {
            accept(data[off + i], sink);
        }
    }

    static boolean isPenKind(int kind) {
        return kind == 0 || kind == 1 || kind == 4;
    }

    static int convertCoord(byte lsb, byte msb) {
        int pos = msb & 0x1F;
        pos <<= 7;
        return pos + (lsb & 0x7F);
    }

    static boolean isBadPacket(byte[] buffer) {
        if ((buffer[1] & 0x80) != 0 || (buffer[2] & 0xE0) != 0
                || (buffer[3] & 0x80) != 0 || (buffer[4] & 0xE0) != 0) {
            return true;
        }
        return (buffer[1] & 0x7F) == 127 && (buffer[2] & 0x1F) == 31
                && (buffer[3] & 0x7F) == 127 && (buffer[4] & 0x1F) == 31;
    }

    /**
     * Reportes HID Topaz 1X5-HID: byte0 = {@code 0x20 | length}, seguido de
     * {@code length} bytes del flujo serial. Devuelve cuántos bytes de payload
     * se copiaron a {@code out} (desde out[0]).
     */
    static int unwrapHidReport(byte[] report, int n, byte[] out) {
        if (n < 2) {
            return 0;
        }
        int st = report[0] & 0xFF;
        if ((st & 0xF0) == 0x20) {
            int len = st & 0x0F;
            if (len < 1 || len > n - 1) {
                return 0;
            }
            System.arraycopy(report, 1, out, 0, len);
            return len;
        }
        // Algunos firmwares envían el paquete serial completo en 5+ bytes con bit7
        if ((report[0] & 0x80) != 0 && n >= 5) {
            System.arraycopy(report, 0, out, 0, Math.min(n, out.length));
            return Math.min(n, out.length);
        }
        return 0;
    }
}
