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
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "seguimientos_caso")
public class SeguimientoCaso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caso_id", nullable = false)
    private Caso caso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoSeguimiento tipo;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "porcentaje_avance")
    private Integer porcentajeAvance;

    @Column(nullable = false)
    private boolean notificado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seguimiento_padre_id")
    private SeguimientoCaso seguimientoPadre;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    @Column(name = "ruta_archivo", length = 500)
    private String rutaArchivo;

    @Column(name = "tipo_mime", length = 100)
    private String tipoMime;

    @Column(name = "tamanio_bytes")
    private Long tamanioBytes;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    public SeguimientoCaso() {
    }

    public SeguimientoCaso(
            Caso caso,
            Usuario usuario,
            TipoSeguimiento tipo,
            String titulo,
            String descripcion,
            Integer porcentajeAvance) {
        this(caso, usuario, tipo, titulo, descripcion, porcentajeAvance, null, false, null, null, null, null);
    }

    public SeguimientoCaso(
            Caso caso,
            Usuario usuario,
            TipoSeguimiento tipo,
            String titulo,
            String descripcion,
            Integer porcentajeAvance,
            SeguimientoCaso seguimientoPadre,
            boolean notificado,
            String nombreArchivo,
            String rutaArchivo,
            String tipoMime,
            Long tamanioBytes) {
        this.caso = caso;
        this.usuario = usuario;
        this.tipo = tipo;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.porcentajeAvance = porcentajeAvance;
        this.seguimientoPadre = seguimientoPadre;
        this.notificado = notificado;
        this.nombreArchivo = nombreArchivo;
        this.rutaArchivo = rutaArchivo;
        this.tipoMime = tipoMime;
        this.tamanioBytes = tamanioBytes;
        this.creadoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Caso getCaso() {
        return caso;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public TipoSeguimiento getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Integer getPorcentajeAvance() {
        return porcentajeAvance;
    }

    public boolean isNotificado() {
        return notificado;
    }

    public SeguimientoCaso getSeguimientoPadre() {
        return seguimientoPadre;
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

    public Long getTamanioBytes() {
        return tamanioBytes;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
