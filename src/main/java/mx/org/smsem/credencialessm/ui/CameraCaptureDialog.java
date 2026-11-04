package mx.org.smsem.credencialessm.ui;

import mx.org.smsem.credencialessm.dispositivos.WebcamSupport;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Captura de fotografía con recorte vertical 3:4 (credencial).
 */
public final class CameraCaptureDialog {

    private CameraCaptureDialog() {
    }

    public static Optional<byte[]> show(Window owner) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Captura de fotografía");
        stage.setMinWidth(520);
        stage.setMinHeight(720);

        ImageView preview = new ImageView();
        preview.setFitWidth(300);
        preview.setFitHeight(400);
        preview.setPreserveRatio(true);
        preview.setSmooth(true);

        Rectangle guide = new Rectangle(300, 400);
        guide.setFill(Color.TRANSPARENT);
        guide.setStroke(Color.web("#243b53"));
        guide.setStrokeWidth(2);
        guide.setArcWidth(8);
        guide.setArcHeight(8);
        guide.setMouseTransparent(true);

        StackPane previewStack = new StackPane(preview, guide);
        previewStack.getStyleClass().add("media-slot-photo");
        previewStack.setAlignment(Pos.CENTER);
        previewStack.setPrefSize(320, 420);
        previewStack.setMaxSize(320, 420);

        Label hint = UiTheme.muted(
                "Recorte vertical de credencial (3:4). Encuadre el rostro y pulse Capturar.");
        hint.setWrapText(true);

        ComboBox<WebcamSupport.CameraDevice> cameras = new ComboBox<>();
        cameras.setPrefWidth(260);
        Button refreshBtn = UiTheme.secondaryButton("Detectar");
        Button captureBtn = UiTheme.primaryButton("Capturar");
        Button acceptBtn = UiTheme.primaryButton("Usar esta foto");
        Button retakeBtn = UiTheme.secondaryButton("Volver a tomar");
        Button cancelBtn = UiTheme.secondaryButton("Cancelar");
        acceptBtn.setDisable(true);
        retakeBtn.setDisable(true);

        Label status = new Label("Buscando cámaras…");
        status.getStyleClass().add("muted");
        status.setWrapText(true);

        AtomicReference<VideoCapture> captureRef = new AtomicReference<>();
        AtomicReference<Mat> lastCropped = new AtomicReference<>();
        AtomicReference<byte[]> capturedJpeg = new AtomicReference<>();
        AtomicBoolean live = new AtomicBoolean(false);
        AtomicBoolean frozen = new AtomicBoolean(false);
        AtomicReference<Integer> lastIndex = new AtomicReference<>(preferredIndex());

        Thread previewThread = new Thread(() -> {
            Mat frame = new Mat();
            while (live.get()) {
                if (frozen.get()) {
                    sleep(80);
                    continue;
                }
                VideoCapture cap = captureRef.get();
                if (cap == null || !cap.isOpened()) {
                    sleep(120);
                    continue;
                }
                if (cap.read(frame) && !frame.empty()) {
                    Mat cropped = WebcamSupport.centerCropPortrait(frame);
                    Mat previous = lastCropped.getAndSet(cropped);
                    if (previous != null) {
                        previous.release();
                    }
                    BufferedImage bi = WebcamSupport.toBufferedImage(cropped);
                    if (bi != null) {
                        Platform.runLater(() -> preview.setImage(SwingFXUtils.toFXImage(bi, null)));
                    }
                } else {
                    sleep(40);
                }
            }
            frame.release();
        }, "sidi-camera-preview");
        previewThread.setDaemon(true);

        Runnable releaseCameraOnly = () -> {
            VideoCapture cap = captureRef.getAndSet(null);
            if (cap != null) {
                cap.release();
            }
            Mat old = lastCropped.getAndSet(null);
            if (old != null) {
                old.release();
            }
        };

        Runnable stopAll = () -> {
            live.set(false);
            releaseCameraOnly.run();
        };

        Runnable openSelected = () -> {
            WebcamSupport.CameraDevice device = cameras.getSelectionModel().getSelectedItem();
            if (device == null) {
                status.setText("No hay cámara seleccionada.");
                return;
            }
            frozen.set(false);
            capturedJpeg.set(null);
            acceptBtn.setDisable(true);
            retakeBtn.setDisable(true);
            captureBtn.setDisable(false);
            releaseCameraOnly.run();
            VideoCapture opened = WebcamSupport.openCapture(device.index());
            if (opened == null || !opened.isOpened()) {
                status.setText("No se pudo abrir: " + device.label());
                UiDialogs.error("No se pudo abrir la cámara «" + device.label() + "».");
                return;
            }
            lastIndex.set(device.index());
            captureRef.set(opened);
            status.setText("En vivo · " + device.label());
        };

