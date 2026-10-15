package gt.municipalidad.qrds.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.dto.ParametroDtos.ActualizarAreaRequest;
import gt.municipalidad.qrds.dto.ParametroDtos.ActualizarParametroRequest;
import gt.municipalidad.qrds.dto.ParametroDtos.AreaExportada;
import gt.municipalidad.qrds.dto.ParametroDtos.AreaGuardada;
import gt.municipalidad.qrds.dto.ParametroDtos.AreaParametro;
import gt.municipalidad.qrds.dto.ParametroDtos.Configuracion;
import gt.municipalidad.qrds.dto.ParametroDtos.CrearAreaRequest;
import gt.municipalidad.qrds.dto.ParametroDtos.CrearFeriadoRequest;
import gt.municipalidad.qrds.dto.ParametroDtos.Exportacion;
import gt.municipalidad.qrds.dto.ParametroDtos.FeriadoParametro;
import gt.municipalidad.qrds.dto.ParametroDtos.GrupoParametros;
import gt.municipalidad.qrds.dto.ParametroDtos.ImportacionRespuesta;
import gt.municipalidad.qrds.dto.ParametroDtos.InstitucionPublica;
import gt.municipalidad.qrds.dto.ParametroDtos.ParametroExportado;
import gt.municipalidad.qrds.dto.ParametroDtos.ParametroGuardado;
import gt.municipalidad.qrds.dto.ParametroDtos.ParametroItem;
import gt.municipalidad.qrds.dto.ParametroDtos.PlazoPublico;
import gt.municipalidad.qrds.dto.ParametroDtos.RecalculoRespuesta;
import gt.municipalidad.qrds.dto.ParametroDtos.RestauracionRespuesta;
import gt.municipalidad.qrds.entity.AreaDependencia;
import gt.municipalidad.qrds.entity.Caso;
import gt.municipalidad.qrds.entity.DiaNoLaborable;
import gt.municipalidad.qrds.entity.EstadoCaso;
import gt.municipalidad.qrds.entity.ParametroSistema;
import gt.municipalidad.qrds.entity.Rol;
import gt.municipalidad.qrds.entity.TipoCaso;
import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.exception.ApiException;
import gt.municipalidad.qrds.repository.AreaDependenciaRepository;
import gt.municipalidad.qrds.repository.CasoRepository;
import gt.municipalidad.qrds.repository.DiaNoLaborableRepository;
import gt.municipalidad.qrds.repository.ParametroSistemaRepository;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Order(20)
public class ParametroService implements CommandLineRunner {

    static final String MENSAJE_VERSION =
            "El valor vigente cambió. Revise el parámetro antes de confirmar de nuevo.";

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CODIGO_AREA = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");
    private static final Set<String> EXTENSIONES = Set.of("pdf", "jpg", "jpeg", "png", "docx");
    private static final List<EstadoCaso> CERRADOS = List.of(EstadoCaso.CERRADO, EstadoCaso.ANULADO);

