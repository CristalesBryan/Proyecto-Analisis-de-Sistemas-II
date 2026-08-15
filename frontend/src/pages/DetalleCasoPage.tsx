import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Download } from 'lucide-react'
import { BadgeEstado, BadgePlazo, InternalLayout } from '../components/InternalLayout'
import { SeguimientoCaso } from '../components/SeguimientoCaso'
import {
  agregarObservacionCaso,
  anularCaso,
  asignarCaso,
  cambiarEstadoCaso,
  descargarDocumentoCaso,
  escalarCaso,
  listarAgentes,
  obtenerCaso,
  reasignarCaso,
  registrarProrroga,
  type AgenteOpcion,
  type ApiError,
  type CasoDetalle,
} from '../services/api'
import { destinosPorRol, getUser } from '../services/auth'

const INPUT =
  'w-full rounded-lg border border-white/20 bg-black/40 px-3 py-2 text-sm text-white outline-none focus:ring-2 focus:ring-white/30'

type Pestana = 'resumen' | 'seguimiento' | 'documentos' | 'historial'

export function DetalleCasoPage() {
  const { id } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const usuario = getUser()
  const casoId = Number(id)
  const [caso, setCaso] = useState<CasoDetalle | null>(null)
  const [agentes, setAgentes] = useState<AgenteOpcion[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [exito, setExito] = useState('')
  const [agenteId, setAgenteId] = useState('')
  const [motivo, setMotivo] = useState('')
  const [nuevoEstado, setNuevoEstado] = useState('')
  const [observacionEstado, setObservacionEstado] = useState('')
  const [nota, setNota] = useState('')
  const [justificacion, setJustificacion] = useState('')
  const [diasProrroga, setDiasProrroga] = useState('3')
  const [justificacionProrroga, setJustificacionProrroga] = useState('')
  const [motivoEscalar, setMotivoEscalar] = useState('')
  const [confirmar, setConfirmar] = useState<
    'asignar' | 'reasignar' | 'estado' | 'anular' | 'prorroga' | 'escalar' | null
  >(null)
  const [enviando, setEnviando] = useState(false)
  const [pestana, setPestana] = useState<Pestana>(
    location.pathname.endsWith('/seguimiento') ? 'seguimiento' : 'resumen',
  )

  const puedeAsignar = usuario?.rol === 'ADMIN' || usuario?.rol === 'SUPERVISOR'
  const puedeAnular = usuario?.rol === 'ADMIN'
  const puedePlazo = usuario?.rol === 'ADMIN' || usuario?.rol === 'SUPERVISOR'
  const bandeja = usuario ? destinosPorRol[usuario.rol] : '/'
  const finalizado = caso?.estado === 'CERRADO' || caso?.estado === 'ANULADO'
  const abiertoSeguimiento = caso?.estado === 'EN_REVISION' || caso?.estado === 'EN_PROCESO'
  const alertaPlazo =
    caso?.plazo &&
    (caso.plazo.vencido || (caso.plazo.diasRestantes !== null && caso.plazo.diasRestantes <= 3))

  async function cargar() {
    setCargando(true)
    setError('')
    try {
      const detalle = await obtenerCaso(casoId)
      setCaso(detalle)
      if (!detalle.transicionesPermitidas.includes(nuevoEstado)) {
        setNuevoEstado(detalle.transicionesPermitidas[0] || '')
      }
      if (puedeAsignar) {
        const lista = await listarAgentes(detalle.area)
        setAgentes(lista)
        setAgenteId((actual) => actual || String(lista[0]?.id || ''))
      }
    } catch (err) {
      const apiError = err as ApiError
      setError(
        apiError.conexion
          ? 'Error al gestionar el caso. Verifique su conexión e intente nuevamente.'
          : apiError.message,
      )
      setCaso(null)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => {
    if (!Number.isFinite(casoId)) {
      setError('Caso no encontrado.')
      setCargando(false)
      return
    }
    void cargar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [casoId])

  useEffect(() => {
    if (location.pathname.endsWith('/seguimiento')) {
      setPestana('seguimiento')
      return
    }
    setPestana((actual) => (actual === 'seguimiento' ? 'resumen' : actual))
  }, [location.pathname])

  function cambiarPestana(siguiente: Pestana) {
    setPestana(siguiente)
    const destino =
      siguiente === 'seguimiento' ? `/casos/${casoId}/seguimiento` : `/casos/${casoId}`
    if (location.pathname !== destino) navigate(destino, { replace: true })
  }

  async function ejecutar() {
    if (!caso || !confirmar) return
    setEnviando(true)
    setError('')
    setExito('')
    try {
      let respuesta
      if (confirmar === 'asignar') {
        respuesta = await asignarCaso(caso.id, Number(agenteId))
      } else if (confirmar === 'reasignar') {
        respuesta = await reasignarCaso(caso.id, Number(agenteId), motivo)
      } else if (confirmar === 'estado') {
        respuesta = await cambiarEstadoCaso(caso.id, nuevoEstado, observacionEstado)
      } else if (confirmar === 'prorroga') {
        respuesta = await registrarProrroga(caso.id, Number(diasProrroga), justificacionProrroga)
        setJustificacionProrroga('')
      } else if (confirmar === 'escalar') {
        respuesta = await escalarCaso(caso.id, motivoEscalar)
        setMotivoEscalar('')
      } else {
        respuesta = await anularCaso(caso.id, justificacion)
      }
      setCaso(respuesta.caso)
      setExito(respuesta.mensaje)
      setConfirmar(null)
      setMotivo('')
      setObservacionEstado('')
      setJustificacion('')
    } catch (err) {
      const apiError = err as ApiError
      setError(
        apiError.conexion
          ? 'Error al gestionar el caso. Verifique su conexión e intente nuevamente.'
          : apiError.message,
      )
    } finally {
      setEnviando(false)
    }
  }

  async function guardarNota() {
    if (!caso) return
    setEnviando(true)
    setError('')
    setExito('')
    try {
      const respuesta = await agregarObservacionCaso(caso.id, nota)
      setCaso(respuesta.caso)
      setExito(respuesta.mensaje)
      setNota('')
    } catch (err) {
      const apiError = err as ApiError
      setError(apiError.message)
    } finally {
      setEnviando(false)
    }
  }

  const pestanas: { id: Pestana; label: string }[] = [
    { id: 'resumen', label: 'Resumen' },
    { id: 'seguimiento', label: 'Seguimiento' },
    { id: 'documentos', label: 'Documentos' },
    { id: 'historial', label: 'Historial' },
  ]

  return (
    <InternalLayout>
      <Link
        to={bandeja}
        className="mb-6 inline-flex items-center gap-2 text-sm text-gray-300 hover:text-white"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Volver a la bandeja
      </Link>

      {cargando && <p className="text-gray-400">Cargando detalle…</p>}

      {error && (
        <div className="mb-4 rounded-lg border border-red-300/30 bg-red-950/40 px-4 py-3 text-sm text-red-100" role="alert">
          {error}
          <button type="button" className="ml-3 underline" onClick={() => void cargar()}>
            Reintentar
          </button>
        </div>
      )}

      {exito && (
        <div className="mb-4 rounded-lg border border-emerald-300/30 bg-emerald-950/40 px-4 py-3 text-sm text-emerald-100">
          {exito}
        </div>
      )}

      {caso && (
        <div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
          <div className="space-y-6">
            <div className="liquid-glass rounded-xl border border-white/20 p-6">
              <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="text-sm text-gray-300">
                    {caso.tipo} · {caso.areaNombre} · {caso.prioridad}
                    {caso.escalado ? ' · Escalado' : ''}
                  </p>
                  <h1 className="mt-1 text-3xl font-normal tracking-[-0.04em]">
                    {caso.codigoSeguimiento}
                  </h1>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <BadgeEstado estado={caso.estado} />
                  <BadgePlazo plazo={caso.plazo} />
                </div>
              </div>

              {alertaPlazo && (
                <div
                  className={`mb-4 rounded-lg border px-4 py-3 text-sm ${
                    caso.plazo?.vencido
                      ? 'border-red-300/30 bg-red-950/40 text-red-100'
                      : 'border-amber-300/30 bg-amber-950/30 text-amber-100'
                  }`}
                  role="status"
                >
                  {caso.plazo?.vencido
                    ? `El plazo de respuesta venció el ${caso.fechaLimiteRespuesta}. Escale o registre una prórroga.`
                    : `Quedan ${caso.plazo?.diasRestantes} día(s) hábil(es). Fecha límite: ${caso.fechaLimiteRespuesta}.`}
                </div>
              )}

              <div className="mb-5">
                <div className="mb-1 flex items-center justify-between text-xs text-gray-400">
                  <span>Avance</span>
                  <span>{caso.avancePorcentaje || 0}%</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-white/10">
                  <div
                    className="h-full rounded-full bg-white"
                    style={{ width: `${caso.avancePorcentaje || 0}%` }}
                  />
                </div>
              </div>

              <div className="flex flex-wrap gap-2 border-t border-white/10 pt-4">
                {pestanas.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => cambiarPestana(item.id)}
                    className={`rounded-lg px-3 py-1.5 text-sm ${
                      pestana === item.id ? 'bg-white text-black' : 'text-gray-300 hover:bg-white/10'
                    }`}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>

            {pestana === 'resumen' && (
              <>
                <div className="liquid-glass rounded-xl border border-white/20 p-6">
                  <dl className="grid gap-3 text-sm sm:grid-cols-2">
                    <div>
                      <dt className="text-gray-400">Ciudadano</dt>
                      <dd>{caso.esAnonimo ? 'Anónimo' : caso.nombreCiudadano}</dd>
                    </div>
                    <div>
                      <dt className="text-gray-400">Correo</dt>
                      <dd>{caso.emailCiudadano || '—'}</dd>
                    </div>
                    <div>
                      <dt className="text-gray-400">Teléfono</dt>
                      <dd>{caso.telefono || '—'}</dd>
                    </div>
                    <div>
                      <dt className="text-gray-400">Agente</dt>
                      <dd>{caso.agenteNombre}</dd>
                    </div>
                    <div>
                      <dt className="text-gray-400">Registro</dt>
                      <dd>{caso.fechaRegistro}</dd>
                    </div>
                    <div>
                      <dt className="text-gray-400">Fecha límite</dt>
                      <dd>{caso.fechaLimiteRespuesta || '—'}</dd>
                    </div>
                    <div>
                      <dt className="text-gray-400">Última actualización</dt>
                      <dd>{caso.fechaUltimaActualizacion}</dd>
                    </div>
                    {caso.fechaProrroga && (
                      <div>
                        <dt className="text-gray-400">Última prórroga</dt>
                        <dd>{caso.fechaProrroga.slice(0, 10)}</dd>
                      </div>
                    )}
                  </dl>
                  <p className="mt-5 text-sm leading-relaxed text-gray-300">{caso.descripcion}</p>
                  {caso.denunciado && (
                    <p className="mt-3 text-sm text-gray-300">Denunciado: {caso.denunciado}</p>
                  )}
                </div>

                <div className="liquid-glass rounded-xl border border-white/20 p-6">
                  <h2 className="mb-4 text-lg font-medium">Observaciones internas</h2>
                  {caso.observaciones.length === 0 ? (
                    <p className="mb-4 text-sm text-gray-400">Aún no hay notas internas.</p>
                  ) : (
                    <ul className="mb-4 space-y-3 text-sm">
                      {caso.observaciones.map((item) => (
                        <li key={item.id} className="rounded-lg border border-white/10 p-3">
                          <p>{item.texto}</p>
                          <p className="mt-1 text-xs text-gray-400">
                            {item.usuarioNombre} · {item.creadoEn.slice(0, 16).replace('T', ' ')}
                          </p>
                        </li>
                      ))}
                    </ul>
                  )}
                  {!finalizado && (
                    <div className="space-y-3">
                      <textarea
                        className={INPUT}
                        rows={3}
                        value={nota}
                        onChange={(event) => setNota(event.target.value)}
                        placeholder="Observación interna (no visible para el ciudadano)"
                      />
                      <button
                        type="button"
                        disabled={enviando || nota.trim().length < 10}
                        onClick={() => void guardarNota()}
                        className="rounded-lg bg-white px-4 py-2 text-sm font-medium text-black hover:bg-gray-100 disabled:opacity-50"
                      >
                        Agregar observación
                      </button>
                    </div>
                  )}
                </div>
              </>
            )}

            {pestana === 'seguimiento' && (
              <div className="liquid-glass rounded-xl border border-white/20 p-6">
                <h2 className="mb-4 text-lg font-medium">Línea de tiempo</h2>
                <SeguimientoCaso
                  caso={caso}
                  onCasoActualizado={setCaso}
                  onError={setError}
                  onExito={setExito}
                />
              </div>
            )}

            {pestana === 'documentos' && (
              <div className="liquid-glass rounded-xl border border-white/20 p-6">
                <h2 className="mb-4 text-lg font-medium">Documentos</h2>
                {caso.documentos.length === 0 ? (
                  <p className="text-sm text-gray-400">Sin adjuntos.</p>
                ) : (
                  <ul className="space-y-2">
                    {caso.documentos.map((doc) => (
                      <li key={doc.id} className="flex items-center justify-between text-sm">
                        <span>{doc.nombreArchivo}</span>
                        <button
                          type="button"
                          className="inline-flex items-center gap-1 text-gray-300 hover:text-white"
                          onClick={() =>
                            void descargarDocumentoCaso(caso.id, doc.id, doc.nombreArchivo).catch(
                              (err: Error) => setError(err.message),
                            )
                          }
                        >
                          <Download className="h-4 w-4" aria-hidden="true" />
                          Descargar
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}

            {pestana === 'historial' && (
              <div className="liquid-glass rounded-xl border border-white/20 p-6">
                <h2 className="mb-4 text-lg font-medium">Historial de estados</h2>
                <ol className="space-y-3 text-sm">
                  {caso.historial.map((evento, index) => (
                    <li key={`${evento.fechaHora}-${index}`} className="border-l border-white/20 pl-3">
                      <p className="text-gray-200">
                        {evento.tipoEvento.split('_').join(' ')}
                        {evento.estadoNuevo ? ` · ${evento.estadoNuevo}` : ''}
                      </p>
                      <p className="text-gray-400">
                        {evento.usuarioNombre} · {evento.fechaHora.slice(0, 16).replace('T', ' ')}
                      </p>
                      <p className="text-gray-300">{evento.descripcion}</p>
                    </li>
                  ))}
                </ol>
              </div>
            )}
          </div>

          <aside className="space-y-6">
            {abiertoSeguimiento && (
              <div className="liquid-glass rounded-xl border border-white/20 p-6">
                <h2 className="mb-2 text-lg font-medium">Resolver caso</h2>
                <p className="mb-3 text-sm text-gray-400">
                  El porcentaje de avance no cierra el expediente. La resolución formal corresponde al CU-05.
                </p>
                <Link
                  to={`/casos/${caso.id}/resolver`}
                  className="block rounded-lg bg-white px-4 py-2 text-center text-sm font-medium text-black hover:bg-gray-100"
                >
                  Proceder a resolver
                </Link>
              </div>
            )}

            {puedePlazo && abiertoSeguimiento && (
              <div className="liquid-glass rounded-xl border border-white/20 p-6">
                <h2 className="mb-4 text-lg font-medium">Plazo</h2>
                <div className="space-y-3">
                  <label className="block text-sm text-gray-300">
                    Días hábiles de prórroga (1–15)
                    <input
                      className={`${INPUT} mt-1`}
                      type="number"
                      min={1}
                      max={15}
                      value={diasProrroga}
                      onChange={(event) => setDiasProrroga(event.target.value)}
                    />
                  </label>
                  <textarea
                    className={INPUT}
                    rows={3}
                    value={justificacionProrroga}
                    onChange={(event) => setJustificacionProrroga(event.target.value)}
                    placeholder="Justificación de prórroga (mín. 20 caracteres)"
                  />
                  <button
                    type="button"
                    onClick={() => setConfirmar('prorroga')}
                    className="w-full rounded-lg bg-white px-4 py-2 text-sm font-medium text-black hover:bg-gray-100"
                  >
                    Registrar prórroga
                  </button>
                  <textarea
                    className={INPUT}
                    rows={3}
                    value={motivoEscalar}
                    onChange={(event) => setMotivoEscalar(event.target.value)}
                    placeholder="Motivo de escalamiento (mín. 10 caracteres)"
                  />
                  <button
                    type="button"
                    onClick={() => setConfirmar('escalar')}
                    className="w-full rounded-lg border border-white/20 px-4 py-2 text-sm hover:bg-white hover:text-black"
                  >
                    Escalar caso
                  </button>
                </div>
              </div>
            )}

            {puedeAsignar && !finalizado && (
              <div className="liquid-glass rounded-xl border border-white/20 p-6">
                <h2 className="mb-4 text-lg font-medium">
                  {caso.agenteAsignadoId ? 'Reasignar agente' : 'Asignar agente'}
                </h2>
                {agentes.length === 0 ? (
                  <p className="text-sm text-amber-100">
                    No hay agentes disponibles en esta área. Contacte al administrador.
                  </p>
                ) : (
                  <div className="space-y-3">
                    <select
                      className={`${INPUT} bg-black`}
                      value={agenteId}
                      onChange={(event) => setAgenteId(event.target.value)}
                    >
                      {agentes.map((agente) => (
                        <option key={agente.id} value={agente.id}>
                          {agente.nombre}
                        </option>
                      ))}
                    </select>
                    {caso.agenteAsignadoId ? (
                      <>
                        <textarea
                          className={INPUT}
                          rows={3}
                          value={motivo}
                          onChange={(event) => setMotivo(event.target.value)}
                          placeholder="Motivo de reasignación (mín. 10 caracteres)"
                        />
                        <button
                          type="button"
                          onClick={() => setConfirmar('reasignar')}
                          className="w-full rounded-lg bg-white px-4 py-2 text-sm font-medium text-black hover:bg-gray-100"
                        >
                          Reasignar
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setConfirmar('asignar')}
                        className="w-full rounded-lg bg-white px-4 py-2 text-sm font-medium text-black hover:bg-gray-100"
                      >
                        Asignar y pasar a revisión
                      </button>
                    )}
                  </div>
                )}
              </div>
            )}

            {!finalizado && caso.transicionesPermitidas.filter((estado) => estado !== 'ANULADO').length > 0 && (
              <div className="liquid-glass rounded-xl border border-white/20 p-6">
                <h2 className="mb-4 text-lg font-medium">Cambiar estado</h2>
                <div className="space-y-3">
                  <select
                    className={`${INPUT} bg-black`}
                    value={nuevoEstado}
                    onChange={(event) => setNuevoEstado(event.target.value)}
                  >
                    {caso.transicionesPermitidas
                      .filter((estado) => estado !== 'ANULADO')
                      .map((estado) => (
                        <option key={estado} value={estado}>
                          {estado.split('_').join(' ')}
                        </option>
                      ))}
                  </select>
                  <textarea
                    className={INPUT}
                    rows={3}
                    value={observacionEstado}
                    onChange={(event) => setObservacionEstado(event.target.value)}
                    placeholder="Observación / justificación (mín. 10 caracteres)"
                  />
                  <button
                    type="button"
                    onClick={() => setConfirmar('estado')}
                    className="w-full rounded-lg bg-white px-4 py-2 text-sm font-medium text-black hover:bg-gray-100"
                  >
                    Cambiar estado
                  </button>
                </div>
              </div>
            )}

            {puedeAnular && !finalizado && caso.transicionesPermitidas.includes('ANULADO') && (
              <div className="liquid-glass rounded-xl border border-white/20 p-6">
                <h2 className="mb-4 text-lg font-medium">Anular caso</h2>
                <textarea
                  className={INPUT}
                  rows={3}
                  value={justificacion}
                  onChange={(event) => setJustificacion(event.target.value)}
                  placeholder="Justificación obligatoria (mín. 20 caracteres)"
                />
                <button
                  type="button"
                  onClick={() => setConfirmar('anular')}
                  className="mt-3 w-full rounded-lg border border-red-300/40 px-4 py-2 text-sm text-red-100 hover:bg-red-950/40"
                >
                  Anular caso
                </button>
              </div>
            )}
          </aside>
        </div>
      )}

      {confirmar && caso && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4">
          <div className="liquid-glass w-full max-w-md rounded-xl border border-white/20 p-6">
            <h2 className="text-xl font-medium">Confirmar acción</h2>
            <p className="mt-3 text-sm leading-relaxed text-gray-300">
              {confirmar === 'asignar' &&
                `Asignar ${caso.codigoSeguimiento} y cambiar RECIBIDO → EN_REVISION.`}
              {confirmar === 'reasignar' && `Reasignar ${caso.codigoSeguimiento} a otro agente. El estado no cambia.`}
              {confirmar === 'estado' &&
                `${caso.codigoSeguimiento}: ${caso.estado} → ${nuevoEstado}.`}
              {confirmar === 'anular' && `${caso.codigoSeguimiento} pasará a ANULADO. Esta acción es final.`}
              {confirmar === 'prorroga' &&
                `Prorrogar ${caso.codigoSeguimiento} ${diasProrroga} día(s) hábil(es).`}
              {confirmar === 'escalar' && `Escalar ${caso.codigoSeguimiento} y marcar prioridad ALTA.`}
            </p>
            <div className="mt-6 flex gap-3">
              <button
                type="button"
                disabled={enviando}
                onClick={() => void ejecutar()}
                className="rounded-lg bg-white px-4 py-2 text-sm font-medium text-black hover:bg-gray-100"
              >
                {enviando ? 'Aplicando…' : 'Confirmar'}
              </button>
              <button
                type="button"
                onClick={() => setConfirmar(null)}
                className="rounded-lg border border-white/20 px-4 py-2 text-sm hover:bg-white hover:text-black"
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}
    </InternalLayout>
  )
}
