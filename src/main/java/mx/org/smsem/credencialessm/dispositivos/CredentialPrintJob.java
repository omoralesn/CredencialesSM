package mx.org.smsem.credencialessm.dispositivos;

import java.nio.file.Path;

/**
 * Trabajo de impresión de credencial (cara CR80) con datos e imágenes locales.
 */
public final class CredentialPrintJob {

    private final String jobId;
    private final String solicitudUuid;
    private final String capturaUuid;
    private final String folio;
    private final String numEmpleado;
    private final String fullName;
    private final String curp;
    private final String tipoCredencial;
    private final Path photoPath;
    private final Path signaturePath;

    public CredentialPrintJob(String jobId,
                              String solicitudUuid,
                              String capturaUuid,
                              String folio,
                              String numEmpleado,
                              String fullName,
                              String curp,
                              String tipoCredencial,
                              Path photoPath,
                              Path signaturePath) {
        this.jobId = jobId;
        this.solicitudUuid = solicitudUuid;
        this.capturaUuid = capturaUuid;
        this.folio = folio;
        this.numEmpleado = numEmpleado;
        this.fullName = fullName;
        this.curp = curp;
        this.tipoCredencial = tipoCredencial;
        this.photoPath = photoPath;
        this.signaturePath = signaturePath;
    }

    public String getJobId() {
        return jobId;
    }

    public String getSolicitudUuid() {
        return solicitudUuid;
    }

    public String getCapturaUuid() {
        return capturaUuid;
    }

    public String getFolio() {
        return folio;
    }

    public String getNumEmpleado() {
        return numEmpleado;
    }

    public String getFullName() {
        return fullName;
    }

    public String getCurp() {
        return curp;
    }

    public String getTipoCredencial() {
        return tipoCredencial;
    }

    public Path getPhotoPath() {
        return photoPath;
    }

    public Path getSignaturePath() {
        return signaturePath;
    }
}
