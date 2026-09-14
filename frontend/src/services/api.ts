import { getToken, marcarSesionExpirada } from './auth'

export type SistemaEstado = {
  estado: 'UP' | 'DOWN'
  mensaje?: string
}

export type SeguimientoPublico = {
  id: number
  tipo: string
  titulo: string
  descripcion: string
  porcentajeAvance: number | null
  creadoEn: string
}

export type CasoPublico = {
  codigoSeguimiento: string
  tipoCaso?: string
  tipo: string
  estado: string
  fechaRegistro: string
  ultimaActualizacion?: string
  avancePorcentaje?: number
  seguimientos?: SeguimientoPublico[]
}

export type Rol = 'ADMIN' | 'SUPERVISOR' | 'AGENTE' | 'CIUDADANO'

export type UsuarioAutenticado = {
  id: number
  userId: number
  nombre: string
  email: string
  telefono?: string | null
  dpi?: string | null
  rol: Rol
  areaDependencia?: string | null
  permisos?: string[]
}

type LoginResponse = {
  token: string
  usuario: UsuarioAutenticado
}

export type ApiError = Error & {
  status?: number
  codigo?: string
  conexion?: boolean
  errores?: Record<string, string>
  codigoExistente?: string
}

const TIMEOUT_MS = 8000
const RUTAS_PUBLICAS = [
  '/api/auth/login',
  '/api/auth/ciudadano/',
  '/api/casos/captcha',
  '/api/casos/publico',
  '/api/sistema/estado',
  '/api/bitacora/acceso-publico',
  '/api/catalogos/',
]

function esRutaPublica(input: RequestInfo | URL) {
  const url = String(input)
  return RUTAS_PUBLICAS.some((ruta) => url.includes(ruta))
}

function errorDeConexion(): ApiError {
  const error = new Error(
    'Error de conexión. Verifique su conexión a internet e intente nuevamente.',
  ) as ApiError
  error.conexion = true
  return error
}

async function parsearJson<T>(res: Response): Promise<T> {
  const body = await res.json().catch(() => ({}))
  if (!res.ok) {
    const error = new Error(
      body.mensaje || body.error || 'No fue posible completar la solicitud.',
    ) as ApiError
    error.status = res.status
    error.codigo = body.codigo
    error.errores = body.errores
    error.codigoExistente = body.codigoExistente
    throw error
  }
  return body as T
}

/** Interceptor: adjunta JWT y detecta sesión expirada (FA-07). */
export async function apiFetch(
  input: RequestInfo | URL,
  init: RequestInit & { timeoutMs?: number } = {},
) {
  const { timeoutMs = TIMEOUT_MS, ...fetchInit } = init
  const headers = new Headers(fetchInit.headers)
  const token = getToken()
  if (token && !headers.has('Authorization') && !esRutaPublica(input)) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (fetchInit.body instanceof FormData) {
    headers.delete('Content-Type')
  }

  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    const res = await fetch(input, {
      ...fetchInit,
      headers,
      signal: fetchInit.signal ?? controller.signal,
    })

    if (res.status === 401 && !esRutaPublica(input)) {
      const body = await res.clone().json().catch(() => ({}))
      if (body.codigo === 'TOKEN_INVALIDO') {
        marcarSesionExpirada()
      }
    }

    return res
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw errorDeConexion()
    }
    if (error instanceof TypeError) {
      throw errorDeConexion()
    }
    throw error
  } finally {
    window.clearTimeout(timeout)
  }
}

export async function getSistemaEstado(): Promise<SistemaEstado> {
  const res = await apiFetch('/api/sistema/estado', { method: 'GET' })
  if (!res.ok) throw new Error('Servicio no disponible')
  return res.json()
}

export async function registrarAccesoPublico(accion: string, resultado = 'OK') {
  try {
    await apiFetch('/api/bitacora/acceso-publico', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ accion, resultado }),
    })
  } catch {
    // No bloquear la UX del portal si falla el registro de bitácora
  }
}

export async function consultarCasoPublico(codigo: string): Promise<CasoPublico> {
  const res = await apiFetch(`/api/casos/publico/${encodeURIComponent(codigo)}`)
  return parsearJson<CasoPublico>(res)
}

