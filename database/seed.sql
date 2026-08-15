-- =============================================================================
-- QRDS Municipal — Datos iniciales (seed)
-- =============================================================================

-- Áreas / dependencias (CU-15, CU-02, CU-10)
INSERT INTO areas_dependencia (codigo, nombre) VALUES
    ('ALCALDIA',   'Alcaldía Municipal'),
    ('OBRAS',      'Dirección de Obras Públicas'),
    ('AMBIENTE',   'Dirección de Medio Ambiente'),
    ('CATASTRO',   'Dirección de Catastro'),
    ('FINANZAS',   'Dirección de Finanzas'),
    ('SERVICIOS',  'Servicios Públicos Municipales'),
    ('RRHH',       'Recursos Humanos'),
    ('ATENCION',   'Atención al Ciudadano');

-- Plazos por tipo de caso (CU-02 RN-07 / RN04)
INSERT INTO plazos_tipo_caso (tipo_caso, dias_habiles, descripcion) VALUES
    ('Q', 15, 'Queja'),
    ('R', 20, 'Reclamo'),
    ('D', 30, 'Denuncia'),
    ('S', 30, 'Sugerencia');

-- Feriados de ejemplo (CU-15 / CU-04)
INSERT INTO dias_no_laborables (fecha, descripcion) VALUES
    ('2026-01-01', 'Año Nuevo'),
    ('2026-05-01', 'Día del Trabajo'),
    ('2026-09-15', 'Independencia'),
    ('2026-12-25', 'Navidad');

