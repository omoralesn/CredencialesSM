package mx.org.smsem.credencialessm.ui;

import mx.org.smsem.credencialessm.dispositivos.TopazPadSupport;
import mx.org.smsem.credencialessm.dispositivos.TopazModel;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Captura de firma: pad Topaz (HID o serial) + ratón/touch como respaldo.
 */
public final class SignatureCaptureDialog {

    private static final int WIDTH = 520;
    private static final int HEIGHT = 180;

    private SignatureCaptureDialog() {
    }

    public static Optional<byte[]> show(Window owner) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Captura de firma");
        stage.setMinWidth(720);
        stage.setMinHeight(320);

        Label hint = new Label();
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        BufferedImage pad = newBlankPad();
        ImageView padView = new ImageView(SwingFXUtils.toFXImage(pad, null));
        padView.setFitWidth(WIDTH);
        padView.setFitHeight(HEIGHT);
        padView.setPreserveRatio(false);
        padView.setSmooth(true);
        padView.setMouseTransparent(true);

        Pane hitArea = new Pane(padView);
        hitArea.setPrefSize(WIDTH, HEIGHT);
        hitArea.setMinSize(WIDTH, HEIGHT);
        hitArea.setMaxSize(WIDTH, HEIGHT);
        hitArea.setStyle("-fx-background-color: white; -fx-border-color: #bcccdc; -fx-border-width: 1;");

        ImageView resultPreview = new ImageView();
        resultPreview.setFitWidth(200);
        resultPreview.setFitHeight(70);
        resultPreview.setPreserveRatio(true);
        Label previewLbl = UiTheme.muted("Vista previa");
        Label deviceLbl = UiTheme.badge("Buscando Topaz…", "warn");

        AtomicBoolean drawing = new AtomicBoolean(false);
        AtomicBoolean hasInk = new AtomicBoolean(false);
        AtomicBoolean topazPenDown = new AtomicBoolean(false);
        AtomicReference<double[]> last = new AtomicReference<>(new double[]{-1, -1});
        AtomicReference<TopazPadSupport.Handle> topazRef = new AtomicReference<>();
        AtomicReference<TopazModel> modelRef = new AtomicReference<>(TopazModel.SIGNATURE_GEM_1X5_HID);

        Runnable refreshViews = () -> {
            Image fx = SwingFXUtils.toFXImage(pad, null);
            padView.setImage(fx);
            if (hasInk.get()) {
                resultPreview.setImage(fx);
            }
        };

