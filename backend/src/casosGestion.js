import {
  AREAS,
  TIPOS,
  buscarCasoPorId,
  casos,
  documentos,
  nombreArea,
  parchearCaso,
} from './casosPublicos.js'
import { evaluarPlazo } from './plazos.js'

const TRANSICIONES = {
  RECIBIDO: ['EN_REVISION', 'ANULADO'],
  EN_REVISION: ['EN_PROCESO', 'ANULADO'],
  EN_PROCESO: ['RESUELTO', 'EN_REVISION'],
  RESUELTO: ['CERRADO', 'EN_PROCESO'],
  CERRADO: [],
  ANULADO: [],
}

const ESTADOS_NOTIFICABLES = new Set(['EN_PROCESO', 'RESUELTO', 'CERRADO', 'ANULADO'])
const ESTADOS_FINALES = new Set(['CERRADO', 'ANULADO'])

const observaciones = []
const bitacoraCasos = []
let siguienteObservacionId = 1
let siguienteBitacoraCasoId = 1

export function usuarioDesdeSesion(usuarios, sesion) {
  return usuarios.find((item) => item.id === sesion.userId) || null
}

function puedeVerCaso(usuario, caso) {
  if (usuario.rol === 'ADMIN') return true
  if (usuario.rol === 'SUPERVISOR') return caso.areaDependencia === usuario.areaDependencia
  if (usuario.rol === 'AGENTE') return caso.agenteAsignadoId === usuario.id
  return false
}

export function registrarBitacoraCaso({
  req,
  usuario,
  caso,
  tipoEvento,
  estadoAnterior = null,
  estadoNuevo = null,
  descripcion,
}) {
  const registro = Object.freeze({
    id: siguienteBitacoraCasoId++,
    casoId: caso.id,
    usuarioId: usuario.id,
    tipoEvento,
    estadoAnterior,
    estadoNuevo,
    descripcion,
    fechaHora: new Date().toISOString(),
    ipCliente:
      req.headers['x-forwarded-for']?.toString().split(',')[0].trim() ||
      req.socket.remoteAddress ||
      '0.0.0.0',
    usuarioNombre: usuario.nombre,
  })
  bitacoraCasos.push(registro)
  return registro
}

function notificarCambioEstado(caso, estadoNuevo) {
  if (!ESTADOS_NOTIFICABLES.has(estadoNuevo) || caso.esAnonimo || !caso.emailCiudadano) {
    return false
  }
  console.log(
    `[QRDS correo estado → ${caso.emailCiudadano}] ${caso.codigoSeguimiento} ahora está ${estadoNuevo}`,
  )
  return true
}

function agenteResumen(usuarios, id) {
  if (!id) return null
  const agente = usuarios.find((item) => item.id === id)
  return agente ? { id: agente.id, nombre: agente.nombre } : { id, nombre: 'Agente no disponible' }
}

function resumenCaso(caso, usuarios) {
  const agente = agenteResumen(usuarios, caso.agenteAsignadoId)
  return {
    id: caso.id,
    codigoSeguimiento: caso.codigoSeguimiento,
    tipoCaso: caso.tipoCaso,
    tipo: TIPOS[caso.tipoCaso].nombre,
    ciudadano: caso.esAnonimo || !caso.nombreCiudadano ? 'Anónimo' : caso.nombreCiudadano,
    area: caso.areaDependencia,
    areaNombre: nombreArea(caso.areaDependencia),
    estado: caso.estado,
    fechaRegistro: caso.fechaRegistro.slice(0, 10),
    agenteAsignadoId: caso.agenteAsignadoId,
    agenteNombre: agente?.nombre || 'Sin asignar',
    prioridad: caso.prioridad || 'NORMAL',
    avancePorcentaje: caso.avancePorcentaje || 0,
    escalado: Boolean(caso.escalado),
    fechaLimiteRespuesta: caso.fechaLimiteRespuesta || null,
    plazo: evaluarPlazo(caso.fechaLimiteRespuesta),
  }
}

