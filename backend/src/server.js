import express from 'express'
import cors from 'cors'
import bcrypt from 'bcryptjs'
import jwt from 'jsonwebtoken'
import rateLimit from 'express-rate-limit'
import { registrarRutasCasos } from './casosPublicos.js'
import { registrarRutasGestion } from './casosGestion.js'
import { registrarRutasSeguimiento } from './casosSeguimiento.js'

const app = express()
const PORT = process.env.PORT || 8080
const JWT_SECRET =
  process.env.JWT_SECRET || 'CAMBIA_ESTA_CLAVE_EN_PRODUCCION_QRDS_2026_HS256'
const JWT_EXPIRATION = '8h'

app.use(cors())
app.use(express.json())

/** Bitácora inmutable en memoria (CU-00 RN-03) */
const bitacoras = []
const tokensRevocados = new Set()

/**
 * Usuarios internos de demostración (CU-01).
 * En producción estos registros y las bitácoras se almacenan en PostgreSQL.
 */
const usuarios = await Promise.all([
  crearUsuario(1, 'Administrador QRDS', 'admin@municipalidad.gt', 'Admin123*', 'ADMIN'),
  crearUsuario(
    2,
    'Supervisor de Servicios',
    'supervisor@municipalidad.gt',
    'Supervisor123*',
    'SUPERVISOR',
    true,
    'SERVICIOS',
  ),
  crearUsuario(
    3,
    'Carlos Agente',
    'agente@municipalidad.gt',
    'Agente123*',
    'AGENTE',
    true,
    'SERVICIOS',
  ),
  crearUsuario(
    4,
    'Usuario Inactivo',
    'inactivo@municipalidad.gt',
    'Inactivo123*',
    'AGENTE',
    false,
    'SERVICIOS',
  ),
  crearUsuario(
    5,
    'Lucía Obras',
    'agente.obras@municipalidad.gt',
    'Obras123*',
    'AGENTE',
    true,
    'OBRAS',
  ),
  crearUsuario(
    6,
    'Pedro Agente',
    'agente2@municipalidad.gt',
    'Agente2123*',
    'AGENTE',
    true,
    'SERVICIOS',
  ),
])

async function crearUsuario(
  id,
  nombre,
  email,
  password,
  rol,
  activo = true,
  areaDependencia = null,
) {
  return {
    id,
    nombre,
    email: email.toLowerCase(),
    passwordHash: await bcrypt.hash(password, 12),
    rol,
    activo,
    areaDependencia,
    intentosFallidos: 0,
    bloqueadoHasta: null,
    creadoEn: new Date().toISOString(),
  }
}

/** Hash dummy para igualar el tiempo de respuesta cuando el correo no existe. */
const HASH_TIMING = await bcrypt.hash('qrds-timing-dummy', 12)

const EVENTOS_PUBLICOS = new Set(['ACCESO_PORTAL', 'CONSULTAR_CASO'])

function clientIp(req) {
  const forwarded = req.headers['x-forwarded-for']
  if (typeof forwarded === 'string' && forwarded.length > 0) {
    return forwarded.split(',')[0].trim()
  }
  return req.socket.remoteAddress || '0.0.0.0'
}

function registrarBitacora({ req, usuarioId = null, tipoEvento, resultado, detalle }) {
  const registro = Object.freeze({
    id: bitacoras.length + 1,
    usuarioId,
    tipoEvento,
    fechaHora: new Date().toISOString(),
    ipCliente: clientIp(req),
    userAgent: req.get('user-agent') || null,
    resultado,
    detalle: detalle || null,
  })

  bitacoras.push(registro)
  return registro
}

function respuestaError(res, status, codigo, mensaje) {
  return res.status(status).json({ codigo, mensaje })
}

const loginLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 10,
  standardHeaders: true,
  legacyHeaders: false,
  handler: (_req, res) =>
    respuestaError(
      res,
      429,
      'RATE_LIMIT_EXCEDIDO',
      'Demasiados intentos de inicio de sesión. Intente nuevamente en un minuto.',
    ),
})

function autenticarToken(req, res, next) {
  const authorization = req.get('authorization')
  const token = authorization?.startsWith('Bearer ') ? authorization.slice(7) : null

  if (!token || tokensRevocados.has(token)) {
    return respuestaError(res, 401, 'TOKEN_INVALIDO', 'Su sesión ha expirado. Por favor inicie sesión nuevamente.')
  }

  try {
    req.sesion = jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] })
    req.token = token
    next()
  } catch {
    return respuestaError(res, 401, 'TOKEN_INVALIDO', 'Su sesión ha expirado. Por favor inicie sesión nuevamente.')
  }
}

/** GET /api/sistema/estado — health check del portal */
app.get('/api/sistema/estado', (_req, res) => {
  res.json({ estado: 'UP', timestamp: new Date().toISOString() })
})

/** POST /api/bitacora/acceso-publico — registra acceso (IP, acción, resultado) */
app.post('/api/bitacora/acceso-publico', (req, res) => {
  const { accion, resultado = 'OK' } = req.body || {}
  if (!accion || typeof accion !== 'string') {
    return res.status(400).json({ error: 'accion es requerida' })
  }

  const registro = registrarBitacora({
    req,
    tipoEvento: accion,
    resultado,
  })

  res.status(201).json({ ok: true, id: registro.id })
})

