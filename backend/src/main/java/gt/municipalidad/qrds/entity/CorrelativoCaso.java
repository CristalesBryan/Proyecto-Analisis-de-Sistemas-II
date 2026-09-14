package gt.municipalidad.qrds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "correlativos_caso")
@IdClass(CorrelativoCasoId.class)
public class CorrelativoCaso {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_caso", nullable = false, length = 1)
    private TipoCaso tipoCaso;

    @Id
    @Column(nullable = false)
    private int anio;

    @Column(nullable = false)
    private int ultimo;

    public CorrelativoCaso() {
    }

    public CorrelativoCaso(TipoCaso tipoCaso, int anio, int ultimo) {
        this.tipoCaso = tipoCaso;
        this.anio = anio;
        this.ultimo = ultimo;
    }

    public TipoCaso getTipoCaso() {
        return tipoCaso;
    }

    public int getAnio() {
        return anio;
    }

    public int getUltimo() {
        return ultimo;
    }

    public int siguiente() {
        ultimo += 1;
        return ultimo;
    }
}
