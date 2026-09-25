package mx.org.smsem.credencialessm;

import java.util.List;

public interface EmpleadoApi {

    String ingresar(String usuario, String password) throws Exception;

    List<EmpleadoSmsem> consultar(String nombre) throws Exception;

    EmpleadoSmsem alta(String clave, String nombre, String adscripcion) throws Exception;

    EmpleadoSmsem modifica(String claveInterna, String clave, String nombre, String adscripcion) throws Exception;

    EmpleadoSmsem imprimir(String claveInterna, byte[] foto, byte[] firma, String usuario) throws Exception;

    List<EmpleadoSmsem> seguimiento() throws Exception;

    int cuenta() throws Exception;

    int cargarPadron(String contenidoTsv) throws Exception;

    static EmpleadoApi crear() {
        String modo = System.getenv().getOrDefault("CREDENCIALES_MOCK", "true");
        if ("false".equalsIgnoreCase(modo) || "0".equals(modo)) {
            return new SmsemEmpleadoClient();
        }
        return new MockEmpleadoClient();
    }
}
