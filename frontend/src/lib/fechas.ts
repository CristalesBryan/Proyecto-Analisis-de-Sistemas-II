const ZONA = 'America/Guatemala'

function parsear(valor: string): Date | null {
  const fecha = new Date(valor)
  return Number.isNaN(fecha.getTime()) ? null : fecha
}

export function formatFechaHora(valor?: string | null): string {
  if (!valor) return '—'
  const fecha = parsear(valor)
  if (!fecha) return valor
  return new Intl.DateTimeFormat('es-GT', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: ZONA,
  }).format(fecha)
}

export function formatFecha(valor?: string | null): string {
  if (!valor) return '—'
  if (/^\d{4}-\d{2}-\d{2}$/.test(valor)) {
    const [anio, mes, dia] = valor.split('-').map(Number)
    return new Intl.DateTimeFormat('es-GT', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: ZONA,
    }).format(new Date(Date.UTC(anio, mes - 1, dia, 18)))
  }
  const fecha = parsear(valor)
  if (!fecha) return valor
  return new Intl.DateTimeFormat('es-GT', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: ZONA,
  }).format(fecha)
}