export async function iniciarSesion(
  email: string,
  password: string,
): Promise<LoginResponse> {
  const res = await apiFetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  return parsearJson<LoginResponse>(res)
}

export async function cerrarSesion() {
  const res = await apiFetch('/api/auth/logout', { method: 'POST' })
  return parsearJson<{ mensaje: string }>(res)
}

export async function obtenerSesion() {
  const res = await apiFetch('/api/auth/me')
  return parsearJson<{ usuario: UsuarioAutenticado }>(res)
}

export type RegistroCiudadanoPayload = {
  nombre: string
  email: string
  telefono: string
  dpi: string
  password: string
  confirmarPassword: string
  aceptaPrivacidad: boolean
  captchaId: string
  captchaRespuesta: string
}

export type CuentaCiudadano = {
  perfil: {
    id: number
    nombre: string
    email: string
    telefono?: string | null
    dpi?: string | null
    rol: Rol
    creadoEn: string
    emailVerificado: boolean
  }
  casos: {
    codigoSeguimiento: string
    tipo: string
    estado: string
    fechaRegistro: string
    ultimaActualizacion?: string
    avancePorcentaje: number
    areaDependencia?: string | null
  }[]
}

export async function iniciarRegistroCiudadano(payload: RegistroCiudadanoPayload) {
  const res = await apiFetch('/api/auth/ciudadano/verificar-inicio', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return parsearJson<{ registroId: string; mensaje: string }>(res)
}

export async function confirmarRegistroCiudadano(registroId: string, codigo: string) {
  const res = await apiFetch('/api/auth/ciudadano/confirmar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ registroId, codigo }),
  })
  return parsearJson<{ mensaje: string }>(res)
}

export async function obtenerCuentaCiudadano() {
  const res = await apiFetch('/api/ciudadano/cuenta')
  return parsearJson<CuentaCiudadano>(res)
}

export type AreaDependencia = {
  codigo: string
  nombre: string
}

export type CaptchaPublico = {
  captchaId: string
  pregunta: string
}

export type RegistroCasoPayload = {
  tipoCaso: 'Q' | 'R' | 'D' | 'S'
  nombreCiudadano: string
  email: string
  telefono: string
  areaDependencia?: string
  descripcion: string
  denunciado?: string
  esAnonimo: boolean
  aceptaPrivacidad: boolean
  captchaId: string
  captchaRespuesta: string
  forzarRegistro?: boolean
}

export type RegistroCasoRespuesta = {
  codigoSeguimiento: string
  mensaje: string
  fechaRegistro: string
  tipoCaso: string
  tipo: string
  estado: string
  plazoEstimado: string
  correoEnviado: boolean
  esAnonimo: boolean
}

export function codigoSeguimientoValido(codigo: string) {
  const valor = codigo.trim().toUpperCase()
  return /^[QRDS]-\d{4}-\d{5}$/.test(valor) || /^[A-Z0-9]{10}$/.test(valor)
}

export async function obtenerAreas(): Promise<AreaDependencia[]> {
  const res = await apiFetch('/api/catalogos/areas')
  return parsearJson<AreaDependencia[]>(res)
}

export async function obtenerCaptcha(): Promise<CaptchaPublico> {
  const res = await apiFetch('/api/casos/captcha')
  return parsearJson<CaptchaPublico>(res)
}

export async function registrarCasoPublico(payload: RegistroCasoPayload) {
  const res = await apiFetch('/api/casos/publico', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    timeoutMs: 12000,
  })
  return parsearJson<RegistroCasoRespuesta>(res)
}

export async function adjuntarDocumentosCaso(codigo: string, archivos: File[]) {
  const form = new FormData()
  for (const archivo of archivos) form.append('archivos', archivo)
  const res = await apiFetch(`/api/casos/publico/${encodeURIComponent(codigo)}/documentos`, {
    method: 'POST',
    body: form,
    timeoutMs: 20000,
  })
  return parsearJson<{
    archivosSubidos: { id?: number; nombreArchivo: string }[]
    rechazados: { nombre: string; motivo: string }[]
  }>(res)
}

export type DocumentoCaso = {
  id: number
  nombreArchivo: string
  tipoMime: string
  tamanioBytes: number
  subidoEn: string
  origen?: string
}

