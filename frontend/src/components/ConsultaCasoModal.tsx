import { useState, type ChangeEvent, type FormEvent } from 'react'
import { X } from 'lucide-react'
import {
  consultarCasoPublico,
  codigoSeguimientoValido,
  listarSeguimientosPublicos,
  type CasoPublico,
  type SeguimientoCaso,
} from '../services/api'

type ConsultaCasoModalProps = {
  open: boolean
  onClose: () => void
}

export function ConsultaCasoModal({ open, onClose }: ConsultaCasoModalProps) {
  const [codigo, setCodigo] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [resultado, setResultado] = useState<CasoPublico | null>(null)
  const [seguimientos, setSeguimientos] = useState<SeguimientoCaso[]>([])

  if (!open) return null

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const valor = codigo.trim().toUpperCase()
    setError(null)
    setResultado(null)
    setSeguimientos([])

    if (!codigoSeguimientoValido(valor)) {
      setError('Ingrese un código válido. Ejemplo: Q-2026-00001.')
      return
    }

    setLoading(true)
    try {
      const data = await consultarCasoPublico(valor)
      setResultado(data)
      try {
        const linea = await listarSeguimientosPublicos(valor)
        setSeguimientos(linea.seguimientos)
        setResultado({ ...data, avancePorcentaje: linea.avancePorcentaje })
      } catch {
        setSeguimientos([])
      }
    } catch {
      setError('Código no encontrado. Verifique el número e intente nuevamente.')
    } finally {
      setLoading(false)
    }
  }

  function handleClose() {
    setCodigo('')
    setError(null)
    setResultado(null)
    setSeguimientos([])
    onClose()
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="consulta-titulo"
    >
      <div className="liquid-glass w-full max-w-lg rounded-xl border border-white/20 p-6">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 id="consulta-titulo" className="text-xl font-medium">
              Consultar Estado de Caso
            </h2>
            <p className="mt-1 text-sm text-gray-300">
              Ingresa tu código de seguimiento (ej. Q-2026-00001).
            </p>
          </div>
          <button
            type="button"
            onClick={handleClose}
            className="rounded-lg p-1 text-gray-300 transition-colors hover:text-white"
            aria-label="Cerrar"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <label className="block text-sm text-gray-300" htmlFor="codigo">
            Código de seguimiento
          </label>
          <input
            id="codigo"
            value={codigo}
            onChange={(e: ChangeEvent<HTMLInputElement>) => setCodigo(e.target.value.toUpperCase())}
            maxLength={20}
            placeholder="Q-2026-00001"
            className="w-full rounded-lg border border-white/20 bg-black/40 px-4 py-3 text-white outline-none ring-white/30 placeholder:text-gray-500 focus:ring-2"
            autoComplete="off"
            aria-label="Código de seguimiento"
          />

          {error && <p className="text-sm text-red-300">{error}</p>}

          {resultado && (
            <div className="rounded-lg border border-white/20 bg-black/30 p-4 text-sm">
              <p>
                <span className="text-gray-300">Código:</span> {resultado.codigoSeguimiento}
              </p>
              <p className="mt-1">
                <span className="text-gray-300">Tipo:</span> {resultado.tipo}
              </p>
              <p className="mt-1">
                <span className="text-gray-300">Estado:</span> {resultado.estado}
              </p>
              <p className="mt-1">
                <span className="text-gray-300">Registrado:</span> {resultado.fechaRegistro}
              </p>
              {resultado.ultimaActualizacion && (
                <p className="mt-1">
                  <span className="text-gray-300">Última actualización:</span>{' '}
                  {resultado.ultimaActualizacion}
                </p>
              )}
              <div className="mt-3">
                <div className="mb-1 flex items-center justify-between text-xs text-gray-400">
                  <span>Avance</span>
                  <span>{resultado.avancePorcentaje || 0}%</span>
                </div>
                <div className="h-1.5 overflow-hidden rounded-full bg-white/10">
                  <div
                    className="h-full rounded-full bg-white"
                    style={{ width: `${resultado.avancePorcentaje || 0}%` }}
                  />
                </div>
              </div>
              {seguimientos.length > 0 && (
                <ul className="mt-3 max-h-48 space-y-2 overflow-y-auto border-t border-white/10 pt-3">
                  {seguimientos.map((item) => (
                    <li key={item.id}>
                      <p className="font-medium text-white">{item.titulo}</p>
                      <p className="text-xs text-gray-400">
                        {item.creadoEn.slice(0, 10)}
                        {item.porcentajeAvance !== null ? ` · ${item.porcentajeAvance}%` : ''}
                      </p>
                      <p className="mt-1 text-gray-300">{item.descripcion}</p>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-lg bg-white px-4 py-3 text-sm font-medium text-black transition-colors hover:bg-gray-100 disabled:opacity-60"
          >
            {loading ? 'Consultando…' : 'Consultar'}
          </button>
        </form>
      </div>
    </div>
  )
}