/** CU-01 — Inicio de sesión con BCrypt, JWT HS256 y control de bloqueo. */
app.post('/api/auth/login', loginLimiter, async (req, res) => {
  const { email: rawEmail, password } = req.body || {}
  const email = typeof rawEmail === 'string' ? rawEmail.trim().toLowerCase() : ''
  const emailValido = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)

  if (!emailValido || typeof password !== 'string' || !password) {
    return respuestaError(
      res,
      400,
      'CREDENCIALES_INVALIDAS',
      'Campos vacíos o correo con formato inválido.',
    )
  }

  const usuario = usuarios.find((item) => item.email === email)
  if (!usuario) {
    await bcrypt.compare(password, HASH_TIMING)
    registrarBitacora({
      req,
      tipoEvento: 'LOGIN_FALLIDO',
      resultado: 'CREDENCIALES',
      detalle: `Intento para correo no registrado: ${email}`,
    })
    return respuestaError(
      res,
      401,
      'CREDENCIALES_INVALIDAS',
      'Usuario o contraseña incorrectos. Verifique sus datos e intente nuevamente.',
    )
  }

  const ahora = new Date()
  if (!usuario.activo) {
    registrarBitacora({
      req,
      usuarioId: usuario.id,
      tipoEvento: 'BLOQUEADO',
      resultado: 'INACTIVO',
    })
    return respuestaError(
      res,
      403,
      'USUARIO_INACTIVO',
      'Su cuenta está inactiva o bloqueada. Contacte al administrador del sistema.',
    )
  }

  if (usuario.bloqueadoHasta && usuario.bloqueadoHasta > ahora) {
    registrarBitacora({
      req,
      usuarioId: usuario.id,
      tipoEvento: 'BLOQUEADO',
      resultado: 'TEMPORAL',
    })
    return respuestaError(
      res,
      403,
      'CUENTA_BLOQUEADA',
      'Cuenta bloqueada temporalmente por múltiples intentos fallidos. Intente en 15 minutos o contacte al administrador.',
    )
  }

  if (usuario.bloqueadoHasta && usuario.bloqueadoHasta <= ahora) {
    usuario.bloqueadoHasta = null
    usuario.intentosFallidos = 0
  }

  const passwordCorrecta = await bcrypt.compare(password, usuario.passwordHash)
  if (!passwordCorrecta) {
    usuario.intentosFallidos += 1
    const bloqueado = usuario.intentosFallidos >= 5
    if (bloqueado) {
      usuario.bloqueadoHasta = new Date(ahora.getTime() + 15 * 60 * 1000)
    }
    registrarBitacora({
      req,
      usuarioId: usuario.id,
      tipoEvento: bloqueado ? 'BLOQUEADO' : 'LOGIN_FALLIDO',
      resultado: bloqueado ? 'MAX_INTENTOS' : 'CREDENCIALES',
    })

    if (bloqueado) {
      return respuestaError(
        res,
        403,
        'CUENTA_BLOQUEADA',
        'Cuenta bloqueada temporalmente por múltiples intentos fallidos. Intente en 15 minutos o contacte al administrador.',
      )
    }

    return respuestaError(
      res,
      401,
      'CREDENCIALES_INVALIDAS',
      'Usuario o contraseña incorrectos. Verifique sus datos e intente nuevamente.',
    )
  }

  usuario.intentosFallidos = 0
  usuario.bloqueadoHasta = null
  const payload = {
    userId: usuario.id,
    email: usuario.email,
    rol: usuario.rol,
    nombre: usuario.nombre,
    areaDependencia: usuario.areaDependencia,
  }
  const token = jwt.sign(payload, JWT_SECRET, {
    algorithm: 'HS256',
    expiresIn: JWT_EXPIRATION,
  })

  registrarBitacora({
    req,
    usuarioId: usuario.id,
    tipoEvento: 'LOGIN',
    resultado: 'EXITOSO',
  })
  return res.json({
    token,
    usuario: {
      id: usuario.id,
      userId: usuario.id,
      email: usuario.email,
      rol: usuario.rol,
      nombre: usuario.nombre,
      areaDependencia: usuario.areaDependencia,
    },
  })
})

/** CU-01 FA-06 — elimina la sesión vigente y registra el cierre. */
app.post('/api/auth/logout', autenticarToken, (req, res) => {
  tokensRevocados.add(req.token)
  registrarBitacora({
    req,
    usuarioId: req.sesion.userId,
    tipoEvento: 'LOGOUT',
    resultado: 'EXITOSO',
  })
  res.json({ mensaje: 'Sesión cerrada.' })
})

/** Verifica JWT para que el frontend detecte sesiones expiradas (FA-07). */
app.get('/api/auth/me', autenticarToken, (req, res) => {
  const { userId, email, rol, nombre, areaDependencia } = req.sesion
  res.json({
    usuario: { id: userId, userId, email, rol, nombre, areaDependencia: areaDependencia || null },
  })
})

registrarRutasCasos(app, { registrarBitacora, respuestaError, clientIp })
registrarRutasGestion(app, { usuarios, autenticarToken, respuestaError })
registrarRutasSeguimiento(app, { usuarios, autenticarToken, respuestaError })

/** Solo lectura de accesos públicos (CU-00). Los eventos de login no se exponen aquí. */
app.get('/api/bitacora/acceso-publico', (_req, res) => {
  res.json(bitacoras.filter((item) => EVENTOS_PUBLICOS.has(item.tipoEvento)))
})

app.listen(PORT, () => {
  console.log(`QRDS backend CU-00 a CU-04 escuchando en http://localhost:${PORT}`)
})
