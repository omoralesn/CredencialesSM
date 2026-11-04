package mx.org.smsem.credencialessm.zxp;

import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.common.card.containers.GraphicsInfo;
import com.zebra.sdk.common.card.enumerations.CardSide;
import com.zebra.sdk.common.card.enumerations.GraphicType;
import com.zebra.sdk.common.card.enumerations.OrientationType;
import com.zebra.sdk.common.card.enumerations.PrintType;
import com.zebra.sdk.common.card.graphics.ZebraCardGraphics;
import com.zebra.sdk.common.card.graphics.ZebraCardImageI;
import com.zebra.sdk.common.card.graphics.enumerations.RotationType;
import com.zebra.sdk.common.card.printer.ZebraCardPrinter;
import com.zebra.sdk.common.card.printer.ZebraCardPrinterFactory;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterDriver;
import com.zebra.sdk.printer.discovery.DiscoveredUsbPrinter;
import com.zebra.sdk.printer.discovery.UsbDiscoverer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Manda a la ZXP Series 7 un PNG que ya generó la estación y deja el registro del intento.
 * No usa cámara, pad ni el ejecutable de CredencialesSM.
 */
public final class ZxpPrueba {

    /** Mismo tamaño que EmpleadoCardComposer: CR80 vertical a 300 dpi. */
    private static final int ANCHO = 638;
    private static final int ALTO = 1012;

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextField archivo = new JTextField(36);
    private final JComboBox<Opcion> impresoras = new JComboBox<>();
    private final JCheckBox frente = new JCheckBox("Frente", true);
    private final JCheckBox reverso = new JCheckBox("Reverso", true);
    private final JCheckBox ajustar = new JCheckBox("Ajustar a 638×1012 px (300 dpi)", true);
    private final JTextArea registro = new JTextArea(18, 80);
    private final Path bitacora = Path.of(System.getProperty("user.home"), ".credencialessm", "zxp-prueba.log");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ZxpPrueba().mostrar());
    }

    private void mostrar() {
        registro.setEditable(false);
        registro.setLineWrap(true);
        registro.setWrapStyleWord(true);

        JButton elegir = new JButton("Elegir PNG…");
        elegir.addActionListener(e -> elegirPng());
        JButton buscar = new JButton("Buscar impresoras");
        buscar.addActionListener(e -> enSegundoPlano(buscar, this::buscarImpresoras));
        JButton imprimir = new JButton("Imprimir");
        imprimir.addActionListener(e -> enSegundoPlano(imprimir, this::imprimir));
        JButton copiar = new JButton("Copiar registro");
        copiar.addActionListener(e -> copiarRegistro());

        JPanel norte = new JPanel(new BorderLayout(8, 8));
        JPanel filaArchivo = new JPanel(new BorderLayout(8, 0));
        filaArchivo.add(new JLabel("Imagen"), BorderLayout.WEST);
        filaArchivo.add(archivo, BorderLayout.CENTER);
        filaArchivo.add(elegir, BorderLayout.EAST);
        JPanel filaImpresora = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filaImpresora.add(new JLabel("Impresora"));
        impresoras.setPrototypeDisplayValue(new Opcion("Zebra ZXP Series 7 (driver)", null));
        filaImpresora.add(impresoras);
        filaImpresora.add(buscar);
        filaImpresora.add(frente);
        filaImpresora.add(reverso);
        filaImpresora.add(ajustar);
        filaImpresora.add(imprimir);
        filaImpresora.add(copiar);
        norte.add(filaArchivo, BorderLayout.NORTH);
        norte.add(filaImpresora, BorderLayout.SOUTH);
        norte.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JFrame ventana = new JFrame("Prueba de impresión ZXP");
        ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ventana.add(norte, BorderLayout.NORTH);
        ventana.add(new JScrollPane(registro), BorderLayout.CENTER);
        ventana.pack();
        ventana.setLocationByPlatform(true);
        ventana.setVisible(true);

        Path reciente = pngMasReciente();
        if (reciente != null) {
            archivo.setText(reciente.toString());
        }
        log("Listo. El registro también se escribe en " + bitacora);
        log("Esta prueba no va dentro del ejecutable de la estación.");
    }

    private void elegirPng() {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new FileNameExtensionFilter("PNG", "png"));
        Path actual = ruta();
        if (actual != null && Files.isDirectory(actual.getParent())) {
            selector.setCurrentDirectory(actual.getParent().toFile());
        } else {
            Path preview = Path.of(System.getProperty("user.home"), ".credencialessm", "print-preview");
            if (Files.isDirectory(preview)) {
                selector.setCurrentDirectory(preview.toFile());
            }
        }
        if (selector.showOpenDialog(registro) == JFileChooser.APPROVE_OPTION) {
            archivo.setText(selector.getSelectedFile().getAbsolutePath());
        }
    }

    private void buscarImpresoras() {
        List<Opcion> halladas = new ArrayList<>();
        log("Buscando impresoras Zebra por el driver de Windows…");
        try {
            DiscoveredPrinterDriver[] drivers = UsbDiscoverer.getZebraDriverPrinters();
            if (drivers == null || drivers.length == 0) {
                log("El driver no reportó impresoras.");
            } else {
                for (DiscoveredPrinterDriver driver : drivers) {
                    String nombre = driver.printerName == null ? driver.address : driver.printerName;
                    halladas.add(new Opcion("driver: " + nombre, driver));
                    log("Driver: " + nombre + "  dirección " + driver.address);
                }
            }
        } catch (Throwable ex) {
            log("No se pudo consultar el driver.");
            log(ex);
        }
        log("Buscando impresoras Zebra por USB…");
        try {
            DiscoveredUsbPrinter[] usb = UsbDiscoverer.getZebraUsbPrinters();
            if (usb == null || usb.length == 0) {
                log("USB no reportó impresoras.");
            } else {
                for (DiscoveredUsbPrinter una : usb) {
                    halladas.add(new Opcion("usb: " + una.address, una));
                    log("USB: " + una.address);
                }
            }
        } catch (Throwable ex) {
            log("No se pudo consultar USB. En Windows, con el driver instalado, use la que aparezca como driver.");
            log(ex);
        }
        SwingUtilities.invokeLater(() -> {
            impresoras.setModel(new DefaultComboBoxModel<>(halladas.toArray(Opcion[]::new)));
            if (halladas.isEmpty()) {
                log("No hay una ZXP para elegir. Revise que esté encendida y que Windows la muestre en Impresoras.");
            }
        });
    }

    private void imprimir() {
        Path png = ruta();
        if (png == null || !Files.isRegularFile(png)) {
            log("Elija un PNG generado por la estación.");
            return;
        }
        Opcion opcion = (Opcion) impresoras.getSelectedItem();
        if (opcion == null || opcion.impresora == null) {
            log("Primero busque y elija la impresora.");
            return;
        }
        if (!frente.isSelected() && !reverso.isSelected()) {
            log("Marque frente, reverso o los dos.");
            return;
        }
        BufferedImage hoja;
        try {
            hoja = ImageIO.read(png.toFile());
        } catch (IOException ex) {
            log("No se pudo leer " + png);
            log(ex);
            return;
        }
        if (hoja == null) {
            log("El archivo no es una imagen legible: " + png);
            return;
        }
        log("Archivo " + png);
        log("Imagen leída: " + hoja.getWidth() + "×" + hoja.getHeight() + " px, tipo " + hoja.getType());
        List<Cara> caras = caras(hoja);
        if (caras.isEmpty()) {
            log("Nada que imprimir con las caras marcadas.");
            return;
        }
        boolean llenar = ajustar.isSelected();
        List<Cara> listas = new ArrayList<>();
        for (Cara cara : caras) {
            listas.add(new Cara(cara.nombre, cara.ladoTarjeta, ajustar(cara.imagen, llenar)));
        }
        enviar(opcion.impresora, listas);
    }

    private List<Cara> caras(BufferedImage hoja) {
        boolean esHoja = hoja.getWidth() == ANCHO && hoja.getHeight() >= ALTO * 2;
        List<Cara> caras = new ArrayList<>();
        if (esHoja) {
            log("Es la hoja de la estación (frente, separación y reverso). Se recorta cada cara a "
                    + ANCHO + "×" + ALTO + ".");
            if (frente.isSelected()) {
                caras.add(new Cara("frente", CardSide.Front, recorte(hoja, 0, 0, ANCHO, ALTO)));
            }
            if (reverso.isSelected()) {
                int y = hoja.getHeight() - ALTO;
                caras.add(new Cara("reverso", CardSide.Back, recorte(hoja, 0, y, ANCHO, ALTO)));
            }
            return caras;
        }
        CardSide lado = reverso.isSelected() && !frente.isSelected() ? CardSide.Back : CardSide.Front;
        if (frente.isSelected() && reverso.isSelected()) {
            log("El PNG es una sola cara. Se manda como frente.");
        }
        caras.add(new Cara(lado == CardSide.Front ? "frente" : "reverso", lado, copia(hoja)));
        return caras;
    }

    private BufferedImage ajustar(BufferedImage origen, boolean llenar) {
        int ancho = origen.getWidth();
        int alto = origen.getHeight();
        boolean vertical = alto >= ancho;
        int destinoAncho = vertical ? ANCHO : ALTO;
        int destinoAlto = vertical ? ALTO : ANCHO;
        if (ancho == destinoAncho && alto == destinoAlto) {
            log("La cara ya está en " + ancho + "×" + alto + ", el tamaño CR80 a 300 dpi.");
            return origen;
        }
        log("La cara mide " + ancho + "×" + alto + ". La tarjeta a 300 dpi mide " + destinoAncho + "×" + destinoAlto + ".");
        if (!llenar) {
            log("Se envía sin ajustar. Una imagen más chica sale mini en la tarjeta.");
            return origen;
        }
        BufferedImage destino = new BufferedImage(destinoAncho, destinoAlto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, destinoAncho, destinoAlto);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(origen, 0, 0, destinoAncho, destinoAlto, null);
        } finally {
            g.dispose();
        }
        log("Ajustada a " + destinoAncho + "×" + destinoAlto + " antes de enviarla.");
        return destino;
    }

    private void enviar(DiscoveredPrinter descubierta, List<Cara> caras) {
        log("Conectando con " + descubierta);
        Connection conexion = null;
        ZebraCardPrinter impresora = null;
        ZebraCardGraphics graficos = null;
        try {
            conexion = descubierta.getConnection();
            log("Conexión " + conexion.getClass().getSimpleName() + " " + conexion);
            conexion.open();
            log("Conexión abierta.");
            impresora = ZebraCardPrinterFactory.getInstance(conexion);
            log("Impresora " + impresora.getClass().getName());
            graficos = new ZebraCardGraphics(impresora);
            List<GraphicsInfo> trabajos = new ArrayList<>();
            for (Cara cara : caras) {
                BufferedImage imagen = cara.imagen;
                OrientationType orientacion = imagen.getHeight() >= imagen.getWidth()
                        ? OrientationType.Portrait
                        : OrientationType.Landscape;
                log(cara.nombre + ": initialize " + imagen.getWidth() + "×" + imagen.getHeight()
                        + " " + orientacion + " " + PrintType.Color + ", rotación " + RotationType.RotateNoneFlipNone);
                graficos.initialize(imagen.getWidth(), imagen.getHeight(), orientacion, PrintType.Color, Color.WHITE);
                byte[] pixeles = graficos.imageToImageData(imagen);
                log(cara.nombre + ": datos de imagen " + pixeles.length + " bytes.");
                graficos.drawImage(pixeles, 0, 0, imagen.getWidth(), imagen.getHeight(), RotationType.RotateNoneFlipNone);
                ZebraCardImageI tarjeta = graficos.createImage();
                GraphicsInfo info = new GraphicsInfo();
                info.side = cara.ladoTarjeta;
                info.printType = PrintType.Color;
                info.graphicType = GraphicType.BMP;
                info.graphicData = tarjeta;
                info.xOffset = 0;
                info.yOffset = 0;
                info.opacity = 100;
                trabajos.add(info);
                graficos.clear();
            }
            int job = impresora.print(1, trabajos);
            log("Trabajo aceptado. Id " + job + ". Caras: " + trabajos.size() + ".");
        } catch (Throwable ex) {
            log("La ZXP no completó la impresión.");
            log(ex);
        } finally {
            cerrar(graficos, conexion);
        }
    }

    private static void cerrar(ZebraCardGraphics graficos, Connection conexion) {
        if (graficos != null) {
            try {
                graficos.close();
            } catch (RuntimeException ignored) {
                // El cierre no cambia el resultado que ya quedó en el registro.
            }
        }
        if (conexion != null) {
            try {
                conexion.close();
            } catch (Exception ignored) {
                // Igual que arriba: el registro ya tiene el error de impresión, si lo hubo.
            }
        }
    }

    private static BufferedImage recorte(BufferedImage origen, int x, int y, int ancho, int alto) {
        BufferedImage salida = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = salida.createGraphics();
        try {
            g.drawImage(origen, 0, 0, ancho, alto, x, y, x + ancho, y + alto, null);
        } finally {
            g.dispose();
        }
        return salida;
    }

    private static BufferedImage copia(BufferedImage origen) {
        return recorte(origen, 0, 0, origen.getWidth(), origen.getHeight());
    }

    private Path ruta() {
        String texto = archivo.getText();
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return Path.of(texto.trim());
    }

    private static Path pngMasReciente() {
        Path carpeta = Path.of(System.getProperty("user.home"), ".credencialessm", "print-preview");
        if (!Files.isDirectory(carpeta)) {
            return null;
        }
        try (Stream<Path> archivos = Files.list(carpeta)) {
            return archivos
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".png"))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    private void enSegundoPlano(JButton boton, Runnable trabajo) {
        boton.setEnabled(false);
        Thread hilo = new Thread(() -> {
            try {
                trabajo.run();
            } finally {
                SwingUtilities.invokeLater(() -> boton.setEnabled(true));
            }
        }, "zxp-prueba");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void copiarRegistro() {
        registro.selectAll();
        registro.copy();
        registro.select(registro.getCaretPosition(), registro.getCaretPosition());
    }

    private void log(String linea) {
        String texto = HORA.format(LocalTime.now()) + "  " + linea;
        SwingUtilities.invokeLater(() -> {
            registro.append(texto);
            registro.append("\n");
            registro.setCaretPosition(registro.getDocument().getLength());
        });
        try {
            Files.createDirectories(bitacora.getParent());
            Files.writeString(bitacora, texto + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ex) {
            SwingUtilities.invokeLater(() -> registro.append("No se pudo escribir la bitácora: " + ex.getMessage() + "\n"));
        }
    }

    private void log(Throwable ex) {
        StringWriter salida = new StringWriter();
        ex.printStackTrace(new PrintWriter(salida));
        log(salida.toString().strip());
    }

    private record Opcion(String etiqueta, DiscoveredPrinter impresora) {
        @Override
        public String toString() {
            return etiqueta;
        }
    }

    private record Cara(String nombre, CardSide ladoTarjeta, BufferedImage imagen) {
    }
}
