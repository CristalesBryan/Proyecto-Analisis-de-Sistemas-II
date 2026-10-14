package gt.municipalidad.qrds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "parametros_sistema")
public class ParametroSistema {

    @Id
    @Column(length = 60)
    private String clave;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String valor;

    @Column(nullable = false, length = 30)
    private String grupo;

    @Column(nullable = false, length = 120)
    private String etiqueta;

    @Column(nullable = false, length = 240)
    private String ayuda;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false)
    private long revision;

    @Column(name = "actualizado_en")
    private Instant actualizadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actualizado_por")
    private Usuario actualizadoPor;

    protected ParametroSistema() {
    }

    public ParametroSistema(String clave, String valor, String grupo, String etiqueta, String ayuda, String tipo) {
        this.clave = clave;
        this.valor = valor;
        this.grupo = grupo;
        this.etiqueta = etiqueta;
        this.ayuda = ayuda;
        this.tipo = tipo;
        this.revision = 0;
    }

    public String getClave() {
        return clave;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getGrupo() {
        return grupo;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getAyuda() {
        return ayuda;
    }

    public String getTipo() {
        return tipo;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(Instant actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }

    public Usuario getActualizadoPor() {
        return actualizadoPor;
    }

    public void setActualizadoPor(Usuario actualizadoPor) {
        this.actualizadoPor = actualizadoPor;
    }
}
