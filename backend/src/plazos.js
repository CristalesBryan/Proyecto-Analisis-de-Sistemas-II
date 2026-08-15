/** Feriados municipales de referencia (RN-12). */
export const DIAS_NO_LABORABLES = [
  { fecha: '2026-01-01', descripcion: 'Año Nuevo' },
  { fecha: '2026-04-02', descripcion: 'Jueves Santo' },
  { fecha: '2026-04-03', descripcion: 'Viernes Santo' },
  { fecha: '2026-05-01', descripcion: 'Día del Trabajo' },
  { fecha: '2026-06-30', descripcion: 'Día del Ejército' },
  { fecha: '2026-09-15', descripcion: 'Independencia' },
  { fecha: '2026-10-20', descripcion: 'Revolución' },
  { fecha: '2026-11-01', descripcion: 'Todos los Santos' },
  { fecha: '2026-12-25', descripcion: 'Navidad' },
]

const FERIADOS = new Set(DIAS_NO_LABORABLES.map((item) => item.fecha))

function inicioDia(fecha) {
  const valor = fecha instanceof Date ? new Date(fecha) : new Date(`${fecha}T00:00:00`)
  valor.setHours(0, 0, 0, 0)
  return valor
}

export function aFechaIso(fecha) {
  const valor = inicioDia(fecha)
  const yyyy = valor.getFullYear()
  const mm = String(valor.getMonth() + 1).padStart(2, '0')
  const dd = String(valor.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

export function esDiaNoLaborable(fecha) {
  const valor = inicioDia(fecha)
  const dia = valor.getDay()
  if (dia === 0 || dia === 6) return true
  return FERIADOS.has(aFechaIso(valor))
}

export function sumarDiasHabiles(fechaInicio, dias) {
  const fecha = inicioDia(fechaInicio)
  let restantes = Number(dias)
  if (!Number.isFinite(restantes) || restantes <= 0) return fecha
  while (restantes > 0) {
    fecha.setDate(fecha.getDate() + 1)
    if (!esDiaNoLaborable(fecha)) restantes -= 1
  }
  return fecha
}

export function diasHabilesEntre(desde, hasta) {
  const inicio = inicioDia(desde)
  const fin = inicioDia(hasta)
  if (fin.getTime() === inicio.getTime()) return 0
  const paso = fin > inicio ? 1 : -1
  let cursor = new Date(inicio)
  let cuenta = 0
  while (aFechaIso(cursor) !== aFechaIso(fin)) {
    cursor.setDate(cursor.getDate() + paso)
    if (!esDiaNoLaborable(cursor)) cuenta += paso
  }
  return cuenta
}

export function evaluarPlazo(fechaLimite, hoy = new Date()) {
  if (!fechaLimite) {
    return { diasRestantes: null, semaforo: 'gris', vencido: false }
  }
  const diasRestantes = diasHabilesEntre(hoy, fechaLimite)
  const vencido = diasRestantes < 0
  let semaforo = 'verde'
  if (vencido) semaforo = 'rojo'
  else if (diasRestantes <= 3) semaforo = 'amarillo'
  return { diasRestantes, semaforo, vencido }
}