    private static final List<Def> DEFECTOS = List.of(
            new Def("plazo.Q", "15", "plazos", "Queja", "Días hábiles para un caso nuevo de queja.", "ENTERO"),
            new Def("plazo.R", "20", "plazos", "Reclamo", "Días hábiles para un caso nuevo de reclamo.", "ENTERO"),
            new Def("plazo.D", "30", "plazos", "Denuncia", "Días hábiles para un caso nuevo de denuncia.", "ENTERO"),
            new Def("plazo.S", "30", "plazos", "Sugerencia", "Días hábiles para un caso nuevo de sugerencia.", "ENTERO"),
            new Def("archivo.maxMb", "5", "archivos", "Tamaño máximo (MB)", "Tamaño máximo de cada archivo.", "ENTERO"),
            new Def("archivo.maxCantidad", "5", "archivos", "Cantidad máxima", "Cantidad máxima de archivos por caso.", "ENTERO"),
            new Def("archivo.tipos", "pdf,jpg,jpeg,png,docx", "archivos", "Tipos permitidos", "Extensiones separadas por coma: pdf, jpg, jpeg, png, docx.", "LISTA"),
            new Def("portal.nombre", "Municipalidad", "portal", "Nombre de la institución", "Nombre que ve el ciudadano en el portal.", "TEXTO"),
            new Def("portal.direccion", "Palacio Municipal, Centro Cívico, Ciudad", "portal", "Dirección", "Dirección de atención presencial.", "TEXTO"),
            new Def("portal.telefono", "(502) 2222-0000", "portal", "Teléfono", "Teléfono de contacto del portal.", "TEXTO"),
            new Def("portal.correo", "no-reply@muniquejas-gt.lat", "portal", "Correo de contacto", "Correo público de contacto. No es la clave de envío.", "CORREO"),
            new Def("portal.horario", "Lunes a viernes, 8:00 – 16:00", "portal", "Horario", "Horario de atención que se muestra en el portal.", "TEXTO"),
            new Def("correo.from", "no-reply@muniquejas-gt.lat", "correo", "Remitente", "Dirección que aparece como remitente de los avisos.", "CORREO"),
            new Def("correo.baseUrl", "http://localhost:4200", "correo", "Dirección del sistema", "Dirección base que se incluye en los avisos.", "URL"),
            new Def("correo.reintentos", "0", "correo", "Reintentos", "Reintentos adicionales si el envío del aviso falla.", "ENTERO"),
            new Def("seguridad.intentos", "5", "seguridad", "Intentos de acceso", "Intentos fallidos antes de bloquear la cuenta. No puede ser cero.", "ENTERO"),
            new Def("seguridad.bloqueoMinutos", "15", "seguridad", "Minutos de bloqueo", "Minutos que permanece bloqueada la cuenta. No puede ser cero.", "ENTERO"),
            new Def("seguridad.sesionHoras", "8", "seguridad", "Duración de la sesión (horas)", "Horas de vigencia de una sesión nueva. No puede ser cero.", "ENTERO"),
            new Def("reportes.umbral", "5000", "reportes", "Umbral de generación", "A partir de esta cantidad de filas el reporte queda en proceso.", "ENTERO"),
            new Def("reportes.retencionHoras", "24", "reportes", "Retención del archivo (horas)", "Horas que se conserva el archivo generado.", "ENTERO"));

    private static final Map<String, String> NOMBRES_GRUPO = Map.of(
            "plazos", "Plazos",
            "archivos", "Archivos",
            "portal", "Portal",
            "correo", "Correo",
            "seguridad", "Seguridad de acceso",
            "reportes", "Reportes");

    private static final Map<String, String> AYUDA_GRUPO = Map.of(
            "plazos", "Los plazos nuevos aplican a los casos que se registren después del cambio.",
            "archivos", "La carga de documentos usa estos límites en el momento de subir el archivo.",
            "portal", "Textos que ve el ciudadano en el portal público.",
            "correo", "Remitente y reintentos. La clave del servicio de correo no se configura aquí.",
            "seguridad", "Intentos, bloqueo y duración de las sesiones que se inicien después del cambio.",
            "reportes", "Umbral de generación en proceso y tiempo de retención del archivo.");

    private static final Map<String, String> AREAS_INICIALES = Map.of(
            "ATENCION", "Atención al Ciudadano",
            "SERVICIOS", "Servicios Públicos Municipales",
            "OBRAS", "Dirección de Obras Públicas",
            "ALCALDIA", "Alcaldía Municipal",
            "AMBIENTE", "Dirección de Medio Ambiente");

    private final ParametroSistemaRepository parametros;
    private final DiaNoLaborableRepository feriados;
    private final AreaDependenciaRepository areas;
    private final CasoRepository casos;
    private final UsuarioRepository usuarios;
    private final PlazoService plazos;
    private final BitacoraSistemaService bitacora;
    private final ObjectMapper objectMapper;

    public ParametroService(
            ParametroSistemaRepository parametros,
            DiaNoLaborableRepository feriados,
            AreaDependenciaRepository areas,
            CasoRepository casos,
            UsuarioRepository usuarios,
            PlazoService plazos,
            BitacoraSistemaService bitacora,
            ObjectMapper objectMapper) {
        this.parametros = parametros;
        this.feriados = feriados;
        this.areas = areas;
        this.casos = casos;
        this.usuarios = usuarios;
        this.plazos = plazos;
        this.bitacora = bitacora;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        asegurar();
    }

