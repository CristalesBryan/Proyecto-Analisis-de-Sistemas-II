import crypto from 'crypto'
import multer from 'multer'
import rateLimit from 'express-rate-limit'
import { aFechaIso, sumarDiasHabiles } from './plazos.js'

const TIPOS = {
  Q: { nombre: 'Queja', plazoDiasHabiles: 15 },
  R: { nombre: 'Reclamo', plazoDiasHabiles: 20 },
  D: { nombre: 'Denuncia', plazoDiasHabiles: 30 },
  S: { nombre: 'Sugerencia', plazoDiasHabiles: 30 },
}

const ESTADOS_ACTIVOS = new Set(['RECIBIDO', 'EN_REVISION', 'EN_PROCESO', 'RESUELTO'])
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const CODIGO_NUEVO = /^[QRDS]-\d{4}-\d{5}$/
const CODIGO_LEGACY = /^[A-Z0-9]{10}$/
const MAX_ARCHIVO = 5 * 1024 * 1024
const MAX_ARCHIVOS = 5
const MIME_PERMITIDOS = new Map([
  ['application/pdf', 'pdf'],
  ['image/jpeg', 'jpg'],
  ['image/jpg', 'jpg'],
  ['image/png', 'png'],
  [
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'docx',
  ],
])
const EXTENSIONES = new Set(['pdf', 'jpg', 'jpeg', 'png', 'docx'])

export const AREAS = [
  { codigo: 'ALCALDIA', nombre: 'Alcaldía Municipal' },
  { codigo: 'OBRAS', nombre: 'Dirección de Obras Públicas' },
  { codigo: 'AMBIENTE', nombre: 'Dirección de Medio Ambiente' },
  { codigo: 'CATASTRO', nombre: 'Dirección de Catastro' },
  { codigo: 'FINANZAS', nombre: 'Dirección de Finanzas' },
  { codigo: 'SERVICIOS', nombre: 'Servicios Públicos Municipales' },
  { codigo: 'RRHH', nombre: 'Recursos Humanos' },
]

const casos = []
const documentos = []
const captchas = new Map()
const correlativos = {
  2026: { Q: 2, R: 1, D: 1, S: 1 },
}

let siguienteCasoId = 6
let siguienteDocumentoId = 1

