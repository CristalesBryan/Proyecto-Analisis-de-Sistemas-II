package gt.municipalidad.qrds.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReporteJobWorker {

    private final ReporteService reportes;

    public ReporteJobWorker(@Lazy ReporteService reportes) {
        this.reportes = reportes;
    }

    @Async("reporteExecutor")
    public void procesar(Long reporteId) {
        reportes.ejecutarJob(reporteId);
    }
}
