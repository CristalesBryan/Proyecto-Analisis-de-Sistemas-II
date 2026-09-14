import { Link } from 'react-router-dom'
import { destinosPorRol, getUser } from '../services/auth'

type NavbarProps = {
  onConsultar: () => void
}

export function Navbar({ onConsultar }: NavbarProps) {
  const usuario = getUser()
  return (
    <div className="relative z-20 px-6 pt-6 md:px-12 lg:px-16">
      <nav className="liquid-glass flex flex-wrap items-center justify-between gap-3 rounded-xl px-4 py-2">
        <Link to="/" className="flex items-baseline gap-2">
          <span className="text-2xl font-semibold tracking-tight text-white">QRDS</span>
          <span className="hidden text-xs text-gray-300 sm:inline">Municipalidad</span>
        </Link>

        <div className="flex flex-1 flex-wrap items-center justify-end gap-x-5 gap-y-2 sm:gap-x-8">
          <Link
            to="/registro-caso?tipo=Q"
            className="text-sm leading-snug text-white transition-colors hover:text-gray-300"
          >
            Registrar Queja
          </Link>
          <Link
            to="/registro-caso?tipo=S"
            className="text-sm leading-snug text-white transition-colors hover:text-gray-300"
          >
            Sugerencias
          </Link>
          <Link
            to="/registro-caso?tipo=RD"
            className="text-sm leading-snug text-white transition-colors hover:text-gray-300"
          >
            Reclamo/Denuncia
          </Link>
          <button
            type="button"
            onClick={onConsultar}
            className="text-sm leading-snug text-white transition-colors hover:text-gray-300"
          >
            Consultar Seguimiento de Caso
          </button>
          <Link
            to={
              usuario?.rol === 'CIUDADANO'
                ? '/ciudadano'
                : usuario
                  ? destinosPorRol[usuario.rol]
                  : '/login'
            }
            className="rounded-lg bg-white px-6 py-2 text-sm font-medium text-black transition-colors hover:bg-gray-100"
          >
            {usuario ? (usuario.rol === 'CIUDADANO' ? 'Mi cuenta' : 'Entrar') : 'Iniciar Sesión'}
          </Link>
        </div>
      </nav>
    </div>
  )
}