-- Parámetros de sistema (CU-15)
INSERT INTO parametros_sistema (clave, valor, grupo, descripcion) VALUES
    ('institucion.nombre',           'Municipalidad',                 'PORTAL',     'Nombre institucional visible en CU-00'),
    ('institucion.telefono',         '(502) 2222-0000',               'PORTAL',     'Teléfono de contacto'),
    ('institucion.correo',           'qrds@municipalidad.gob.gt',     'PORTAL',     'Correo de contacto'),
    ('institucion.horario',          'Lunes a viernes, 8:00 – 16:00', 'PORTAL',     'Horario presencial'),
    ('archivos.max_mb',              '5',                             'DOCUMENTOS', 'Tamaño máximo por archivo (MB)'),
    ('archivos.max_por_caso',        '5',                             'DOCUMENTOS', 'Máximo de archivos por caso'),
    ('archivos.mime_permitidos',     'application/pdf,image/jpeg,image/png,application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'DOCUMENTOS', 'MIME allowlist'),
    ('login.max_intentos',           '5',                             'SEGURIDAD',  'Intentos fallidos antes de bloqueo'),
    ('login.bloqueo_minutos',        '15',                            'SEGURIDAD',  'Minutos de bloqueo temporal'),
    ('jwt.expiracion_horas',         '8',                             'SEGURIDAD',  'Expiración del JWT'),
    ('notificacion.max_reintentos',  '3',                             'CORREO',     'Reintentos SMTP'),
    ('notificacion.from',            'noreply@municipalidad.gob.gt',  'CORREO',     'Remitente institucional'),
    ('app.base_url',                 'http://localhost:5173',         'CORREO',     'URL base para links en correos'),
    ('consulta.rate_limit_minuto',   '20',                            'SEGURIDAD',  'Límite consultas públicas por IP/min');

-- Admin inicial (password en texto solo para desarrollo — cambiar en producción)
-- Password plano de ejemplo: AdminQRDS2026!
-- Hash BCrypt strength 12 generado para ese valor (reemplazar en prod)
INSERT INTO usuarios (
    nombre, email, password_hash, rol, area_dependencia_id,
    activo, forzar_cambio_password, token_version
) VALUES (
    'Administrador QRDS',
    'admin@municipalidad.gob.gt',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4.G2oQYqJ5Y5Y5Yu',
    'ADMIN',
    NULL,
    TRUE,
    TRUE,
    0
);

-- Agente y supervisor de ejemplo (misma área Atención)
INSERT INTO usuarios (nombre, email, password_hash, rol, area_dependencia_id, forzar_cambio_password)
SELECT
    'Supervisor Atención',
    'supervisor@municipalidad.gob.gt',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4.G2oQYqJ5Y5Y5Yu',
    'SUPERVISOR',
    id,
    TRUE
FROM areas_dependencia WHERE codigo = 'ATENCION';

INSERT INTO usuarios (nombre, email, password_hash, rol, area_dependencia_id, forzar_cambio_password)
SELECT
    'Agente Atención',
    'agente@municipalidad.gob.gt',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/X4.G2oQYqJ5Y5Y5Yu',
    'AGENTE',
    id,
    TRUE
FROM areas_dependencia WHERE codigo = 'ATENCION';

-- Plantillas de notificación (CU-08)
INSERT INTO plantillas_notificacion (codigo, asunto_template, cuerpo_html, version) VALUES
(
    'N_REGISTRO_CASO',
    'QRDS — Caso registrado {{codigo}}',
    '<p>Su caso <strong>{{codigo}}</strong> fue registrado. Tipo: {{tipo}}. Plazo estimado: {{plazo}} días hábiles.</p><p><a href="{{url_consulta}}">Consultar estado</a></p>',
    1
),
(
    'N_SEGUIMIENTO',
    'QRDS — Actualización de su caso {{codigo}}',
    '<p>Hay una nueva actualización pública en su caso <strong>{{codigo}}</strong>.</p><p>{{titulo}}</p>',
    1
),
(
    'N_RESOLUCION',
    'QRDS — Caso {{codigo}} resuelto',
    '<p>Su caso <strong>{{codigo}}</strong> fue marcado como RESUELTO.</p><p>{{resumen}}</p>',
    1
),
(
    'N_CIERRE',
    'QRDS — Caso {{codigo}} cerrado',
    '<p>Su caso <strong>{{codigo}}</strong> fue archivado (CERRADO).</p>',
    1
),
(
    'N_ASIGNACION',
    'QRDS — Nuevo caso asignado {{codigo}}',
    '<p>Se le asignó el caso <strong>{{codigo}}</strong>.</p>',
    1
),
(
    'N_REASIGNACION',
    'QRDS — Caso reasignado {{codigo}}',
    '<p>El caso <strong>{{codigo}}</strong> fue reasignado. Justificación registrada en el sistema.</p>',
    1
);

-- Caso demo para consulta pública (CU-00 / CU-07)
INSERT INTO casos (
    codigo_seguimiento, tipo_caso, nombre_ciudadano, email_ciudadano, telefono,
    area_dependencia_id, area_dependencia_texto, descripcion,
    es_anonimo, acepta_privacidad, estado, avance_porcentaje,
    fecha_limite_respuesta, ip_registro
)
SELECT
    'Q-2026-00001',
    'Q',
    'Ciudadano Demo',
    'ciudadano@email.com',
    '55551234',
    id,
    nombre,
    'Solicito revisión del servicio de recolección de basura en mi colonia. El camión no pasa desde hace dos semanas y hay acumulación en la esquina principal.',
    FALSE,
    TRUE,
    'EN_PROCESO',
    40,
    CURRENT_DATE + 15,
    '127.0.0.1'
FROM areas_dependencia WHERE codigo = 'SERVICIOS';

INSERT INTO correlativos_caso (tipo_caso, anio, ultimo) VALUES ('Q', 2026, 1)
ON CONFLICT (tipo_caso, anio) DO UPDATE SET ultimo = GREATEST(correlativos_caso.ultimo, 1);

UPDATE casos
SET agente_asignado_id = u.id
FROM usuarios u
WHERE casos.codigo_seguimiento = 'Q-2026-00001'
  AND u.email = 'agente@municipalidad.gob.gt';
