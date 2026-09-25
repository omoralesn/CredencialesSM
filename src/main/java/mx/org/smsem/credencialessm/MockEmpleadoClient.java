package mx.org.smsem.credencialessm;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Padrón en memoria, sin Oracle ni Mongo. Sirve para probar la estación.
 * Usuario de prueba: operador / operador123.
 */
public final class MockEmpleadoClient implements EmpleadoApi {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final List<EmpleadoSmsem> personas = new ArrayList<>();
    private int secuencia;

    public MockEmpleadoClient() {
        cargarRecurso();
    }

    @Override
    public String ingresar(String usuario, String password) {
        if (!"operador".equals(usuario == null ? "" : usuario.trim()) || !"operador123".equals(password)) {
            throw new IllegalArgumentException("Usuario o contraseña incorrectos. Prueba: operador / operador123");
        }
        return "Operador de prueba";
    }

    @Override
    public List<EmpleadoSmsem> consultar(String nombre) {
        String filtro = TextoBusqueda.sinAcentos(nombre);
        return personas.stream()
                .filter(p -> filtro.isEmpty() || TextoBusqueda.sinAcentos(p.nombreCompleto).contains(filtro))
                .limit(50)
                .toList();
    }

    @Override
    public EmpleadoSmsem alta(String clave, String nombre, String adscripcion) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre completo es obligatorio");
        }
        EmpleadoSmsem persona = nueva(clave, nombre.trim(), adscripcion);
        personas.add(persona);
        return persona;
    }

    @Override
    public EmpleadoSmsem modifica(String claveInterna, String clave, String nombre, String adscripcion) {
        EmpleadoSmsem persona = buscar(claveInterna);
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre completo es obligatorio");
        }
        persona.clave = clave == null ? "" : clave.trim();
        persona.nombreCompleto = nombre.trim();
        persona.adscripcion = adscripcion == null ? "" : adscripcion.trim();
        persona.cadenaQr = persona.claveInterna + "||" + persona.nombreCompleto;
        return persona;
    }

    @Override
    public EmpleadoSmsem imprimir(String claveInterna, byte[] foto, byte[] firma, String usuario) {
        if (foto == null || foto.length == 0 || firma == null || firma.length == 0) {
            throw new IllegalArgumentException("Faltan la fotografía o la firma");
        }
        EmpleadoSmsem persona = buscar(claveInterna);
        String hoy = LocalDate.now().format(FECHA);
        persona.idMongoFoto = "mock-foto-" + persona.claveInterna;
        persona.idMongoFirma = "mock-firma-" + persona.claveInterna;
        persona.fechaCaptura = hoy;
        persona.fechaImpresion = hoy;
        persona.usuarioEstacion = usuario;
        return persona;
    }

    @Override
    public List<EmpleadoSmsem> seguimiento() {
        return List.copyOf(personas);
    }

    @Override
    public int cuenta() {
        return personas.size();
    }

    @Override
    public int cargarPadron(String contenidoTsv) {
        if (!personas.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (String[] fila : filas(contenidoTsv)) {
            personas.add(nueva(fila[0], fila[1], fila.length > 2 ? fila[2] : ""));
            n++;
        }
        return n;
    }

    private void cargarRecurso() {
        try (InputStream in = MockEmpleadoClient.class.getResourceAsStream("/credencialessm/padron-smsem.tsv")) {
            if (in == null) {
                return;
            }
            cargarPadron(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo cargar el padrón de prueba", ex);
        }
    }

    private EmpleadoSmsem buscar(String claveInterna) {
        return personas.stream()
                .filter(p -> p.claveInterna.equals(claveInterna))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe el empleado " + claveInterna));
    }

    private EmpleadoSmsem nueva(String clave, String nombre, String adscripcion) {
        EmpleadoSmsem persona = new EmpleadoSmsem();
        persona.claveInterna = String.format("ESM%06d", ++secuencia);
        persona.clave = clave == null ? "" : clave.trim();
        persona.nombreCompleto = nombre;
        persona.adscripcion = adscripcion == null ? "" : adscripcion.trim();
        persona.cadenaQr = persona.claveInterna + "||" + nombre;
        persona.fechaAlta = LocalDate.now().format(FECHA);
        return persona;
    }

    private static List<String[]> filas(String contenido) {
        List<String[]> filas = new ArrayList<>();
        if (contenido == null) {
            return filas;
        }
        String[] lineas = contenido.split("\\R");
        for (int i = 0; i < lineas.length; i++) {
            if (lineas[i].isBlank() || (i == 0 && lineas[i].toLowerCase(Locale.ROOT).startsWith("clave"))) {
                continue;
            }
            String[] cols = lineas[i].split("\t", -1);
            if (cols.length >= 2 && !cols[1].isBlank()) {
                filas.add(cols);
            }
        }
        return filas;
    }
}
