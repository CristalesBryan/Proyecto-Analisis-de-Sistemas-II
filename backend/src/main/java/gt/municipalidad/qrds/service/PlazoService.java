package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.CasoDtos.PlazoCaso;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class PlazoService {

    public LocalDate fechaLimite(LocalDate inicio, int diasHabiles) {
        LocalDate fecha = inicio;
        int agregados = 0;
        while (agregados < diasHabiles) {
            fecha = fecha.plusDays(1);
            if (esHabil(fecha)) {
                agregados += 1;
            }
        }
        return fecha;
    }

    public PlazoCaso de(LocalDate limite) {
        if (limite == null) {
            return new PlazoCaso(null, "gris", false);
        }
        LocalDate hoy = LocalDate.now();
        if (hoy.isAfter(limite)) {
            return new PlazoCaso(-diasHabilesEntre(limite, hoy), "rojo", true);
        }
        int restantes = diasHabilesEntre(hoy, limite);
        return new PlazoCaso(restantes, restantes >= 4 ? "verde" : "amarillo", false);
    }

    private int diasHabilesEntre(LocalDate desde, LocalDate hasta) {
        int total = 0;
        LocalDate fecha = desde;
        while (fecha.isBefore(hasta)) {
            fecha = fecha.plusDays(1);
            if (esHabil(fecha)) {
                total += 1;
            }
        }
        return total;
    }

    private boolean esHabil(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY;
    }
}
