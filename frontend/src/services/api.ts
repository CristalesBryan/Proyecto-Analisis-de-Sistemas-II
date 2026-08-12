export type SistemaEstado = {
  estado: 'UP' | 'DOWN'
  mensaje?: string
}

export type CasoPublico = {
  codigoSeguimiento: string
  tipo: string
  estado: string
  fechaRegistro: string
}

export async function getSistemaEstado(): Promise<SistemaEstado> {
  const res = await fetch('/api/sistema/estado', { method: 'GET' })
  if (!res.ok) throw new Error('Servicio no disponible')
  return res.json()
}

export async function registrarAccesoPublico(accion: string, resultado = 'OK') {
  try {
    await fetch('/api/bitacora/acceso-publico', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ accion, resultado }),
    })
  } catch {
    // No bloquear la UX del portal si falla el registro de bitácora
  }
}

export async function consultarCasoPublico(codigo: string): Promise<CasoPublico> {
  const res = await fetch(`/api/casos/publico/${encodeURIComponent(codigo)}`)
  if (!res.ok) throw new Error('Código no encontrado')
  return res.json()
}
