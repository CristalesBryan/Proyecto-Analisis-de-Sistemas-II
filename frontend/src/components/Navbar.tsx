import { Link } from 'react-router-dom'

type NavbarProps = {
  onConsultar: () => void
}

export function Navbar({ onConsultar }: NavbarProps) {
  return (
    <div className="relative z-20 px-6 pt-6 md:px-12 lg:px-16">
      <nav className="liquid-glass flex items-center justify-between rounded-xl px-4 py-2">
        <Link to="/" className="flex items-baseline gap-2">
          <span className="text-2xl font-semibold tracking-tight text-white">QRDS</span>
          <span className="hidden text-xs text-gray-300 sm:inline">Municipalidad</span>
        </Link>

        <div className="hidden items-center gap-8 md:flex">
          <a href="#inicio" className="text-sm text-white transition-colors hover:text-gray-300">
            Inicio
          </a>
          <Link
            to="/registro-caso"
            className="text-sm text-white transition-colors hover:text-gray-300"
          >
            Registrar Queja
          </Link>
          <button
            type="button"
            onClick={onConsultar}
            className="text-sm text-white transition-colors hover:text-gray-300"
          >
            Consultar Caso
          </button>
          <Link to="/login" className="text-sm text-white transition-colors hover:text-gray-300">
            Iniciar Sesión
          </Link>
        </div>

        <Link
          to="/login"
          className="rounded-lg bg-white px-6 py-2 text-sm font-medium text-black transition-colors hover:bg-gray-100"
        >
          Iniciar Sesión
        </Link>
      </nav>
    </div>
  )
}