        // —— Ratón / touch (respaldo) ——
        hitArea.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            drawing.set(true);
            hasInk.set(true);
            double x = clamp(e.getX(), 0, WIDTH - 1);
            double y = clamp(e.getY(), 0, HEIGHT - 1);
            last.set(new double[]{x, y});
            stroke(pad, x, y, x + 0.01, y + 0.01);
            refreshViews.run();
            e.consume();
        });
        hitArea.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!drawing.get()) {
                return;
            }
            double x = clamp(e.getX(), 0, WIDTH - 1);
            double y = clamp(e.getY(), 0, HEIGHT - 1);
            double[] p = last.get();
            stroke(pad, p[0], p[1], x, y);
            last.set(new double[]{x, y});
            hasInk.set(true);
            refreshViews.run();
            e.consume();
        });
        hitArea.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            drawing.set(false);
            last.set(new double[]{-1, -1});
            e.consume();
        });

        // —— Topaz (HID 06A8 o serial FTDI/BSB) ——
        Optional<TopazPadSupport.Handle> topazOpt = TopazPadSupport.openBest();
        if (topazOpt.isPresent()) {
            TopazPadSupport.Handle handle = topazOpt.get();
            topazRef.set(handle);
            modelRef.set(handle.model());
            TopazModel model = handle.model();
            handle.addListener(pt -> Platform.runLater(() -> {
                TopazModel m = modelRef.get();
                double x = pt.x() * (WIDTH - 1.0) / Math.max(1, m.logicalW());
                double y = pt.y() * (HEIGHT - 1.0) / Math.max(1, m.logicalH());
                x = clamp(x, 0, WIDTH - 1);
                y = clamp(y, 0, HEIGHT - 1);
                boolean ink = pt.penDown();
                if (ink) {
                    if (!topazPenDown.get()) {
                        topazPenDown.set(true);
                        last.set(new double[]{x, y});
                        stroke(pad, x, y, x + 0.01, y + 0.01);
                    } else {
                        double[] p = last.get();
                        if (p[0] >= 0) {
                            stroke(pad, p[0], p[1], x, y);
                        }
                        last.set(new double[]{x, y});
                    }
                    hasInk.set(true);
                    refreshViews.run();
                } else {
                    topazPenDown.set(false);
                    last.set(new double[]{-1, -1});
                }
            }));
            handle.start();
            deviceLbl.setText(handle.label());
            deviceLbl.getStyleClass().setAll("badge", "badge-ok");
            if (windows()) {
                hint.setText("Firme con el stylus Topaz apoyado (" + model.displayName() + "). "
                        + "También puede usar el ratón.");
            } else {
                hint.setText("Firme con el stylus Topaz apoyado (" + model.displayName() + "). "
                        + "También puede usar el ratón.\n"
                        + "Si no hay trazo: DESCONECTE el USB 10 s, reconecte, luego "
                        + "./scripts/test-topaz-hid.sh y ./sidid.sh restart");
            }
        } else {
            deviceLbl.setText("Sin Topaz");
            deviceLbl.getStyleClass().setAll("badge", "badge-warn");
            hint.setText("No se detectó pad Topaz. Firme con el ratón/touch.\n"
                    + String.join("\n", TopazPadSupport.diagnose()));
        }

        Button clearBtn = UiTheme.secondaryButton("Limpiar");
        Button acceptBtn = UiTheme.primaryButton("Usar esta firma");
        Button cancelBtn = UiTheme.secondaryButton("Cancelar");
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        acceptBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setMaxWidth(Double.MAX_VALUE);

        clearBtn.setOnAction(e -> {
            Graphics2D g = pad.createGraphics();
            fillBlank(g);
            g.dispose();
            hasInk.set(false);
            topazPenDown.set(false);
            last.set(new double[]{-1, -1});
            resultPreview.setImage(null);
            refreshViews.run();
        });

        AtomicReference<byte[]> result = new AtomicReference<>();
        Runnable shutdownTopaz = () -> {
            TopazPadSupport.Handle t = topazRef.getAndSet(null);
            if (t != null) {
                t.close();
            }
        };

        acceptBtn.setOnAction(e -> {
            if (!hasInk.get()) {
                UiDialogs.error("Capture una firma en el pad Topaz (o con el ratón) antes de continuar.");
                return;
            }
            try {
                byte[] png = toPng(pad);
                Image check = new Image(new ByteArrayInputStream(png));
                if (check.isError() || png.length < 64) {
                    throw new IllegalStateException("La firma exportada quedó vacía");
                }
                resultPreview.setImage(check);
                result.set(png);
                shutdownTopaz.run();
                stage.close();
            } catch (Exception ex) {
                UiDialogs.error("No se pudo guardar la firma: " + ex.getMessage());
            }
        });
        cancelBtn.setOnAction(e -> {
            result.set(null);
            shutdownTopaz.run();
            stage.close();
        });
        stage.setOnCloseRequest(e -> shutdownTopaz.run());

        VBox actions = new VBox(8, deviceLbl, acceptBtn, clearBtn, cancelBtn, new Region(), previewLbl, resultPreview);
        actions.setAlignment(Pos.TOP_CENTER);
        actions.setPadding(new Insets(0, 0, 0, 12));
        actions.setPrefWidth(190);
        VBox.setVgrow(actions.getChildren().get(4), Priority.ALWAYS);

        HBox body = new HBox(8, hitArea, actions);
        body.setAlignment(Pos.CENTER_LEFT);

        VBox rootContent = new VBox(8, hint, body);
        rootContent.setPadding(new Insets(12));

        BorderPane root = new BorderPane(rootContent);
        Scene scene = new Scene(root, 760, 310);
        var css = UiTheme.class.getResource(UiTheme.CSS);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.sizeToScene();
        stage.showAndWait();
        shutdownTopaz.run();
        return Optional.ofNullable(result.get());
    }

    private static BufferedImage newBlankPad() {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        fillBlank(g);
        g.dispose();
        return img;
    }

    private static void fillBlank(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(188, 204, 220));
        g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);
        g.setColor(new Color(217, 226, 236));
        g.drawLine(20, HEIGHT - 28, WIDTH - 20, HEIGHT - 28);
    }

    private static void stroke(BufferedImage img, double x1, double y1, double x2, double y2) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(16, 42, 67));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) Math.round(x1), (int) Math.round(y1),
                (int) Math.round(x2), (int) Math.round(y2));
        g.dispose();
    }

    private static byte[] toPng(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "png", out)) {
            throw new IllegalStateException("ImageIO no pudo escribir PNG");
        }
        return out.toByteArray();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