export function detalleCaso(caso, usuarios) {
  const docs = documentos
    .filter((doc) => doc.casoId === caso.id)
    .map((doc) => ({
      id: doc.id,
      nombreArchivo: doc.nombreArchivo,
      tipoMime: doc.tipoMime,
      tamanioBytes: doc.tamanioBytes,
      subidoEn: doc.subidoEn,
    }))

  const notas = observaciones
    .filter((item) => item.casoId === caso.id)
    .map((item) => ({
      id: item.id,
      texto: item.texto,
      creadoEn: item.creadoEn,
      usuarioNombre: item.usuarioNombre,
    }))

  const historial = [
    {
      tipoEvento: 'REGISTRO',
      estadoAnterior: null,
      estadoNuevo: 'RECIBIDO',
      descripcion: 'Caso registrado por el ciudadano',
      fechaHora: caso.fechaRegistro,
      usuarioNombre: 'Ciudadano',
    },
    ...bitacoraCasos.filter((item) => item.casoId === caso.id),
  ]

  return {
    ...resumenCaso(caso, usuarios),
    nombreCiudadano: caso.esAnonimo ? null : caso.nombreCiudadano,
    emailCiudadano: caso.esAnonimo ? null : caso.emailCiudadano,
    telefono: caso.esAnonimo ? null : caso.telefono,
    esAnonimo: caso.esAnonimo,
    descripcion: caso.descripcion,
    denunciado: caso.denunciado,
    transicionesPermitidas: TRANSICIONES[caso.estado] || [],
    fechaUltimaActualizacion: caso.fechaUltimaActualizacion.slice(0, 10),
    fechaLimiteRespuesta: caso.fechaLimiteRespuesta || null,
    fechaProrroga: caso.fechaProrroga || null,
    avancePorcentaje: caso.avancePorcentaje || 0,
    escalado: Boolean(caso.escalado),
    documentos: docs,
    observaciones: notas,
    historial,
    plazo: evaluarPlazo(caso.fechaLimiteRespuesta),
  }
}

function casosVisibles(usuario) {
  return casos.filter((caso) => puedeVerCaso(usuario, caso))
}

export function obtenerCasoAutorizado(req, res, usuarios, respuestaError) {
  const usuario = usuarioDesdeSesion(usuarios, req.sesion)
  if (!usuario || !usuario.activo) {
    respuestaError(res, 403, 'ACCESO_DENEGADO', 'No tiene permisos para gestionar este caso.')
    return null
  }

  const caso = buscarCasoPorId(req.params.id)
  if (!caso) {
    respuestaError(res, 404, 'CASO_NO_ENCONTRADO', 'Caso no encontrado.')
    return null
  }

  if (!puedeVerCaso(usuario, caso)) {
    registrarBitacoraCaso({
      req,
      usuario,
      caso,
      tipoEvento: 'ACCESO_DENEGADO',
      descripcion: 'Intento de acceso a un caso fuera de su visibilidad',
    })
    respuestaError(res, 403, 'ACCESO_DENEGADO', 'No tiene permisos para gestionar este caso.')
    return null
  }

  return { usuario, caso }
}

