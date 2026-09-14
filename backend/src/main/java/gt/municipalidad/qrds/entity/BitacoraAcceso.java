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
@Table(name = "bitacora_accesos")
public class BitacoraAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 30)
    private TipoEventoAcceso tipoEvento;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora = Instant.now();

    @Column(name = "ip_cliente", length = 45)
    private String ipCliente;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false, length = 30)
    private String resultado = "OK";

    @Column(columnDefinition = "TEXT")
    private String detalle;

    public BitacoraAcceso() {
    }

    public BitacoraAcceso(
            Usuario usuario,
            TipoEventoAcceso tipoEvento,
            String ipCliente,
            String userAgent,
            String resultado,
            String detalle) {
        this.usuario = usuario;
        this.tipoEvento = tipoEvento;
        this.fechaHora = Instant.now();
        this.ipCliente = ipCliente;
        this.userAgent = userAgent;
        this.resultado = resultado;
        this.detalle = detalle;
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public TipoEventoAcceso getTipoEvento() {
        return tipoEvento;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public String getIpCliente() {
        return ipCliente;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getResultado() {
        return resultado;
    }

    public String getDetalle() {
        return detalle;
    }
}
