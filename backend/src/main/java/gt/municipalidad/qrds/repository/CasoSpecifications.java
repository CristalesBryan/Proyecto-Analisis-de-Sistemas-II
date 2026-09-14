package gt.municipalidad.qrds.repository;

import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.TipoCaso;
import gt.municipalidad.qrds.entity.Usuario;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.springframework.data.jpa.domain.Specification;

public final class CasoSpecifications {

    private CasoSpecifications() {
    }

    public static Specification<Caso> visiblePara(Usuario usuario) {
        return (root, query, builder) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("areaDependencia", JoinType.LEFT);
                root.fetch("agenteAsignado", JoinType.LEFT);
                query.distinct(true);
            }
            return switch (usuario.getRol()) {
                case ADMIN -> builder.conjunction();
                case SUPERVISOR -> {
                    if (usuario.getAreaDependencia() == null) {
                        yield builder.disjunction();
                    }
                    yield builder.equal(root.get("areaDependencia"), usuario.getAreaDependencia());
                }
                case AGENTE -> {
                    var asignados = builder.equal(root.get("agenteAsignado"), usuario);
                    if (usuario.getAreaDependencia() == null) {
                        yield asignados;
                    }
                    yield builder.or(
                            asignados,
                            builder.and(
                                    builder.isNull(root.get("agenteAsignado")),
                                    builder.equal(root.get("areaDependencia"), usuario.getAreaDependencia())));
                }
                case CIUDADANO -> builder.disjunction();
            };
        };
    }

    public static Specification<Caso> conFiltros(
            String estado,
            String tipo,
            String area,
            String codigo,
            LocalDate desde,
            LocalDate hasta,
            Boolean sinAsignar) {
        Specification<Caso> spec = (root, query, builder) -> builder.conjunction();
        if (estado != null && !estado.isBlank()) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("estado"), EstadoCaso.valueOf(estado)));
        }
        if (tipo != null && !tipo.isBlank()) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("tipoCaso"), TipoCaso.valueOf(tipo)));
        }
        if (area != null && !area.isBlank()) {
            spec = spec.and((root, query, builder) ->
                    builder.equal(root.get("areaDependencia").get("codigo"), area.trim().toUpperCase()));
        }
        if (codigo != null && !codigo.isBlank()) {
            spec = spec.and((root, query, builder) -> builder.like(
                    builder.upper(root.get("codigoSeguimiento")),
                    "%" + codigo.trim().toUpperCase() + "%"));
        }
        if (Boolean.TRUE.equals(sinAsignar)) {
            spec = spec.and((root, query, builder) -> builder.isNull(root.get("agenteAsignado")));
        }
        if (desde != null) {
            Instant inicio = desde.atStartOfDay().toInstant(ZoneOffset.UTC);
            spec = spec.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("fechaRegistro"), inicio));
        }
        if (hasta != null) {
            Instant fin = hasta.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
            spec = spec.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("fechaRegistro"), fin));
        }
        return spec;
    }
}
