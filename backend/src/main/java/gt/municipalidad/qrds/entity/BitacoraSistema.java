package gt.municipalidad.qrds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "bitacora_sistema")
public class BitacoraSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String dominio;

    @Column(name = "tipo_evento", nullable = false, length = 40)
    private String tipoEvento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "entidad_tipo", length = 40)
    private String entidadTipo;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora = Instant.now();

    @Column(name = "ip_cliente", length = 45)
    private String ipCliente;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    protected BitacoraSistema() {
    }

    public BitacoraSistema(
            String tipoEvento,
            Usuario usuario,
            Long entidadId,
            String descripcion,
            String ipCliente,
            String metadataJson) {
        this("REPORTES", "REPORTE", tipoEvento, usuario, entidadId, descripcion, ipCliente, metadataJson);
    }

    public BitacoraSistema(
            String dominio,
            String entidadTipo,
            String tipoEvento,
            Usuario usuario,
            Long entidadId,
            String descripcion,
            String ipCliente,
            String metadataJson) {
        this.dominio = dominio;
        this.entidadTipo = entidadTipo;
        this.tipoEvento = tipoEvento;
        this.usuario = usuario;
        this.entidadId = entidadId;
        this.descripcion = descripcion;
        this.ipCliente = ipCliente;
        this.metadataJson = metadataJson;
        this.fechaHora = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDominio() {
        return dominio;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getEntidadTipo() {
        return entidadTipo;
    }

    public Long getEntidadId() {
        return entidadId;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getIpCliente() {
        return ipCliente;
    }

    public String getMetadataJson() {
        return metadataJson;
    }
}
