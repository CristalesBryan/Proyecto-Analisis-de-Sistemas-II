import multer from 'multer'
import {
  buscarCasoPorCodigo,
  casos,
  documentos,
  parchearCaso,
} from './casosPublicos.js'
import {
  detalleCaso,
  obtenerCasoAutorizado,
  registrarBitacoraCaso,
} from './casosGestion.js'
import { aFechaIso, evaluarPlazo, sumarDiasHabiles } from './plazos.js'

const ESTADOS_ABIERTOS = new Set(['EN_REVISION', 'EN_PROCESO'])
const TIPOS_SEG = new Set(['PUBLICA', 'INTERNA', 'CORRECCION'])
const MAX_ARCHIVO = 5 * 1024 * 1024
const MIME_PERMITIDOS = new Set([
  'application/pdf',
  'image/jpeg',
  'image/jpg',
  'image/png',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
])
const EXTENSIONES = new Set(['pdf', 'jpg', 'jpeg', 'png', 'docx'])

const seguimientos = []
const documentosSeguimiento = []
let siguienteSeguimientoId = 1
let siguienteDocSegId = 1

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_ARCHIVO, files: 1 },
})

function extensionDe(nombre) {
  const partes = String(nombre || '').toLowerCase().split('.')
  return partes.length > 1 ? partes.at(-1) : ''
}

function archivoPermitido(file) {
  return EXTENSIONES.has(extensionDe(file.originalname)) && MIME_PERMITIDOS.has(file.mimetype)
}

function dtoSeguimiento(item, { internos }) {
  const base = {
    id: item.id,
    tipo: item.tipo,
    titulo: item.titulo,
    descripcion: item.descripcion,
    porcentajeAvance: item.porcentajeAvance,
    creadoEn: item.creadoEn,
    seguimientoPadreId: item.seguimientoPadreId,
    notificado: item.notificado,
    adjunto: item.adjunto
      ? { id: item.adjunto.id, nombreArchivo: item.adjunto.nombreArchivo }
      : null,
  }
  if (!internos) return base
  return { ...base, usuarioNombre: item.usuarioNombre, usuarioId: item.usuarioId }
}

function listaDeCaso(casoId, { internos }) {
  return seguimientos
    .filter((item) => item.casoId === casoId && (internos || item.tipo === 'PUBLICA'))
    .sort((a, b) => (a.creadoEn < b.creadoEn ? 1 : -1))
    .map((item) => dtoSeguimiento(item, { internos }))
}

function puedeRegistrar(usuario, caso, tipo) {
  if (ESTADOS_ABIERTOS.has(caso.estado)) return { ok: true }
  if (usuario.rol === 'ADMIN' && tipo === 'INTERNA') return { ok: true, excepcional: true }
  return {
    ok: false,
    mensaje: 'No se pueden registrar seguimientos en casos finalizados.',
  }
}

function notificarAvance(caso, seguimiento) {
  if (caso.esAnonimo || !caso.emailCiudadano) return { enviado: false, motivo: 'ANONIMO' }
  try {
    console.log(
      `[QRDS correo seguimiento → ${caso.emailCiudadano}]\nCódigo: ${caso.codigoSeguimiento}\n${seguimiento.titulo}\n${seguimiento.descripcion}\nEstado: ${caso.estado}`,
    )
    return { enviado: true }
  } catch (error) {
    console.error('[QRDS correo seguimiento] fallo', error)
    return { enviado: false, motivo: 'SMTP' }
  }
}

function reqSistema() {
  return { headers: {}, socket: { remoteAddress: '127.0.0.1' } }
}

export function detectarVencimientos(usuarios = []) {
  const sistema = { id: 0, nombre: 'Sistema QRDS' }
  let marcados = 0
  for (const caso of casos) {
    if (!ESTADOS_ABIERTOS.has(caso.estado) || caso.escalado) continue
    const plazo = evaluarPlazo(caso.fechaLimiteRespuesta)
    if (!plazo.vencido) continue
    const actualizado = parchearCaso(caso.id, { escalado: true, prioridad: 'ALTA' })
    registrarBitacoraCaso({
      req: reqSistema(),
      usuario: sistema,
      caso: actualizado,
      tipoEvento: 'ESCALAMIENTO',
      estadoAnterior: actualizado.estado,
      estadoNuevo: actualizado.estado,
      descripcion: 'Plazo vencido sin resolución. Prioridad ALTA y caso escalado.',
    })
    const supervisor = usuarios.find(
      (item) =>
        item.rol === 'SUPERVISOR' &&
        item.activo &&
        item.areaDependencia === actualizado.areaDependencia,
    )
    console.log(
      `[QRDS escalamiento] ${actualizado.codigoSeguimiento} vencido. Aviso a ${supervisor?.email || 'administración'}`,
    )
    marcados += 1
  }
  return marcados
}

