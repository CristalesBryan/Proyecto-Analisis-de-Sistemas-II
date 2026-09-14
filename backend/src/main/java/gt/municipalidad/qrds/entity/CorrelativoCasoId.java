package gt.municipalidad.qrds.entity;

import java.io.Serializable;
import java.util.Objects;

public class CorrelativoCasoId implements Serializable {

    private TipoCaso tipoCaso;
    private int anio;

    public CorrelativoCasoId() {
    }

    public CorrelativoCasoId(TipoCaso tipoCaso, int anio) {
        this.tipoCaso = tipoCaso;
        this.anio = anio;
    }

    public TipoCaso getTipoCaso() {
        return tipoCaso;
    }

    public int getAnio() {
        return anio;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CorrelativoCasoId that)) {
            return false;
        }
        return anio == that.anio && tipoCaso == that.tipoCaso;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tipoCaso, anio);
    }
}