        Runnable detect = () -> {
            status.setText("Detectando cámaras (liberando dispositivo)…");
            refreshBtn.setDisable(true);
            // Crítico: soltar la cámara abierta; si no, Detectar no encuentra nada.
            releaseCameraOnly.run();
            Integer previous = lastIndex.get();
            new Thread(() -> {
                try {
                    List<WebcamSupport.CameraDevice> found = WebcamSupport.listDevices(6);
                    Platform.runLater(() -> {
                        refreshBtn.setDisable(false);
                        cameras.getItems().setAll(found);
                        if (found.isEmpty()) {
                            status.setText("No se detectó cámara usable. Verifique conexión y que no esté en uso.");
                            captureBtn.setDisable(true);
                            return;
                        }
                        WebcamSupport.CameraDevice pick = found.stream()
                                .filter(d -> previous != null && d.index() == previous)
                                .findFirst()
                                .orElse(found.get(0));
                        cameras.getSelectionModel().select(pick);
                        openSelected.run();
                    });
                } catch (Throwable ex) {
                    Platform.runLater(() -> {
                        refreshBtn.setDisable(false);
                        status.setText("Error: " + ex.getMessage());
                        UiDialogs.error("No se pudo detectar cámaras: " + ex.getMessage());
                    });
                }
            }, "sidi-camera-detect").start();
        };

        cameras.setOnAction(e -> {
            if (cameras.getSelectionModel().getSelectedItem() != null) {
                openSelected.run();
            }
        });
        refreshBtn.setOnAction(e -> detect.run());

        captureBtn.setOnAction(e -> {
            Mat cropped = lastCropped.get();
            if (cropped == null || cropped.empty()) {
                UiDialogs.error("Aún no hay imagen. Espere el preview.");
                return;
            }
            Mat snapshot = cropped.clone();
            try {
                byte[] jpeg = WebcamSupport.encodeJpeg(snapshot, 90);
                capturedJpeg.set(jpeg);
                frozen.set(true);
                acceptBtn.setDisable(false);
                retakeBtn.setDisable(false);
                captureBtn.setDisable(true);
                status.setText("Foto lista. Use esta foto o vuelva a tomar.");
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            } finally {
                snapshot.release();
            }
        });

        retakeBtn.setOnAction(e -> {
            capturedJpeg.set(null);
            frozen.set(false);
            acceptBtn.setDisable(true);
            retakeBtn.setDisable(true);
            captureBtn.setDisable(false);
            status.setText("Preview en vivo. Encuadre y capture de nuevo.");
        });

        AtomicReference<byte[]> result = new AtomicReference<>();
        acceptBtn.setOnAction(e -> {
            byte[] jpeg = capturedJpeg.get();
            if (jpeg == null) {
                return;
            }
            result.set(jpeg);
            stopAll.run();
            stage.close();
        });
        cancelBtn.setOnAction(e -> {
            result.set(null);
            stopAll.run();
            stage.close();
        });
        stage.setOnCloseRequest(e -> stopAll.run());

        HBox camRow = new HBox(10, new Label("Cámara"), cameras, refreshBtn);
        camRow.setAlignment(Pos.CENTER_LEFT);

        // Botones siempre visibles abajo
        HBox actions = new HBox(10, captureBtn, retakeBtn, acceptBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(12, 16, 16, 16));
        actions.getStyleClass().add("dialog-actions");

        VBox top = new VBox(12, hint, camRow, status);
        top.setPadding(new Insets(16, 16, 8, 16));

        StackPane centerWrap = new StackPane(previewStack);
        centerWrap.setPadding(new Insets(8, 16, 8, 16));
        centerWrap.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(centerWrap);
        root.setBottom(actions);

        Scene scene = new Scene(root, 520, 740);
        var css = UiTheme.class.getResource(UiTheme.CSS);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);

        live.set(true);
        previewThread.start();
        detect.run();
        stage.showAndWait();
        stopAll.run();
        return Optional.ofNullable(result.get());
    }

    private static int preferredIndex() {
        String env = System.getenv("SIDI_CAMERA_INDEX");
        if (env == null || env.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(env.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
