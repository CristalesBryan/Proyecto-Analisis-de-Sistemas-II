# Base de datos QRDS (PostgreSQL)

Esquema alineado a **CU-00 … CU-16** y a las Reglas de Negocio del proyecto.

## Cómo crear la BD

```bash
createdb qrds
psql -d qrds -f schema.sql
psql -d qrds -f seed.sql
```

Con Docker:

```bash
docker run --name qrds-pg -e POSTGRES_PASSWORD=qrds -e POSTGRES_DB=qrds -p 5432:5432 -d postgres:16
# luego ejecutar schema.sql y seed.sql contra localhost:5432
```

## Tablas por caso de uso

| CU | Tablas / vistas principales |
|----|-----------------------------|
| CU-00 Portal | `bitacora_accesos`, `bitacora_consultas_publicas`, `parametros_sistema` |
| CU-01 Login | `usuarios`, `bitacora_accesos` |
| CU-02 Registrar | `casos`, `correlativos_caso`, `documentos_caso`, `areas_dependencia`, `plazos_tipo_caso` |
| CU-03 Gestionar | `casos`, `bitacora_casos` |
| CU-04 Seguimiento | `seguimientos_caso`, `documentos_seguimiento`, `dias_no_laborables` |
| CU-05 Resolver | `resoluciones_caso` |
| CU-06 Cerrar | `cierres_caso` |
| CU-07 Consulta pública | `casos` (campos no sensibles), `seguimientos_caso` tipo `PUBLICA` |
| CU-08 Notificaciones | `notificaciones`, `plantillas_notificacion` |
| CU-09 Reportes | `reportes_generados` |
| CU-10 Usuarios | `usuarios`, `areas_dependencia`, `bitacora_sistema` |
| CU-11 Bitácoras | `v_bitacora_eventos` (+ tablas `bitacora_*`) |
| CU-12 Documentos | `documentos_caso`, `documentos_seguimiento` |
| CU-13 Historial | `v_historial_caso` |
| CU-14 Reasignar | `reasignaciones_caso` |
| CU-15 Parámetros | `parametros_sistema`, `areas_dependencia`, `plazos_tipo_caso`, `dias_no_laborables` |
| CU-16 Dashboard | agregaciones sobre `casos` / `usuarios` |

## Reglas importantes reflejadas en el esquema

- **Roles:** `ADMIN`, `SUPERVISOR`, `AGENTE` (ciudadano = público, sin fila en `usuarios`)
- **Estados:** `RECIBIDO → EN_REVISION → EN_PROCESO → RESUELTO → CERRADO` (+ `ANULADO`)
- **Código de seguimiento:** `Q|R|D|S-AAAA-#####` vía `generar_codigo_seguimiento()`
- **Bitácoras y seguimientos:** pensados como solo `INSERT` (inmutables)
- **Plazos:** Q=15, R=20, D=30, S=30 días hábiles (configurables)

## Datos demo (seed)

| Usuario | Rol | Email |
|---------|-----|-------|
| Admin | ADMIN | `admin@municipalidad.gob.gt` |
| Supervisor | SUPERVISOR | `supervisor@municipalidad.gob.gt` |
| Agente | AGENTE | `agente@municipalidad.gob.gt` |

Caso de prueba para consulta pública: **`Q-2026-00001`**

> Los hashes BCrypt del seed son placeholders. Al conectar Spring Boot / Express, regenera con BCrypt strength 12.
