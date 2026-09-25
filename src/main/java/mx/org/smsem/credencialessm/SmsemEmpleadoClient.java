package mx.org.smsem.credencialessm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Cliente de la estación hacia las acciones JSON de empleados en SMSEM.
 */
public final class SmsemEmpleadoClient implements EmpleadoApi {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String token;

    public SmsemEmpleadoClient() {
        String url = System.getenv().getOrDefault("CREDENCIALES_SMSEM_URL", "http://localhost:8080/SMSEM");
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.token = System.getenv().getOrDefault("CREDENCIALES_TOKEN", "estacion-credenciales-smsem");
    }

    public String ingresar(String usuario, String password) throws Exception {
        ApiRespuesta respuesta = post("accesoEmpleadosSmsem.action",
                form("usuario", usuario, "password", password));
        return respuesta.nombre == null || respuesta.nombre.isBlank() ? usuario : respuesta.nombre;
    }

    public List<EmpleadoSmsem> consultar(String nombre) throws Exception {
        ApiRespuesta respuesta = post("consultaEmpleadosSmsem.action",
                form("nombre", nombre));
        return respuesta.personas == null ? List.of() : respuesta.personas;
    }

    public EmpleadoSmsem alta(String clave, String nombre, String adscripcion) throws Exception {
        ApiRespuesta respuesta = post("altaEmpleadoSmsem.action",
                form("clave", clave, "nombre", nombre, "adscripcion", adscripcion));
        return respuesta.persona;
    }

    public EmpleadoSmsem modifica(String claveInterna, String clave, String nombre, String adscripcion) throws Exception {
        ApiRespuesta respuesta = post("modificaEmpleadoSmsem.action",
                form("claveInterna", claveInterna, "clave", clave, "nombre", nombre, "adscripcion", adscripcion));
        return respuesta.persona;
    }

    public EmpleadoSmsem imprimir(String claveInterna, byte[] foto, byte[] firma, String usuario) throws Exception {
        ApiRespuesta respuesta = post("imprimeEmpleadoSmsem.action",
                form("claveInterna", claveInterna,
                        "fotoBase64", Base64.getEncoder().encodeToString(foto),
                        "firmaBase64", Base64.getEncoder().encodeToString(firma),
                        "usuarioEstacion", usuario));
        return respuesta.persona;
    }

    public List<EmpleadoSmsem> seguimiento() throws Exception {
        ApiRespuesta respuesta = post("seguimientoEmpleadosSmsem.action", form());
        return respuesta.personas == null ? List.of() : respuesta.personas;
    }

    public int cuenta() throws Exception {
        return post("cuentaEmpleadosSmsem.action", form()).total;
    }

    public int cargarPadron(String contenidoTsv) throws Exception {
        return post("cargaPadronEmpleadosSmsem.action", form("contenido", contenidoTsv)).insertados;
    }

    private ApiRespuesta post(String action, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + action))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("SMSEM respondió HTTP " + response.statusCode());
        }
        ApiRespuesta respuesta = json.readValue(response.body(), ApiRespuesta.class);
        if (!respuesta.ok) {
            throw new IllegalStateException(respuesta.mensaje == null ? "SMSEM rechazó la operación" : respuesta.mensaje);
        }
        return respuesta;
    }

    private String form(String... pares) {
        List<String> partes = new ArrayList<>();
        partes.add("tokenEstacion=" + enc(token));
        for (int i = 0; i + 1 < pares.length; i += 2) {
            partes.add(enc(pares[i]) + "=" + enc(pares[i + 1]));
        }
        return String.join("&", partes);
    }

    private static String enc(String valor) {
        return URLEncoder.encode(valor == null ? "" : valor, StandardCharsets.UTF_8);
    }
}
