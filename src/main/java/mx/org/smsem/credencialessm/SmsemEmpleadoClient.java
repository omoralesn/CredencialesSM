package mx.org.smsem.credencialessm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.net.SocketTimeoutException;
import java.util.List;

/**
 * Cliente de la estación hacia las acciones JSON de empleados en SMSEM.
 */
public final class SmsemEmpleadoClient implements EmpleadoApi {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(ConstantesEstacion.entero("servidor.conexionSegundos"))).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String token;

    public SmsemEmpleadoClient() {
        this.baseUrl = ConstantesEstacion.urlServidor();
        this.token = ConstantesEstacion.texto("servidor.token");
    }

    public String ingresar(String usuario, String password) throws Exception {
        ApiRespuesta respuesta = post(ConstantesEstacion.texto("accion.acceso"),
                form("usuario", usuario, "password", password));
        return respuesta.nombre == null || respuesta.nombre.isBlank() ? usuario : respuesta.nombre;
    }

    public List<EmpleadoSmsem> consultar(String nombre) throws Exception {
        ApiRespuesta respuesta = post(ConstantesEstacion.texto("accion.consulta"),
                form("nombre", nombre));
        return respuesta.personas == null ? List.of() : respuesta.personas;
    }

    public EmpleadoSmsem alta(String clave, String nombre, String adscripcion) throws Exception {
        ApiRespuesta respuesta = post(ConstantesEstacion.texto("accion.alta"),
                form("clave", clave, "nombre", nombre, "adscripcion", adscripcion));
        return respuesta.persona;
    }

    public EmpleadoSmsem modifica(String claveInterna, String clave, String nombre, String adscripcion) throws Exception {
        ApiRespuesta respuesta = post(ConstantesEstacion.texto("accion.modifica"),
                form("claveInterna", claveInterna, "clave", clave, "nombre", nombre, "adscripcion", adscripcion));
        return respuesta.persona;
    }

    public EmpleadoSmsem imprimir(String claveInterna, byte[] foto, byte[] firma, String usuario) throws Exception {
        ApiRespuesta respuesta = post(ConstantesEstacion.texto("accion.imprime"),
                form("claveInterna", claveInterna,
                        "fotoBase64", Base64.getEncoder().encodeToString(foto),
                        "firmaBase64", Base64.getEncoder().encodeToString(firma),
                        "usuarioEstacion", usuario));
        return respuesta.persona;
    }

    public List<EmpleadoSmsem> seguimiento() throws Exception {
        ApiRespuesta respuesta = post(ConstantesEstacion.texto("accion.seguimiento"), form());
        return respuesta.personas == null ? List.of() : respuesta.personas;
    }

    private ApiRespuesta post(String action, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + action))
                .timeout(Duration.ofMinutes(ConstantesEstacion.entero("servidor.esperaMinutos")))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("SMSEM respondió HTTP " + response.statusCode());
            }
            ApiRespuesta respuesta = json.readValue(response.body(), ApiRespuesta.class);
            if (!respuesta.ok) {
                throw new IllegalStateException(respuesta.mensaje == null ? "SMSEM rechazó la operación" : respuesta.mensaje);
            }
            return respuesta;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(mensajeConexion(ex), ex);
        }
    }

    private String mensajeConexion(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof HttpTimeoutException || cursor instanceof SocketTimeoutException) {
                return "El servidor SMSEM no respondió a tiempo (" + baseUrl + ").";
            }
            if (cursor instanceof UnknownHostException || cursor instanceof ConnectException
                    || cursor instanceof NoRouteToHostException) {
                return "Sin conexión con el servidor SMSEM (" + baseUrl + "). Revise la red.";
            }
            if (cursor instanceof SSLException) {
                return "No se pudo abrir una conexión segura con SMSEM (" + baseUrl + ").";
            }
            if (cursor instanceof JsonProcessingException) {
                return "SMSEM respondió un texto que no se pudo leer.";
            }
            cursor = cursor.getCause();
        }
        if (ex instanceof IOException) {
            return "Sin conexión con el servidor SMSEM (" + baseUrl + "). Revise la red.";
        }
        String mensaje = ex.getMessage();
        return mensaje == null || mensaje.isBlank() ? "No se pudo completar la operación." : mensaje;
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
