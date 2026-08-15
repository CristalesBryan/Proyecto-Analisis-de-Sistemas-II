import { Link, useLocation, useNavigate } from 'react-router-dom'
import { LogOut } from 'lucide-react'
import type { ReactNode } from 'react'
import { cerrarSesion, type Rol } from '../services/api'
import { destinosPorRol, getToken, getUser, limpiarSesion } from '../services/auth'

type InternalLayoutProps = {
  children: ReactNode
}

const inicioPorRol: Record<Rol, string> = {
  ADMIN: '/admin/dashboard',
  SUPERVISOR: '/supervisor/dashboard',
  AGENTE: '/agente/casos',
}

export const ESTILO_ESTADO: Record<string, string> = {
  RECIBIDO: 'border-white/25 text-gray-200',
  EN_REVISION: 'border-amber-300/40 text-amber-100',
  EN_PROCESO: 'border-sky-300/40 text-sky-100',
  RESUELTO: 'border-emerald-300/40 text-emerald-100',
  CERRADO: 'border-white/15 text-gray-400',
  ANULADO: 'border-red-300/40 text-red-200',
}

export function BadgeEstado({ estado }: { estado: string }) {
  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs ${ESTILO_ESTADO[estado] || 'border-white/20'}`}
    >
      {estado.split('_').join(' ')}
    </span>
  )
}

export type PlazoInfo = {
  diasRestantes: number | null
  semaforo: 'verde' | 'amarillo' | 'rojo' | 'gris' | string
  vencido: boolean
}

const ESTILO_PLAZO: Record<string, string> = {
  verde: 'border-emerald-300/40 text-emerald-100',
  amarillo: 'border-amber-300/40 text-amber-100',
  rojo: 'border-red-300/40 text-red-100',
  gris: 'border-white/20 text-gray-400',
}

export function BadgePlazo({ plazo }: { plazo?: PlazoInfo | null }) {
  if (!plazo || plazo.diasRestantes === null) {
    return <span className="text-xs text-gray-500">Sin plazo</span>
  }
  const etiqueta = plazo.vencido
    ? 'Vencido'
    : `${Math.abs(plazo.diasRestantes)} día${Math.abs(plazo.diasRestantes) === 1 ? '' : 's'} hábil${Math.abs(plazo.diasRestantes) === 1 ? '' : 'es'}`
  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs ${ESTILO_PLAZO[plazo.semaforo] || ESTILO_PLAZO.gris}`}
    >
      {etiqueta}
    </span>
  )
}

export function InternalLayout({ children }: InternalLayoutProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const usuario = getUser()
  const rol = usuario?.rol
  const bandeja = rol ? destinosPorRol[rol] : '/login'
  const inicio = rol ? inicioPorRol[rol] : '/login'

  async function logout() {
    try {
      if (getToken()) await cerrarSesion()
    } catch {
      // La limpieza local debe suceder incluso si el token ya venció.
    }
    limpiarSesion()
    navigate('/', { replace: true })
  }

  const links = [
    { to: inicio, label: 'Inicio', visible: rol !== 'AGENTE' },
    { to: bandeja, label: rol === 'AGENTE' ? 'Mis asignados' : 'Bandeja de casos', visible: true },
  ].filter((item) => item.visible)

  return (
    <main className="min-h-screen bg-black px-6 py-6 text-white md:px-12 lg:px-16">
      <header className="liquid-glass mx-auto flex max-w-6xl items-center justify-between rounded-xl px-4 py-2">
        <Link to={bandeja} className="text-2xl font-semibold tracking-tight">
          QRDS
        </Link>
        <div className="flex items-center gap-3">
          <div className="hidden text-right sm:block">
            <p className="text-sm">{usuario?.nombre}</p>
            <p className="text-xs text-gray-400">
              {rol}
              {usuario?.areaDependencia ? ` · ${usuario.areaDependencia}` : ''}
            </p>
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

      <div className="mx-auto mt-8 grid max-w-6xl gap-6 lg:grid-cols-[200px_1fr]">
        <aside className="liquid-glass h-fit rounded-xl border border-white/20 p-3">
          <nav className="flex flex-row gap-2 lg:flex-col">
            {links.map((item) => {
              const activo = location.pathname === item.to
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={`rounded-lg px-3 py-2 text-sm transition-colors ${
                    activo ? 'bg-white text-black' : 'text-gray-300 hover:bg-white/10 hover:text-white'
                  }`}
                >
                  {item.label}
                </Link>
              )
            })}
            {rol === 'ADMIN' && (
              <>
                <span className="rounded-lg px-3 py-2 text-sm text-gray-500">Usuarios</span>
                <span className="rounded-lg px-3 py-2 text-sm text-gray-500">Bitácoras</span>
              </>
            )}
            {rol === 'SUPERVISOR' && (
              <span className="rounded-lg px-3 py-2 text-sm text-gray-500">Reportes</span>
            )}
          </nav>
        </aside>
        <section>{children}</section>
      </div>
    </main>
  )
}
