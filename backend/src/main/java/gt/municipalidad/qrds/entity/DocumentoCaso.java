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
@Table(name = "documentos_caso")
public class DocumentoCaso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caso_id", nullable = false)
    private Caso caso;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "ruta_archivo", nullable = false, length = 500)
    private String rutaArchivo;

    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime;

    @Column(name = "tamanio_bytes", nullable = false)
    private long tamanioBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenDocumento origen = OrigenDocumento.CIUDADANO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subido_por_id")
    private Usuario subidoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seguimiento_id")
    private SeguimientoCaso seguimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolucion_id")
    private ResolucionCaso resolucion;

    @Column
    private Boolean activo = true;

    @Column(name = "motivo_baja", columnDefinition = "TEXT")
    private String motivoBaja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "baja_por_id")
    private Usuario bajaPor;

    @Column(name = "baja_en")
    private Instant bajaEn;

    @Column(name = "subido_en", nullable = false)
    private Instant subidoEn = Instant.now();

    public DocumentoCaso() {
    }

    public DocumentoCaso(
            Caso caso,
            String nombreArchivo,
            String rutaArchivo,
            String tipoMime,
            long tamanioBytes,
            OrigenDocumento origen,
            Usuario subidoPor) {
        this.caso = caso;
        this.nombreArchivo = nombreArchivo;
        this.rutaArchivo = rutaArchivo;
        this.tipoMime = tipoMime;
        this.tamanioBytes = tamanioBytes;
        this.origen = origen;
        this.subidoPor = subidoPor;
        this.subidoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Caso getCaso() {
        return caso;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public long getTamanioBytes() {
        return tamanioBytes;
    }

    public OrigenDocumento getOrigen() {
        return origen;
    }

    public Usuario getSubidoPor() {
        return subidoPor;
    }

    public Instant getSubidoEn() {
        return subidoEn;
    }

    public boolean isActivo() {
        return activo == null || activo;
    }

    public String getMotivoBaja() {
        return motivoBaja;
    }

    public void asociarSeguimiento(SeguimientoCaso seguimiento) {
        this.seguimiento = seguimiento;
    }

    public void asociarResolucion(ResolucionCaso resolucion) {
        this.resolucion = resolucion;
    }

    public void darDeBaja(Usuario usuario, String motivo) {
        this.activo = false;
        this.motivoBaja = motivo;
        this.bajaPor = usuario;
        this.bajaEn = Instant.now();
    }
}
