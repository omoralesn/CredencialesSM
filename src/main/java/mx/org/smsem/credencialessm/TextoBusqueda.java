package mx.org.smsem.credencialessm;

import java.text.Normalizer;
import java.util.Locale;

/** Comparación de nombres sin distinguir acentos. */
public final class TextoBusqueda {

    private TextoBusqueda() {
    }

    public static String sinAcentos(String valor) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        String plano = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plano.toUpperCase(Locale.ROOT);
    }
}
