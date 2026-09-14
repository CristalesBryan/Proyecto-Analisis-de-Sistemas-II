package gt.municipalidad.qrds.entity;

import java.util.List;

public enum EstadoCaso {
    RECIBIDO,
    EN_REVISION,
    EN_PROCESO,
    RESUELTO,
    CERRADO,
    ANULADO;

    public List<EstadoCaso> transiciones() {
        return switch (this) {
            case RECIBIDO -> List.of(EN_REVISION, ANULADO);
            case EN_REVISION -> List.of(EN_PROCESO, ANULADO);
            case EN_PROCESO -> List.of(RESUELTO, EN_REVISION);
            case RESUELTO -> List.of(EN_PROCESO, CERRADO);
            case CERRADO, ANULADO -> List.of();
        };
    }

    public boolean esFinal() {
        return this == CERRADO || this == ANULADO;
    }
}
