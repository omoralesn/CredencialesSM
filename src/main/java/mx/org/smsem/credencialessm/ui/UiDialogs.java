package mx.org.smsem.credencialessm.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

final class UiDialogs {

    private UiDialogs() {
    }

    static void error(String message) {
        show(Alert.AlertType.ERROR, "Error", message);
    }

    static void info(String header, String message) {
        show(Alert.AlertType.INFORMATION, header, message);
    }

    static void warn(String header, String message) {
        show(Alert.AlertType.WARNING, header, message);
    }

    static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(header);
        style(alert);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private static void show(Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type, message);
        alert.setHeaderText(header);
        style(alert);
        alert.showAndWait();
    }

    private static void style(Alert alert) {
        DialogPane pane = alert.getDialogPane();
        var css = UiTheme.class.getResource(UiTheme.CSS);
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
    }
}
