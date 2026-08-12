import { useState, type FormEvent } from 'react'
import { X } from 'lucide-react'
import { consultarCasoPublico } from '../services/api'

type ConsultaCasoModalProps = {
  open: boolean
  onClose: () => void
}

type CasoPublico = {
  codigoSeguimiento: string
  tipo: string
  estado: string
  fechaRegistro: string
}

export function ConsultaCasoModal({ open, onClose }: ConsultaCasoModalProps) {
  const [codigo, setCodigo] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [resultado, setResultado] = useState<CasoPublico | null>(null)

  if (!open) return null

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const valor = codigo.trim().toUpperCase()
    setError(null)
    setResultado(null)

    if (!/^[A-Z0-9]{10}$/.test(valor)) {
      setError('El código debe ser alfanumérico de exactamente 10 caracteres.')
      return
    }

    setLoading(true)
    try {
      const data = await consultarCasoPublico(valor)
      setResultado(data)
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
    onClose()
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="consulta-titulo"
    >
      <div className="liquid-glass w-full max-w-md rounded-xl border border-white/20 p-6">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 id="consulta-titulo" className="text-xl font-medium">
              Consultar Estado de Caso
            </h2>
            <p className="mt-1 text-sm text-gray-300">
              Ingresa tu código de seguimiento (10 caracteres).
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
            onChange={(e) => setCodigo(e.target.value.toUpperCase())}
            maxLength={10}
            placeholder="ABC1234567"
            className="w-full rounded-lg border border-white/20 bg-black/40 px-4 py-3 text-white outline-none ring-white/30 placeholder:text-gray-500 focus:ring-2"
            autoComplete="off"
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
