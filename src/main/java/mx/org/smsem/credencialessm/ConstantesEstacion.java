package mx.org.smsem.credencialessm;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Propiedades de la estación. Se leen de constantes.properties.
 */
public final class ConstantesEstacion {

    private static final Properties VALORES = new Properties();

    static {
        try (InputStream in = ConstantesEstacion.class.getResourceAsStream("/credencialessm/constantes.properties")) {
            if (in == null) {
                throw new IllegalStateException("No está credencialessm/constantes.properties");
            }
            VALORES.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudieron leer las constantes de la estación", ex);
        }
    }

    private ConstantesEstacion() {
    }

    public static String texto(String clave) {
        String valor = VALORES.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la constante " + clave);
        }
        return valor.trim();
    }

    public static int entero(String clave) {
        try {
            return Integer.parseInt(texto(clave));
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("La constante " + clave + " no es un número", ex);
        }
    }

    public static String urlServidor() {
        String url = texto("servidor.url");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static Path directorioEstacion() {
        return Paths.get(System.getProperty("user.home"), texto("estacion.carpeta"));
    }
}
