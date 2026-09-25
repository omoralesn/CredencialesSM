package mx.org.smsem.credencialessm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmpleadoSmsem {

    public String claveInterna;
    public String clave;
    public String nombreCompleto;
    public String adscripcion;
    public String idMongoFoto;
    public String idMongoFirma;
    public String cadenaQr;
    public String fechaAlta;
    public String fechaCaptura;
    public String fechaImpresion;
    public String usuarioEstacion;

    public boolean impresa() {
        return fechaImpresion != null && !fechaImpresion.isBlank();
    }
}
