import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { BadgeEstado, BadgePlazo, InternalLayout, etiquetaEstado } from '../components/InternalLayout'
import {
  listarCasos,
  obtenerAreas,
  type ApiError,
  type AreaDependencia,
  type CasoResumen,
  type FiltrosBandeja,
} from '../services/api'
import { getUser } from '../services/auth'
import { formatFechaHora } from '../lib/fechas'

const ESTADOS = ['RECIBIDO', 'EN_REVISION', 'EN_PROCESO', 'RESUELTO', 'CERRADO', 'ANULADO']
const TIPOS = [
  { codigo: 'Q', nombre: 'Queja' },
  { codigo: 'R', nombre: 'Reclamo' },
  { codigo: 'D', nombre: 'Denuncia' },
  { codigo: 'S', nombre: 'Sugerencia' },
]

const INPUT =
  'rounded-lg border border-white/20 bg-black/40 px-3 py-2 text-sm text-white outline-none focus:ring-2 focus:ring-white/30'

export function BandejaCasosPage() {
  const location = useLocation()
  const usuario = getUser()
  const [filtros, setFiltros] = useState<FiltrosBandeja>({
    page: 1,
    size: 20,
    orden: 'fechaRegistro',
    direccion: 'desc',
  })
  const [casos, setCasos] = useState<CasoResumen[]>([])
  const [areas, setAreas] = useState<AreaDependencia[]>([])
  const [total, setTotal] = useState(0)
  const [paginas, setPaginas] = useState(1)
  const [pagina, setPagina] = useState(1)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    void obtenerAreas().then(setAreas).catch(() => setAreas([]))
  }, [])

  useEffect(() => {
    function recargarAlVolver() {
      setFiltros((actual) => ({ ...actual }))
    }
    window.addEventListener('focus', recargarAlVolver)
    return () => window.removeEventListener('focus', recargarAlVolver)
  }, [])

  useEffect(() => {
    let cancelado = false
    setCargando(true)
    setError('')
    listarCasos(filtros)
      .then((respuesta) => {
        if (cancelado) return
        setCasos(respuesta.content)
        setTotal(respuesta.totalElements)
        setPaginas(respuesta.totalPages)
        setPagina(respuesta.page)
      })
      .catch((err: ApiError) => {
        if (cancelado) return
        setError(
          err.conexion
            ? 'Error al gestionar el caso. Verifique su conexión e intente nuevamente.'
            : err.message,
        )
      })
      .finally(() => {
        if (!cancelado) setCargando(false)
      })
    return () => {
      cancelado = true
    }
  }, [filtros, location.key])

  function actualizar<K extends keyof FiltrosBandeja>(clave: K, valor: FiltrosBandeja[K]) {
    setFiltros((actual) => ({ ...actual, [clave]: valor, page: 1 }))
  }

  function ordenar(campo: string) {
    setFiltros((actual) => ({
      ...actual,
      orden: campo,
      direccion: actual.orden === campo && actual.direccion === 'desc' ? 'asc' : 'desc',
    }))
  }

  const titulo = usuario?.rol === 'AGENTE' ? 'Casos de mi área' : 'Bandeja de casos'

  return (
    <InternalLayout>
      <div className="mb-6">
        <p className="mb-2 text-sm text-gray-300">Gestión interna · {usuario?.rol}</p>
        <h1 className="text-3xl font-normal tracking-[-0.04em]">{titulo}</h1>
      </div>

      <form
        className="liquid-glass mb-6 grid gap-3 rounded-xl border border-white/20 p-4 sm:grid-cols-2 lg:grid-cols-4"
        onSubmit={(event) => event.preventDefault()}
      >
        <input
          className={INPUT}
          placeholder="Código de seguimiento"
          value={filtros.codigo || ''}
          onChange={(event) => actualizar('codigo', event.target.value.toUpperCase())}
          aria-label="Buscar por código"
        />
        <select
          className={`${INPUT} bg-black`}
          value={filtros.estado || ''}
          onChange={(event) => actualizar('estado', event.target.value)}
          aria-label="Filtrar por estado"
        >
          <option value="">Todos los estados</option>
          {ESTADOS.map((estado) => (
            <option key={estado} value={estado}>
              {etiquetaEstado(estado)}
            </option>
          ))}
        </select>
        <select
          className={`${INPUT} bg-black`}
          value={filtros.tipo || ''}
          onChange={(event) => actualizar('tipo', event.target.value)}
          aria-label="Filtrar por tipo"
        >
          <option value="">Todos los tipos</option>
          {TIPOS.map((tipo) => (
            <option key={tipo.codigo} value={tipo.codigo}>
              {tipo.nombre}
            </option>
          ))}
        </select>
        {usuario?.rol === 'ADMIN' && (
          <select
            className={`${INPUT} bg-black`}
            value={filtros.area || ''}
            onChange={(event) => actualizar('area', event.target.value)}
            aria-label="Filtrar por área"
          >
            <option value="">Todas las áreas</option>
            {areas.map((area) => (
              <option key={area.codigo} value={area.codigo}>
                {area.nombre}
              </option>
            ))}
          </select>
        )}
        <input
          type="date"
          className={INPUT}
          value={filtros.desde || ''}
          onChange={(event) => actualizar('desde', event.target.value)}
          aria-label="Desde"
        />
        <input
          type="date"
          className={INPUT}
          value={filtros.hasta || ''}
          onChange={(event) => actualizar('hasta', event.target.value)}
          aria-label="Hasta"
        />
        <label className="flex items-center gap-2 text-sm text-gray-300">
          <input
            type="checkbox"
            checked={Boolean(filtros.sinAsignar)}
            onChange={(event) => actualizar('sinAsignar', event.target.checked)}
            className="accent-white"
          />
          Sin asignar
        </label>
      </form>

      {error && (
        <div className="mb-4 rounded-lg border border-red-300/30 bg-red-950/40 px-4 py-3 text-sm text-red-100" role="alert">
          {error}
          <button type="button" className="ml-3 underline" onClick={() => setFiltros((actual) => ({ ...actual }))}>
            Reintentar
          </button>
        </div>
      )}

      <div className="liquid-glass overflow-x-auto rounded-xl border border-white/20">
        <table className="min-w-full text-left text-sm">
          <thead className="border-b border-white/10 text-gray-400">
            <tr>
              <th className="cursor-pointer px-4 py-3 font-medium" onClick={() => ordenar('codigo')}>
                Código
              </th>
              <th className="cursor-pointer px-4 py-3 font-medium" onClick={() => ordenar('tipo')}>
                Tipo
              </th>
              <th className="px-4 py-3 font-medium">Ciudadano</th>
              <th className="cursor-pointer px-4 py-3 font-medium" onClick={() => ordenar('area')}>
                Área
              </th>
              <th className="cursor-pointer px-4 py-3 font-medium" onClick={() => ordenar('estado')}>
                Estado
              </th>
              <th className="px-4 py-3 font-medium">Agente</th>
              <th className="cursor-pointer px-4 py-3 font-medium" onClick={() => ordenar('fechaRegistro')}>
                Fecha
              </th>
              <th className="px-4 py-3 font-medium">Plazo</th>
              <th className="px-4 py-3 font-medium"> </th>
            </tr>
          </thead>
          <tbody>
            {cargando && (
              <tr>
                <td colSpan={9} className="px-4 py-10 text-center text-gray-400">
                  Cargando bandeja…
                </td>
              </tr>
            )}
            {!cargando && casos.length === 0 && (
              <tr>
                <td colSpan={9} className="px-4 py-10 text-center text-gray-400">
                  No se encontraron casos con los criterios indicados.
                </td>
              </tr>
            )}
            {!cargando &&
              casos.map((caso) => (
                <tr key={caso.id} className="border-t border-white/10">
                  <td className="px-4 py-3 font-medium">{caso.codigoSeguimiento}</td>
                  <td className="px-4 py-3">{caso.tipo}</td>
                  <td className="px-4 py-3">{caso.ciudadano}</td>
                  <td className="px-4 py-3">{caso.areaNombre}</td>
                  <td className="px-4 py-3">
                    <BadgeEstado estado={caso.estado} />
                  </td>
                  <td className="px-4 py-3 text-gray-300">{caso.agenteNombre}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-gray-300">
                    {formatFechaHora(caso.fechaRegistro)}
                  </td>
                  <td className="px-4 py-3">
                    <BadgePlazo plazo={caso.plazo} />
                  </td>
                  <td className="px-4 py-3">
                    <Link
                      to={
                        caso.estado === 'EN_REVISION' || caso.estado === 'EN_PROCESO'
                          ? `/casos/${caso.id}/seguimiento`
                          : `/casos/${caso.id}`
                      }
                      className="text-sm underline-offset-4 hover:underline"
                    >
                      Ver detalle
                    </Link>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      <div className="mt-4 flex items-center justify-between text-sm text-gray-300">
        <p>
          {total} caso{total === 1 ? '' : 's'}
        </p>
        <div className="flex gap-2">
          <button
            type="button"
            disabled={pagina <= 1}
            onClick={() => setFiltros((actual) => ({ ...actual, page: pagina - 1 }))}
            className="rounded-lg border border-white/20 px-3 py-1.5 disabled:opacity-40"
          >
            Anterior
          </button>
          <span className="px-2 py-1.5">
            {pagina} / {paginas}
          </span>
          <button
            type="button"
            disabled={pagina >= paginas}
            onClick={() => setFiltros((actual) => ({ ...actual, page: pagina + 1 }))}
            className="rounded-lg border border-white/20 px-3 py-1.5 disabled:opacity-40"
          >
            Siguiente
          </button>
        </div>
      </div>
    </InternalLayout>
  )
}
