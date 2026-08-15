import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { Download } from 'lucide-react'
import {
  descargarEvidenciaSeguimiento,
  listarSeguimientos,
  registrarSeguimiento,
  type ApiError,
  type CasoDetalle,
  type SeguimientoCaso as SeguimientoItem,
  type TipoSeguimiento,
} from '../services/api'
import { getUser } from '../services/auth'

const INPUT =
  'w-full rounded-lg border border-white/20 bg-black/40 px-3 py-2 text-sm text-white outline-none focus:ring-2 focus:ring-white/30'

const ESTILOS_TIPO: Record<TipoSeguimiento, string> = {
  PUBLICA: 'border-sky-300/40 text-sky-100',
  INTERNA: 'border-white/25 text-gray-200',
  CORRECCION: 'border-amber-300/40 text-amber-100',
}

const ABIERTOS = new Set(['EN_REVISION', 'EN_PROCESO'])

type Props = {
  caso: CasoDetalle
  onCasoActualizado: (caso: CasoDetalle) => void
  onError: (mensaje: string) => void
  onExito: (mensaje: string) => void
}

export function SeguimientoCaso({ caso, onCasoActualizado, onError, onExito }: Props) {
  const usuario = getUser()
  const [items, setItems] = useState<SeguimientoItem[]>([])
  const [cargando, setCargando] = useState(true)
  const [enviando, setEnviando] = useState(false)
  const [tipo, setTipo] = useState<TipoSeguimiento>('PUBLICA')
  const [titulo, setTitulo] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [porcentaje, setPorcentaje] = useState(String(caso.avancePorcentaje ?? 0))
  const [notificar, setNotificar] = useState(false)
  const [padreId, setPadreId] = useState('')
  const [justificacion, setJustificacion] = useState('')
  const [archivo, setArchivo] = useState<File | null>(null)

  const abierto = ABIERTOS.has(caso.estado)
  const excepcional = !abierto && usuario?.rol === 'ADMIN'
  const puedeRegistrar = abierto || excepcional

  useEffect(() => {
    let cancelado = false
    setCargando(true)
    listarSeguimientos(caso.id)
      .then((respuesta) => {
        if (cancelado) return
        setItems(respuesta.seguimientos)
        if (respuesta.caso) onCasoActualizado(respuesta.caso)
      })
      .catch((err: ApiError) => {
        if (!cancelado) onError(err.message)
      })
      .finally(() => {
        if (!cancelado) setCargando(false)
      })
    return () => {
      cancelado = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [caso.id])

  useEffect(() => {
    if (excepcional) setTipo('INTERNA')
  }, [excepcional])

  async function enviar(event: FormEvent) {
    event.preventDefault()
    if (titulo.trim().length < 5 || titulo.trim().length > 120) {
      onError('El título debe tener entre 5 y 120 caracteres.')
      return
    }
    if (descripcion.trim().length < 20 || descripcion.trim().length > 2000) {
      onError('La descripción debe tener entre 20 y 2000 caracteres.')
      return
    }
    if (tipo === 'CORRECCION' && !padreId) {
      onError('La corrección debe referenciar un seguimiento existente.')
      return
    }
    if (excepcional && justificacion.trim().length < 20) {
      onError('En casos finalizados justifique la nota interna (mín. 20 caracteres).')
      return
    }
    if (archivo && archivo.size > 5 * 1024 * 1024) {
      onError('El archivo supera el máximo de 5 MB.')
      return
    }

    const form = new FormData()
    form.append('tipo', excepcional ? 'INTERNA' : tipo)
    form.append('titulo', titulo.trim())
    form.append('descripcion', descripcion.trim())
    if (porcentaje !== '') form.append('porcentajeAvance', porcentaje)
    form.append('notificarCiudadano', String(tipo === 'PUBLICA' && notificar && !caso.esAnonimo && abierto))
    if (tipo === 'CORRECCION' && padreId) form.append('seguimientoPadreId', padreId)
    if (excepcional) form.append('justificacionExcepcional', justificacion.trim())
    if (archivo) form.append('archivo', archivo)

    setEnviando(true)
    try {
      const respuesta = await registrarSeguimiento(caso.id, form)
      setItems(respuesta.seguimientos)
      onCasoActualizado(respuesta.caso)
      onExito(respuesta.avisoCorreo ? `${respuesta.mensaje} ${respuesta.avisoCorreo}` : respuesta.mensaje)
      setTitulo('')
      setDescripcion('')
      setJustificacion('')
      setArchivo(null)
      setNotificar(false)
      setPadreId('')
      if (respuesta.caso.avancePorcentaje === 100) {
        onExito(
          `${respuesta.mensaje} El 100% de avance no resuelve el caso. Use Proceder a resolver.`,
        )
      }
    } catch (err) {
      onError((err as ApiError).message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="space-y-6">
      {cargando ? (
        <p className="text-sm text-gray-400">Cargando línea de tiempo…</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-gray-400">Aún no hay avances registrados en este caso.</p>
      ) : (
        <ol className="space-y-3">
          {items.map((item) => (
            <li key={item.id} className="rounded-xl border border-white/15 p-4">
              <div className="mb-2 flex flex-wrap items-center gap-2">
                <span className={`rounded-full border px-2 py-0.5 text-xs ${ESTILOS_TIPO[item.tipo]}`}>
                  {item.tipo}
                </span>
                {item.porcentajeAvance !== null && (
                  <span className="text-xs text-gray-400">{item.porcentajeAvance}% de avance</span>
                )}
                {item.notificado && <span className="text-xs text-gray-500">Notificado</span>}
              </div>
              <h3 className="text-base font-medium">{item.titulo}</h3>
              <p className="mt-1 text-sm leading-relaxed text-gray-300">{item.descripcion}</p>
              <p className="mt-2 text-xs text-gray-500">
                {item.usuarioNombre || 'Agente'} · {item.creadoEn.slice(0, 16).replace('T', ' ')}
                {item.seguimientoPadreId ? ` · corrige #${item.seguimientoPadreId}` : ''}
              </p>
              {item.adjunto && (
                <button
                  type="button"
                  className="mt-3 inline-flex items-center gap-1 text-sm text-gray-300 hover:text-white"
                  onClick={() =>
                    void descargarEvidenciaSeguimiento(
                      caso.id,
                      item.id,
                      item.adjunto!.nombreArchivo,
                    ).catch((err: Error) => onError(err.message))
                  }
                >
                  <Download className="h-4 w-4" aria-hidden="true" />
                  {item.adjunto.nombreArchivo}
                </button>
              )}
            </li>
          ))}
        </ol>
      )}

      {puedeRegistrar ? (
        <form className="space-y-3 rounded-xl border border-white/15 p-4" onSubmit={(event) => void enviar(event)}>
          <h3 className="text-lg font-medium">Registrar avance</h3>
          {excepcional && (
            <p className="text-sm text-amber-100">
              El caso está finalizado. Solo puede agregar una nota interna con justificación.
            </p>
          )}
          <select
            className={`${INPUT} bg-black`}
            value={tipo}
            disabled={excepcional}
            onChange={(event) => setTipo(event.target.value as TipoSeguimiento)}
            aria-label="Tipo de seguimiento"
          >
            <option value="PUBLICA">Pública (visible al ciudadano)</option>
            <option value="INTERNA">Interna</option>
            <option value="CORRECCION">Corrección</option>
          </select>
          {tipo === 'CORRECCION' && (
            <select
              className={`${INPUT} bg-black`}
              value={padreId}
              onChange={(event) => setPadreId(event.target.value)}
              aria-label="Seguimiento a corregir"
            >
              <option value="">Seleccione el seguimiento a corregir</option>
              {items.map((item) => (
                <option key={item.id} value={item.id}>
                  #{item.id} · {item.titulo}
                </option>
              ))}
            </select>
          )}
          <input
            className={INPUT}
            value={titulo}
            onChange={(event) => setTitulo(event.target.value)}
            placeholder="Título (5 a 120 caracteres)"
            maxLength={120}
          />
          <textarea
            className={INPUT}
            rows={4}
            value={descripcion}
            onChange={(event) => setDescripcion(event.target.value)}
            placeholder="Descripción del avance (20 a 2000 caracteres)"
            maxLength={2000}
          />
          <label className="block text-sm text-gray-300">
            Porcentaje de avance
            <input
              className={`${INPUT} mt-1`}
              type="number"
              min={0}
              max={100}
              step={1}
              value={porcentaje}
              onChange={(event) => setPorcentaje(event.target.value)}
            />
          </label>
          {excepcional && (
            <textarea
              className={INPUT}
              rows={3}
              value={justificacion}
              onChange={(event) => setJustificacion(event.target.value)}
              placeholder="Justificación excepcional (mín. 20 caracteres)"
            />
          )}
          {tipo === 'PUBLICA' && abierto && !caso.esAnonimo && (
            <label className="flex items-center gap-2 text-sm text-gray-300">
              <input
                type="checkbox"
                checked={notificar}
                onChange={(event) => setNotificar(event.target.checked)}
                className="accent-white"
              />
              Notificar al ciudadano por correo
            </label>
          )}
          {caso.esAnonimo && tipo === 'PUBLICA' && (
            <p className="text-xs text-gray-500">Caso anónimo: no se envía notificación por correo.</p>
          )}
          <input
            type="file"
            accept=".pdf,.jpg,.jpeg,.png,.docx,application/pdf,image/jpeg,image/png,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              setArchivo(event.target.files?.[0] || null)
            }
            className="text-sm text-gray-300 file:mr-3 file:rounded-lg file:border-0 file:bg-white file:px-3 file:py-1.5 file:text-sm file:text-black"
          />
          <button
            type="submit"
            disabled={enviando}
            className="rounded-lg bg-white px-4 py-2 text-sm font-medium text-black hover:bg-gray-100 disabled:opacity-50"
          >
            {enviando ? 'Guardando…' : 'Registrar seguimiento'}
          </button>
        </form>
      ) : (
        <p className="text-sm text-gray-400">
          {caso.estado === 'RECIBIDO'
            ? 'El caso debe pasar a revisión o proceso para registrar seguimientos.'
            : 'No se pueden registrar seguimientos en casos finalizados.'}
        </p>
      )}
    </div>
  )
}