export async function listarDocumentosCaso(casoId: number) {
  const res = await apiFetch(`/api/casos/${casoId}/documentos`)
  return parsearJson<DocumentoCaso[]>(res)
}

export async function adjuntarDocumentosInterno(casoId: number, archivos: File[]) {
  const form = new FormData()
  for (const archivo of archivos) form.append('archivos', archivo)
  const res = await apiFetch(`/api/casos/${casoId}/documentos`, {
    method: 'POST',
    body: form,
    timeoutMs: 20000,
  })
  return parsearJson<{
    archivosSubidos: { id: number; nombreArchivo: string }[]
    rechazados: { nombre: string; motivo: string }[]
  }>(res)
}

export type PlazoCaso = {
  diasRestantes: number | null
  semaforo: 'verde' | 'amarillo' | 'rojo' | 'gris' | string
  vencido: boolean
}

export type CasoResumen = {
  id: number
  codigoSeguimiento: string
  tipoCaso: string
  tipo: string
  ciudadano: string
  area: string
  areaNombre: string
  estado: string
  fechaRegistro: string
  agenteAsignadoId: number | null
  agenteNombre: string
  prioridad: string
  avancePorcentaje?: number
  escalado?: boolean
  fechaLimiteRespuesta?: string | null
  plazo?: PlazoCaso
}

export type CasoDetalle = CasoResumen & {
  nombreCiudadano: string | null
  emailCiudadano: string | null
  telefono: string | null
  esAnonimo: boolean
  descripcion: string
  denunciado: string | null
  transicionesPermitidas: string[]
  fechaUltimaActualizacion: string
  documentos: {
    id: number
    nombreArchivo: string
    tipoMime: string
    tamanioBytes: number
    subidoEn: string
  }[]
  observaciones: {
    id: number
    texto: string
    creadoEn: string
    usuarioNombre: string
  }[]
  historial: {
    tipoEvento: string
    estadoAnterior: string | null
    estadoNuevo: string | null
    descripcion: string
    fechaHora: string
    usuarioNombre: string
  }[]
  fechaProrroga?: string | null
}

export type PaginaCasos = {
  content: CasoResumen[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type AgenteOpcion = {
  id: number
  nombre: string
  email: string
  areaDependencia: string
  areaNombre: string
}

export type FiltrosBandeja = {
  estado?: string
  tipo?: string
  area?: string
  codigo?: string
  desde?: string
  hasta?: string
  sinAsignar?: boolean
  orden?: string
  direccion?: 'asc' | 'desc'
  page?: number
  size?: number
}

function queryBandeja(filtros: FiltrosBandeja) {
  const params = new URLSearchParams()
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor === undefined || valor === '' || valor === false) continue
    params.set(clave, String(valor))
  }
  return params.toString()
}

export async function listarCasos(filtros: FiltrosBandeja = {}) {
  const query = queryBandeja(filtros)
  const res = await apiFetch(`/api/casos${query ? `?${query}` : ''}`)
  return parsearJson<PaginaCasos>(res)
}

export async function obtenerCaso(id: number) {
  const res = await apiFetch(`/api/casos/${id}`)
  return parsearJson<CasoDetalle>(res)
}

