-- =============================================================================
-- QRDS Municipal — Esquema PostgreSQL
-- Sistema de Quejas, Reclamos, Denuncias y Sugerencias + Bitácoras
-- Alineado a CU-00 … CU-16 y Reglas de Negocio V1.0
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- Catálogos / paramétría (CU-15)
-- -----------------------------------------------------------------------------

CREATE TABLE areas_dependencia (
    id              BIGSERIAL PRIMARY KEY,
    codigo          VARCHAR(30)  NOT NULL UNIQUE,
    nombre          VARCHAR(150) NOT NULL,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE dias_no_laborables (
    id          BIGSERIAL PRIMARY KEY,
    fecha       DATE         NOT NULL UNIQUE,
    descripcion VARCHAR(200) NOT NULL,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE plazos_tipo_caso (
    tipo_caso       CHAR(1) PRIMARY KEY
        CHECK (tipo_caso IN ('Q', 'R', 'D', 'S')),
    dias_habiles    INT NOT NULL CHECK (dias_habiles > 0),
    descripcion     VARCHAR(100) NOT NULL,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE parametros_sistema (
    clave           VARCHAR(80) PRIMARY KEY,
    valor           TEXT        NOT NULL,
    grupo           VARCHAR(40) NOT NULL,
    descripcion     VARCHAR(255),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_por BIGINT
);

-- -----------------------------------------------------------------------------
-- Usuarios internos (CU-01, CU-10) — el ciudadano NO tiene fila aquí
-- -----------------------------------------------------------------------------

CREATE TABLE usuarios (
    id                      BIGSERIAL PRIMARY KEY,
    nombre                  VARCHAR(100) NOT NULL,
    email                   VARCHAR(150) NOT NULL UNIQUE,
    password_hash           VARCHAR(255) NOT NULL,
    rol                     VARCHAR(20)  NOT NULL
        CHECK (rol IN ('ADMIN', 'SUPERVISOR', 'AGENTE')),
    area_dependencia_id     BIGINT REFERENCES areas_dependencia(id),
    activo                  BOOLEAN      NOT NULL DEFAULT TRUE,
    intentos_fallidos       INT          NOT NULL DEFAULT 0,
    bloqueado_hasta         TIMESTAMPTZ,
    forzar_cambio_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    token_version           INT          NOT NULL DEFAULT 0,
    creado_en               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    actualizado_en          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_area_rol CHECK (
        rol = 'ADMIN' OR area_dependencia_id IS NOT NULL
    )
);

CREATE INDEX idx_usuarios_rol_activo ON usuarios (rol, activo);
CREATE INDEX idx_usuarios_area ON usuarios (area_dependencia_id);

ALTER TABLE parametros_sistema
    ADD CONSTRAINT fk_param_usuario
    FOREIGN KEY (actualizado_por) REFERENCES usuarios(id);

-- -----------------------------------------------------------------------------
-- Casos (CU-02 … CU-07, CU-14)
-- Código: [TIPO]-[AÑO]-[CORRELATIVO 5 dígitos]  ej. Q-2026-00001
-- -----------------------------------------------------------------------------

CREATE TABLE casos (
    id                          BIGSERIAL PRIMARY KEY,
    codigo_seguimiento          VARCHAR(20)  NOT NULL UNIQUE,
    tipo_caso                   CHAR(1)      NOT NULL
        CHECK (tipo_caso IN ('Q', 'R', 'D', 'S')),
    nombre_ciudadano            VARCHAR(150),
    email_ciudadano             VARCHAR(150),
    telefono                    VARCHAR(20),
    area_dependencia_id         BIGINT       NOT NULL
        REFERENCES areas_dependencia(id),
    area_dependencia_texto      VARCHAR(200),
    descripcion                 TEXT         NOT NULL,
    denunciado_nombre           VARCHAR(150),
    es_anonimo                  BOOLEAN      NOT NULL DEFAULT FALSE,
    acepta_privacidad           BOOLEAN      NOT NULL DEFAULT FALSE,
    estado                      VARCHAR(20)  NOT NULL DEFAULT 'RECIBIDO'
        CHECK (estado IN (
            'RECIBIDO', 'EN_REVISION', 'EN_PROCESO',
            'RESUELTO', 'CERRADO', 'ANULADO'
        )),
    agente_asignado_id          BIGINT REFERENCES usuarios(id),
    prioridad                   VARCHAR(10)  NOT NULL DEFAULT 'NORMAL'
        CHECK (prioridad IN ('BAJA', 'NORMAL', 'ALTA', 'URGENTE')),
    avance_porcentaje           INT          NOT NULL DEFAULT 0
        CHECK (avance_porcentaje BETWEEN 0 AND 100),
    fecha_registro              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fecha_limite_respuesta      DATE,
    fecha_prorroga              TIMESTAMPTZ,
    fecha_resolucion            TIMESTAMPTZ,
    fecha_cierre                TIMESTAMPTZ,
    fecha_ultima_actualizacion  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    escalado                    BOOLEAN      NOT NULL DEFAULT FALSE,
    motivo_anulacion            TEXT,
    ip_registro                 VARCHAR(45),
    creado_en                   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_anonimo_datos CHECK (
        es_anonimo = FALSE
        OR (nombre_ciudadano IS NULL AND email_ciudadano IS NULL)
        OR tipo_caso = 'D'
    ),
    CONSTRAINT chk_privacidad CHECK (acepta_privacidad = TRUE)
);

CREATE INDEX idx_casos_estado ON casos (estado);
CREATE INDEX idx_casos_tipo_anio ON casos (tipo_caso, fecha_registro);
CREATE INDEX idx_casos_agente ON casos (agente_asignado_id);
CREATE INDEX idx_casos_area ON casos (area_dependencia_id);
CREATE INDEX idx_casos_limite ON casos (fecha_limite_respuesta)
    WHERE estado IN ('EN_REVISION', 'EN_PROCESO');

CREATE TABLE correlativos_caso (
    tipo_caso   CHAR(1) NOT NULL CHECK (tipo_caso IN ('Q', 'R', 'D', 'S')),
    anio        INT     NOT NULL,
    ultimo      INT     NOT NULL DEFAULT 0,
    PRIMARY KEY (tipo_caso, anio)
);

-- -----------------------------------------------------------------------------
-- Documentos (CU-02, CU-12)
-- -----------------------------------------------------------------------------

CREATE TABLE documentos_caso (
    id              BIGSERIAL PRIMARY KEY,
    caso_id         BIGINT       NOT NULL REFERENCES casos(id),
    nombre_archivo  VARCHAR(255) NOT NULL,
    ruta_archivo    VARCHAR(500) NOT NULL,
    tipo_mime       VARCHAR(100) NOT NULL,
    tamanio_bytes   BIGINT       NOT NULL CHECK (tamanio_bytes > 0),
    subido_por_id   BIGINT REFERENCES usuarios(id),
    origen          VARCHAR(20)  NOT NULL DEFAULT 'CIUDADANO'
        CHECK (origen IN ('CIUDADANO', 'AGENTE', 'SISTEMA')),
    subido_en       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_docs_caso ON documentos_caso (caso_id);

-- -----------------------------------------------------------------------------
-- Seguimientos (CU-04) — inmutables (solo INSERT)
-- -----------------------------------------------------------------------------

CREATE TABLE seguimientos_caso (
    id                    BIGSERIAL PRIMARY KEY,
    caso_id               BIGINT       NOT NULL REFERENCES casos(id),
    usuario_id            BIGINT       NOT NULL REFERENCES usuarios(id),
    tipo                  VARCHAR(20)  NOT NULL
        CHECK (tipo IN ('PUBLICA', 'INTERNA', 'CORRECCION')),
    titulo                VARCHAR(120) NOT NULL,
    descripcion           TEXT         NOT NULL,
    porcentaje_avance     INT CHECK (porcentaje_avance IS NULL OR porcentaje_avance BETWEEN 0 AND 100),
    notificado            BOOLEAN      NOT NULL DEFAULT FALSE,
    seguimiento_padre_id  BIGINT REFERENCES seguimientos_caso(id),
    creado_en             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_registro           VARCHAR(45)
);

CREATE INDEX idx_seg_caso_fecha ON seguimientos_caso (caso_id, creado_en DESC);

CREATE TABLE documentos_seguimiento (
    id              BIGSERIAL PRIMARY KEY,
    seguimiento_id  BIGINT       NOT NULL REFERENCES seguimientos_caso(id),
    nombre_archivo  VARCHAR(255) NOT NULL,
    ruta_archivo    VARCHAR(500) NOT NULL,
    tipo_mime       VARCHAR(100) NOT NULL,
    tamanio_bytes   BIGINT       NOT NULL,
    subido_en       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- Resolución y cierre (CU-05, CU-06)
-- -----------------------------------------------------------------------------

CREATE TABLE resoluciones_caso (
    id                   BIGSERIAL PRIMARY KEY,
    caso_id              BIGINT       NOT NULL REFERENCES casos(id),
    usuario_id           BIGINT       NOT NULL REFERENCES usuarios(id),
    comentario           TEXT         NOT NULL,
    tipo_resultado       VARCHAR(40)  NOT NULL
        CHECK (tipo_resultado IN (
            'ATENDIDO', 'PARCIALMENTE_ATENDIDO',
            'IMPROCEDENTE', 'SIN_RESPUESTA_CIUDADANO'
        )),
    vigente              BOOLEAN      NOT NULL DEFAULT TRUE,
    notificado           BOOLEAN      NOT NULL DEFAULT FALSE,
    estado_notificacion  VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado_notificacion IN ('PENDIENTE', 'ENVIADA', 'FALLIDA', 'OMITIDA')),
    archivo_ruta         VARCHAR(500),
    creado_en            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_registro          VARCHAR(45),
    CONSTRAINT chk_comentario_min CHECK (char_length(comentario) >= 100)
);

CREATE UNIQUE INDEX uq_resolucion_vigente
    ON resoluciones_caso (caso_id) WHERE vigente = TRUE;

CREATE TABLE cierres_caso (
    id           BIGSERIAL PRIMARY KEY,
    caso_id      BIGINT       NOT NULL UNIQUE REFERENCES casos(id),
    usuario_id   BIGINT       NOT NULL REFERENCES usuarios(id),
    motivo       TEXT         NOT NULL,
    notificado   BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_registro  VARCHAR(45),
    CONSTRAINT chk_motivo_cierre CHECK (char_length(motivo) >= 20)
);

-- -----------------------------------------------------------------------------
-- Reasignaciones (CU-14)
-- -----------------------------------------------------------------------------

CREATE TABLE reasignaciones_caso (
    id                 BIGSERIAL PRIMARY KEY,
    caso_id            BIGINT       NOT NULL REFERENCES casos(id),
    agente_anterior_id BIGINT       NOT NULL REFERENCES usuarios(id),
    agente_nuevo_id    BIGINT       NOT NULL REFERENCES usuarios(id),
    solicitado_por_id  BIGINT       NOT NULL REFERENCES usuarios(id),
    justificacion      TEXT         NOT NULL,
    notificado         BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_registro        VARCHAR(45),
    CONSTRAINT chk_justificacion_reasig CHECK (char_length(justificacion) >= 50),
    CONSTRAINT chk_agentes_distintos CHECK (agente_anterior_id <> agente_nuevo_id)
);

CREATE INDEX idx_reasig_caso ON reasignaciones_caso (caso_id, creado_en DESC);

-- -----------------------------------------------------------------------------
-- Notificaciones (CU-08)
-- -----------------------------------------------------------------------------

CREATE TABLE plantillas_notificacion (
    codigo          VARCHAR(40) PRIMARY KEY,
    asunto_template TEXT        NOT NULL,
    cuerpo_html     TEXT        NOT NULL,
    version         INT         NOT NULL DEFAULT 1,
    activo          BOOLEAN     NOT NULL DEFAULT TRUE,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notificaciones (
    id                      BIGSERIAL PRIMARY KEY,
    tipo_evento             VARCHAR(40)  NOT NULL,
    caso_id                 BIGINT REFERENCES casos(id),
    destinatario_email      VARCHAR(150),
    destinatario_usuario_id BIGINT REFERENCES usuarios(id),
    payload_json            JSONB,
    plantilla_codigo        VARCHAR(40) REFERENCES plantillas_notificacion(codigo),
    estado                  VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN (
            'PENDIENTE', 'ENVIADA', 'FALLIDA',
            'REINTENTANDO', 'OMITIDA_POR_REGLA'
        )),
    intentos                INT          NOT NULL DEFAULT 0,
    max_intentos            INT          NOT NULL DEFAULT 3,
    ultimo_error            TEXT,
    fecha_creacion          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    fecha_envio             TIMESTAMPTZ,
    fecha_proximo_intento   TIMESTAMPTZ,
    idempotency_key         VARCHAR(120) UNIQUE
);

CREATE INDEX idx_notif_estado ON notificaciones (estado, fecha_proximo_intento);
CREATE INDEX idx_notif_caso ON notificaciones (caso_id);

-- -----------------------------------------------------------------------------
-- Reportes (CU-09)
-- -----------------------------------------------------------------------------

CREATE TABLE reportes_generados (
    id              BIGSERIAL PRIMARY KEY,
    tipo_reporte    VARCHAR(20)  NOT NULL,
    solicitado_por  BIGINT       NOT NULL REFERENCES usuarios(id),
    filtros_json    JSONB,
    formato         VARCHAR(10)  NOT NULL CHECK (formato IN ('PDF', 'XLSX', 'PANTALLA')),
    estado          VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE', 'PROCESANDO', 'LISTO', 'FALLIDO')),
    ruta_archivo    VARCHAR(500),
    error_mensaje   TEXT,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    finalizado_en   TIMESTAMPTZ
);

-- -----------------------------------------------------------------------------
-- Bitácoras inmutables (CU-00, CU-01, CU-07, CU-11) — solo INSERT + SELECT
-- -----------------------------------------------------------------------------

CREATE TABLE bitacora_accesos (
    id           BIGSERIAL PRIMARY KEY,
    usuario_id   BIGINT REFERENCES usuarios(id),
    tipo_evento  VARCHAR(30)  NOT NULL
        CHECK (tipo_evento IN (
            'LOGIN', 'LOGOUT', 'LOGIN_FALLIDO', 'BLOQUEADO',
            'ACCESO_PORTAL', 'CONSULTA_PUBLICA'
        )),
    fecha_hora   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_cliente   VARCHAR(45),
    user_agent   TEXT,
    resultado    VARCHAR(30)  NOT NULL DEFAULT 'OK',
    detalle      TEXT
);

CREATE INDEX idx_bit_acc_fecha ON bitacora_accesos (fecha_hora DESC);
CREATE INDEX idx_bit_acc_usuario ON bitacora_accesos (usuario_id, fecha_hora DESC);

CREATE TABLE bitacora_consultas_publicas (
    id               BIGSERIAL PRIMARY KEY,
    codigo_consultado VARCHAR(20),
    ip_cliente       VARCHAR(45),
    user_agent       TEXT,
    resultado        VARCHAR(30) NOT NULL
        CHECK (resultado IN ('ENCONTRADO', 'NO_ENCONTRADO', 'RATE_LIMIT', 'ERROR')),
    fecha_hora       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bit_pub_fecha ON bitacora_consultas_publicas (fecha_hora DESC);

CREATE TABLE bitacora_casos (
    id              BIGSERIAL PRIMARY KEY,
    caso_id         BIGINT REFERENCES casos(id),
    usuario_id      BIGINT REFERENCES usuarios(id),
    tipo_evento     VARCHAR(40)  NOT NULL,
    estado_anterior VARCHAR(20),
    estado_nuevo    VARCHAR(20),
    descripcion     TEXT         NOT NULL,
    fecha_hora      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_cliente      VARCHAR(45),
    metadata_json   JSONB
);

CREATE INDEX idx_bit_casos_fecha ON bitacora_casos (fecha_hora DESC);
CREATE INDEX idx_bit_casos_caso ON bitacora_casos (caso_id, fecha_hora DESC);
CREATE INDEX idx_bit_casos_tipo ON bitacora_casos (tipo_evento);

CREATE TABLE bitacora_sistema (
    id              BIGSERIAL PRIMARY KEY,
    dominio         VARCHAR(30)  NOT NULL
        CHECK (dominio IN (
            'USUARIOS', 'DOCUMENTOS', 'NOTIFICACIONES',
            'REPORTES', 'PARAMETROS', 'AUDITORIA', 'SISTEMA'
        )),
    tipo_evento     VARCHAR(40)  NOT NULL,
    usuario_id      BIGINT REFERENCES usuarios(id),
    entidad_tipo    VARCHAR(40),
    entidad_id      BIGINT,
    descripcion     TEXT         NOT NULL,
    fecha_hora      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_cliente      VARCHAR(45),
    metadata_json   JSONB
);

CREATE INDEX idx_bit_sis_fecha ON bitacora_sistema (fecha_hora DESC);
CREATE INDEX idx_bit_sis_dominio ON bitacora_sistema (dominio, fecha_hora DESC);

-- Vista unificada para CU-11
CREATE OR REPLACE VIEW v_bitacora_eventos AS
SELECT
    'ACCESOS'::text AS tipo_dominio,
    id,
    tipo_evento,
    usuario_id,
    NULL::bigint AS caso_id,
    NULL::varchar AS estado_anterior,
    NULL::varchar AS estado_nuevo,
    COALESCE(detalle, resultado) AS descripcion,
    fecha_hora,
    ip_cliente,
    resultado
FROM bitacora_accesos
UNION ALL
SELECT
    'CASOS',
    id,
    tipo_evento,
    usuario_id,
    caso_id,
    estado_anterior,
    estado_nuevo,
    descripcion,
    fecha_hora,
    ip_cliente,
    NULL
FROM bitacora_casos
UNION ALL
SELECT
    dominio,
    id,
    tipo_evento,
    usuario_id,
    CASE WHEN entidad_tipo = 'CASO' THEN entidad_id ELSE NULL END,
    NULL,
    NULL,
    descripcion,
    fecha_hora,
    ip_cliente,
    NULL
FROM bitacora_sistema;

-- -----------------------------------------------------------------------------
-- Historial operativo del caso (CU-13) — vista de solo lectura
-- -----------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_historial_caso AS
SELECT
    c.id AS caso_id,
    c.codigo_seguimiento,
    'BITACORA'::text AS fuente,
    b.tipo_evento AS tipo,
    b.descripcion,
    b.estado_anterior,
    b.estado_nuevo,
    b.usuario_id,
    b.fecha_hora,
    b.id AS evento_id
FROM bitacora_casos b
JOIN casos c ON c.id = b.caso_id
UNION ALL
SELECT
    s.caso_id,
    c.codigo_seguimiento,
    'SEGUIMIENTO',
    s.tipo,
    s.titulo || ' — ' || left(s.descripcion, 200),
    NULL,
    NULL,
    s.usuario_id,
    s.creado_en,
    s.id
FROM seguimientos_caso s
JOIN casos c ON c.id = s.caso_id
UNION ALL
SELECT
    r.caso_id,
    c.codigo_seguimiento,
    'RESOLUCION',
    r.tipo_resultado,
    left(r.comentario, 200),
    'EN_PROCESO',
    'RESUELTO',
    r.usuario_id,
    r.creado_en,
    r.id
FROM resoluciones_caso r
JOIN casos c ON c.id = r.caso_id
UNION ALL
SELECT
    cl.caso_id,
    c.codigo_seguimiento,
    'CIERRE',
    'CASO_CERRADO',
    left(cl.motivo, 200),
    'RESUELTO',
    'CERRADO',
    cl.usuario_id,
    cl.creado_en,
    cl.id
FROM cierres_caso cl
JOIN casos c ON c.id = cl.caso_id;

-- -----------------------------------------------------------------------------
-- Función: generar código de seguimiento (CU-02 RN-06)
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION generar_codigo_seguimiento(p_tipo CHAR(1))
RETURNS VARCHAR(20)
LANGUAGE plpgsql
AS $$
DECLARE
    v_anio INT := EXTRACT(YEAR FROM NOW())::INT;
    v_num  INT;
BEGIN
    INSERT INTO correlativos_caso (tipo_caso, anio, ultimo)
    VALUES (p_tipo, v_anio, 1)
    ON CONFLICT (tipo_caso, anio)
    DO UPDATE SET ultimo = correlativos_caso.ultimo + 1
    RETURNING ultimo INTO v_num;

    RETURN p_tipo || '-' || v_anio::text || '-' || lpad(v_num::text, 5, '0');
END;
$$;

-- Fin del esquema QRDS (CU-00 a CU-16)
