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
@Table(name = "resoluciones_caso")
public class ResolucionCaso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caso_id", nullable = false)
    private Caso caso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_resultado", nullable = false, length = 40)
    private TipoResultado tipoResultado;

    @Column(nullable = false)
    private boolean vigente = true;

    @Column(nullable = false)
    private boolean notificado = false;

    @Column(name = "estado_notificacion", nullable = false, length = 20)
    private String estadoNotificacion = "PENDIENTE";

    @Column(name = "archivo_ruta", length = 500)
    private String archivoRuta;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    @Column(name = "ip_registro", length = 45)
    private String ipRegistro;

    public ResolucionCaso() {
    }

    public ResolucionCaso(
            Caso caso,
            Usuario usuario,
            String comentario,
            TipoResultado tipoResultado,
            String archivoRuta,
            String ipRegistro) {
        this.caso = caso;
        this.usuario = usuario;
        this.comentario = comentario;
        this.tipoResultado = tipoResultado;
        this.archivoRuta = archivoRuta;
        this.ipRegistro = ipRegistro;
        this.creadoEn = Instant.now();
    }

    public void setVigente(boolean vigente) {
        this.vigente = vigente;
    }

    public void registrarNotificacion(boolean enviada) {
        this.notificado = enviada;
        this.estadoNotificacion = enviada ? "ENVIADA" : "OMITIDA";
    }
}
