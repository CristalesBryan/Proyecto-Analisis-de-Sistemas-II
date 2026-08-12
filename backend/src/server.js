import express from 'express'
import cors from 'cors'

const app = express()
const PORT = process.env.PORT || 8080

app.use(cors())
app.use(express.json())

/** Bitácora inmutable en memoria (CU-00 RN-03) */
const bitacoras = []

/** Casos de demostración para consulta pública (CU-00 FA-02 / RN-04) */
const casosDemo = {
  QRDS000001: {
    codigoSeguimiento: 'QRDS000001',
    tipo: 'QUEJA',
    estado: 'EN_PROCESO',
    fechaRegistro: '2026-07-20',
  },
  ABC1234567: {
    codigoSeguimiento: 'ABC1234567',
    tipo: 'SUGERENCIA',
    estado: 'REGISTRADO',
    fechaRegistro: '2026-08-01',
  },
}

function clientIp(req) {
  const forwarded = req.headers['x-forwarded-for']
  if (typeof forwarded === 'string' && forwarded.length > 0) {
    return forwarded.split(',')[0].trim()
  }
  return req.socket.remoteAddress || '0.0.0.0'
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

  const registro = Object.freeze({
    id: bitacoras.length + 1,
    ip: clientIp(req),
    fechaHora: new Date().toISOString(),
    accion,
    resultado,
    tipo: 'CONSULTA',
  })

  bitacoras.push(registro)
  res.status(201).json({ ok: true, id: registro.id })
})

/** GET /api/casos/publico/:codigo — consulta pública sin datos sensibles */
app.get('/api/casos/publico/:codigo', (req, res) => {
  const codigo = String(req.params.codigo || '').toUpperCase()

  if (!/^[A-Z0-9]{10}$/.test(codigo)) {
    return res.status(400).json({
      error: 'El código debe ser alfanumérico de 10 caracteres',
    })
  }

  const caso = casosDemo[codigo]
  if (!caso) {
    return res.status(404).json({
      error: 'Código no encontrado. Verifique el número e intente nuevamente.',
    })
  }

  res.json(caso)
})

/** Solo lectura de bitácoras (diagnóstico local) */
app.get('/api/bitacora/acceso-publico', (_req, res) => {
  res.json(bitacoras)
})

app.listen(PORT, () => {
  console.log(`QRDS backend CU-00 escuchando en http://localhost:${PORT}`)
})
