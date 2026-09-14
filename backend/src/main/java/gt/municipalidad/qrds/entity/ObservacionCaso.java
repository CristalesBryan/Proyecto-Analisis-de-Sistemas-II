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
@Table(name = "observaciones_caso")
public class ObservacionCaso {

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
    private String texto;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    public ObservacionCaso() {
    }

    public ObservacionCaso(Caso caso, Usuario usuario, String texto) {
        this.caso = caso;
        this.usuario = usuario;
        this.texto = texto;
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

    public String getTexto() {
        return texto;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
