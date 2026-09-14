import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Download, Upload } from 'lucide-react'
import { BadgeEstado, BadgePlazo, InternalLayout, etiquetaEstado } from '../components/InternalLayout'
import { SeguimientoCaso } from '../components/SeguimientoCaso'
import {
  adjuntarDocumentosInterno,
  agregarObservacionCaso,
  anularCaso,
  asignarCaso,
  cambiarEstadoCaso,
  cerrarCaso,
  descargarDocumentoCaso,
  escalarCaso,
  listarAgentes,
  listarDocumentosCaso,
  modificarCaso,
  obtenerAreas,
  obtenerCaso,
  reasignarCaso,
  registrarProrroga,
  type AgenteOpcion,
  type ApiError,
  type AreaDependencia,
  type CasoDetalle,
} from '../services/api'
import { destinosPorRol, getUser } from '../services/auth'
import { formatFecha, formatFechaHora } from '../lib/fechas'

const INPUT =
  'w-full min-w-0 max-w-full rounded-lg border border-white/20 bg-black/40 px-3 py-2 text-sm text-white outline-none focus:ring-2 focus:ring-white/30'
const AREA = `${INPUT} resize-y break-words`
const SELECT = `${INPUT} bg-black pr-8`

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
  const [observacionCierre, setObservacionCierre] = useState('')
  const [diasProrroga, setDiasProrroga] = useState('3')
  const [justificacionProrroga, setJustificacionProrroga] = useState('')
  const [motivoEscalar, setMotivoEscalar] = useState('')
  const [confirmar, setConfirmar] = useState<
    'asignar' | 'reasignar' | 'estado' | 'anular' | 'cerrar' | 'prorroga' | 'escalar' | null
  >(null)
  const [enviando, setEnviando] = useState(false)
  const [subiendoDoc, setSubiendoDoc] = useState(false)
  const [avisoDocs, setAvisoDocs] = useState('')
  const [editando, setEditando] = useState(false)
  const [nombreEdit, setNombreEdit] = useState('')
  const [emailEdit, setEmailEdit] = useState('')
  const [telefonoEdit, setTelefonoEdit] = useState('')
  const [areaEdit, setAreaEdit] = useState('')
  const [prioridadEdit, setPrioridadEdit] = useState('MEDIA')
  const [descripcionEdit, setDescripcionEdit] = useState('')
  const [denunciadoEdit, setDenunciadoEdit] = useState('')
  const [motivoEdit, setMotivoEdit] = useState('')
  const [areas, setAreas] = useState<AreaDependencia[]>([])
  const [pestana, setPestana] = useState<Pestana>(
    location.pathname.endsWith('/seguimiento') ? 'seguimiento' : 'resumen',
  )

  const puedeAsignar = usuario?.rol === 'ADMIN' || usuario?.rol === 'SUPERVISOR'
  const puedeAnular = usuario?.rol === 'ADMIN'
  const puedeCerrar =
    usuario?.permisos?.includes('CASOS_CERRAR') ||
    usuario?.rol === 'ADMIN' ||
    usuario?.rol === 'SUPERVISOR'
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
      setNombreEdit(detalle.nombreCiudadano || '')
      setEmailEdit(detalle.emailCiudadano || '')
      setTelefonoEdit(detalle.telefono || '')
      setAreaEdit(detalle.area)
      setPrioridadEdit(detalle.prioridad || 'MEDIA')
      setDescripcionEdit(detalle.descripcion)
      setDenunciadoEdit(detalle.denunciado || '')
      setEditando(false)
      setMotivoEdit('')
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
    void obtenerAreas().then(setAreas).catch(() => setAreas([]))
  }, [])

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
      } else if (confirmar === 'cerrar') {
        respuesta = await cerrarCaso(caso.id, observacionCierre)
        setObservacionCierre('')
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

  async function guardarModificacion() {
    if (!caso) return
    setEnviando(true)
    setError('')
    setExito('')
    try {
      const respuesta = await modificarCaso(caso.id, {
        nombreCiudadano: caso.esAnonimo ? undefined : nombreEdit,
        email: emailEdit,
        telefono: telefonoEdit,
        areaDependencia: usuario?.rol === 'AGENTE' ? undefined : areaEdit,
        descripcion: descripcionEdit,
        denunciado: denunciadoEdit,
        prioridad: prioridadEdit,
        motivo: motivoEdit,
      })
      setCaso(respuesta.caso)
      setExito(respuesta.mensaje)
      setEditando(false)
      setMotivoEdit('')
    } catch (err) {
      const apiError = err as ApiError
      setError(apiError.message)
    } finally {
      setEnviando(false)
    }
  }

  async function refrescarDocumentos() {
    const lista = await listarDocumentosCaso(casoId)
    setCaso((actual) => (actual ? { ...actual, documentos: lista } : actual))
  }

  async function subirDocumentos(lista: FileList | null) {
    if (!caso || !lista || lista.length === 0 || finalizado) return
    setSubiendoDoc(true)
    setAvisoDocs('')
    setError('')
    try {
      const respuesta = await adjuntarDocumentosInterno(caso.id, Array.from(lista))
      await refrescarDocumentos()
      if (respuesta.rechazados.length > 0) {
        setAvisoDocs(
          `Algunos archivos no se adjuntaron: ${respuesta.rechazados
            .map((item) => `${item.nombre} (${item.motivo})`)
            .join(', ')}.`,
        )
      } else if (respuesta.archivosSubidos.length > 0) {
        setExito('Documento cargado y asociado al caso.')
      }
    } catch (err) {
      const apiError = err as ApiError
      setError(apiError.message || 'No fue posible almacenar el documento.')
    } finally {
      setSubiendoDoc(false)
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
        <div className="grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(18rem,20rem)]">
          <div className="min-w-0 space-y-6">
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
                    ? `El plazo de respuesta venció el ${formatFecha(caso.fechaLimiteRespuesta)}. Escale o registre una prórroga.`
                    : `Quedan ${caso.plazo?.diasRestantes} día(s) hábil(es). Fecha límite: ${formatFecha(caso.fechaLimiteRespuesta)}.`}
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
                    <div className="min-w-0">
                      <dt className="text-gray-400">Ciudadano</dt>
                      <dd className="break-words">{caso.esAnonimo ? 'Anónimo' : caso.nombreCiudadano}</dd>
                    </div>
                    <div className="min-w-0">
                      <dt className="text-gray-400">Correo</dt>
                      <dd className="break-all">{caso.emailCiudadano || '—'}</dd>
                    </div>
                    <div className="min-w-0">
                      <dt className="text-gray-400">Teléfono</dt>
                      <dd>{caso.telefono || '—'}</dd>
                    </div>
                    <div className="min-w-0">
                      <dt className="text-gray-400">Agente</dt>
                      <dd className="break-words">{caso.agenteNombre}</dd>
                    </div>
                    <div className="min-w-0">
                      <dt className="text-gray-400">Registro</dt>
                      <dd>{formatFechaHora(caso.fechaRegistro)}</dd>
                    </div>
                    <div className="min-w-0">
                      <dt className="text-gray-400">Fecha límite</dt>
                      <dd>{formatFecha(caso.fechaLimiteRespuesta)}</dd>
                    </div>
                    <div className="min-w-0 sm:col-span-2">
                      <dt className="text-gray-400">Última actualización</dt>
                      <dd>{formatFechaHora(caso.fechaUltimaActualizacion)}</dd>
                    </div>
                    {caso.fechaProrroga && (
                      <div className="min-w-0">
                        <dt className="text-gray-400">Última prórroga</dt>
                        <dd>{formatFecha(caso.fechaProrroga)}</dd>
                      </div>
                    )}
                  </dl>
                  <p className="mt-5 max-w-full overflow-hidden whitespace-pre-wrap break-all text-sm leading-relaxed text-gray-300">
                    {caso.descripcion}
                  </p>
                  {caso.denunciado && (
                    <p className="mt-3 text-sm text-gray-300">Denunciado: {caso.denunciado}</p>
                  )}
                  {!finalizado && (
                    <div className="mt-5 border-t border-white/10 pt-4">
                      {!editando ? (
                        <button
                          type="button"
                          onClick={() => setEditando(true)}
                          className="rounded-lg border border-white/20 px-4 py-2 text-sm hover:bg-white hover:text-black"
                        >
                          Modificar
                        </button>
                      ) : (
                        <form
                          className="space-y-3"
                          onSubmit={(event) => {
                            event.preventDefault()
                            void guardarModificacion()
                          }}
                        >
                          <h2 className="text-lg font-medium">Modificar datos del caso</h2>
                          {!caso.esAnonimo && (
                            <input
                              className={INPUT}
                              value={nombreEdit}
                              onChange={(event) => setNombreEdit(event.target.value)}
                              placeholder="Nombre del ciudadano"
                            />
                          )}
                          <input
                            className={INPUT}
                            value={emailEdit}
                            onChange={(event) => setEmailEdit(event.target.value)}
                            placeholder="Correo"
                          />
                          <input
                            className={INPUT}
                            value={telefonoEdit}
                            onChange={(event) => setTelefonoEdit(event.target.value)}
                            placeholder="Teléfono"
                          />
                          {usuario?.rol !== 'AGENTE' && (
                            <select
                              className={SELECT}
                              value={areaEdit}
                              onChange={(event) => setAreaEdit(event.target.value)}
                              aria-label="Área"
                            >
                              {areas.map((area) => (
                                <option key={area.codigo} value={area.codigo}>
                                  {area.nombre}
                                </option>
                              ))}
                            </select>
                          )}
                          <select
                            className={SELECT}
                            value={prioridadEdit}
                            onChange={(event) => setPrioridadEdit(event.target.value)}
                            aria-label="Prioridad"
                          >
                            <option value="BAJA">BAJA</option>
                            <option value="MEDIA">MEDIA</option>
                            <option value="ALTA">ALTA</option>
                          </select>
                          <textarea
                            className={AREA}
                            rows={4}
                            value={descripcionEdit}
                            onChange={(event) => setDescripcionEdit(event.target.value)}
                            placeholder="Descripción (50 a 2000 caracteres)"
                          />
                          <input
                            className={INPUT}
                            value={denunciadoEdit}
                            onChange={(event) => setDenunciadoEdit(event.target.value)}
                            placeholder="Denunciado (opcional)"
                          />
                          <textarea
                            className={AREA}
                            rows={2}
                            value={motivoEdit}
                            onChange={(event) => setMotivoEdit(event.target.value)}
                            placeholder="Motivo de la modificación"
                          />
                          <div className="flex gap-2">
                            <button
                              type="submit"
                              disabled={enviando || motivoEdit.trim().length < 10}
                              className="rounded-lg bg-white px-4 py-2 text-sm font-medium text-black hover:bg-gray-100 disabled:opacity-50"
                            >
                              {enviando ? 'Guardando…' : 'Confirmar modificación'}
                            </button>
                            <button
                              type="button"
                              onClick={() => setEditando(false)}
                              className="rounded-lg border border-white/20 px-4 py-2 text-sm hover:bg-white hover:text-black"
                            >
                              Cancelar
                            </button>
                          </div>
                        </form>
                      )}
                    </div>
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
                          <p className="max-w-full overflow-hidden whitespace-pre-wrap break-words">{item.texto}</p>
                          <p className="mt-1 text-xs text-gray-400">
                            {item.usuarioNombre} · {formatFechaHora(item.creadoEn)}
                          </p>
                        </li>
                      ))}
                    </ul>
                  )}
                  {!finalizado && (
                    <div className="space-y-3">
                      <textarea
                        className={AREA}
                        rows={3}
                        value={nota}
                        onChange={(event) => setNota(event.target.value)}
                        placeholder="Observación interna"
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
                {!finalizado && (
                  <label className="mb-5 flex cursor-pointer flex-col items-center rounded-xl border border-dashed border-white/20 px-4 py-6 text-center transition-colors hover:border-white/40">
                    <Upload className="mb-2 h-5 w-5" aria-hidden="true" />
                    <span className="text-sm text-gray-300">
                      {subiendoDoc
                        ? 'Cargando documento…'
                        : 'PDF, JPG, PNG o DOCX · máximo 5 MB · hasta 5 archivos'}
                    </span>
                    <input
                      type="file"
                      className="sr-only"
                      multiple
                      disabled={subiendoDoc}
                      accept=".pdf,.jpg,.jpeg,.png,.docx,application/pdf,image/jpeg,image/png,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                      onChange={(event) => {
                        void subirDocumentos(event.target.files)
                        event.target.value = ''
                      }}
                    />
                  </label>
                )}
                {avisoDocs && (
                  <p className="mb-4 rounded-lg border border-amber-300/30 bg-amber-950/40 px-4 py-3 text-sm text-amber-100">
                    {avisoDocs}
                  </p>
                )}
                {caso.documentos.length === 0 ? (
                  <p className="text-sm text-gray-400">Sin adjuntos.</p>
                ) : (
                  <ul className="space-y-2">
                    {caso.documentos.map((doc) => (
                      <li key={doc.id} className="flex min-w-0 items-center justify-between gap-3 text-sm">
                        <span className="min-w-0 truncate" title={doc.nombreArchivo}>
                          {doc.nombreArchivo}
                        </span>
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
                        {evento.usuarioNombre} · {formatFechaHora(evento.fechaHora)}
                      </p>
                      <p className="whitespace-pre-wrap break-words text-gray-300">{evento.descripcion}</p>
                    </li>
                  ))}
                </ol>
              </div>
            )}
          </div>

          <aside className="min-w-0 space-y-6">
            {caso.estado === 'CERRADO' && (
              <div className="liquid-glass rounded-xl border border-white/20 p-6">
                <h2 className="mb-2 text-lg font-medium">Expediente cerrado</h2>
                <p className="text-sm leading-relaxed text-gray-400">
                  El caso quedó archivado. La información y el historial se conservan para consulta;
                  no admite modificaciones ni nuevos adjuntos.
                </p>
              </div>
            )}

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
                    className={AREA}
                    rows={3}
                    value={justificacionProrroga}
                    onChange={(event) => setJustificacionProrroga(event.target.value)}
                    placeholder="Justificación de prórroga"
                  />
                  <button
                    type="button"
                    onClick={() => setConfirmar('prorroga')}
                    className="w-full rounded-lg bg-white px-4 py-2 text-sm font-medium text-black hover:bg-gray-100"
                  >
                    Registrar prórroga
                  </button>
                  <textarea
                    className={AREA}
                    rows={3}
                    value={motivoEscalar}
                    onChange={(event) => setMotivoEscalar(event.target.value)}
                    placeholder="Motivo de escalamiento"
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
                      className={SELECT}
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
                          className={AREA}
                          rows={3}
                          value={motivo}
                          onChange={(event) => setMotivo(event.target.value)}
                          placeholder="Motivo de reasignación"
                        />
                        <button
                          type="button"
                          onClick={() => setConfirmar('reasignar')}
                          className="w-full rounded-lg bg-white px-4 py-2.5 text-sm font-medium leading-snug text-black hover:bg-gray-100"
                        >
                          Reasignar
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setConfirmar('asignar')}
                        className="w-full whitespace-normal rounded-lg bg-white px-4 py-2.5 text-sm font-medium leading-snug text-black hover:bg-gray-100"
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
                    className={SELECT}
                    value={nuevoEstado}
                    onChange={(event) => setNuevoEstado(event.target.value)}
                    aria-label="Nuevo estado"
                  >
                    {caso.transicionesPermitidas
                      .filter((estado) => estado !== 'ANULADO')
                      .map((estado) => (
                        <option key={estado} value={estado}>
                          {etiquetaEstado(estado)}
                        </option>
                      ))}
                  </select>
                  <textarea
                    className={AREA}
                    rows={4}
                    value={observacionEstado}
                    onChange={(event) => setObservacionEstado(event.target.value)}
                    placeholder="Observación de la transición"
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

            {puedeCerrar && caso.estado === 'RESUELTO' && (
              <div className="liquid-glass rounded-xl border border-white/20 p-6">
                <h2 className="mb-2 text-lg font-medium">Cerrar caso</h2>
                <p className="mb-3 text-sm leading-relaxed text-gray-400">
                  El cierre archiva el expediente resuelto. Documente la operación antes de confirmar.
                </p>
                <textarea
                  className={AREA}
                  rows={3}
                  value={observacionCierre}
                  onChange={(event) => setObservacionCierre(event.target.value)}
                  placeholder="Observación de cierre"
                />
                <button
                  type="button"
                  onClick={() => setConfirmar('cerrar')}
                  disabled={observacionCierre.trim().length < 20}
                  className="mt-3 w-full rounded-lg bg-white px-4 py-2 text-sm font-medium text-black hover:bg-gray-100 disabled:opacity-50"
                >
                  Cerrar caso
                </button>
              </div>
            )}

            {puedeAnular && !finalizado && caso.transicionesPermitidas.includes('ANULADO') && (
              <div className="liquid-glass rounded-xl border border-white/20 p-6">
                <h2 className="mb-4 text-lg font-medium">Anular caso</h2>
                <textarea
                  className={AREA}
                  rows={3}
                  value={justificacion}
                  onChange={(event) => setJustificacion(event.target.value)}
                  placeholder="Justificación de anulación"
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
                `${caso.codigoSeguimiento}: ${etiquetaEstado(caso.estado)} → ${etiquetaEstado(nuevoEstado)}.`}
              {confirmar === 'anular' && `${caso.codigoSeguimiento} pasará a ANULADO. Esta acción es final.`}
              {confirmar === 'cerrar' &&
                `${caso.codigoSeguimiento} pasará de RESUELTO a CERRADO. El historial se conserva y no podrá modificarse.`}
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