    @Transactional
    public void asegurar() {
        for (Def def : DEFECTOS) {
            if (!parametros.existsById(def.clave())) {
                parametros.save(new ParametroSistema(
                        def.clave(), def.valor(), def.grupo(), def.etiqueta(), def.ayuda(), def.tipo()));
            }
        }
    }

    @Transactional
    public Configuracion consultar(Usuario actor) {
        exigirConsulta(actor);
        asegurar();
        return new Configuracion(grupos(), areasDe(), feriadosDe(), actor.getRol() == Rol.ADMIN);
    }

    @Transactional
    public InstitucionPublica institucion() {
        asegurar();
        List<PlazoPublico> lista = new ArrayList<>();
        for (TipoCaso tipo : TipoCaso.values()) {
            int dias = dias(tipo);
            lista.add(new PlazoPublico(tipo.name(), tipo.getEtiqueta(), dias, dias + " días hábiles"));
        }
        return new InstitucionPublica(
                texto("portal.nombre", "Municipalidad"),
                texto("portal.direccion", ""),
                texto("portal.telefono", ""),
                texto("portal.correo", ""),
                texto("portal.horario", ""),
                lista);
    }

    @Transactional
    public ParametroGuardado actualizar(Usuario actor, String clave, ActualizarParametroRequest request, String ip) {
        exigirAdmin(actor);
        asegurar();
        ParametroSistema parametro = parametros.findById(clave == null ? "" : clave.trim())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "CLAVE_INVALIDA", "Esa clave no se puede editar."));
        if (request.version() != parametro.getRevision()) {
            throw conflicto();
        }
        String nuevo = normalizar(parametro, request.valor());
        String anterior = parametro.getValor();
        if (nuevo.equals(anterior)) {
            return new ParametroGuardado(parametro.getClave(), anterior, nuevo, parametro.getRevision(), "El valor ya estaba aplicado.");
        }
        parametro.setValor(nuevo);
        parametro.setRevision(parametro.getRevision() + 1);
        parametro.setActualizadoEn(Instant.now());
        parametro.setActualizadoPor(actor);
        auditar(actor, ip, "PARAMETRO_ACTUALIZADO", "Parámetro " + parametro.getClave()
                + " actualizado de " + anterior + " a " + nuevo + ".", metadata(parametro.getClave(), anterior, nuevo));
        return new ParametroGuardado(
                parametro.getClave(), anterior, nuevo, parametro.getRevision(), "Parámetro actualizado correctamente.");
    }

    @Transactional
    public RestauracionRespuesta restaurar(Usuario actor, String grupo, String ip) {
        exigirAdmin(actor);
        asegurar();
        String codigo = grupo == null ? "" : grupo.trim().toLowerCase(Locale.ROOT);
        if ("feriados".equals(codigo)) {
            long antes = feriados.count();
            feriados.deleteAll();
            auditar(actor, ip, "PARAMETRO_RESTAURADO",
                    "Días no laborables restaurados. Se quitaron " + antes + " fechas.",
                    metadata("feriados", String.valueOf(antes), "0"));
            return new RestauracionRespuesta(codigo, "Los días no laborables volvieron a quedar vacíos.");
        }
        if ("areas".equals(codigo)) {
            int tocadas = 0;
            for (Map.Entry<String, String> inicial : AREAS_INICIALES.entrySet()) {
                AreaDependencia area = areas.findByCodigo(inicial.getKey()).orElse(null);
                if (area == null) {
                    continue;
                }
                String anterior = area.getNombre() + " activo=" + area.isActivo();
                area.setNombre(inicial.getValue());
                area.setActivo(true);
                area.setRevision(area.getRevision() + 1);
                tocadas += 1;
                auditar(actor, ip, "PARAMETRO_RESTAURADO",
                        "Área " + area.getCodigo() + " restaurada de " + anterior + " a "
                                + area.getNombre() + " activo=true.",
                        metadata(area.getCodigo(), anterior, area.getNombre() + " activo=true"));
            }
            return new RestauracionRespuesta(codigo, "Se restauraron " + tocadas + " áreas institucionales.");
        }
        List<Def> defs = DEFECTOS.stream().filter(def -> def.grupo().equals(codigo)).toList();
        if (defs.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GRUPO_INVALIDO", "Seleccione un grupo de parámetros.");
        }
        for (Def def : defs) {
            ParametroSistema parametro = parametros.findById(def.clave()).orElseThrow();
            String anterior = parametro.getValor();
            if (anterior.equals(def.valor())) {
                continue;
            }
            parametro.setValor(def.valor());
            parametro.setRevision(parametro.getRevision() + 1);
            parametro.setActualizadoEn(Instant.now());
            parametro.setActualizadoPor(actor);
            auditar(actor, ip, "PARAMETRO_RESTAURADO",
                    "Parámetro " + def.clave() + " restaurado de " + anterior + " a " + def.valor() + ".",
                    metadata(def.clave(), anterior, def.valor()));
        }
        return new RestauracionRespuesta(codigo, "El grupo volvió a sus valores iniciales.");
    }

    @Transactional
    public AreaGuardada crearArea(Usuario actor, CrearAreaRequest request, String ip) {
        exigirAdmin(actor);
        String codigo = codigoArea(request.codigo());
        if (areas.findByCodigo(codigo).isPresent()) {
            throw validacion("codigo", "Ya existe un área con ese código.");
        }
        String nombre = nombreArea(request.nombre());
        AreaDependencia area = areas.save(new AreaDependencia(codigo, nombre));
        auditar(actor, ip, "AREA_CREADA", "Área " + codigo + " creada: " + nombre + ".", metadata(codigo, "", nombre));
        return new AreaGuardada(areaDe(area), "Área creada correctamente.", null);
    }

    @Transactional
    public AreaGuardada actualizarArea(Usuario actor, Long id, ActualizarAreaRequest request, String ip) {
        exigirAdmin(actor);
        AreaDependencia area = areas.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "El área no existe."));
        if (request.version() != area.getRevision()) {
            throw conflicto();
        }
        String codigo = codigoArea(request.codigo());
        areas.findByCodigo(codigo).filter(otra -> !otra.getId().equals(area.getId())).ifPresent(otra -> {
            throw validacion("codigo", "Ya existe un área con ese código.");
        });
        String anterior = area.getCodigo() + " / " + area.getNombre() + " activo=" + area.isActivo();
        area.setCodigo(codigo);
        area.setNombre(nombreArea(request.nombre()));
        area.setActivo(Boolean.TRUE.equals(request.activo()));
        area.setRevision(area.getRevision() + 1);
        String nuevo = area.getCodigo() + " / " + area.getNombre() + " activo=" + area.isActivo();
        auditar(actor, ip, "AREA_ACTUALIZADA", "Área actualizada de " + anterior + " a " + nuevo + ".",
                metadata(area.getCodigo(), anterior, nuevo));
        String advertencia = null;
        if (!area.isActivo() && historial(area) > 0) {
            advertencia = "El área quedó inactiva y no recibirá casos nuevos. Los casos y usuarios ya asociados se conservan.";
        }
        String mensaje = area.isActivo() ? "Área actualizada correctamente." : "El área quedó inactiva.";
        return new AreaGuardada(areaDe(area), mensaje, advertencia);
    }

    @Transactional
    public void eliminarArea(Usuario actor, Long id, String ip) {
        exigirAdmin(actor);
        AreaDependencia area = areas.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "El área no existe."));
        if (historial(area) > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AREA_CON_HISTORIAL",
                    "Un área con historial no se elimina. Puede desactivarla.");
        }
        String codigo = area.getCodigo();
        areas.delete(area);
        auditar(actor, ip, "AREA_ELIMINADA", "Área " + codigo + " eliminada. No tenía casos ni usuarios.",
                metadata(codigo, codigo, ""));
    }

    @Transactional
    public FeriadoParametro crearFeriado(Usuario actor, CrearFeriadoRequest request, String ip) {
        exigirAdmin(actor);
        if (feriados.existsById(request.fecha())) {
            throw validacion("fecha", "Esa fecha ya está registrada como día no laborable.");
        }
        String descripcion = request.descripcion().trim();
        if (descripcion.length() < 3 || descripcion.length() > 150) {
            throw validacion("descripcion", "La descripción debe tener entre 3 y 150 caracteres.");
        }
        feriados.save(new DiaNoLaborable(request.fecha(), descripcion));
        auditar(actor, ip, "FERIADO_CREADO",
                "Día no laborable " + request.fecha() + " agregado: " + descripcion + ". Los plazos ya calculados no cambian.",
                metadata(request.fecha().toString(), "", descripcion));
        return new FeriadoParametro(request.fecha(), descripcion);
    }

    @Transactional
    public void eliminarFeriado(Usuario actor, LocalDate fecha, String ip) {
        exigirAdmin(actor);
        DiaNoLaborable dia = feriados.findById(fecha).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Esa fecha no está registrada."));
        feriados.delete(dia);
        auditar(actor, ip, "FERIADO_ELIMINADO", "Día no laborable " + fecha + " quitado.",
                metadata(fecha.toString(), dia.getDescripcion(), ""));
    }

    @Transactional
    public RecalculoRespuesta recalcular(Usuario actor, String tipo, boolean confirmar, String ip) {
        exigirAdmin(actor);
        if (!confirmar) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CONFIRMACION",
                    "Confirme el recálculo de los plazos ya calculados.");
        }
        TipoCaso tipoCaso = tipoDe(tipo);
        int dias = dias(tipoCaso);
        List<Caso> abiertos = casos.findByTipoCasoAndEstadoNotIn(tipoCaso, CERRADOS);
        for (Caso caso : abiertos) {
            LocalDate inicio = caso.getFechaRegistro().atZone(ZoneOffset.UTC).toLocalDate();
            caso.setFechaLimiteRespuesta(plazos.fechaLimite(inicio, dias));
        }
        auditar(actor, ip, "PLAZO_RECALCULADO",
                "Recálculo confirmado de " + abiertos.size() + " casos " + tipoCaso.getEtiqueta()
                        + " con " + dias + " días hábiles.",
                metadata("plazo." + tipoCaso.name(), "", String.valueOf(dias)));
        return new RecalculoRespuesta(abiertos.size(), "Se recalcularon " + abiertos.size() + " casos abiertos.");
    }

    @Transactional
    public Exportacion exportar(Usuario actor, String ip) {
        exigirAdmin(actor);
        asegurar();
        Exportacion exportacion = new Exportacion(
                parametros.findAllByOrderByGrupoAscClaveAsc().stream()
                        .map(item -> new ParametroExportado(item.getClave(), item.getValor()))
                        .toList(),
                areas.findAll().stream()
                        .map(area -> new AreaExportada(area.getCodigo(), area.getNombre(), area.isActivo()))
                        .toList(),
                feriadosDe());
        auditar(actor, ip, "PARAMETROS_EXPORTADOS", "Exportación de parámetros.", metadata("exportar", "", "ok"));
        return exportacion;
    }

    @Transactional
    public ImportacionRespuesta importar(Usuario actor, byte[] contenido, String ip) {
        exigirAdmin(actor);
        asegurar();
        Exportacion datos;
        try {
            datos = objectMapper.readValue(contenido, Exportacion.class);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ARCHIVO_INVALIDO", "El archivo de parámetros no tiene el formato esperado.");
        }
        if (datos.parametros() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ARCHIVO_INVALIDO", "El archivo no incluye parámetros.");
        }
        Map<String, String> cambios = new LinkedHashMap<>();
        for (ParametroExportado item : datos.parametros()) {
            if (item == null || item.clave() == null) {
                continue;
            }
            ParametroSistema parametro = parametros.findById(item.clave()).orElseThrow(() -> new ApiException(
                    HttpStatus.BAD_REQUEST, "CLAVE_INVALIDA", "Hay claves que no se pueden editar."));
            cambios.put(parametro.getClave(), normalizar(parametro, item.valor()));
        }
        int aplicados = 0;
        for (Map.Entry<String, String> cambio : cambios.entrySet()) {
            ParametroSistema parametro = parametros.findById(cambio.getKey()).orElseThrow();
            String anterior = parametro.getValor();
            if (anterior.equals(cambio.getValue())) {
                continue;
            }
            parametro.setValor(cambio.getValue());
            parametro.setRevision(parametro.getRevision() + 1);
            parametro.setActualizadoEn(Instant.now());
            parametro.setActualizadoPor(actor);
            aplicados += 1;
            auditar(actor, ip, "PARAMETRO_IMPORTADO",
                    "Parámetro " + parametro.getClave() + " importado de " + anterior + " a " + cambio.getValue() + ".",
                    metadata(parametro.getClave(), anterior, cambio.getValue()));
        }
        int areasTocadas = importarAreas(actor, datos.areas(), ip);
        int feriadosTocados = importarFeriados(actor, datos.feriados(), ip);
        return new ImportacionRespuesta(
                aplicados, areasTocadas, feriadosTocados, "La paramétría importada quedó aplicada y auditada.");
    }

    public int dias(TipoCaso tipo) {
        asegurar();
        return entero("plazo." + tipo.name(), tipo.getDiasHabiles());
    }

    public String textoPlazo(TipoCaso tipo) {
        return dias(tipo) + " días hábiles";
    }

    public int maxArchivos() {
        asegurar();
        return entero("archivo.maxCantidad", 5);
    }

    public long maxBytes() {
        asegurar();
        return entero("archivo.maxMb", 5) * 1024L * 1024L;
    }

    public Set<String> extensiones() {
        asegurar();
        return Set.copyOf(List.of(texto("archivo.tipos", "pdf,jpg,jpeg,png,docx").split(",")));
    }

    public int intentosAcceso() {
        asegurar();
        return entero("seguridad.intentos", 5);
    }

    public int minutosBloqueo() {
        asegurar();
        return entero("seguridad.bloqueoMinutos", 15);
    }

    public int horasSesion() {
        asegurar();
        return entero("seguridad.sesionHoras", 8);
    }

    public int umbralReportes() {
        asegurar();
        return entero("reportes.umbral", 5000);
    }

    public int retencionReportes() {
        asegurar();
        return entero("reportes.retencionHoras", 24);
    }

    public int reintentosCorreo() {
        asegurar();
        return entero("correo.reintentos", 0);
    }

    public String remitenteCorreo(String respaldo) {
        asegurar();
        String valor = texto("correo.from", respaldo == null ? "" : respaldo);
        return CORREO.matcher(valor).matches() ? valor : respaldo;
    }

    private int importarAreas(Usuario actor, List<AreaExportada> lista, String ip) {
        if (lista == null) {
            return 0;
        }
        int tocadas = 0;
        for (AreaExportada item : lista) {
            if (item == null) {
                continue;
            }
            String codigo = codigoArea(item.codigo());
            String nombre = nombreArea(item.nombre());
            AreaDependencia area = areas.findByCodigo(codigo).orElse(null);
            if (area == null) {
                AreaDependencia creada = new AreaDependencia(codigo, nombre);
                creada.setActivo(item.activo());
                areas.save(creada);
                tocadas += 1;
                auditar(actor, ip, "AREA_IMPORTADA", "Área " + codigo + " importada.", metadata(codigo, "", nombre));
                continue;
            }
            String anterior = area.getNombre() + " activo=" + area.isActivo();
            String nuevo = nombre + " activo=" + item.activo();
            if (anterior.equals(nuevo) && area.getNombre().equals(nombre) && area.isActivo() == item.activo()) {
                continue;
            }
            area.setNombre(nombre);
            area.setActivo(item.activo());
            area.setRevision(area.getRevision() + 1);
            tocadas += 1;
            auditar(actor, ip, "AREA_IMPORTADA", "Área " + codigo + " importada de " + anterior + " a " + nuevo + ".",
                    metadata(codigo, anterior, nuevo));
        }
        return tocadas;
    }

    private int importarFeriados(Usuario actor, List<FeriadoParametro> lista, String ip) {
        if (lista == null) {
            return 0;
        }
        long antes = feriados.count();
        feriados.deleteAll();
        int creados = 0;
        for (FeriadoParametro item : lista) {
            if (item == null || item.fecha() == null || feriados.existsById(item.fecha())) {
                continue;
            }
            String descripcion = item.descripcion() == null ? "Día no laborable" : item.descripcion().trim();
            if (descripcion.length() < 3) {
                descripcion = "Día no laborable";
            }
            feriados.save(new DiaNoLaborable(item.fecha(), descripcion));
            creados += 1;
        }
        auditar(actor, ip, "FERIADOS_IMPORTADOS",
                "Días no laborables importados. Antes: " + antes + ". Ahora: " + creados + ".",
                metadata("feriados", String.valueOf(antes), String.valueOf(creados)));
        return creados;
    }

    private List<GrupoParametros> grupos() {
        List<GrupoParametros> lista = new ArrayList<>();
        for (String codigo : List.of("plazos", "archivos", "portal", "correo", "seguridad", "reportes")) {
            List<ParametroItem> items = parametros.findByGrupoOrderByClaveAsc(codigo).stream()
                    .map(item -> new ParametroItem(
                            item.getClave(),
                            item.getEtiqueta(),
                            item.getValor(),
                            item.getAyuda(),
                            item.getTipo(),
                            item.getRevision()))
                    .toList();
            lista.add(new GrupoParametros(codigo, NOMBRES_GRUPO.get(codigo), AYUDA_GRUPO.get(codigo), items));
        }
        return lista;
    }

    private List<AreaParametro> areasDe() {
        return areas.findAll().stream()
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .map(this::areaDe)
                .toList();
    }

    private AreaParametro areaDe(AreaDependencia area) {
        return new AreaParametro(
                area.getId(),
                area.getCodigo(),
                area.getNombre(),
                area.isActivo(),
                area.getRevision(),
                casos.countByAreaDependencia_Id(area.getId()),
                usuarios.countByAreaDependencia_Id(area.getId()));
    }

    private List<FeriadoParametro> feriadosDe() {
        return feriados.findAllByOrderByFechaAsc().stream()
                .map(dia -> new FeriadoParametro(dia.getFecha(), dia.getDescripcion()))
                .toList();
    }

    private long historial(AreaDependencia area) {
        return casos.countByAreaDependencia_Id(area.getId()) + usuarios.countByAreaDependencia_Id(area.getId());
    }

    private String normalizar(ParametroSistema parametro, String valor) {
        String texto = valor == null ? "" : valor.trim();
        return switch (parametro.getClave()) {
            case "plazo.Q", "plazo.R", "plazo.D", "plazo.S" -> String.valueOf(enteroPositivo(texto, parametro.getEtiqueta(), 1, 365));
            case "archivo.maxMb" -> String.valueOf(enteroPositivo(texto, parametro.getEtiqueta(), 1, 20));
            case "archivo.maxCantidad" -> String.valueOf(enteroPositivo(texto, parametro.getEtiqueta(), 1, 20));
            case "archivo.tipos" -> tipos(texto);
            case "portal.correo", "correo.from" -> correo(texto, parametro.getEtiqueta());
            case "correo.baseUrl" -> url(texto);
            case "correo.reintentos" -> String.valueOf(enteroPositivo(texto, parametro.getEtiqueta(), 0, 5));
            case "seguridad.intentos", "seguridad.bloqueoMinutos", "seguridad.sesionHoras" ->
                    String.valueOf(enteroPositivo(texto, parametro.getEtiqueta(), 1, 720));
            case "reportes.umbral" -> String.valueOf(enteroPositivo(texto, parametro.getEtiqueta(), 1, 1000000));
            case "reportes.retencionHoras" -> String.valueOf(enteroPositivo(texto, parametro.getEtiqueta(), 1, 8760));
            default -> {
                if (texto.length() < 2 || texto.length() > 200) {
                    throw validacion("valor", "El valor debe tener entre 2 y 200 caracteres.");
                }
                yield texto;
            }
        };
    }

    private int enteroPositivo(String texto, String etiqueta, int minimo, int maximo) {
        int numero;
        try {
            numero = Integer.parseInt(texto);
        } catch (NumberFormatException ex) {
            throw validacion("valor", etiqueta + " debe ser un número entero.");
        }
        if (numero < minimo || numero > maximo) {
            throw validacion("valor", etiqueta + " debe estar entre " + minimo + " y " + maximo + ".");
        }
        return numero;
    }

    private String correo(String texto, String etiqueta) {
        if (!CORREO.matcher(texto).matches() || texto.length() > 120) {
            throw validacion("valor", etiqueta + " no tiene un formato de correo válido.");
        }
        return texto.toLowerCase(Locale.ROOT);
    }

    private String url(String texto) {
        if (!(texto.startsWith("http://") || texto.startsWith("https://")) || texto.length() > 200) {
            throw validacion("valor", "La dirección del sistema debe comenzar con http:// o https://.");
        }
        return texto;
    }

    private String tipos(String texto) {
        String[] partes = texto.toLowerCase(Locale.ROOT).split(",");
        List<String> validas = new ArrayList<>();
        for (String parte : partes) {
            String ext = parte.trim();
            if (ext.isEmpty()) {
                continue;
            }
            if (!EXTENSIONES.contains(ext)) {
                throw validacion("valor", "Tipo no permitido: " + ext + ". Use pdf, jpg, jpeg, png o docx.");
            }
            if (!validas.contains(ext)) {
                validas.add(ext);
            }
        }
        if (validas.isEmpty()) {
            throw validacion("valor", "Indique al menos un tipo de archivo permitido.");
        }
        return String.join(",", validas);
    }

    private String codigoArea(String codigo) {
        String normalizado = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        if (!CODIGO_AREA.matcher(normalizado).matches()) {
            throw validacion("codigo", "El código del área usa letras mayúsculas y no se repite.");
        }
        return normalizado;
    }

    private String nombreArea(String nombre) {
        String texto = nombre == null ? "" : nombre.trim();
        if (texto.length() < 2 || texto.length() > 150) {
            throw validacion("nombre", "El nombre del área debe tener entre 2 y 150 caracteres.");
        }
        return texto;
    }

    private TipoCaso tipoDe(String tipo) {
        try {
            return TipoCaso.valueOf(tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw validacion("tipo", "Seleccione un tipo de caso.");
        }
    }

    private int entero(String clave, int respaldo) {
        ParametroSistema parametro = parametros.findById(clave).orElse(null);
        if (parametro == null) {
            return respaldo;
        }
        try {
            return Integer.parseInt(parametro.getValor());
        } catch (NumberFormatException ex) {
            return respaldo;
        }
    }

    private String texto(String clave, String respaldo) {
        return parametros.findById(clave).map(ParametroSistema::getValor).orElse(respaldo);
    }

    private void exigirConsulta(Usuario actor) {
        if (actor == null || (actor.getRol() != Rol.ADMIN && actor.getRol() != Rol.SUPERVISOR)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene autorización para realizar esta operación.");
        }
    }

    private void exigirAdmin(Usuario actor) {
        if (actor == null || actor.getRol() != Rol.ADMIN) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PERMISO_DENEGADO",
                    "No tiene autorización para realizar esta operación.");
        }
    }

    private ApiException conflicto() {
        return new ApiException(HttpStatus.CONFLICT, "VERSION", MENSAJE_VERSION);
    }

    private ApiException validacion(String campo, String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje, Map.of(campo, mensaje));
    }

    private void auditar(Usuario actor, String ip, String evento, String descripcion, String metadata) {
        bitacora.registrarEnCurso("PARAMETROS", "PARAMETRO", evento, actor, null, descripcion, ip, metadata);
    }

    private String metadata(String clave, String anterior, String nuevo) {
        return "{\"clave\":\"" + escapar(clave) + "\",\"anterior\":\"" + escapar(anterior)
                + "\",\"nuevo\":\"" + escapar(nuevo) + "\"}";
    }

    private String escapar(String texto) {
        return texto == null ? "" : texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Def(String clave, String valor, String grupo, String etiqueta, String ayuda, String tipo) {
    }
}