export async function modificarCaso(
  id: number,
  payload: {
    nombreCiudadano?: string
    email?: string
    telefono?: string
    areaDependencia?: string
    descripcion?: string
    denunciado?: string
    prioridad?: string
    motivo: string
  },
) {
  const res = await apiFetch(`/api/casos/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return parsearJson<{ mensaje: string; caso: CasoDetalle }>(res)
}

export async function listarAgentes(area?: string) {
  const query = area ? `?area=${encodeURIComponent(area)}` : ''
  const res = await apiFetch(`/api/agentes${query}`)
  return parsearJson<AgenteOpcion[]>(res)
}

export async function asignarCaso(id: number, agenteId: number) {
  const res = await apiFetch(`/api/casos/${id}/asignar`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ agenteId }),
  })
  return parsearJson<{ mensaje: string; caso: CasoDetalle }>(res)
}

export async function reasignarCaso(id: number, agenteId: number, motivo: string) {
  const res = await apiFetch(`/api/casos/${id}/reasignar`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ agenteId, motivo }),
  })
  return parsearJson<{ mensaje: string; caso: CasoDetalle }>(res)
}

export async function cambiarEstadoCaso(id: number, nuevoEstado: string, observacion: string) {
  const res = await apiFetch(`/api/casos/${id}/estado`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nuevoEstado, observacion }),
  })
  return parsearJson<{ mensaje: string; caso: CasoDetalle }>(res)
}

export async function agregarObservacionCaso(id: number, texto: string) {
  const res = await apiFetch(`/api/casos/${id}/observaciones`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ texto }),
  })
  return parsearJson<{ mensaje: string; caso: CasoDetalle }>(res)
}

export async function anularCaso(id: number, justificacion: string) {
  const res = await apiFetch(`/api/casos/${id}/anular`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ justificacion }),
  })
  return parsearJson<{ mensaje: string; caso: CasoDetalle }>(res)
}

export async function cerrarCaso(id: number, observacion: string) {
  const res = await apiFetch(`/api/casos/${id}/cerrar`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ observacion }),
  })
  return parsearJson<{ mensaje: string; caso: CasoDetalle }>(res)
}

export async function descargarDocumentoCaso(casoId: number, docId: number, nombre: string) {
  const res = await apiFetch(`/api/casos/${casoId}/documentos/${docId}`)
  if (!res.ok) {
    throw new Error('No fue posible descargar el documento.')
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  enlace.click()
  URL.revokeObjectURL(url)
}

export type TipoSeguimiento = 'PUBLICA' | 'INTERNA' | 'CORRECCION'

export type SeguimientoCaso = {
  id: number
  tipo: TipoSeguimiento
  titulo: string
  descripcion: string
  porcentajeAvance: number | null
  creadoEn: string
  seguimientoPadreId: number | null
  notificado: boolean
  usuarioNombre?: string
  usuarioId?: number
  adjunto: { id: number; nombreArchivo: string } | null
}

export type ListaSeguimientos = {
  caso?: CasoDetalle
  seguimientos: SeguimientoCaso[]
  codigoSeguimiento?: string
  avancePorcentaje?: number
}

export async function listarSeguimientos(casoId: number, incluirInternos = true) {
  const query = incluirInternos ? '' : '?incluirInternos=false'
  const res = await apiFetch(`/api/casos/${casoId}/seguimientos${query}`)
  return parsearJson<ListaSeguimientos>(res)
}

export async function listarSeguimientosPublicos(codigo: string) {
  const res = await apiFetch(`/api/casos/publico/${encodeURIComponent(codigo)}/seguimientos`)
  return parsearJson<{
    codigoSeguimiento: string
    avancePorcentaje: number
    seguimientos: SeguimientoCaso[]
  }>(res)
}

export async function registrarSeguimiento(casoId: number, form: FormData) {
  const res = await apiFetch(`/api/casos/${casoId}/seguimientos`, {
    method: 'POST',
    body: form,
    timeoutMs: 20000,
  })
  return parsearJson<{
    mensaje: string
    avisoCorreo: string | null
    seguimiento: SeguimientoCaso
    caso: CasoDetalle
    seguimientos: SeguimientoCaso[]
  }>(res)
}

export async function registrarProrroga(casoId: number, diasHabiles: number, justificacion: string) {
  const res = await apiFetch(`/api/casos/${casoId}/prorroga`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ diasHabiles, justificacion }),
  })
  return parsearJson<{ mensaje: string; caso: CasoDetalle }>(res)
}

export async function escalarCaso(casoId: number, motivo: string) {
  const res = await apiFetch(`/api/casos/${casoId}/escalar`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ motivo }),
  })
  return parsearJson<{ mensaje: string; caso: CasoDetalle }>(res)
}

export async function descargarEvidenciaSeguimiento(
  casoId: number,
  seguimientoId: number,
  nombre: string,
) {
  const res = await apiFetch(`/api/casos/${casoId}/seguimientos/${seguimientoId}/documento`)
  if (!res.ok) {
    throw new Error('No fue posible descargar la evidencia.')
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  enlace.click()
  URL.revokeObjectURL(url)
}
