package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.dto.DashboardDtos.CargaAgente;
import gt.municipalidad.qrds.dto.DashboardDtos.CasoVencido;
import gt.municipalidad.qrds.dto.DashboardDtos.ConteoEstado;
import gt.municipalidad.qrds.dto.DashboardDtos.ResumenDashboard;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.CasoSpecifications;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final String ERROR = "No fue posible calcular este indicador.";

    private final CasoRepository casoRepository;
    private final BitacoraSistemaService bitacora;

    public DashboardService(CasoRepository casoRepository, BitacoraSistemaService bitacora) {
        this.casoRepository = casoRepository;
        this.bitacora = bitacora;
    }

    @Transactional(readOnly = true)
    public ResumenDashboard consultar(Usuario usuario, String periodo, String ip) {
        if (usuario.getRol() != Rol.ADMIN && usuario.getRol() != Rol.SUPERVISOR && usuario.getRol() != Rol.AGENTE) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene autorización para realizar esta operación.");
        }
        Rango rango = rango(periodo);
        boolean agente = usuario.getRol() == Rol.AGENTE;
        Specification<Caso> alcance = alcance(usuario);
        LocalDate hoy = LocalDate.now(ZoneOffset.UTC);

        long abiertos = 0;
        long vencidos = 0;
        Long recibidos = agente ? null : 0L;
        long resueltos = 0;
        Double diasPromedio = null;
        Long anulados = agente ? null : 0L;
        String errorIndicadores = null;
        try {
            abiertos = casoRepository.count(alcance.and(CasoSpecifications.abiertos()));
            vencidos = casoRepository.count(alcance.and(CasoSpecifications.vencidosAbiertos(hoy)));
            if (!agente) {
                recibidos = casoRepository.count(alcance.and(entre("fechaRegistro", rango)));
            }
            List<Caso> resueltosPeriodo = casoRepository.findAll(alcance.and(entre("fechaResolucion", rango)));
            resueltos = resueltosPeriodo.size();
            diasPromedio = promedio(resueltosPeriodo);
            if (!agente) {
                anulados = casoRepository.count(alcance
                        .and((root, query, builder) -> builder.equal(root.get("estado"), EstadoCaso.ANULADO))
                        .and(entre("fechaUltimaActualizacion", rango)));
            }
        } catch (RuntimeException ex) {
            errorIndicadores = ERROR;
        }

        List<ConteoEstado> porEstado = List.of();
        String errorEstados = null;
        try {
            List<ConteoEstado> conteos = new ArrayList<>();
            for (EstadoCaso estado : EstadoCaso.values()) {
                long total = casoRepository.count(alcance.and(
                        (root, query, builder) -> builder.equal(root.get("estado"), estado)));
                conteos.add(new ConteoEstado(estado.name(), total));
            }
            porEstado = conteos;
        } catch (RuntimeException ex) {
            errorEstados = ERROR;
        }

        List<CargaAgente> carga = agente ? null : List.of();
        String errorCarga = null;
        if (!agente) {
            try {
                Map<String, Long> totales = new LinkedHashMap<>();
                for (Caso caso : casoRepository.findAll(alcance.and(CasoSpecifications.abiertos()))) {
                    if (caso.getAgenteAsignado() == null) {
                        continue;
                    }
                    totales.merge(caso.getAgenteAsignado().getNombre(), 1L, Long::sum);
                }
                carga = totales.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .map(entrada -> new CargaAgente(entrada.getKey(), entrada.getValue()))
                        .toList();
            } catch (RuntimeException ex) {
                errorCarga = ERROR;
                carga = List.of();
            }
        }

        List<CasoVencido> top = List.of();
        String errorVencidos = null;
        try {
            top = casoRepository.findAll(alcance.and(CasoSpecifications.vencidosAbiertos(hoy))).stream()
                    .filter(caso -> caso.getFechaLimiteRespuesta() != null
                            && caso.getFechaLimiteRespuesta().isBefore(hoy))
                    .sorted(Comparator.comparing(Caso::getFechaLimiteRespuesta))
                    .limit(5)
                    .map(caso -> new CasoVencido(
                            caso.getId(),
                            caso.getCodigoSeguimiento(),
                            caso.getEstado().name(),
                            caso.getFechaLimiteRespuesta(),
                            ChronoUnit.DAYS.between(caso.getFechaLimiteRespuesta(), hoy)))
                    .toList();
        } catch (RuntimeException ex) {
            errorVencidos = ERROR;
        }

        String alcanceNombre = switch (usuario.getRol()) {
            case ADMIN -> "INSTITUCION";
            case SUPERVISOR -> "AREA";
            default -> "ASIGNADOS";
        };
        bitacora.registrarNueva(
                "AUDITORIA",
                "DASHBOARD",
                "DASHBOARD_CONSULTADO",
                usuario,
                null,
                "Consulta del tablero. Período " + rango.codigo() + ". Alcance " + alcanceNombre + ".",
                ip,
                null);
        return new ResumenDashboard(
                rango.codigo(),
                rango.desdeFecha(),
                rango.hastaFecha(),
                alcanceNombre,
                !agente,
                abiertos,
                vencidos,
                recibidos,
                resueltos,
                diasPromedio,
                anulados,
                porEstado,
                carga,
                top,
                errorIndicadores,
                errorEstados,
                errorCarga,
                errorVencidos);
    }

    private Specification<Caso> alcance(Usuario usuario) {
        return switch (usuario.getRol()) {
            case ADMIN -> (root, query, builder) -> builder.conjunction();
            case SUPERVISOR -> {
                if (usuario.getAreaDependencia() == null) {
                    yield (root, query, builder) -> builder.disjunction();
                }
                yield (root, query, builder) -> builder.equal(root.get("areaDependencia"), usuario.getAreaDependencia());
            }
            case AGENTE -> CasoSpecifications.asignadosA(usuario);
            default -> (root, query, builder) -> builder.disjunction();
        };
    }

    private Specification<Caso> entre(String campo, Rango rango) {
        return (root, query, builder) -> builder.and(
                builder.greaterThanOrEqualTo(root.get(campo), rango.desde()),
                builder.lessThanOrEqualTo(root.get(campo), rango.hasta()));
    }

    private Double promedio(List<Caso> casos) {
        if (casos.isEmpty()) {
            return null;
        }
        double segundos = 0;
        int utiles = 0;
        for (Caso caso : casos) {
            if (caso.getFechaResolucion() == null || caso.getFechaRegistro() == null) {
                continue;
            }
            segundos += Duration.between(caso.getFechaRegistro(), caso.getFechaResolucion()).getSeconds();
            utiles++;
        }
        if (utiles == 0) {
            return null;
        }
        return Math.round((segundos / utiles / 86400.0) * 10.0) / 10.0;
    }

    private Rango rango(String periodo) {
        String codigo = periodo == null || periodo.isBlank() ? "30d" : periodo.trim().toLowerCase();
        LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
        LocalDate inicio = switch (codigo) {
            case "hoy" -> hoy;
            case "7d" -> hoy.minusDays(6);
            case "30d" -> hoy.minusDays(29);
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PERIODO_INVALIDO",
                    "El período debe ser hoy, 7 días o 30 días.");
        };
        return new Rango(
                codigo,
                inicio,
                hoy,
                inicio.atStartOfDay().toInstant(ZoneOffset.UTC),
                hoy.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC));
    }

    private record Rango(String codigo, LocalDate desdeFecha, LocalDate hastaFecha, Instant desde, Instant hasta) {
    }
}
