package mx.org.smsem.credencialessm;

import mx.gob.sidi.desk.app.ui.UiTheme;
import mx.gob.sidi.desk.devices.PrintResult;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.Region;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Estación ligera de credenciales de empleados SMSEM.
 */
public class CredencialesApp extends Application {

    private EmpleadoApi cliente;
    private EmpleadoPrinter impresora;
    private String operador = "";
    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        Path dataDir = Paths.get(System.getProperty("user.home"), ".credencialessm");
        cliente = EmpleadoApi.crear();
        impresora = new EmpleadoPrinter(dataDir);
        stage.setTitle("CredencialesSM");
        mostrarLogin();
        stage.show();
    }

    @Override
    public void stop() {
    }

    private void mostrarLogin() {
        TextField usuario = new TextField();
        usuario.setPromptText("Usuario");
        PasswordField clave = new PasswordField();
        clave.setPromptText("Contraseña");
        Label error = new Label();
        error.getStyleClass().add("login-error");
        Button entrar = UiTheme.primaryButton("Entrar");
        entrar.setOnAction(e -> {
            try {
                operador = cliente.ingresar(usuario.getText(), clave.getText());
                asegurarPadron();
                mostrarTrabajo();
            } catch (Exception ex) {
                error.setText(ex.getMessage());
            }
        });
        VBox caja = new VBox(12,
                UiTheme.title("CredencialesSM"),
                UiTheme.muted("Modo de prueba, sin base de datos"),
                new Label("Usuario"), usuario,
                new Label("Contraseña"), clave,
                entrar, error);
        caja.setPadding(new Insets(28));
        caja.setAlignment(Pos.CENTER_LEFT);
        caja.setMaxWidth(360);
        Scene scene = new Scene(new VBox(caja), 420, 420);
        aplicarCss(scene);
        stage.setScene(scene);
    }

    private void asegurarPadron() {
        try {
            if (cliente.cuenta() > 0) {
                return;
            }
            try (InputStream in = CredencialesApp.class.getResourceAsStream("/credencialessm/padron-smsem.tsv")) {
                if (in == null) {
                    return;
                }
                String tsv = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                cliente.cargarPadron(tsv);
            }
        } catch (Exception ignored) {
            // La ventana de trabajo muestra el error al consultar si el servidor no responde.
        }
    }

    private void mostrarTrabajo() {
        BorderPane raiz = new BorderPane();
        Label estado = new Label("Busque por nombre, o capture un empleado nuevo.");
        estado.getStyleClass().add("status-bar");
        estado.setMaxWidth(Double.MAX_VALUE);

        TextField filtro = new TextField();
        filtro.setPromptText("Nombre…");
        HBox.setHgrow(filtro, Priority.ALWAYS);
        HBox barra = new HBox(8, filtro);
        barra.setPadding(new Insets(12));
        barra.setAlignment(Pos.CENTER_LEFT);

        TableView<EmpleadoSmsem> tabla = tablaEmpleados();
        TextField claveInterna = soloLectura();
        TextField clave = new TextField();
        TextField nombre = new TextField();
        TextField adscripcion = new TextField();
        Button nuevo = UiTheme.secondaryButton("Nuevo");
        Button guardar = UiTheme.primaryButton("Guardar");
        Button imprimir = UiTheme.primaryButton("Imprimir");
        CheckBox aArchivo = new CheckBox("Guardar en archivo, sin impresora");
        aArchivo.setSelected(true);
        aArchivo.setWrapText(true);
        VBox forma = new VBox(8,
                new Label("Clave interna"), claveInterna,
                new Label("Clave"), clave,
                new Label("Nombre completo"), nombre,
                new Label("Adscripción"), adscripcion,
                aArchivo,
                new HBox(8, nuevo, guardar, imprimir));
        forma.setPadding(new Insets(12));
        forma.setPrefWidth(340);

        final EmpleadoSmsem[] seleccionado = {null};
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, persona) -> {
            if (persona == null) {
                return;
            }
            seleccionado[0] = persona;
            llenar(persona, claveInterna, clave, nombre, adscripcion);
        });
        buscarPorNombre(filtro, tabla, estado);
        nuevo.setOnAction(e -> {
            seleccionado[0] = null;
            tabla.getSelectionModel().clearSelection();
            claveInterna.clear();
            clave.clear();
            nombre.clear();
            adscripcion.clear();
            estado.setText("Alta nueva. La clave interna la asigna SMSEM al guardar.");
        });
        guardar.setOnAction(e -> guardar(seleccionado, clave, nombre, adscripcion, tabla, estado, filtro));
        imprimir.setOnAction(e -> imprimir(seleccionado, clave, nombre, adscripcion, tabla, estado, filtro, aArchivo.isSelected()));

        raiz.setTop(new VBox(barraSesion(), barra, estado));
        raiz.setCenter(tabla);
        raiz.setRight(forma);
        Scene scene = new Scene(raiz, 1100, 680);
        aplicarCss(scene);
        stage.setScene(scene);
        cargarBusqueda("", tabla, estado);
    }

    private HBox barraSesion() {
        Label usuario = new Label(operador);
        usuario.getStyleClass().add("nav-title");
        Button impresion = UiTheme.secondaryButton("Impresión");
        Button seguimiento = UiTheme.secondaryButton("Seguimiento");
        Button salir = UiTheme.dangerButton("Salir");
        impresion.setOnAction(e -> mostrarTrabajo());
        seguimiento.setOnAction(e -> mostrarSeguimiento());
        salir.setOnAction(e -> {
            operador = "";
            mostrarLogin();
        });
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(12, usuario, espacio, impresion, seguimiento, salir);
        barra.getStyleClass().add("nav-bar");
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    private void buscarPorNombre(TextField filtro, TableView<EmpleadoSmsem> tabla, Label estado) {
        filtro.textProperty().addListener((o, anterior, texto) -> cargarBusqueda(texto, tabla, estado));
    }

    private boolean guardar(EmpleadoSmsem[] seleccionado, TextField clave, TextField nombre, TextField adscripcion,
                         TableView<EmpleadoSmsem> tabla, Label estado, TextField filtro) {
        try {
            EmpleadoSmsem guardado;
            if (seleccionado[0] == null || seleccionado[0].claveInterna == null || seleccionado[0].claveInterna.isBlank()) {
                guardado = cliente.alta(clave.getText(), nombre.getText(), adscripcion.getText());
            } else {
                guardado = cliente.modifica(seleccionado[0].claveInterna, clave.getText(), nombre.getText(), adscripcion.getText());
            }
            seleccionado[0] = guardado;
            estado.setText("Guardado " + guardado.claveInterna + " · " + guardado.nombreCompleto);
            filtro.setText(guardado.nombreCompleto);
            cargarBusqueda(guardado.nombreCompleto, tabla, estado);
            return true;
        } catch (Exception ex) {
            estado.setText(ex.getMessage());
            return false;
        }
    }

    private void imprimir(EmpleadoSmsem[] seleccionado, TextField clave, TextField nombre, TextField adscripcion,
                          TableView<EmpleadoSmsem> tabla, Label estado, TextField filtro, boolean aArchivo) {
        if (datosDistintos(seleccionado[0], clave, nombre, adscripcion)) {
            if (!guardar(seleccionado, clave, nombre, adscripcion, tabla, estado, filtro)) {
                return;
            }
        }
        EmpleadoSmsem persona = seleccionado[0];
        if (persona == null || persona.claveInterna == null || persona.claveInterna.isBlank()) {
            estado.setText("Capture el nombre antes de imprimir.");
            return;
        }
        CapturaEmpleado.Resultado foto = CapturaEmpleado.foto(stage);
        if (foto.bytes() == null) {
            estado.setText("Impresión cancelada: falta la fotografía.");
            return;
        }
        CapturaEmpleado.Resultado firma = CapturaEmpleado.firma(stage);
        if (firma.bytes() == null) {
            estado.setText("Impresión cancelada: falta la firma.");
            return;
        }
        try {
            PrintResult resultado = impresora.imprimir(persona, foto.bytes(), firma.bytes(), aArchivo);
            if (!resultado.isAccepted()) {
                estado.setText(resultado.getMessage());
                return;
            }
            EmpleadoSmsem impresa = cliente.imprimir(persona.claveInterna, foto.bytes(), firma.bytes(), operador);
            String destino = resultado.getArtifactPath() == null
                    ? "impresora"
                    : resultado.getArtifactPath().toString();
            String origen = (foto.dispositivo() ? "foto de cámara" : "foto de prueba")
                    + " · " + (firma.dispositivo() ? "firma del pad" : "firma de prueba");
            estado.setText((aArchivo ? "Guardada en archivo: " : "Enviada a impresora. ")
                    + destino
                    + " · " + origen
                    + " · " + impresa.idMongoFoto
                    + " · " + impresa.idMongoFirma);
        } catch (Exception ex) {
            estado.setText(ex.getMessage());
        }
    }

    private static boolean datosDistintos(EmpleadoSmsem persona, TextField clave, TextField nombre, TextField adscripcion) {
        if (persona == null || persona.claveInterna == null || persona.claveInterna.isBlank()) {
            return !nombre.getText().isBlank() || !clave.getText().isBlank() || !adscripcion.getText().isBlank();
        }
        return !igual(persona.clave, clave.getText())
                || !igual(persona.nombreCompleto, nombre.getText())
                || !igual(persona.adscripcion, adscripcion.getText());
    }

    private static boolean igual(String guardado, String escrito) {
        String a = guardado == null ? "" : guardado.trim();
        String b = escrito == null ? "" : escrito.trim();
        return a.equals(b);
    }

    private void mostrarSeguimiento() {
        BorderPane raiz = new BorderPane();
        Label estado = new Label();
        estado.getStyleClass().add("status-bar");
        Button actualizar = UiTheme.primaryButton("Actualizar");
        HBox barra = new HBox(8, actualizar);
        barra.setPadding(new Insets(12));
        TableView<EmpleadoSmsem> tabla = tablaEmpleados();
        TableColumn<EmpleadoSmsem, String> impresa = new TableColumn<>("Impresión");
        impresa.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().impresa() ? c.getValue().fechaImpresion : "Pendiente"));
        tabla.getColumns().add(impresa);
        Runnable cargar = () -> {
            try {
                List<EmpleadoSmsem> filas = cliente.seguimiento();
                tabla.getItems().setAll(filas);
                estado.setText(filas.size() + " registros en SMSEM");
            } catch (Exception ex) {
                estado.setText(ex.getMessage());
            }
        };
        actualizar.setOnAction(e -> cargar.run());
        raiz.setTop(new VBox(barraSesion(), barra, estado));
        raiz.setCenter(tabla);
        Scene scene = new Scene(raiz, 1100, 680);
        aplicarCss(scene);
        stage.setScene(scene);
        cargar.run();
    }

    private void cargarBusqueda(String nombre, TableView<EmpleadoSmsem> tabla, Label estado) {
        try {
            List<EmpleadoSmsem> filas = cliente.consultar(nombre);
            tabla.getItems().setAll(filas);
            estado.setText(filas.size() + " coincidencias en SMSEM");
        } catch (Exception ex) {
            estado.setText(ex.getMessage());
        }
    }

    private static TableView<EmpleadoSmsem> tablaEmpleados() {
        TableView<EmpleadoSmsem> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<EmpleadoSmsem, String> interna = new TableColumn<>("Clave interna");
        interna.setCellValueFactory(c -> new SimpleStringProperty(nulo(c.getValue().claveInterna)));
        TableColumn<EmpleadoSmsem, String> clave = new TableColumn<>("Clave");
        clave.setCellValueFactory(c -> new SimpleStringProperty(nulo(c.getValue().clave)));
        TableColumn<EmpleadoSmsem, String> nombre = new TableColumn<>("Nombre");
        nombre.setCellValueFactory(c -> new SimpleStringProperty(nulo(c.getValue().nombreCompleto)));
        TableColumn<EmpleadoSmsem, String> ads = new TableColumn<>("Adscripción");
        ads.setCellValueFactory(c -> new SimpleStringProperty(nulo(c.getValue().adscripcion)));
        tabla.getColumns().addAll(List.of(interna, clave, nombre, ads));
        return tabla;
    }

    private static void llenar(EmpleadoSmsem persona, TextField claveInterna, TextField clave,
                               TextField nombre, TextField adscripcion) {
        if (persona == null) {
            return;
        }
        claveInterna.setText(nulo(persona.claveInterna));
        clave.setText(nulo(persona.clave));
        nombre.setText(nulo(persona.nombreCompleto));
        adscripcion.setText(nulo(persona.adscripcion));
    }

    private static TextField soloLectura() {
        TextField campo = new TextField();
        campo.setEditable(false);
        return campo;
    }

    private static String nulo(String valor) {
        return valor == null ? "" : valor;
    }

    private static void aplicarCss(Scene scene) {
        var css = CredencialesApp.class.getResource(UiTheme.CSS);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }
}
