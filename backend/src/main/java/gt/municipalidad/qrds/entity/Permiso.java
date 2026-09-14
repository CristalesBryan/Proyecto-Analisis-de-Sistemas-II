package gt.municipalidad.qrds.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Permisos de funciones internas (RN-CU RN01/RN02 y RN municipales RN01/RN02).
 * La administración dinámica corresponde a CU-12; aquí se publican los
 * permisos vigentes del rol para que el login identifique qué puede hacer el usuario.
 */
public enum Permiso {
    CASOS_VER,
    CASOS_GESTIONAR,
    CASOS_ASIGNAR,
    CASOS_CERRAR,
    DOCUMENTOS_CARGAR,
    DOCUMENTOS_DESCARGAR,
    USUARIOS_ADMINISTRAR,
    ROLES_ADMINISTRAR,
    REPORTES_GENERAR,
    AUDITORIA_CONSULTAR,
    NOTIFICACIONES_GESTIONAR;

    public static Set<Permiso> deRol(Rol rol) {
        return switch (rol) {
            case ADMIN -> EnumSet.allOf(Permiso.class);
            case SUPERVISOR -> EnumSet.of(
                    CASOS_VER,
                    CASOS_GESTIONAR,
                    CASOS_ASIGNAR,
                    CASOS_CERRAR,
                    DOCUMENTOS_CARGAR,
                    DOCUMENTOS_DESCARGAR,
                    REPORTES_GENERAR,
                    NOTIFICACIONES_GESTIONAR);
            case AGENTE -> EnumSet.of(
                    CASOS_VER,
                    CASOS_GESTIONAR,
                    DOCUMENTOS_CARGAR,
                    DOCUMENTOS_DESCARGAR);
            case CIUDADANO -> EnumSet.noneOf(Permiso.class);
        };
    }
}
