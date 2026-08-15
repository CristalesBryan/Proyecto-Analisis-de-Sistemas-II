import { Link, useNavigate } from 'react-router-dom'
import { ArrowLeft, LogOut, ShieldAlert } from 'lucide-react'
import { cerrarSesion } from '../services/api'
import { destinoDeRol, getToken, getUser, limpiarSesion } from '../services/auth'

export function AccesoDenegadoPage() {
  const navigate = useNavigate()
  const usuario = getUser()
  const destino = usuario ? destinoDeRol(usuario.rol) : '/'

  async function logout() {
    try {
      if (getToken()) await cerrarSesion()
    } catch {
      // La limpieza local debe suceder incluso si el token ya venció.
    }
    limpiarSesion()
    navigate('/', { replace: true })
  }

  return (
    <main className="flex min-h-screen flex-col bg-black px-6 py-6 text-white md:px-12 lg:px-16">
      <header className="mx-auto flex w-full max-w-6xl items-center justify-between">
        <Link to="/" className="flex items-baseline gap-2">
          <span className="text-2xl font-semibold tracking-tight">QRDS</span>
          <span className="hidden text-xs text-gray-300 sm:inline">Municipalidad</span>
        </Link>
        {usuario && (
          <button
            type="button"
            onClick={() => void logout()}
            className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-gray-300 transition-colors hover:bg-white hover:text-black"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Cerrar sesión
          </button>
        )}
      </header>

      <section className="mx-auto flex w-full max-w-6xl flex-1 items-center">
        <div className="liquid-glass max-w-xl rounded-xl border border-white/20 p-8">
          <ShieldAlert className="mb-8 h-6 w-6" aria-hidden="true" />
          <p className="mb-3 text-sm text-gray-300">Acceso restringido</p>
          <h1 className="text-4xl font-normal tracking-[-0.04em]">Acceso denegado</h1>
          <p className="mt-5 max-w-md leading-relaxed text-gray-300">
            Su rol no tiene permiso para ver este módulo. Si cree que se trata de un
            error, contacte al administrador del sistema.
          </p>
          <div className="mt-10 flex flex-wrap gap-3">
            {usuario && destino !== '/acceso-denegado' && (
              <Link
                to={destino}
                className="rounded-lg bg-white px-6 py-3 text-sm font-medium text-black transition-colors hover:bg-gray-100"
              >
                Ir a mi panel
              </Link>
            )}
            <Link
              to="/"
              className="inline-flex items-center gap-2 rounded-lg border border-white/20 px-6 py-3 text-sm text-white transition-colors hover:bg-white hover:text-black"
            >
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              Volver al portal
            </Link>
          </div>
        </div>
      </section>
    </main>
  )
}