export function registrarRutasGestion(app, { usuarios, autenticarToken, respuestaError }) {
  app.get('/api/agentes', autenticarToken, (req, res) => {
    const actor = usuarioDesdeSesion(usuarios, req.sesion)
    if (!actor) {
      return respuestaError(res, 403, 'ACCESO_DENEGADO', 'No tiene permisos para gestionar este caso.')
    }

    const areaFiltro = String(req.query.area || '').toUpperCase()
    let lista = usuarios.filter((item) => item.rol === 'AGENTE' && item.activo)
    if (actor.rol === 'SUPERVISOR') {
      lista = lista.filter((item) => item.areaDependencia === actor.areaDependencia)
    }
    if (areaFiltro) lista = lista.filter((item) => item.areaDependencia === areaFiltro)

    res.json(
      lista.map((item) => ({
        id: item.id,
        nombre: item.nombre,
        email: item.email,
        areaDependencia: item.areaDependencia,
        areaNombre: nombreArea(item.areaDependencia),
      })),
    )
  })

  app.get('/api/casos', autenticarToken, (req, res) => {
    const actor = usuarioDesdeSesion(usuarios, req.sesion)
    if (!actor) {
      return respuestaError(res, 403, 'ACCESO_DENEGADO', 'No tiene permisos para gestionar este caso.')
    }

    const estado = String(req.query.estado || '').toUpperCase()
    const tipo = String(req.query.tipo || '').toUpperCase()
    const area = String(req.query.area || '').toUpperCase()
    const codigo = String(req.query.codigo || '').trim().toUpperCase()
    const desde = req.query.desde ? String(req.query.desde) : ''
    const hasta = req.query.hasta ? String(req.query.hasta) : ''
    const sinAsignar = String(req.query.sinAsignar || '') === 'true'
    const orden = String(req.query.orden || 'fechaRegistro')
    const direccion = String(req.query.direccion || 'desc').toLowerCase() === 'asc' ? 1 : -1
    const page = Math.max(1, Number(req.query.page) || 1)
    const size = Math.min(100, Math.max(1, Number(req.query.size) || 20))

    let lista = casosVisibles(actor)
    if (estado) lista = lista.filter((caso) => caso.estado === estado)
    if (tipo) lista = lista.filter((caso) => caso.tipoCaso === tipo)
    if (area) lista = lista.filter((caso) => caso.areaDependencia === area)
    if (codigo) {
      lista = lista.filter(
        (caso) =>
          caso.codigoSeguimiento.includes(codigo) ||
          (caso.codigoLegacy && caso.codigoLegacy.includes(codigo)),
      )
    }
    if (desde) lista = lista.filter((caso) => caso.fechaRegistro.slice(0, 10) >= desde)
    if (hasta) lista = lista.filter((caso) => caso.fechaRegistro.slice(0, 10) <= hasta)
    if (sinAsignar) lista = lista.filter((caso) => !caso.agenteAsignadoId)

    const campos = {
      fechaRegistro: (caso) => caso.fechaRegistro,
      codigo: (caso) => caso.codigoSeguimiento,
      estado: (caso) => caso.estado,
      tipo: (caso) => caso.tipoCaso,
      area: (caso) => caso.areaDependencia,
    }
    const getter = campos[orden] || campos.fechaRegistro
    lista = [...lista].sort((a, b) => {
      const va = getter(a)
      const vb = getter(b)
      if (va < vb) return -1 * direccion
      if (va > vb) return 1 * direccion
      return 0
    })

    const totalElements = lista.length
    const totalPages = Math.max(1, Math.ceil(totalElements / size))
    const pageClamped = Math.min(page, totalPages)
    const content = lista
      .slice((pageClamped - 1) * size, pageClamped * size)
      .map((caso) => resumenCaso(caso, usuarios))

    res.json({ content, page: pageClamped, size, totalElements, totalPages })
  })

  app.get('/api/casos/:id', autenticarToken, (req, res) => {
    const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
    if (!ctx) return
    res.json(detalleCaso(ctx.caso, usuarios))
  })

  app.post('/api/casos/:id/asignar', autenticarToken, (req, res) => {
    const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
    if (!ctx) return
    const { usuario, caso } = ctx

    if (usuario.rol !== 'ADMIN' && usuario.rol !== 'SUPERVISOR') {
      return respuestaError(res, 403, 'ACCESO_DENEGADO', 'No tiene permisos para gestionar este caso.')
    }

    const agenteId = Number(req.body?.agenteId)
    const agentesArea = usuarios.filter(
      (item) => item.rol === 'AGENTE' && item.activo && item.areaDependencia === caso.areaDependencia,
    )
    if (agentesArea.length === 0) {
      return respuestaError(
        res,
        409,
        'SIN_AGENTES',
        'No hay agentes disponibles en esta área. Contacte al administrador.',
      )
    }

    const agente = agentesArea.find((item) => item.id === agenteId)
    if (!agente) {
      return respuestaError(
        res,
        400,
        'AGENTE_INVALIDO',
        'Seleccione un agente activo del área del caso.',
      )
    }

    const estadoAnterior = caso.estado
    const cambiaEstado = caso.estado === 'RECIBIDO'
    const actualizado = parchearCaso(caso.id, {
      agenteAsignadoId: agente.id,
      estado: cambiaEstado ? 'EN_REVISION' : caso.estado,
    })

    registrarBitacoraCaso({
      req,
      usuario,
      caso: actualizado,
      tipoEvento: 'ASIGNACION',
      estadoAnterior,
      estadoNuevo: actualizado.estado,
      descripcion: `Asignado a ${agente.nombre}`,
    })

    res.json({
      mensaje: 'Caso actualizado correctamente.',
      caso: detalleCaso(actualizado, usuarios),
    })
  })

  app.post('/api/casos/:id/reasignar', autenticarToken, (req, res) => {
    const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
    if (!ctx) return
    const { usuario, caso } = ctx

    if (usuario.rol !== 'ADMIN' && usuario.rol !== 'SUPERVISOR') {
      return respuestaError(res, 403, 'ACCESO_DENEGADO', 'No tiene permisos para gestionar este caso.')
    }
    if (!caso.agenteAsignadoId) {
      return respuestaError(res, 400, 'NO_ASIGNADO', 'El caso aún no tiene agente. Use asignar.')
    }

    const motivo = String(req.body?.motivo || '').trim()
    if (motivo.length < 10) {
      return respuestaError(res, 400, 'VALIDACION', 'El motivo de reasignación debe tener al menos 10 caracteres.')
    }

    const agentesArea = usuarios.filter(
      (item) => item.rol === 'AGENTE' && item.activo && item.areaDependencia === caso.areaDependencia,
    )
    if (agentesArea.length === 0) {
      return respuestaError(
        res,
        409,
        'SIN_AGENTES',
        'No hay agentes disponibles en esta área. Contacte al administrador.',
      )
    }

    const agenteId = Number(req.body?.agenteId)
    const agente = agentesArea.find((item) => item.id === agenteId)
    if (!agente) {
      return respuestaError(res, 400, 'AGENTE_INVALIDO', 'Seleccione un agente activo del área del caso.')
    }
    if (agente.id === caso.agenteAsignadoId) {
      return respuestaError(res, 400, 'AGENTE_INVALIDO', 'Seleccione un agente distinto al actual.')
    }

    const anterior = agenteResumen(usuarios, caso.agenteAsignadoId)
    const actualizado = parchearCaso(caso.id, { agenteAsignadoId: agente.id })
    registrarBitacoraCaso({
      req,
      usuario,
      caso: actualizado,
      tipoEvento: 'REASIGNACION',
      estadoAnterior: actualizado.estado,
      estadoNuevo: actualizado.estado,
      descripcion: `Reasignado de ${anterior?.nombre || 'N/D'} a ${agente.nombre}. Motivo: ${motivo}`,
    })
    console.log(`[QRDS aviso] Reasignación ${actualizado.codigoSeguimiento}: ${anterior?.nombre} → ${agente.nombre}`)

    res.json({
      mensaje: 'Caso actualizado correctamente.',
      caso: detalleCaso(actualizado, usuarios),
    })
  })

  app.patch('/api/casos/:id/estado', autenticarToken, (req, res) => {
    const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
    if (!ctx) return
    const { usuario, caso } = ctx
    const nuevoEstado = String(req.body?.nuevoEstado || '').toUpperCase()
    const observacion = String(req.body?.observacion || '').trim()

    if (ESTADOS_FINALES.has(caso.estado)) {
      return respuestaError(
        res,
        400,
        'ESTADO_FINAL',
        'Un caso CERRADO o ANULADO no admite cambios de estado.',
      )
    }
    if (nuevoEstado === 'ANULADO') {
      return respuestaError(res, 403, 'ACCESO_DENEGADO', 'La anulación debe realizarse con la acción Anular caso.')
    }
    if (!(TRANSICIONES[caso.estado] || []).includes(nuevoEstado)) {
      return respuestaError(
        res,
        400,
        'TRANSICION_INVALIDA',
        'Transición de estado no permitida. Consulte el ciclo de vida del caso.',
      )
    }
    if (observacion.length < 10) {
      return respuestaError(
        res,
        400,
        'VALIDACION',
        'Los cambios de estado requieren una observación de al menos 10 caracteres.',
      )
    }

    const estadoAnterior = caso.estado
    const actualizado = parchearCaso(caso.id, { estado: nuevoEstado })
    observaciones.push({
      id: siguienteObservacionId++,
      casoId: caso.id,
      usuarioId: usuario.id,
      usuarioNombre: usuario.nombre,
      texto: observacion,
      creadoEn: new Date().toISOString(),
    })
    registrarBitacoraCaso({
      req,
      usuario,
      caso: actualizado,
      tipoEvento: 'CAMBIO_ESTADO',
      estadoAnterior,
      estadoNuevo: nuevoEstado,
      descripcion: observacion,
    })
    notificarCambioEstado(actualizado, nuevoEstado)

    res.json({
      mensaje: 'Caso actualizado correctamente.',
      caso: detalleCaso(actualizado, usuarios),
    })
  })

  app.post('/api/casos/:id/observaciones', autenticarToken, (req, res) => {
    const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
    if (!ctx) return
    const { usuario, caso } = ctx
    const texto = String(req.body?.texto || '').trim()

    if (ESTADOS_FINALES.has(caso.estado)) {
      return respuestaError(
        res,
        400,
        'ESTADO_FINAL',
        'Un caso CERRADO o ANULADO no admite nuevas observaciones.',
      )
    }
    if (texto.length < 10) {
      return respuestaError(res, 400, 'VALIDACION', 'La observación debe tener al menos 10 caracteres.')
    }

    observaciones.push({
      id: siguienteObservacionId++,
      casoId: caso.id,
      usuarioId: usuario.id,
      usuarioNombre: usuario.nombre,
      texto,
      creadoEn: new Date().toISOString(),
    })
    const actualizado = parchearCaso(caso.id, {})
    registrarBitacoraCaso({
      req,
      usuario,
      caso: actualizado,
      tipoEvento: 'OBSERVACION',
      estadoAnterior: actualizado.estado,
      estadoNuevo: actualizado.estado,
      descripcion: 'Observación interna registrada',
    })

    res.status(201).json({
      mensaje: 'Caso actualizado correctamente.',
      caso: detalleCaso(actualizado, usuarios),
    })
  })

  app.post('/api/casos/:id/anular', autenticarToken, (req, res) => {
    const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
    if (!ctx) return
    const { usuario, caso } = ctx

    if (usuario.rol !== 'ADMIN') {
      return respuestaError(res, 403, 'ACCESO_DENEGADO', 'No tiene permisos para gestionar este caso.')
    }
    if (ESTADOS_FINALES.has(caso.estado)) {
      return respuestaError(res, 400, 'ESTADO_FINAL', 'El caso ya se encuentra en un estado final.')
    }
    if (!(TRANSICIONES[caso.estado] || []).includes('ANULADO')) {
      return respuestaError(
        res,
        400,
        'TRANSICION_INVALIDA',
        'Transición de estado no permitida. Consulte el ciclo de vida del caso.',
      )
    }

    const justificacion = String(req.body?.justificacion || '').trim()
    if (justificacion.length < 20) {
      return respuestaError(
        res,
        400,
        'VALIDACION',
        'La anulación exige una justificación de al menos 20 caracteres.',
      )
    }

    const estadoAnterior = caso.estado
    const actualizado = parchearCaso(caso.id, { estado: 'ANULADO' })
    registrarBitacoraCaso({
      req,
      usuario,
      caso: actualizado,
      tipoEvento: 'ANULACION',
      estadoAnterior,
      estadoNuevo: 'ANULADO',
      descripcion: justificacion,
    })
    notificarCambioEstado(actualizado, 'ANULADO')

    res.json({
      mensaje: 'Caso actualizado correctamente.',
      caso: detalleCaso(actualizado, usuarios),
    })
  })

  app.get('/api/casos/:id/documentos/:docId', autenticarToken, (req, res) => {
    const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
    if (!ctx) return
    const doc = documentos.find(
      (item) => item.id === Number(req.params.docId) && item.casoId === ctx.caso.id,
    )
    if (!doc) {
      return respuestaError(res, 404, 'DOCUMENTO_NO_ENCONTRADO', 'Documento no encontrado.')
    }

    registrarBitacoraCaso({
      req,
      usuario: ctx.usuario,
      caso: ctx.caso,
      tipoEvento: 'DESCARGA',
      descripcion: `Descarga de ${doc.nombreArchivo}`,
    })

    res.setHeader('Content-Type', doc.tipoMime || 'application/octet-stream')
    res.setHeader(
      'Content-Disposition',
      `attachment; filename="${encodeURIComponent(doc.nombreArchivo)}"`,
    )
    res.send(doc.buffer)
  })

  app.get('/api/catalogos/gestion', autenticarToken, (_req, res) => {
    res.json({
      areas: AREAS,
      tipos: Object.entries(TIPOS).map(([codigo, valor]) => ({ codigo, nombre: valor.nombre })),
      estados: Object.keys(TRANSICIONES),
      transiciones: TRANSICIONES,
    })
  })
}
