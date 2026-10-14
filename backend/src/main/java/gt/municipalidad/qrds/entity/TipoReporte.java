package gt.municipalidad.qrds.entity;

import java.util.Arrays;

public enum TipoReporte {
    RPT_01("RPT-01", "Casos por período", "Listado detallado filtrado por rango de fechas y criterios."),
    RPT_02("RPT-02", "Casos por estado", "Conteo y porcentaje por estado."),
    RPT_03("RPT-03", "Casos por tipo", "Conteo de quejas, reclamos, denuncias y sugerencias."),
    RPT_04("RPT-04", "Casos por área", "Volumen y estados por área o dependencia."),
    RPT_05("RPT-05", "Desempeño por agente", "Asignados, resueltos, cerrados y promedio de días. Es informativo."),
    RPT_06("RPT-06", "Casos vencidos o escalados", "Casos fuera de plazo o escalados."),
    RPT_07("RPT-07", "Tiempos de atención", "Promedio de días hasta la resolución o el cierre."),
    RPT_08("RPT-08", "Casos anulados", "Casos anulados con su justificación.");

    private final String codigo;
    private final String nombre;
    private final String descripcion;

    TipoReporte(String codigo, String nombre, String descripcion) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public static TipoReporte deCodigo(String codigo) {
        if (codigo == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(tipo -> tipo.codigo.equalsIgnoreCase(codigo.trim()))
                .findFirst()
                .orElse(null);
    }
}