export function registrarRutasSeguimiento(app, { usuarios, autenticarToken, respuestaError }) {
  detectarVencimientos(usuarios)
  const intervalo = setInterval(() => detectarVencimientos(usuarios), 60 * 60 * 1000)
  if (typeof intervalo.unref === 'function') intervalo.unref()

  app.get('/api/casos/publico/:codigo/seguimientos', (req, res) => {
    const caso = buscarCasoPorCodigo(req.params.codigo)
    if (!caso) {
      return respuestaError(res, 404, 'CASO_NO_ENCONTRADO', 'Código no encontrado.')
    }
    res.json({
      codigoSeguimiento: caso.codigoSeguimiento,
      avancePorcentaje: caso.avancePorcentaje || 0,
      seguimientos: listaDeCaso(caso.id, { internos: false }),
    })
  })

  app.get('/api/casos/:id/seguimientos', autenticarToken, (req, res) => {
    detectarVencimientos(usuarios)
    const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
    if (!ctx) return
    const incluirInternos = String(req.query.incluirInternos || 'true') !== 'false'
    res.json({
      caso: detalleCaso(ctx.caso, usuarios),
      seguimientos: listaDeCaso(ctx.caso.id, { internos: incluirInternos }),
    })
  })

  const inmutable = (_req, res) =>
    respuestaError(
      res,
      405,
      'INMUTABLE',
      'Los seguimientos no se pueden modificar ni eliminar. Registre una corrección.',
    )
  app.put('/api/casos/:id/seguimientos/:segId', autenticarToken, inmutable)
  app.patch('/api/casos/:id/seguimientos/:segId', autenticarToken, inmutable)
  app.delete('/api/casos/:id/seguimientos/:segId', autenticarToken, inmutable)

  app.post(
    '/api/casos/:id/seguimientos',
    autenticarToken,
    (req, res, next) => {
      upload.single('archivo')(req, res, (error) => {
        if (error) {
          const mensaje =
            error.code === 'LIMIT_FILE_SIZE'
              ? 'El archivo supera el máximo de 5 MB.'
              : 'No fue posible procesar el archivo adjunto.'
          return respuestaError(res, 400, 'ARCHIVO_INVALIDO', mensaje)
        }
        next()
      })
    },
    (req, res) => {
      const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
      if (!ctx) return
      const { usuario, caso } = ctx

      const tipo = String(req.body?.tipo || '').toUpperCase()
      const titulo = String(req.body?.titulo || '').trim()
      const descripcion = String(req.body?.descripcion || '').trim()
      const porcentajeRaw = req.body?.porcentajeAvance
      const notificarCiudadano = String(req.body?.notificarCiudadano) === 'true'
      const justificacionExcepcional = String(req.body?.justificacionExcepcional || '').trim()
      const padreId = req.body?.seguimientoPadreId ? Number(req.body.seguimientoPadreId) : null

      if (!TIPOS_SEG.has(tipo)) {
        return respuestaError(res, 400, 'VALIDACION', 'El tipo debe ser PUBLICA, INTERNA o CORRECCION.')
      }
      const permiso = puedeRegistrar(usuario, caso, tipo)
      if (!permiso.ok) {
        registrarBitacoraCaso({
          req,
          usuario,
          caso,
          tipoEvento: 'INTENTO_DENEGADO',
          descripcion: 'Intento de seguimiento en caso finalizado',
        })
        return respuestaError(res, 400, 'ESTADO_NO_VALIDO', permiso.mensaje)
      }
      if (permiso.excepcional && justificacionExcepcional.length < 20) {
        return respuestaError(
          res,
          400,
          'VALIDACION',
          'En casos finalizados el administrador debe justificar la nota interna (mín. 20 caracteres).',
        )
      }
      if (titulo.length < 5 || titulo.length > 120) {
        return respuestaError(res, 400, 'VALIDACION', 'El título debe tener entre 5 y 120 caracteres.')
      }
      if (descripcion.length < 20 || descripcion.length > 2000) {
        return respuestaError(res, 400, 'VALIDACION', 'La descripción debe tener entre 20 y 2000 caracteres.')
      }

      let porcentajeAvance = null
      if (porcentajeRaw !== undefined && porcentajeRaw !== null && String(porcentajeRaw) !== '') {
        porcentajeAvance = Number(porcentajeRaw)
        if (!Number.isInteger(porcentajeAvance) || porcentajeAvance < 0 || porcentajeAvance > 100) {
          return respuestaError(res, 400, 'VALIDACION', 'El porcentaje de avance debe ser un entero entre 0 y 100.')
        }
      }

      if (tipo === 'CORRECCION') {
        const padre = seguimientos.find((item) => item.id === padreId && item.casoId === caso.id)
        if (!padre) {
          return respuestaError(res, 400, 'VALIDACION', 'La corrección debe referenciar un seguimiento existente.')
        }
      }

      if (req.file && !archivoPermitido(req.file)) {
        return respuestaError(res, 400, 'ARCHIVO_INVALIDO', 'Formato no permitido. Use PDF, JPG, PNG o DOCX.')
      }

      let adjunto = null
      if (req.file) {
        adjunto = {
          id: siguienteDocSegId++,
          nombreArchivo: req.file.originalname,
          tipoMime: req.file.mimetype,
          tamanioBytes: req.file.size,
          buffer: req.file.buffer,
          subidoEn: new Date().toISOString(),
        }
        documentosSeguimiento.push(Object.freeze({ ...adjunto, seguimientoId: siguienteSeguimientoId }))
        documentos.push(
          Object.freeze({
            id: Date.now(),
            casoId: caso.id,
            nombreArchivo: adjunto.nombreArchivo,
            rutaArchivo: `seguimiento://${siguienteSeguimientoId}`,
            tipoMime: adjunto.tipoMime,
            tamanioBytes: adjunto.tamanioBytes,
            buffer: adjunto.buffer,
            subidoEn: adjunto.subidoEn,
          }),
        )
      }

      const seguimiento = Object.freeze({
        id: siguienteSeguimientoId++,
        casoId: caso.id,
        usuarioId: usuario.id,
        usuarioNombre: usuario.nombre,
        tipo,
        titulo,
        descripcion,
        porcentajeAvance,
        notificado: false,
        seguimientoPadreId: tipo === 'CORRECCION' ? padreId : null,
        creadoEn: new Date().toISOString(),
        ipRegistro:
          req.headers['x-forwarded-for']?.toString().split(',')[0].trim() ||
          req.socket.remoteAddress ||
          '0.0.0.0',
        adjunto: adjunto
          ? { id: adjunto.id, nombreArchivo: adjunto.nombreArchivo, tipoMime: adjunto.tipoMime }
          : null,
        justificacionExcepcional: permiso.excepcional ? justificacionExcepcional : null,
      })
      seguimientos.push(seguimiento)

      const patch = {}
      if (porcentajeAvance !== null) patch.avancePorcentaje = porcentajeAvance
      const actualizado = parchearCaso(caso.id, patch)

      let avisoCorreo = null
      const debeNotificar =
        tipo === 'PUBLICA' && notificarCiudadano && !permiso.excepcional && !actualizado.esAnonimo
      if (debeNotificar) {
        const correo = notificarAvance(actualizado, seguimiento)
        if (!correo.enviado) {
          avisoCorreo = 'Seguimiento guardado, pero no fue posible notificar al ciudadano.'
          registrarBitacoraCaso({
            req,
            usuario,
            caso: actualizado,
            tipoEvento: 'NOTIFICACION_FALLIDA',
            descripcion: `Fallo SMTP al notificar seguimiento ${seguimiento.id}`,
          })
        } else {
          const indice = seguimientos.findIndex((item) => item.id === seguimiento.id)
          seguimientos[indice] = Object.freeze({ ...seguimiento, notificado: true })
        }
      }

      registrarBitacoraCaso({
        req,
        usuario,
        caso: actualizado,
        tipoEvento: 'SEGUIMIENTO_REGISTRADO',
        estadoAnterior: actualizado.estado,
        estadoNuevo: actualizado.estado,
        descripcion: `${tipo}: ${titulo}`,
      })

      res.status(201).json({
        mensaje: 'Seguimiento registrado correctamente.',
        avisoCorreo,
        seguimiento: dtoSeguimiento(
          seguimientos.find((item) => item.id === seguimiento.id),
          { internos: true },
        ),
        caso: detalleCaso(actualizado, usuarios),
        seguimientos: listaDeCaso(actualizado.id, { internos: true }),
      })
    },
  )

  app.get('/api/casos/:id/seguimientos/:segId/documento', autenticarToken, (req, res) => {
    const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
    if (!ctx) return
    const seguimiento = seguimientos.find(
      (item) => item.id === Number(req.params.segId) && item.casoId === ctx.caso.id,
    )
    const doc = documentosSeguimiento.find((item) => item.seguimientoId === seguimiento?.id)
    if (!seguimiento || !doc) {
      return respuestaError(res, 404, 'DOCUMENTO_NO_ENCONTRADO', 'Evidencia no encontrada.')
    }
    registrarBitacoraCaso({
      req,
      usuario: ctx.usuario,
      caso: ctx.caso,
      tipoEvento: 'DESCARGA_EVIDENCIA',
      descripcion: `Descarga de evidencia ${doc.nombreArchivo}`,
    })
    res.setHeader('Content-Type', doc.tipoMime)
    res.setHeader(
      'Content-Disposition',
      `attachment; filename="${encodeURIComponent(doc.nombreArchivo)}"`,
    )
    res.send(doc.buffer)
  })

  app.post('/api/casos/:id/prorroga', autenticarToken, (req, res) => {
    const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
    if (!ctx) return
    const { usuario, caso } = ctx
    if (usuario.rol !== 'ADMIN' && usuario.rol !== 'SUPERVISOR') {
      registrarBitacoraCaso({
        req,
        usuario,
        caso,
        tipoEvento: 'INTENTO_DENEGADO',
        descripcion: 'Intento de prórroga sin permiso',
      })
      return respuestaError(res, 403, 'ACCESO_DENEGADO', 'No tiene permisos para actualizar este caso.')
    }
    if (!ESTADOS_ABIERTOS.has(caso.estado)) {
      return respuestaError(res, 400, 'ESTADO_NO_VALIDO', 'Solo se prorrogan casos en revisión o en proceso.')
    }
    const diasHabiles = Number(req.body?.diasHabiles)
    const justificacion = String(req.body?.justificacion || '').trim()
    if (!Number.isInteger(diasHabiles) || diasHabiles < 1 || diasHabiles > 15) {
      return respuestaError(res, 400, 'VALIDACION', 'Los días hábiles de prórroga deben estar entre 1 y 15.')
    }
    if (justificacion.length < 20) {
      return respuestaError(res, 400, 'VALIDACION', 'La justificación debe tener al menos 20 caracteres.')
    }

    const base = caso.fechaLimiteRespuesta || aFechaIso(new Date())
    const nuevaLimite = aFechaIso(sumarDiasHabiles(base, diasHabiles))
    const actualizado = parchearCaso(caso.id, {
      fechaLimiteRespuesta: nuevaLimite,
      fechaProrroga: new Date().toISOString(),
      escalado: false,
    })
    registrarBitacoraCaso({
      req,
      usuario,
      caso: actualizado,
      tipoEvento: 'PRORROGA_PLAZO',
      descripcion: `Prórroga de ${diasHabiles} días hábiles. ${justificacion}`,
    })
    if (!actualizado.esAnonimo && actualizado.emailCiudadano) {
      console.log(
        `[QRDS correo prórroga → ${actualizado.emailCiudadano}] ${actualizado.codigoSeguimiento} nueva fecha límite ${nuevaLimite}`,
      )
    }
    res.json({
      mensaje: 'Prórroga registrada correctamente.',
      caso: detalleCaso(actualizado, usuarios),
    })
  })

  app.post('/api/casos/:id/escalar', autenticarToken, (req, res) => {
    const ctx = obtenerCasoAutorizado(req, res, usuarios, respuestaError)
    if (!ctx) return
    const { usuario, caso } = ctx
    if (usuario.rol !== 'ADMIN' && usuario.rol !== 'SUPERVISOR') {
      return respuestaError(res, 403, 'ACCESO_DENEGADO', 'No tiene permisos para actualizar este caso.')
    }
    const motivo = String(req.body?.motivo || '').trim()
    if (motivo.length < 10) {
      return respuestaError(res, 400, 'VALIDACION', 'El motivo de escalamiento debe tener al menos 10 caracteres.')
    }
    const actualizado = parchearCaso(caso.id, { escalado: true, prioridad: 'ALTA' })
    registrarBitacoraCaso({
      req,
      usuario,
      caso: actualizado,
      tipoEvento: 'ESCALAMIENTO',
      descripcion: motivo,
    })
    res.json({
      mensaje: 'Caso escalado correctamente.',
      caso: detalleCaso(actualizado, usuarios),
    })
  })
}

seguimientos.push(
  Object.freeze({
    id: siguienteSeguimientoId++,
    casoId: 1,
    usuarioId: 3,
    usuarioNombre: 'Carlos Agente',
    tipo: 'PUBLICA',
    titulo: 'Inspección en sitio realizada',
    descripcion:
      'Se visitó el sector de la 4a. avenida y se constató la intermitencia del alumbrado. Se coordinó reparación con la cuadrilla municipal.',
    porcentajeAvance: 40,
    notificado: false,
    seguimientoPadreId: null,
    creadoEn: '2026-07-22T09:15:00.000Z',
    ipRegistro: '0.0.0.0',
    adjunto: null,
    justificacionExcepcional: null,
  }),
)
parchearCaso(1, { avancePorcentaje: 40 })
