package mx.org.smsem.credencialessm;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Notas de la estación. Siempre quedan en memoria para revisarlas en la ventana.
 * El archivo se intenta aparte: si el equipo no deja escribir, el registro sigue visible.
 */
public final class BitacoraEstacion {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record Entrada(String momento, String nivel, String operacion, String detalle, String traza) {
        public String linea() {
            return momento + "  " + nivel + "  " + operacion;
        }

        public String completa() {
            String texto = detalle == null ? "" : detalle;
            if (traza == null || traza.isBlank()) {
                return texto;
            }
            return texto + "\n" + traza;
        }
    }

    private final ObservableList<Entrada> entradas = FXCollections.observableArrayList();
    private final StringProperty aviso = new SimpleStringProperty("");
    private Path archivo;

    public ObservableList<Entrada> entradas() {
        return entradas;
    }

    public StringProperty avisoProperty() {
        return aviso;
    }

    public void iniciar(Path directorio) {
        Path candidato = directorio.resolve(ConstantesEstacion.texto("bitacora.archivo"));
        try {
            Files.createDirectories(directorio);
            Files.writeString(candidato, "", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            archivo = candidato;
            aviso.set(candidato.toString());
        } catch (Exception ex) {
            archivo = null;
            aviso.set(candidato + " (este equipo no permitió guardar el archivo; el registro queda en esta ventana)");
        }
        info("estacion", "Inicio de CredencialesSM");
    }

    public void info(String operacion, String detalle) {
        anotar("INFO", operacion, detalle, "");
    }

    public void error(String operacion, String detalle, Throwable causa) {
        anotar("ERROR", operacion, detalle, traza(causa));
    }

    public String todo() {
        List<Entrada> copia;
        synchronized (entradas) {
            copia = new ArrayList<>(entradas);
        }
        StringBuilder texto = new StringBuilder();
        for (int i = copia.size() - 1; i >= 0; i--) {
            Entrada entrada = copia.get(i);
            texto.append(entrada.linea()).append("  ").append(unaLinea(entrada.detalle));
            if (entrada.traza != null && !entrada.traza.isBlank()) {
                texto.append("  ").append(unaLinea(entrada.traza));
            }
            texto.append('\n');
        }
        return texto.toString();
    }

    private void anotar(String nivel, String operacion, String detalle, String traza) {
        Entrada entrada = new Entrada(LocalDateTime.now().format(HORA), nivel, operacion,
                detalle == null ? "" : detalle, traza == null ? "" : traza);
        Runnable enPantalla = () -> {
            entradas.add(0, entrada);
            int tope = ConstantesEstacion.entero("bitacora.tope");
            while (entradas.size() > tope) {
                entradas.remove(entradas.size() - 1);
            }
        };
        if (Platform.isFxApplicationThread()) {
            enPantalla.run();
        } else {
            Platform.runLater(enPantalla);
        }
        escribir(entrada);
    }

    private void escribir(Entrada entrada) {
        Path destino = archivo;
        if (destino == null) {
            return;
        }
        String linea = entrada.momento + "\t" + entrada.nivel + "\t" + entrada.operacion
                + "\t" + unaLinea(entrada.detalle)
                + (entrada.traza == null || entrada.traza.isBlank() ? "" : "\t" + unaLinea(entrada.traza))
                + System.lineSeparator();
        try {
            synchronized (this) {
                Files.writeString(destino, linea, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            }
        } catch (IOException ex) {
            archivo = null;
            Runnable avisoArchivo = () -> aviso.set(
                    destino + " (este equipo no permitió seguir guardando el archivo; el registro queda en esta ventana)");
            if (Platform.isFxApplicationThread()) {
                avisoArchivo.run();
            } else {
                Platform.runLater(avisoArchivo);
            }
        }
    }

    private static String traza(Throwable causa) {
        if (causa == null) {
            return "";
        }
        StringBuilder texto = new StringBuilder(causa.getClass().getSimpleName());
        if (causa.getMessage() != null && !causa.getMessage().isBlank()) {
            texto.append(": ").append(unaLinea(causa.getMessage()));
        }
        Throwable cursor = causa;
        int n = 0;
        while (cursor != null && n < 8) {
            StackTraceElement[] pila = cursor.getStackTrace();
            int tope = Math.min(4, pila.length);
            for (int i = 0; i < tope; i++) {
                texto.append(" | ").append(pila[i]);
            }
            cursor = cursor.getCause();
            n++;
            if (cursor != null) {
                texto.append(" | causado por ").append(cursor.getClass().getSimpleName());
            }
        }
        return texto.toString();
    }

    private static String unaLinea(String valor) {
        return valor.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
