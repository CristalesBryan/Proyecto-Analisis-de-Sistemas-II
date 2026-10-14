package gt.municipalidad.qrds.service;

import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.TipoCaso;
import gt.municipalidad.qrds.entity.TipoReporte;
import gt.municipalidad.qrds.repository.CasoRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReporteConsultaService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);
    private static final int VISTA_MAXIMA = 100;

    private final CasoRepository casoRepository;

    public ReporteConsultaService(CasoRepository casoRepository) {
        this.casoRepository = casoRepository;
    }

    public record TablaReporte(List<String> columnas, List<List<String>> filas, int casos) {
        public List<List<String>> vistaPrevia() {
            if (filas.size() <= VISTA_MAXIMA) {
                return filas;
            }
            return filas.subList(0, VISTA_MAXIMA);
        }
    }

    public record AlcanceConsulta(
            TipoReporte tipo,
            LocalDate desde,
            LocalDate hasta,
            TipoCaso tipoCaso,
            EstadoCaso estado,
            Long areaId,
            Long agenteId,
            boolean incluirAnulados) {
    }

    @Transactional(readOnly = true)
    public long contar(AlcanceConsulta alcance) {
        return casoRepository.count(especificacion(alcance));
    }

    @Transactional(readOnly = true)
    public TablaReporte consultar(AlcanceConsulta alcance) {
        List<Caso> casos = casoRepository.findAll(especificacion(alcance));
        casos.sort(Comparator.comparing(Caso::getFechaRegistro).reversed());
        return switch (alcance.tipo()) {
            case RPT_02 -> porEstado(casos);
            case RPT_03 -> porTipo(casos);
            case RPT_04 -> porArea(casos);
            case RPT_05 -> porAgente(casos);
            case RPT_07 -> tiempos(casos);
            default -> detalle(casos);
        };
    }

    private Specification<Caso> especificacion(AlcanceConsulta alcance) {
        return (root, query, builder) -> {
            List<Predicate> filtros = new ArrayList<>();
            Instant inicio = alcance.desde().atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant fin = alcance.hasta().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            filtros.add(builder.greaterThanOrEqualTo(root.get("fechaRegistro"), inicio));
            filtros.add(builder.lessThan(root.get("fechaRegistro"), fin));
            if (alcance.areaId() != null) {
                filtros.add(builder.equal(root.get("areaDependencia").get("id"), alcance.areaId()));
            }
            if (alcance.tipoCaso() != null) {
                filtros.add(builder.equal(root.get("tipoCaso"), alcance.tipoCaso()));
            }
            if (alcance.agenteId() != null) {
                filtros.add(builder.equal(root.get("agenteAsignado").get("id"), alcance.agenteId()));
            }
            if (alcance.tipo() == TipoReporte.RPT_08) {
                filtros.add(builder.equal(root.get("estado"), EstadoCaso.ANULADO));
            } else if (alcance.estado() != null) {
                filtros.add(builder.equal(root.get("estado"), alcance.estado()));
            } else if (!alcance.incluirAnulados()) {
                filtros.add(builder.notEqual(root.get("estado"), EstadoCaso.ANULADO));
            }
            if (alcance.tipo() == TipoReporte.RPT_06) {
                Predicate vencido = builder.and(
                        builder.isNotNull(root.get("fechaLimiteRespuesta")),
                        builder.lessThan(root.get("fechaLimiteRespuesta"), LocalDate.now(ZoneOffset.UTC)),
                        builder.not(root.get("estado").in(EstadoCaso.RESUELTO, EstadoCaso.CERRADO, EstadoCaso.ANULADO)));
                filtros.add(builder.or(builder.isTrue(root.get("escalado")), vencido));
            }
            return builder.and(filtros.toArray(Predicate[]::new));
        };
    }

    private TablaReporte detalle(List<Caso> casos) {
        List<String> columnas = List.of(
                "Código", "Tipo", "Estado", "Área", "Agente", "Ciudadano", "Correo", "Registro", "Límite", "Escalado");
        List<List<String>> filas = new ArrayList<>();
        for (Caso caso : casos) {
            boolean anonimo = caso.isEsAnonimo();
            filas.add(List.of(
                    texto(caso.getCodigoSeguimiento()),
                    caso.getTipoCaso().getEtiqueta(),
                    caso.getEstado().name(),
                    caso.getAreaDependencia().getNombre(),
                    caso.getAgenteAsignado() == null ? "Sin asignar" : caso.getAgenteAsignado().getNombre(),
                    anonimo ? "Anónimo" : texto(caso.getNombreCiudadano()),
                    anonimo ? "" : enmascararCorreo(caso.getEmailCiudadano()),
                    FECHA.format(caso.getFechaRegistro()),
                    caso.getFechaLimiteRespuesta() == null ? "" : caso.getFechaLimiteRespuesta().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    caso.isEscalado() ? "Sí" : "No"));
        }
        return new TablaReporte(columnas, filas, casos.size());
    }

    private TablaReporte porEstado(List<Caso> casos) {
        Map<EstadoCaso, Long> conteo = new EnumMap<>(EstadoCaso.class);
        for (Caso caso : casos) {
            conteo.merge(caso.getEstado(), 1L, Long::sum);
        }
        long total = casos.size();
        List<List<String>> filas = new ArrayList<>();
        for (EstadoCaso estado : EstadoCaso.values()) {
            long cantidad = conteo.getOrDefault(estado, 0L);
            if (cantidad == 0) {
                continue;
            }
            filas.add(List.of(estado.name(), Long.toString(cantidad), porcentaje(cantidad, total)));
        }
        return new TablaReporte(List.of("Estado", "Cantidad", "Porcentaje"), filas, casos.size());
    }

    private TablaReporte porTipo(List<Caso> casos) {
        Map<TipoCaso, Long> conteo = new EnumMap<>(TipoCaso.class);
        for (Caso caso : casos) {
            conteo.merge(caso.getTipoCaso(), 1L, Long::sum);
        }
        List<List<String>> filas = new ArrayList<>();
        for (TipoCaso tipo : TipoCaso.values()) {
            filas.add(List.of(tipo.getEtiqueta(), Long.toString(conteo.getOrDefault(tipo, 0L))));
        }
        return new TablaReporte(List.of("Tipo", "Cantidad"), filas, casos.size());
    }

    private TablaReporte porArea(List<Caso> casos) {
        Map<String, Map<EstadoCaso, Long>> areas = new LinkedHashMap<>();
        for (Caso caso : casos) {
            areas.computeIfAbsent(caso.getAreaDependencia().getNombre(), nombre -> new EnumMap<>(EstadoCaso.class))
                    .merge(caso.getEstado(), 1L, Long::sum);
        }
        List<List<String>> filas = new ArrayList<>();
        for (var entrada : areas.entrySet()) {
            Map<EstadoCaso, Long> estados = entrada.getValue();
            long total = estados.values().stream().mapToLong(Long::longValue).sum();
            long enProceso = estados.getOrDefault(EstadoCaso.EN_PROCESO, 0L)
                    + estados.getOrDefault(EstadoCaso.EN_REVISION, 0L);
            filas.add(List.of(
                    entrada.getKey(),
                    Long.toString(total),
                    cantidad(estados, EstadoCaso.RECIBIDO),
                    Long.toString(enProceso),
                    cantidad(estados, EstadoCaso.RESUELTO),
                    cantidad(estados, EstadoCaso.CERRADO),
                    cantidad(estados, EstadoCaso.ANULADO)));
        }
        return new TablaReporte(
                List.of("Área", "Total", "Recibidos", "En proceso", "Resueltos", "Cerrados", "Anulados"),
                filas,
                casos.size());
    }

    private TablaReporte porAgente(List<Caso> casos) {
        Map<String, List<Caso>> porAgente = new LinkedHashMap<>();
        for (Caso caso : casos) {
            String nombre = caso.getAgenteAsignado() == null ? "Sin asignar" : caso.getAgenteAsignado().getNombre();
            porAgente.computeIfAbsent(nombre, clave -> new ArrayList<>()).add(caso);
        }
        List<List<String>> filas = new ArrayList<>();
        for (var entrada : porAgente.entrySet()) {
            List<Caso> grupo = entrada.getValue();
            long resueltos = grupo.stream().filter(caso -> caso.getEstado() == EstadoCaso.RESUELTO).count();
            long cerrados = grupo.stream().filter(caso -> caso.getEstado() == EstadoCaso.CERRADO).count();
            filas.add(List.of(
                    entrada.getKey(),
                    Integer.toString(grupo.size()),
                    Long.toString(resueltos),
                    Long.toString(cerrados),
                    promedio(grupo)));
        }
        return new TablaReporte(
                List.of("Agente", "Asignados", "Resueltos", "Cerrados", "Promedio días"), filas, casos.size());
    }

    private TablaReporte tiempos(List<Caso> casos) {
        List<Caso> resueltos = casos.stream().filter(caso -> caso.getEstado() == EstadoCaso.RESUELTO).toList();
        List<Caso> cerrados = casos.stream().filter(caso -> caso.getEstado() == EstadoCaso.CERRADO).toList();
        return new TablaReporte(
                List.of("Indicador", "Casos", "Promedio días"),
                List.of(
                        List.of("Hasta resuelto", Integer.toString(resueltos.size()), promedio(resueltos)),
                        List.of("Hasta cerrado", Integer.toString(cerrados.size()), promedio(cerrados))),
                casos.size());
    }

    private String promedio(List<Caso> casos) {
        double suma = 0;
        int considerados = 0;
        for (Caso caso : casos) {
            Long dias = diasAtencion(caso);
            if (dias == null) {
                continue;
            }
            suma += dias;
            considerados++;
        }
        if (considerados == 0) {
            return "0";
        }
        return String.format(java.util.Locale.ROOT, "%.1f", suma / considerados);
    }

    private Long diasAtencion(Caso caso) {
        Instant fin = caso.getFechaResolucion();
        if (fin == null && (caso.getEstado() == EstadoCaso.RESUELTO || caso.getEstado() == EstadoCaso.CERRADO)) {
            fin = caso.getFechaUltimaActualizacion();
        }
        if (fin == null) {
            return null;
        }
        return Math.max(0, Duration.between(caso.getFechaRegistro(), fin).toDays());
    }

    private String cantidad(Map<EstadoCaso, Long> estados, EstadoCaso estado) {
        return Long.toString(estados.getOrDefault(estado, 0L));
    }

    private String porcentaje(long cantidad, long total) {
        if (total == 0) {
            return "0%";
        }
        return String.format(java.util.Locale.ROOT, "%.1f%%", (cantidad * 100.0) / total);
    }

    static String enmascararCorreo(String correo) {
        if (correo == null || correo.isBlank() || !correo.contains("@")) {
            return "";
        }
        int arroba = correo.indexOf('@');
        if (arroba <= 1) {
            return "***" + correo.substring(arroba);
        }
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
