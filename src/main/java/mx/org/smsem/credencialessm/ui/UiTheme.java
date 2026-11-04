package mx.org.smsem.credencialessm.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * Tema visual institucional SIDIDesk (legible, sobrio, multiplataforma).
 */
public final class UiTheme {

    public static final String CSS = "/ui/sididesk.css";

    public static final String COLOR_NAVY = "#1f3a5f";
    public static final String COLOR_INK = "#102a43";
    public static final String COLOR_MUTED = "#627d98";
    public static final String COLOR_SURFACE = "#f5f7fa";
    public static final String COLOR_OK = "#0f7b3a";
    public static final String COLOR_WARN = "#b36b00";
    public static final String COLOR_ERR = "#9b1c1c";
    public static final String COLOR_INFO = "#0b6e99";

    private UiTheme() {
    }

    public static Button primaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-primary");
        return b;
    }

    public static Button secondaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-secondary");
        return b;
    }

    public static Button dangerButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-danger");
        return b;
    }

    public static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("title");
        return l;
    }

    public static Label subtitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("subtitle");
        return l;
    }

    public static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        return l;
    }

    public static Label badge(String text, String kind) {
        Label l = new Label(text);
        l.getStyleClass().addAll("badge", "badge-" + kind);
        return l;
    }

    public static String statusKind(String status) {
        if (status == null) {
            return "muted";
        }
        switch (status) {
            case "ERROR_REINTENTABLE":
            case "ERROR":
                return "err";
            case "CAPTURADA":
            case "ENVIADO":
                return "ok";
            case "IMPRESA_LOCAL":
            case "IMPRESA":
                return "info";
            case "EN_COLA":
            case "PENDIENTE_CAPTURA":
            case "PENDIENTE":
            case "SIN_IMPRESION":
            case "SIN_SYNC":
                return "warn";
            default:
                return "muted";
        }
    }

    public static Background loginBackground() {
        LinearGradient g = new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#d9e2ec")),
                new Stop(0.55, Color.web("#f0f4f8")),
                new Stop(1, Color.web("#bcccdc")));
        return new Background(new BackgroundFill(g, CornerRadii.EMPTY, Insets.EMPTY));
    }
}
