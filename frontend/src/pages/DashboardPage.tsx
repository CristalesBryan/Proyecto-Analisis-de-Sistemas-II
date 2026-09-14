import { ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { InternalLayout } from '../components/InternalLayout'
import { destinosPorRol, getUser } from '../services/auth'
import type { Rol } from '../services/api'

type DashboardPageProps = {
  rolRequerido: Rol
}

const contenidoPorRol: Record<Rol, { titulo: string; descripcion: string }> = {
  ADMIN: {
    titulo: 'Panel de administración',
    descripcion: 'Administración de usuarios, parámetros, reportes y bitácoras del sistema.',
  },
  SUPERVISOR: {
    titulo: 'Panel de supervisión',
    descripcion: 'Gestión de casos, asignaciones y reportes de su área institucional.',
  },
  AGENTE: {
    titulo: 'Casos de mi área',
    descripcion: 'Atención de los casos asignados y de los nuevos registros pendientes de su dependencia.',
  },
  CIUDADANO: {
    titulo: 'Mi cuenta',
    descripcion: 'Consulta tus datos y el seguimiento de tus casos registrados.',
  },
}

export function DashboardPage({ rolRequerido }: DashboardPageProps) {
  const usuario = getUser()
  const contenido = contenidoPorRol[rolRequerido]
  const bandeja = destinosPorRol[rolRequerido]

  return (
    <InternalLayout>
      <div className="liquid-glass max-w-2xl rounded-xl border border-white/20 p-8">
        <ShieldCheck className="mb-8 h-6 w-6" aria-hidden="true" />
        <p className="mb-3 text-sm text-gray-300">Sesión activa · {rolRequerido}</p>
        <h1 className="text-4xl font-normal tracking-[-0.04em]">{contenido.titulo}</h1>
        <p className="mt-5 max-w-xl leading-relaxed text-gray-300">{contenido.descripcion}</p>
        <p className="mt-10 border-t border-white/10 pt-4 text-sm text-gray-300">
          Bienvenido, <span className="font-medium text-white">{usuario?.nombre}</span>.
        </p>
        <Link
          to={bandeja}
          className="mt-8 inline-flex rounded-lg bg-white px-5 py-3 text-sm font-medium text-black hover:bg-gray-100"
        >
          Ir a la bandeja de casos
        </Link>
      </div>
    </InternalLayout>
  )
}
