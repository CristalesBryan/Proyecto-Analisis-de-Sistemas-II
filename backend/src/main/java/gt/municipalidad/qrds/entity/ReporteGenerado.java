package gt.municipalidad.qrds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "reportes_generados")
public class ReporteGenerado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_reporte", nullable = false, length = 20)
    private String tipoReporte;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitado_por", nullable = false)
    private Usuario solicitadoPor;

    @Column(name = "filtros_json", columnDefinition = "TEXT")
    private String filtrosJson;

    @Column(name = "filtros_hash", length = 64)
    private String filtrosHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FormatoReporte formato;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReporte estado = EstadoReporte.PENDIENTE;

    @Column(name = "ruta_archivo", length = 500)
    private String rutaArchivo;

    @Column(name = "cantidad_filas")
    private Integer cantidadFilas;

    @Column(name = "error_mensaje", columnDefinition = "TEXT")
    private String errorMensaje;

    @Column(name = "vista_previa", columnDefinition = "TEXT")
    private String vistaPrevia;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    @Column(name = "finalizado_en")
    private Instant finalizadoEn;

    @Column(name = "expira_en")
    private Instant expiraEn;

    public Long getId() {
        return id;
    }

    public String getTipoReporte() {
        return tipoReporte;
    }

    public void setTipoReporte(String tipoReporte) {
        this.tipoReporte = tipoReporte;
    }

    public Usuario getSolicitadoPor() {
        return solicitadoPor;
    }

    public void setSolicitadoPor(Usuario solicitadoPor) {
        this.solicitadoPor = solicitadoPor;
    }

    public String getFiltrosJson() {
        return filtrosJson;
    }

    public void setFiltrosJson(String filtrosJson) {
        this.filtrosJson = filtrosJson;
    }

    public String getFiltrosHash() {
        return filtrosHash;
    }

    public void setFiltrosHash(String filtrosHash) {
        this.filtrosHash = filtrosHash;
    }

    public FormatoReporte getFormato() {
        return formato;
    }

    public void setFormato(FormatoReporte formato) {
        this.formato = formato;
    }

    public EstadoReporte getEstado() {
        return estado;
    }

    public void setEstado(EstadoReporte estado) {
        this.estado = estado;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    public Integer getCantidadFilas() {
        return cantidadFilas;
    }

    public void setCantidadFilas(Integer cantidadFilas) {
        this.cantidadFilas = cantidadFilas;
    }

    public String getErrorMensaje() {
        return errorMensaje;
    }

    public void setErrorMensaje(String errorMensaje) {
        this.errorMensaje = errorMensaje;
    }

    public String getVistaPrevia() {
        return vistaPrevia;
    }

    public void setVistaPrevia(String vistaPrevia) {
        this.vistaPrevia = vistaPrevia;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Instant getFinalizadoEn() {
        return finalizadoEn;
    }

    public void setFinalizadoEn(Instant finalizadoEn) {
        this.finalizadoEn = finalizadoEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(Instant expiraEn) {
        this.expiraEn = expiraEn;
    }
}
