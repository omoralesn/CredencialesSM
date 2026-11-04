package mx.org.smsem.credencialessm.dispositivos;

import java.util.Locale;
import java.util.Optional;

/**
 * Calibración de modelos Topaz (SigPlus / topaz-dext).
 * Cubre pads HID (VID 06A8) y seriales FTDI (BSB).
 */
public record TopazModel(
        String id,
        String displayName,
        int xStart,
        int xStop,
        int yStart,
        int yStop,
        int logicalW,
        int logicalH,
        int baud,
        int dataBits,
        boolean oddParity,
        Transport transport
) {
    public enum Transport { HID, SERIAL }

    public static final int VENDOR_TOPAZ = 0x06a8;
    public static final int VENDOR_FTDI = 0x0403;
    public static final int PRODUCT_FTDI_GENERIC = 0x6001;

    /** SignatureGem 1X5-HID — equipo actual SIDID (06a8:0043). */
    public static final TopazModel SIGNATURE_GEM_1X5_HID = new TopazModel(
            "SignatureGem1X5", "SignatureGem 1X5-HID",
            400, 2400, 350, 950, 2000, 600,
            19200, 8, true, Transport.HID);

    public static final TopazModel SIGNATURE_GEM_1X5 = new TopazModel(
            "SignatureGem1X5", "SignatureGem 1X5",
            400, 2400, 350, 950, 2000, 600,
            19200, 8, true, Transport.SERIAL);

    public static final TopazModel SIGNATURE_GEM_LCD_1X5 = new TopazModel(
            "SignatureGemLCD1X5", "SignatureGem LCD 1X5",
            400, 2400, 350, 1050, 2000, 700,
            19200, 7, true, Transport.SERIAL);

    public static final TopazModel SIGNATURE_GEM_4X5 = new TopazModel(
            "SignatureGem4X5", "SignatureGem 4X5",
            500, 2650, 700, 2100, 2150, 1400,
            19200, 8, true, Transport.SERIAL);

    public static final TopazModel SIGLITE_1X5 = new TopazModel(
            "SigLite", "SigLite 1X5",
            500, 2650, 700, 2100, 2150, 1400,
            19200, 8, true, Transport.SERIAL);

    public static final TopazModel SIGLITE_LCD_1X5 = new TopazModel(
            "SigLiteLCD1X5", "SigLite LCD 1X5",
            400, 2400, 350, 1050, 2000, 700,
            19200, 8, true, Transport.SERIAL);

    public static final TopazModel SIGLITE_LCD_4X5 = new TopazModel(
            "SignatureGemLCD4X5", "SigLite LCD 4X5",
            500, 2600, 500, 2100, 2100, 1600,
            38400, 8, true, Transport.SERIAL);

    public static final TopazModel CLIP_GEM = new TopazModel(
            "ClipGem", "ClipGem",
            485, 2800, 170, 3200, 2315, 3030,
            9600, 8, true, Transport.SERIAL);

    private static final TopazModel[] ALL = {
            SIGNATURE_GEM_1X5_HID,
            SIGNATURE_GEM_1X5,
            SIGNATURE_GEM_LCD_1X5,
            SIGNATURE_GEM_4X5,
            SIGLITE_1X5,
            SIGLITE_LCD_1X5,
            SIGLITE_LCD_4X5,
            CLIP_GEM
    };

    public int scaleX(int raw) {
        int den = xStop - xStart;
        if (den == 0) {
            return 0;
        }
        return clamp((raw - xStart) * logicalW / den, 0, logicalW);
    }

    public int scaleY(int raw) {
        int den = yStop - yStart;
        if (den == 0) {
            return 0;
        }
        return clamp((raw - yStart) * logicalH / den, 0, logicalH);
    }

    public boolean rawInRange(int x, int y) {
        return x >= xStart - 300 && x <= xStop + 300
                && y >= yStart - 300 && y <= yStop + 300;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Resuelve modelo por USB product id Topaz (06a8:xxxx). */
    public static TopazModel forUsbProduct(int productId) {
        return switch (productId) {
            case 0x0043, 0x0042 -> SIGNATURE_GEM_1X5_HID;
            case 0x0009, 0x0029 -> SIGNATURE_GEM_LCD_1X5; // aproximaciones comunes
            case 0x0020, 0x0021 -> SIGNATURE_GEM_4X5;
            default -> SIGNATURE_GEM_1X5_HID; // HID genérico Topaz reciente
        };
    }

    public static Optional<TopazModel> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String key = id.trim();
        for (TopazModel m : ALL) {
            if (m.id.equalsIgnoreCase(key)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    public static TopazModel[] all() {
        return ALL.clone();
    }

    public static boolean isTopazVendor(int vendorId) {
        return vendorId == VENDOR_TOPAZ;
    }

    public static boolean looksLikeTopazSerialName(String name) {
        if (name == null) {
            return false;
        }
        String u = name.toUpperCase(Locale.ROOT);
        return u.contains("TOPAZ") || u.contains("SIGPLUS") || u.contains("SIGNATUREGEM") || u.contains("SIGLITE");
    }
}