casos.push(
  {
    id: 1,
    codigoSeguimiento: 'Q-2026-00001',
    codigoLegacy: 'QRDS000001',
    tipoCaso: 'Q',
    nombreCiudadano: null,
    emailCiudadano: null,
    telefono: null,
    areaDependencia: 'SERVICIOS',
    descripcion: 'Caso de demostración para consulta pública del portal. El alumbrado de la 4a. avenida permanece intermitente desde hace varias semanas.',
    esAnonimo: true,
    denunciado: null,
    aceptaPrivacidad: true,
    estado: 'EN_PROCESO',
    agenteAsignadoId: 3,
    prioridad: 'ALTA',
    fechaRegistro: '2026-07-20T12:00:00.000Z',
    fechaUltimaActualizacion: '2026-07-22T09:00:00.000Z',
    ipRegistro: '0.0.0.0',
    creadoEn: '2026-07-20T12:00:00.000Z',
  },
  {
    id: 2,
    codigoSeguimiento: 'S-2026-00001',
    codigoLegacy: 'ABC1234567',
    tipoCaso: 'S',
    nombreCiudadano: null,
    emailCiudadano: null,
    telefono: null,
    areaDependencia: 'ALCALDIA',
    descripcion: 'Sugerencia de demostración para consulta pública del portal: habilitar más ventanillas de atención los sábados.',
    esAnonimo: true,
    denunciado: null,
    aceptaPrivacidad: true,
    estado: 'RECIBIDO',
    agenteAsignadoId: null,
    prioridad: 'NORMAL',
    fechaRegistro: '2026-08-01T12:00:00.000Z',
    fechaUltimaActualizacion: '2026-08-01T12:00:00.000Z',
    ipRegistro: '0.0.0.0',
    creadoEn: '2026-08-01T12:00:00.000Z',
  },
  {
    id: 3,
    codigoSeguimiento: 'R-2026-00001',
    codigoLegacy: null,
    tipoCaso: 'R',
    nombreCiudadano: 'Ana Pérez',
    emailCiudadano: 'ana.perez@correo.com',
    telefono: '55551234',
    areaDependencia: 'SERVICIOS',
    descripcion: 'Solicito revisión de la tarifa cobrada en julio por recolección de basura. El monto no coincide con el aviso municipal publicado.',
    esAnonimo: false,
    denunciado: null,
    aceptaPrivacidad: true,
    estado: 'RECIBIDO',
    agenteAsignadoId: null,
    prioridad: 'NORMAL',
    fechaRegistro: '2026-08-10T15:00:00.000Z',
    fechaUltimaActualizacion: '2026-08-10T15:00:00.000Z',
    ipRegistro: '0.0.0.0',
    creadoEn: '2026-08-10T15:00:00.000Z',
  },
  {
    id: 4,
    codigoSeguimiento: 'D-2026-00001',
    codigoLegacy: null,
    tipoCaso: 'D',
    nombreCiudadano: null,
    emailCiudadano: null,
    telefono: null,
    areaDependencia: 'OBRAS',
    descripcion: 'Denuncia anónima sobre un posible incumplimiento en la supervisión de una obra vial en el sector norte del municipio.',
    esAnonimo: true,
    denunciado: 'Dirección de Obras Públicas',
    aceptaPrivacidad: true,
    estado: 'EN_REVISION',
    agenteAsignadoId: 5,
    prioridad: 'ALTA',
    fechaRegistro: '2026-08-08T11:00:00.000Z',
    fechaUltimaActualizacion: '2026-08-09T08:30:00.000Z',
    ipRegistro: '0.0.0.0',
    creadoEn: '2026-08-08T11:00:00.000Z',
  },
  {
    id: 5,
    codigoSeguimiento: 'Q-2026-00002',
    codigoLegacy: null,
    tipoCaso: 'Q',
    nombreCiudadano: 'Luis Gómez',
    emailCiudadano: 'luis.gomez@correo.com',
    telefono: '42223344',
    areaDependencia: 'CATASTRO',
    descripcion: 'El plano catastral de mi predio no aparece actualizado en ventanilla pese a haber presentado la documentación hace dos meses.',
    esAnonimo: false,
    denunciado: null,
    aceptaPrivacidad: true,
    estado: 'RECIBIDO',
    agenteAsignadoId: null,
    prioridad: 'NORMAL',
    fechaRegistro: '2026-08-12T10:00:00.000Z',
    fechaUltimaActualizacion: '2026-08-12T10:00:00.000Z',
    ipRegistro: '0.0.0.0',
    creadoEn: '2026-08-12T10:00:00.000Z',
  },
)

for (const caso of casos) {
  caso.fechaLimiteRespuesta =
    caso.fechaLimiteRespuesta ||
    aFechaIso(sumarDiasHabiles(caso.fechaRegistro, TIPOS[caso.tipoCaso].plazoDiasHabiles))
  caso.avancePorcentaje = caso.avancePorcentaje ?? 0
  caso.escalado = caso.escalado ?? false
  caso.fechaProrroga = caso.fechaProrroga ?? null
}

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_ARCHIVO, files: MAX_ARCHIVOS },
})

function extensionDe(nombre) {
  const partes = String(nombre || '').toLowerCase().split('.')
  return partes.length > 1 ? partes.at(-1) : ''
}

function archivoPermitido(file) {
  const ext = extensionDe(file.originalname)
  return EXTENSIONES.has(ext) && MIME_PERMITIDOS.has(file.mimetype)
}

