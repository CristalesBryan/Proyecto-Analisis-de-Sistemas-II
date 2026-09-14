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
@Table(name = "bitacora_casos")
public class BitacoraCaso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caso_id")
    private Caso caso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 40)
    private TipoEventoCaso tipoEvento;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora = Instant.now();

    @Column(name = "ip_cliente", length = 45)
    private String ipCliente;

    @Column(name = "estado_anterior", length = 20)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", length = 20)
    private String estadoNuevo;

    public BitacoraCaso() {
    }

    public BitacoraCaso(Caso caso, Usuario usuario, TipoEventoCaso tipoEvento, String descripcion, String ipCliente) {
        this(caso, usuario, tipoEvento, descripcion, ipCliente, null, null);
    }

    public BitacoraCaso(
            Caso caso,
            Usuario usuario,
            TipoEventoCaso tipoEvento,
            String descripcion,
            String ipCliente,
            EstadoCaso estadoAnterior,
            EstadoCaso estadoNuevo) {
        this.caso = caso;
        this.usuario = usuario;
        this.tipoEvento = tipoEvento;
        this.descripcion = descripcion;
        this.fechaHora = Instant.now();
        this.ipCliente = ipCliente;
        this.estadoAnterior = estadoAnterior == null ? null : estadoAnterior.name();
        this.estadoNuevo = estadoNuevo == null ? null : estadoNuevo.name();
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

    public TipoEventoCaso getTipoEvento() {
        return tipoEvento;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public String getIpCliente() {
        return ipCliente;
    }

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public String getEstadoNuevo() {
        return estadoNuevo;
    }
}
