package mx.org.smsem.credencialessm;

import javafx.application.Application;

/**
 * Punto de entrada para Eclipse y para el ejecutable.
 * No extiende Application: si la clase principal es la de JavaFX,
 * el runtime avisa que faltan los componentes aunque las librerías estén en el classpath.
 */
public final class CredencialesMain {

    private CredencialesMain() {
    }

    public static void main(String[] args) {
        Application.launch(CredencialesApp.class, args);
    }
}