function normalizarTexto(texto) {
  return String(texto || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function descripcionesSimilares(a, b) {
  const na = normalizarTexto(a)
  const nb = normalizarTexto(b)
  if (!na || !nb) return false
  if (na === nb) return true
  if (na.includes(nb) || nb.includes(na)) return true

  const palabrasA = new Set(na.split(' ').filter((w) => w.length > 3))
  const palabrasB = new Set(nb.split(' ').filter((w) => w.length > 3))
  if (palabrasA.size === 0 || palabrasB.size === 0) return false

  let interseccion = 0
  for (const palabra of palabrasA) {
    if (palabrasB.has(palabra)) interseccion += 1
  }
  return interseccion / new Set([...palabrasA, ...palabrasB]).size >= 0.6
}

function buscarCaso(codigoCrudo) {
  const codigo = String(codigoCrudo || '').trim().toUpperCase()
  return casos.find(
    (caso) => caso.codigoSeguimiento === codigo || caso.codigoLegacy === codigo,
  )
}

function vistaPublica(caso) {
  return {
    codigoSeguimiento: caso.codigoSeguimiento,
    tipoCaso: caso.tipoCaso,
    tipo: TIPOS[caso.tipoCaso].nombre,
    estado: caso.estado,
    fechaRegistro: caso.fechaRegistro.slice(0, 10),
    ultimaActualizacion: caso.fechaUltimaActualizacion.slice(0, 10),
    avancePorcentaje: caso.avancePorcentaje || 0,
  }
}

function generarCodigo(tipoCaso, fecha = new Date()) {
  const anio = fecha.getFullYear()
  if (!correlativos[anio]) {
    correlativos[anio] = { Q: 0, R: 0, D: 0, S: 0 }
  }
  correlativos[anio][tipoCaso] += 1
  return `${tipoCaso}-${anio}-${String(correlativos[anio][tipoCaso]).padStart(5, '0')}`
}

function encontrarDuplicado(email, descripcion) {
  if (!email) return null
  const limite = Date.now() - 24 * 60 * 60 * 1000
  return (
    casos.find(
      (caso) =>
        caso.emailCiudadano === email &&
        ESTADOS_ACTIVOS.has(caso.estado) &&
        new Date(caso.fechaRegistro).getTime() >= limite &&
        descripcionesSimilares(caso.descripcion, descripcion),
    ) || null
  )
}

function enviarConfirmacion(caso) {
  if (caso.esAnonimo || !caso.emailCiudadano) {
    return { enviado: false, motivo: 'ANONIMO' }
  }

  const tipo = TIPOS[caso.tipoCaso]
  const cuerpo = [
    `Confirmación de registro QRDS`,
    `Código: ${caso.codigoSeguimiento}`,
    `Tipo: ${tipo.nombre}`,
    `Fecha: ${caso.fechaRegistro.slice(0, 10)}`,
    `Plazo estimado: ${tipo.plazoDiasHabiles} días hábiles`,
    `Consulta: /registro-caso o Consultar Caso en el portal`,
  ].join('\n')

  try {
    console.log(`[QRDS correo → ${caso.emailCiudadano}]\n${cuerpo}`)
    return { enviado: true, motivo: 'SIMULADO' }
  } catch (error) {
    console.error('[QRDS correo] fallo de notificación', error)
    return { enviado: false, motivo: 'SMTP' }
  }
}

function validarRegistro(body) {
  const errores = {}
  const tipoCaso = String(body.tipoCaso || '').toUpperCase()
  const esAnonimo = Boolean(body.esAnonimo) && tipoCaso === 'D'
  const nombre = typeof body.nombreCiudadano === 'string' ? body.nombreCiudadano.trim() : ''
  const email = typeof body.email === 'string' ? body.email.trim().toLowerCase() : ''
  const telefono = typeof body.telefono === 'string' ? body.telefono.replace(/\D/g, '') : ''
  const areaDependencia = String(body.areaDependencia || '').trim().toUpperCase()
  const descripcion = typeof body.descripcion === 'string' ? body.descripcion.trim() : ''
  const denunciado = typeof body.denunciado === 'string' ? body.denunciado.trim() : ''
  const aceptaPrivacidad = Boolean(body.aceptaPrivacidad)

  if (!TIPOS[tipoCaso]) errores.tipoCaso = 'Seleccione un tipo de caso válido.'
  if (!esAnonimo && (nombre.length < 2 || nombre.length > 150)) {
    errores.nombreCiudadano = 'El nombre debe tener entre 2 y 150 caracteres.'
  }
  if (!esAnonimo && !EMAIL_REGEX.test(email)) {
    errores.email = 'Ingrese un correo electrónico válido.'
  }
  if (telefono && (telefono.length < 8 || telefono.length > 15)) {
    errores.telefono = 'El teléfono debe tener entre 8 y 15 dígitos.'
  }
  if (!AREAS.some((area) => area.codigo === areaDependencia)) {
    errores.areaDependencia = 'Seleccione un área o dependencia válida.'
  }
  if (descripcion.length < 50 || descripcion.length > 2000) {
    errores.descripcion = 'La descripción debe tener entre 50 y 2000 caracteres.'
  }
  if (tipoCaso === 'D' && denunciado.length > 150) {
    errores.denunciado = 'El nombre del denunciado no puede superar 150 caracteres.'
  }
  if (!aceptaPrivacidad) {
    errores.aceptaPrivacidad = 'Debe aceptar el aviso de privacidad para enviar el caso.'
  }

  return {
    errores,
    datos: {
      tipoCaso,
      esAnonimo,
      nombreCiudadano: esAnonimo ? null : nombre,
      emailCiudadano: esAnonimo ? null : email,
      telefono: telefono || null,
      areaDependencia,
      descripcion,
      denunciado: tipoCaso === 'D' && denunciado ? denunciado : null,
      aceptaPrivacidad,
    },
  }
}

function verificarCaptcha(captchaId, respuesta, { consumir }) {
  const reto = captchas.get(captchaId)
  if (!reto) return false
  if (reto.expira < Date.now()) {
    captchas.delete(captchaId)
    return false
  }
  const valido = String(respuesta).trim() === String(reto.respuesta)
  if (valido && consumir) captchas.delete(captchaId)
  return valido
}

export function registrarRutasCasos(app, { registrarBitacora, respuestaError, clientIp }) {
  const registroLimiter = rateLimit({
    windowMs: 10 * 60 * 1000,
    max: 5,
    standardHeaders: true,
    legacyHeaders: false,
    handler: (_req, res) =>
      respuestaError(
        res,
        429,
        'RATE_LIMIT_EXCEDIDO',
        'Ha superado el límite de registros. Intente nuevamente en 10 minutos.',
      ),
  })

  app.get('/api/catalogos/areas', (_req, res) => {
    res.json(AREAS)
  })

  app.get('/api/casos/captcha', (_req, res) => {
    const a = 1 + Math.floor(Math.random() * 9)
    const b = 1 + Math.floor(Math.random() * 9)
    const captchaId = crypto.randomUUID()
    captchas.set(captchaId, {
      respuesta: a + b,
      expira: Date.now() + 10 * 60 * 1000,
    })
    res.json({ captchaId, pregunta: `¿Cuánto es ${a} + ${b}?` })
  })

  app.post('/api/casos/publico', registroLimiter, (req, res) => {
    const { errores, datos } = validarRegistro(req.body || {})
    if (Object.keys(errores).length > 0) {
      return res.status(400).json({
        codigo: 'VALIDACION',
        mensaje: 'Campos obligatorios incompletos o con formato inválido.',
        errores,
      })
    }

    if (!verificarCaptcha(req.body?.captchaId, req.body?.captchaRespuesta, { consumir: false })) {
      return respuestaError(
        res,
        400,
        'CAPTCHA_INVALIDO',
        'La verificación anti-bot es incorrecta o expiró. Intente nuevamente.',
      )
    }

    const duplicado = encontrarDuplicado(datos.emailCiudadano, datos.descripcion)
    if (duplicado && !req.body?.forzarRegistro) {
      return res.status(409).json({
        codigo: 'CASO_SIMILAR',
        mensaje: `Detectamos que puede tener un caso similar registrado recientemente. Código: ${duplicado.codigoSeguimiento}. ¿Desea continuar con un nuevo registro?`,
        codigoExistente: duplicado.codigoSeguimiento,
      })
    }

    if (!verificarCaptcha(req.body?.captchaId, req.body?.captchaRespuesta, { consumir: true })) {
      return respuestaError(
        res,
        400,
        'CAPTCHA_INVALIDO',
        'La verificación anti-bot es incorrecta o expiró. Intente nuevamente.',
      )
    }

    const ahora = new Date().toISOString()
    const caso = {
      id: siguienteCasoId++,
      codigoSeguimiento: generarCodigo(datos.tipoCaso),
      codigoLegacy: null,
      ...datos,
      estado: 'RECIBIDO',
      agenteAsignadoId: null,
      prioridad: 'NORMAL',
      avancePorcentaje: 0,
      escalado: false,
      fechaProrroga: null,
      fechaLimiteRespuesta: aFechaIso(
        sumarDiasHabiles(ahora, TIPOS[datos.tipoCaso].plazoDiasHabiles),
      ),
      fechaRegistro: ahora,
      fechaUltimaActualizacion: ahora,
      ipRegistro: clientIp(req),
      creadoEn: ahora,
    }
    casos.push(caso)

    const correo = enviarConfirmacion(caso)
    if (!correo.enviado && correo.motivo === 'SMTP') {
      console.error(`[QRDS] Fallo de notificación para ${caso.codigoSeguimiento}`)
    }

    registrarBitacora({
      req,
      tipoEvento: 'REGISTRO_CASO',
      resultado: 'EXITOSO',
      detalle: JSON.stringify({
        codigo: caso.codigoSeguimiento,
        tipoCaso: caso.tipoCaso,
        email: caso.emailCiudadano,
      }),
    })

    const tipo = TIPOS[caso.tipoCaso]
    return res.status(201).json({
      codigoSeguimiento: caso.codigoSeguimiento,
      mensaje: 'Caso registrado correctamente.',
      fechaRegistro: caso.fechaRegistro.slice(0, 10),
      tipoCaso: caso.tipoCaso,
      tipo: tipo.nombre,
      estado: caso.estado,
      plazoEstimado: `${tipo.plazoDiasHabiles} días hábiles`,
      correoEnviado: correo.enviado,
      esAnonimo: caso.esAnonimo,
    })
  })

  app.post(
    '/api/casos/publico/:codigo/documentos',
    (req, res, next) => {
      upload.array('archivos', MAX_ARCHIVOS)(req, res, (error) => {
        if (error) {
          const mensaje =
            error.code === 'LIMIT_FILE_SIZE'
              ? 'Cada archivo debe pesar máximo 5 MB.'
              : error.code === 'LIMIT_FILE_COUNT'
                ? 'Máximo 5 archivos por caso.'
                : 'No fue posible procesar los archivos adjuntos.'
          return respuestaError(res, 400, 'ARCHIVO_INVALIDO', mensaje)
        }
        next()
      })
    },
    (req, res) => {
      const caso = buscarCaso(req.params.codigo)
      if (!caso) {
        return respuestaError(res, 404, 'CASO_NO_ENCONTRADO', 'Código no encontrado.')
      }

      const archivos = req.files || []
      if (archivos.length === 0) {
        return respuestaError(res, 400, 'ARCHIVO_INVALIDO', 'Adjunte al menos un archivo.')
      }

      const existentes = documentos.filter((doc) => doc.casoId === caso.id)
      if (existentes.length + archivos.length > MAX_ARCHIVOS) {
        return respuestaError(
          res,
          400,
          'ARCHIVO_INVALIDO',
          'Máximo 5 archivos por caso.',
        )
      }

      const subidos = []
      const rechazados = []
      for (const file of archivos) {
        if (!archivoPermitido(file)) {
          rechazados.push({
            nombre: file.originalname,
            motivo: 'Formato no permitido. Use PDF, JPG, PNG o DOCX.',
          })
          continue
        }
        if (file.size > MAX_ARCHIVO) {
          rechazados.push({
            nombre: file.originalname,
            motivo: 'El archivo supera el máximo de 5 MB.',
          })
          continue
        }

        const registro = Object.freeze({
          id: siguienteDocumentoId++,
          casoId: caso.id,
          nombreArchivo: file.originalname,
          rutaArchivo: `memoria://${caso.codigoSeguimiento}/${file.originalname}`,
          tipoMime: file.mimetype,
          tamanioBytes: file.size,
          buffer: file.buffer,
          subidoEn: new Date().toISOString(),
        })
        documentos.push(registro)
        subidos.push({
          nombreArchivo: registro.nombreArchivo,
          tipoMime: registro.tipoMime,
          tamanioBytes: registro.tamanioBytes,
        })
      }

      if (subidos.length === 0) {
        return res.status(400).json({
          codigo: 'ARCHIVO_INVALIDO',
          mensaje: 'Ningún archivo cumplió las reglas de adjuntos.',
          rechazados,
        })
      }

      return res.status(201).json({ archivosSubidos: subidos, rechazados })
    },
  )

  app.get('/api/casos/publico/:codigo', (req, res) => {
    const codigo = String(req.params.codigo || '').toUpperCase()
    if (!CODIGO_NUEVO.test(codigo) && !CODIGO_LEGACY.test(codigo)) {
      return res.status(400).json({
        codigo: 'CODIGO_INVALIDO',
        mensaje: 'El código debe tener el formato TIPO-AÑO-CORRELATIVO (ej. Q-2026-00001).',
      })
    }

    const caso = buscarCaso(codigo)
    if (!caso) {
      return res.status(404).json({
        codigo: 'CASO_NO_ENCONTRADO',
        mensaje: 'Código no encontrado. Verifique el número e intente nuevamente.',
      })
    }

    res.json(vistaPublica(caso))
  })
}

export function buscarCasoPorCodigo(codigoCrudo) {
  const codigo = String(codigoCrudo || '').trim().toUpperCase()
  return casos.find(
    (caso) => caso.codigoSeguimiento === codigo || caso.codigoLegacy === codigo,
  )
}

export function buscarCasoPorId(id) {
  return casos.find((caso) => caso.id === Number(id)) || null
}

export function parchearCaso(id, patch) {
  const indice = casos.findIndex((caso) => caso.id === Number(id))
  if (indice < 0) return null
  casos[indice] = {
    ...casos[indice],
    ...patch,
    fechaUltimaActualizacion: new Date().toISOString(),
  }
  return casos[indice]
}

export function nombreArea(codigo) {
  return AREAS.find((area) => area.codigo === codigo)?.nombre || codigo
}

export { casos, documentos, TIPOS }
