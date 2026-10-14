package gt.municipalidad.qrds.dto;

import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record ResumenDashboard(
            String periodo,
            LocalDate desde,
            LocalDate hasta,
            String alcance,
            boolean puedeReportes,
            long abiertos,
            long vencidos,
            Long recibidos,
            long resueltos,
            Double diasPromedio,
            Long anulados,
            List<ConteoEstado> porEstado,
            List<CargaAgente> cargaAgentes,
            List<CasoVencido> topVencidos,
            String errorIndicadores,
            String errorEstados,
            String errorCarga,
            String errorVencidos) {
    }

    public record ConteoEstado(String estado, long total) {
    }

    public record CargaAgente(String nombre, long abiertos) {
    }

    public record CasoVencido(long id, String codigo, String estado, LocalDate fechaLimite, long diasAtraso) {
    }
}
