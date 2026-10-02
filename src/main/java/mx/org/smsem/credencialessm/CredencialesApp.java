package mx.org.smsem.credencialessm;

import mx.gob.sidi.desk.app.ui.UiTheme;
import mx.gob.sidi.desk.devices.PrintResult;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Estación ligera de credenciales de empleados SMSEM.
 */
public class CredencialesApp extends Application {

    private EmpleadoApi cliente;
    private EmpleadoPrinter impresora;
    private BitacoraEstacion bitacora;
    private VBox registro;
    private boolean registroVisible;
    private StackPane velo;
    private Label veloMensaje;
    private int veloActivo;
    private String seccion = "impresion";
    private String operador = "";
    private Stage stage;
    private int vista;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        Path dataDir = ConstantesEstacion.directorioEstacion();
        bitacora = new BitacoraEstacion();
        bitacora.iniciar(dataDir);
        cliente = EmpleadoApi.crear();
        impresora = new EmpleadoPrinter(dataDir);
        registro = panelRegistro();
        velo = veloModal();
        stage.setTitle("CredencialesSM");
        var icono = CredencialesApp.class.getResource("/credencialessm/icono.png");
        if (icono != null) {
            stage.getIcons().add(new Image(icono.toExternalForm()));
        }
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
        error.setWrapText(true);
        error.getStyleClass().add("login-error");
        Indicador indicador = new Indicador();
        Button entrar = UiTheme.primaryButton("Entrar");
        int marca = ++vista;
        entrar.setOnAction(e -> enCurso(marca, indicador, error, "Ingresando…", "ingreso", true,
				() -> cliente.ingresar(usuario.getText(), clave.getText()),
                nombre -> {
                    operador = nombre;
                    mostrarTrabajo();
                },
                fallo -> error.setText(mensaje(fallo)),
                entrar));
        HBox marcas = new HBox(18, logo("logoSMSEM.png", 128), logo("logoBienestar.png", 96));
        marcas.setAlignment(Pos.CENTER);
        VBox caja = new VBox(12,
                marcas,
                UiTheme.title("CredencialesSM"),
                UiTheme.muted("Ingreso con usuario de SMSEM"),
                new Label("Usuario"), usuario,
                new Label("Contraseña"), clave,
                entrar, indicador.fila(error));
        caja.setPadding(new Insets(28));
        caja.setAlignment(Pos.CENTER);
        caja.setMaxWidth(420);
        usuario.setMaxWidth(Double.MAX_VALUE);
        clave.setMaxWidth(Double.MAX_VALUE);
        BorderPane raiz = new BorderPane(caja);
        stage.setScene(escena(raiz, 520, 680));
    }

    private void mostrarTrabajo() {
        seccion = "impresion";
        BorderPane raiz = new BorderPane();
        Label estado = new Label("Busque por nombre, o capture un empleado nuevo.");
        estado.getStyleClass().setAll("status-bar");
        estado.setWrapText(true);
        estado.setMaxWidth(Double.MAX_VALUE);
        Indicador indicador = new Indicador();
        int marca = ++vista;

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
        PauseTransition pausa = new PauseTransition(Duration.millis(ConstantesEstacion.entero("busqueda.esperaMillis")));
        filtro.textProperty().addListener((o, anterior, texto) -> {
            pausa.setOnFinished(ev -> cargarBusqueda(marca, texto, tabla, estado, indicador));
            pausa.playFromStart();
        });
        nuevo.setOnAction(e -> {
            seleccionado[0] = null;
            tabla.getSelectionModel().clearSelection();
            claveInterna.clear();
            clave.clear();
            nombre.clear();
            adscripcion.clear();
            estado.setText("Alta nueva. La clave, el nombre y la adscripción son obligatorios.");
        });
        guardar.setOnAction(e -> {
            if (!camposObligatorios(clave, nombre, adscripcion, estado)) {
                return;
            }
            guardar(marca, seleccionado, clave, nombre, adscripcion, tabla, estado, filtro, indicador,
                    () -> cargarBusqueda(marca, filtro.getText(), tabla, estado, indicador),
                    nuevo, guardar, imprimir);
        });
        imprimir.setOnAction(e -> {
            if (!camposObligatorios(clave, nombre, adscripcion, estado)) {
                return;
            }
            imprimir(marca, seleccionado, clave, nombre, adscripcion, tabla, estado, filtro,
                    aArchivo.isSelected(), indicador, nuevo, guardar, imprimir);
        });

        raiz.setTop(new VBox(barraSesion(), indicador.barra(), barra, indicador.fila(estado)));
        raiz.setCenter(tabla);
        raiz.setRight(forma);
        stage.setScene(escena(raiz, 1100, 680));
        cargarBusqueda(marca, "", tabla, estado, indicador);
    }

    private HBox barraSesion() {
        Label usuario = new Label(operador);
        usuario.getStyleClass().add("nav-title");
        Button impresion = botonMenu("Impresión", "impresion");
        Button seguimiento = botonMenu("Seguimiento", "seguimiento");
        Button salir = UiTheme.dangerButton("Salir");
        impresion.setOnAction(e -> {
            if (!"impresion".equals(seccion)) {
                mostrarTrabajo();
            }
        });
        seguimiento.setOnAction(e -> {
            if (!"seguimiento".equals(seccion)) {
                mostrarSeguimiento();
            }
        });
        salir.setOnAction(e -> {
            operador = "";
            mostrarLogin();
        });
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox marcas = new HBox(8, logo("logoSMSEM.png", 36), logo("logoBienestar.png", 32));
        marcas.setAlignment(Pos.CENTER_LEFT);
        HBox barra = new HBox(12, marcas, usuario, espacio, impresion, seguimiento, salir);
        barra.getStyleClass().add("nav-bar");
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    private void guardar(int marca, EmpleadoSmsem[] seleccionado, TextField clave, TextField nombre, TextField adscripcion,
                         TableView<EmpleadoSmsem> tabla, Label estado, TextField filtro, Indicador indicador,
                         Runnable alTerminar, Button... botones) {
        enCurso(marca, indicador, estado, "Guardando en SMSEM…", "guardar", true, () -> {
            if (seleccionado[0] == null || seleccionado[0].claveInterna == null || seleccionado[0].claveInterna.isBlank()) {
                return cliente.alta(clave.getText(), nombre.getText(), adscripcion.getText());
            }
            return cliente.modifica(seleccionado[0].claveInterna, clave.getText(), nombre.getText(), adscripcion.getText());
        }, guardado -> {
            seleccionado[0] = guardado;
            estadoOk(estado, "Guardado " + guardado.claveInterna + " · " + guardado.nombreCompleto);
            filtro.setText(guardado.nombreCompleto);
            if (alTerminar != null) {
                alTerminar.run();
            }
        }, fallo -> estadoError(estado, mensaje(fallo)), botones);
    }

    private void imprimir(int marca, EmpleadoSmsem[] seleccionado, TextField clave, TextField nombre, TextField adscripcion,
                          TableView<EmpleadoSmsem> tabla, Label estado, TextField filtro, boolean aArchivo,
                          Indicador indicador, Button... botones) {
        Runnable capturar = () -> capturarEImprimir(marca, seleccionado[0], estado, aArchivo, indicador, botones);
        if (datosDistintos(seleccionado[0], clave, nombre, adscripcion)) {
            guardar(marca, seleccionado, clave, nombre, adscripcion, tabla, estado, filtro, indicador, capturar, botones);
            return;
        }
        capturar.run();
    }

    private void capturarEImprimir(int marca, EmpleadoSmsem persona, Label estado, boolean aArchivo,
                                   Indicador indicador, Button... botones) {
        if (persona == null || persona.claveInterna == null || persona.claveInterna.isBlank()) {
            estadoError(estado, "Capture el nombre antes de imprimir.");
            return;
        }
        try {
            estadoOcupado(estado, "Esperando la fotografía…");
            byte[] foto = CapturaEmpleado.foto(stage);
            if (foto == null) {
                estadoError(estado, "Impresión cancelada: falta la fotografía.");
                return;
            }
            estadoOcupado(estado, "Esperando la firma…");
            byte[] firma = CapturaEmpleado.firma(stage);
            if (firma == null) {
                estadoError(estado, "Impresión cancelada: falta la firma.");
                return;
            }
            enCurso(marca, indicador, estado, "Enviando la credencial…", "imprimir", true, () -> {
                PrintResult resultado = impresora.imprimir(persona, foto, firma, aArchivo);
                if (!resultado.isAccepted()) {
                    throw new IllegalStateException(resultado.getMessage());
                }
                EmpleadoSmsem impresa = cliente.imprimir(persona.claveInterna, foto, firma, operador);
                String destino = resultado.getArtifactPath() == null
                        ? "impresora"
                        : resultado.getArtifactPath().toString();
                return (aArchivo ? "Guardada en archivo: " : "Enviada a impresora. ")
                        + destino
                        + " · foto de cámara · firma del pad · "
                        + impresa.idMongoFoto
                        + " · " + impresa.idMongoFirma;
            }, texto -> estadoOk(estado, texto), fallo -> estadoError(estado, mensaje(fallo)), botones);
        } catch (Exception ex) {
            String texto = mensaje(ex);
            bitacora.error("captura", texto, ex);
            estadoError(estado, texto);
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
        seccion = "seguimiento";
        BorderPane raiz = new BorderPane();
        Label estado = new Label();
        estado.setWrapText(true);
        estado.getStyleClass().setAll("status-bar");
        Indicador indicador = new Indicador();
        int marca = ++vista;
        Button actualizar = UiTheme.primaryButton("Actualizar");
        HBox barra = new HBox(8, actualizar);
        barra.setPadding(new Insets(12));
        TableView<EmpleadoSmsem> tabla = tablaEmpleados();
        TableColumn<EmpleadoSmsem, String> impresa = new TableColumn<>("Impresión");
        impresa.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().impresa() ? c.getValue().fechaImpresion : "Pendiente"));
        tabla.getColumns().add(impresa);
        Runnable cargar = () -> enCurso(marca, indicador, estado, "Consultando el seguimiento…", "seguimiento", true,
                () -> cliente.seguimiento(),
                filas -> {
                    tabla.getItems().setAll(filas);
                    estadoOk(estado, filas.size() + " registros en SMSEM");
                },
                fallo -> estadoError(estado, mensaje(fallo)),
                actualizar);
        actualizar.setOnAction(e -> cargar.run());
        raiz.setTop(new VBox(barraSesion(), indicador.barra(), barra, indicador.fila(estado)));
        raiz.setCenter(tabla);
        stage.setScene(escena(raiz, 1100, 680));
        cargar.run();
    }

    private int consultaSerial;

    private void cargarBusqueda(int marca, String nombre, TableView<EmpleadoSmsem> tabla, Label estado, Indicador indicador) {
        int token = ++consultaSerial;
        enCurso(marca, indicador, estado, "Buscando en SMSEM…", "consulta", false,
                () -> cliente.consultar(nombre),
                filas -> {
                    if (token != consultaSerial) {
                        return;
                    }
                    tabla.getItems().setAll(filas);
                    estadoOk(estado, filas.size() + " coincidencias en SMSEM");
                },
                fallo -> {
                    if (token != consultaSerial) {
                        return;
                    }
                    estadoError(estado, mensaje(fallo));
                });
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

    private boolean camposObligatorios(TextField clave, TextField nombre, TextField adscripcion, Label estado) {
        StringBuilder faltan = new StringBuilder();
        if (clave.getText().trim().isEmpty()) {
            faltan.append("clave, ");
        }
        if (nombre.getText().trim().isEmpty()) {
            faltan.append("nombre completo, ");
        }
        if (adscripcion.getText().trim().isEmpty()) {
            faltan.append("adscripción, ");
        }
        if (faltan.length() == 0) {
            return true;
        }
        faltan.setLength(faltan.length() - 2);
        estadoError(estado, "Campos obligatorios: " + faltan + ".");
        return false;
    }

    private <T> void enCurso(int marca, Indicador indicador, Label estado, String espera, String operacion,
                             boolean modal, Callable<T> trabajo, Consumer<T> alTerminar, Consumer<Exception> alFallar,
                             Button... botones) {
        if (modal) {
            mostrarVelo(espera);
        } else {
            indicador.mostrar();
        }
        deshabilitar(true, botones);
        estadoOcupado(estado, espera);
        Thread hilo = new Thread(() -> {
            try {
                T resultado = trabajo.call();
                Platform.runLater(() -> {
                    if (modal) {
                        ocultarVelo();
                    } else {
                        indicador.ocultar();
                    }
                    deshabilitar(false, botones);
                    if (marca != vista) {
                        return;
                    }
                    alTerminar.accept(resultado);
                });
            } catch (Exception ex) {
                String texto = mensaje(ex);
                bitacora.error(operacion, texto, ex);
                Platform.runLater(() -> {
                    if (modal) {
                        ocultarVelo();
                    } else {
                        indicador.ocultar();
                    }
                    deshabilitar(false, botones);
                    if (marca != vista) {
                        return;
                    }
                    alFallar.accept(ex);
                });
            }
        }, "credenciales-" + operacion);
        hilo.setDaemon(true);
        hilo.start();
    }

    private VBox panelRegistro() {
        Label ruta = new Label();
        ruta.textProperty().bind(javafx.beans.binding.Bindings.concat("Ruta de los logs: ", bitacora.avisoProperty()));
        ruta.setWrapText(true);
        ruta.getStyleClass().add("muted");
        ListView<BitacoraEstacion.Entrada> lista = new ListView<>(bitacora.entradas());
        lista.setPrefHeight(140);
        lista.setCellFactory(columna -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(BitacoraEstacion.Entrada entrada, boolean vacio) {
                super.updateItem(entrada, vacio);
                setText(vacio || entrada == null ? "" : entrada.linea() + "  " + entrada.detalle());
            }
        });
        TextArea detalle = new TextArea();
        detalle.setEditable(false);
        detalle.setWrapText(true);
        detalle.setPrefRowCount(4);
        lista.getSelectionModel().selectedItemProperty().addListener((o, anterior, entrada) ->
                detalle.setText(entrada == null ? "" : entrada.completa()));
        Button copiarNota = UiTheme.secondaryButton("Copiar nota");
        Button copiarTodo = UiTheme.secondaryButton("Copiar todo");
        copiarNota.setOnAction(e -> copiar(detalle.getText()));
        copiarTodo.setOnAction(e -> copiar(bitacora.todo()));
        VBox panel = new VBox(8, ruta, lista, detalle, new HBox(8, copiarNota, copiarTodo));
        panel.setPadding(new Insets(8, 12, 12, 12));
        panel.setVisible(false);
        panel.setManaged(false);
        return panel;
    }

    private Button botonAyuda() {
        Button boton = new Button("?");
        boton.getStyleClass().add("btn-ayuda");
        boton.setFocusTraversable(false);
        boton.setOnAction(e -> alternarRegistro());
        return boton;
    }

    private Button botonMenu(String texto, String id) {
        Button boton = new Button(texto);
        boton.getStyleClass().add(id.equals(seccion) ? "btn-menu-activo" : "btn-menu");
        return boton;
    }

    private StackPane veloModal() {
        veloMensaje = new Label();
        veloMensaje.getStyleClass().add("velo-texto");
        ProgressIndicator giro = new ProgressIndicator();
        giro.setPrefSize(64, 64);
        VBox caja = new VBox(14, giro, veloMensaje);
        caja.setAlignment(Pos.CENTER);
        caja.getStyleClass().add("velo-caja");
        StackPane capa = new StackPane(caja);
        capa.getStyleClass().add("velo");
        capa.setVisible(false);
        capa.setManaged(false);
        return capa;
    }

    private void mostrarVelo(String texto) {
        veloMensaje.setText(texto);
        veloActivo++;
        velo.setVisible(true);
        velo.setManaged(true);
        velo.setMouseTransparent(false);
    }

    private void ocultarVelo() {
        veloActivo = Math.max(0, veloActivo - 1);
        boolean encendido = veloActivo > 0;
        velo.setVisible(encendido);
        velo.setManaged(encendido);
        velo.setMouseTransparent(!encendido);
    }

    private Scene escena(BorderPane raiz, double ancho, double alto) {
        colocarRegistro(raiz);
        if (velo.getParent() instanceof Pane padre) {
            padre.getChildren().remove(velo);
        }
        Button ayuda = botonAyuda();
        StackPane capa = new StackPane(raiz, ayuda, velo);
        capa.setPickOnBounds(false);
        StackPane.setAlignment(ayuda, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(ayuda, new Insets(0, 16, 16, 0));
        Scene scene = new Scene(capa, ancho, alto);
        preparar(scene);
        return scene;
    }

    private void alternarRegistro() {
        registroVisible = !registroVisible;
        registro.setVisible(registroVisible);
        registro.setManaged(registroVisible);
    }

    private void colocarRegistro(BorderPane raiz) {
        if (registro.getParent() instanceof Pane padre) {
            padre.getChildren().remove(registro);
        }
        raiz.setBottom(registro);
    }

    private void preparar(Scene scene) {
        aplicarCss(scene);
    }

    private static void estadoOcupado(Label estado, String texto) {
        estado.setText(texto);
        estado.getStyleClass().setAll("status-bar", "status-busy");
    }

    private static void estadoOk(Label estado, String texto) {
        estado.setText(texto);
        estado.getStyleClass().setAll("status-bar", "status-ok");
    }

    private static void estadoError(Label estado, String texto) {
        estado.setText(texto);
        estado.getStyleClass().setAll("status-bar", "status-err");
    }

    private static String mensaje(Exception ex) {
        String texto = ex.getMessage();
        return texto == null || texto.isBlank() ? "No se pudo completar la operación." : texto;
    }

    private static void deshabilitar(boolean apagado, Button... botones) {
        if (botones == null) {
            return;
        }
        for (Button boton : botones) {
            if (boton != null) {
                boton.setDisable(apagado);
            }
        }
    }

    private static void copiar(String texto) {
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(texto == null ? "" : texto);
        Clipboard.getSystemClipboard().setContent(contenido);
    }

    private static void aplicarCss(Scene scene) {
        var css = CredencialesApp.class.getResource(UiTheme.CSS);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        var identidad = CredencialesApp.class.getResource("/credencialessm/credenciales.css");
        if (identidad != null) {
            scene.getStylesheets().add(identidad.toExternalForm());
        }
    }

    private static ImageView logo(String nombre, double alto) {
        var recurso = CredencialesApp.class.getResource("/credencialessm/" + nombre);
        ImageView vista = new ImageView();
        if (recurso != null) {
            vista.setImage(new Image(recurso.toExternalForm()));
        }
        vista.setPreserveRatio(true);
        vista.setFitHeight(alto);
        vista.setSmooth(true);
        return vista;
    }

    private static final class Indicador {
        private final ProgressIndicator giro = new ProgressIndicator();
        private final ProgressBar barra = new ProgressBar();
        private int activos;

        private Indicador() {
            giro.setPrefSize(16, 16);
            giro.setMinSize(16, 16);
            barra.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            barra.setMaxWidth(Double.MAX_VALUE);
            barra.setPrefHeight(4);
            barra.setMaxHeight(4);
            aplicar();
        }

        private ProgressBar barra() {
            return barra;
        }

        private HBox fila(Label estado) {
            HBox.setHgrow(estado, Priority.ALWAYS);
            HBox fila = new HBox(8, giro, estado);
            fila.setAlignment(Pos.CENTER_LEFT);
            return fila;
        }

        private void mostrar() {
            activos++;
            aplicar();
        }

        private void ocultar() {
            activos = Math.max(0, activos - 1);
            aplicar();
        }

        private void aplicar() {
            boolean encendido = activos > 0;
            giro.setVisible(encendido);
            giro.setManaged(encendido);
            barra.setVisible(encendido);
            barra.setManaged(encendido);
        }
    }
}
