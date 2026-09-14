import { useEffect, useState } from 'react'
import { ArrowLeft, LogOut, ShieldCheck } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { cerrarSesion, obtenerCuentaCiudadano, type CuentaCiudadano } from '../services/api'
import { getToken, getUser, limpiarSesion } from '../services/auth'
import { BadgeEstado } from '../components/InternalLayout'

export function CiudadanoCuentaPage() {
  const navigate = useNavigate()
  const [cuenta, setCuenta] = useState<CuentaCiudadano | null>(null)
  const [error, setError] = useState('')
  const usuario = getUser()

  useEffect(() => {
    let cancelado = false
    obtenerCuentaCiudadano()
      .then((data) => {
        if (!cancelado) setCuenta(data)
      })
      .catch((err) => {
        if (!cancelado) setError(err.message || 'No fue posible cargar su cuenta.')
      })
    return () => {
      cancelado = true
    }
  }, [])

  async function logout() {
    try {
      if (getToken()) await cerrarSesion()
    } catch {
      // La sesión local se limpia de todos modos.
    }
    limpiarSesion()
    navigate('/', { replace: true })
  }

  const perfil = cuenta?.perfil

  return (
    <main className="min-h-screen bg-black px-6 py-6 text-white md:px-12 lg:px-16">
      <header className="liquid-glass mx-auto flex max-w-6xl items-center justify-between rounded-xl px-4 py-2">
        <Link to="/" className="text-2xl font-semibold tracking-tight">
          QRDS
        </Link>
        <div className="flex items-center gap-3">
          <div className="hidden text-right sm:block">
            <p className="text-sm">{perfil?.nombre || usuario?.nombre}</p>
            <p className="text-xs text-gray-400">Ciudadano</p>
          </div>
          <button
            type="button"
            onClick={() => void logout()}
            className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-gray-300 transition-colors hover:bg-white hover:text-black"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Cerrar sesión
          </button>
        </div>
      </header>

      <div className="mx-auto mt-8 grid max-w-6xl gap-6 lg:grid-cols-[1fr_1.2fr]">
        <section className="liquid-glass rounded-xl border border-white/20 p-8">
          <ShieldCheck className="mb-6 h-6 w-6" aria-hidden="true" />
          <p className="mb-2 text-sm text-gray-300">Mis datos</p>
          <h1 className="text-3xl font-normal tracking-[-0.04em]">Cuenta de ciudadano</h1>
          {error && (
            <p className="mt-6 rounded-lg border border-red-300/30 bg-red-950/40 px-4 py-3 text-sm text-red-100">
              {error}
            </p>
          )}
          {!cuenta && !error && <p className="mt-6 text-sm text-gray-400">Cargando sus datos…</p>}
          {perfil && (
            <dl className="mt-8 space-y-4 text-sm">
              <Dato etiqueta="Nombre" valor={perfil.nombre} />
              <Dato etiqueta="Correo" valor={perfil.email} />
              <Dato etiqueta="Teléfono" valor={perfil.telefono || 'No registrado'} />
              <Dato etiqueta="DPI / CUI" valor={perfil.dpi || 'No registrado'} />
              <Dato etiqueta="Verificación de correo" valor={perfil.emailVerificado ? 'Verificado' : 'Pendiente'} />
            </dl>
          )}
          <Link
            to="/registro-caso"
            className="mt-8 inline-flex rounded-lg bg-white px-5 py-3 text-sm font-medium text-black transition-colors hover:bg-gray-100"
          >
            Registrar un caso
          </Link>
        </section>

        <section className="liquid-glass rounded-xl border border-white/20 p-8">
          <p className="mb-2 text-sm text-gray-300">Seguimiento</p>
          <h2 className="text-2xl font-normal tracking-[-0.04em]">Mis casos</h2>
          {cuenta && cuenta.casos.length === 0 && (
            <p className="mt-6 text-sm leading-relaxed text-gray-300">
              Aún no hay casos asociados a este correo. Puede registrar una queja, reclamo, denuncia o
              sugerencia cuando lo necesite.
            </p>
          )}
          <ul className="mt-6 space-y-3">
            {cuenta?.casos.map((caso) => (
              <li
                key={caso.codigoSeguimiento}
                className="rounded-lg border border-white/15 px-4 py-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <p className="text-sm font-medium">{caso.codigoSeguimiento}</p>
                  <BadgeEstado estado={caso.estado} />
                </div>
                <p className="mt-2 text-sm text-gray-300">
                  {caso.tipo}
                  {caso.areaDependencia ? ` · ${caso.areaDependencia}` : ''}
                </p>
                <p className="mt-1 text-xs text-gray-500">Avance {caso.avancePorcentaje}%</p>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <p className="mx-auto mt-8 max-w-6xl">
        <Link to="/" className="inline-flex items-center gap-2 text-sm text-gray-300 hover:text-white">
          <ArrowLeft className="h-4 w-4" />
          Volver al portal
        </Link>
      </p>
    </main>
  )
}

function Dato({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <div className="border-b border-white/10 pb-3">
      <dt className="text-xs uppercase tracking-wide text-gray-500">{etiqueta}</dt>
      <dd className="mt-1 text-white">{valor}</dd>
    </div>
  )
}
