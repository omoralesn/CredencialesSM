package mx.org.smsem.credencialessm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiRespuesta {

    public boolean ok;
    public String mensaje;
    public String nombre;
    public int total;
    public int insertados;
    public EmpleadoSmsem persona;
    public List<EmpleadoSmsem> personas;
}
