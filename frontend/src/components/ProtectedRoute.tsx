import { useEffect, useState, type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { obtenerSesion, type Rol } from '../services/api'
import {
  getToken,
  guardarSesion,
  limpiarSesion,
  marcarSesionExpirada,
} from '../services/auth'

type ProtectedRouteProps = {
  rolRequerido?: Rol
  rolesPermitidos?: Rol[]
  children: ReactNode
}

type EstadoGuard = 'validando' | 'ok' | 'login' | 'expirada' | 'denegado' | 'conexion'

export function ProtectedRoute({
  rolRequerido,
  rolesPermitidos,
  children,
}: ProtectedRouteProps) {
  const permitidos = rolesPermitidos ?? (rolRequerido ? [rolRequerido] : [])
  const [estado, setEstado] = useState<EstadoGuard>('validando')
  const [reintento, setReintento] = useState(0)

  useEffect(() => {
    const token = getToken()
    if (!token) {
      setEstado('login')
      return
    }

    let cancelado = false
    setEstado('validando')
    obtenerSesion()
      .then(({ usuario }) => {
        if (cancelado) return
        guardarSesion(token, usuario)
        setEstado(permitidos.includes(usuario.rol) ? 'ok' : 'denegado')
      })
      .catch((error) => {
        if (cancelado) return
        if (error?.codigo === 'TOKEN_INVALIDO' || error?.status === 401) {
          marcarSesionExpirada()
          setEstado('expirada')
          return
        }
        if (error?.conexion) {
          setEstado('conexion')
          return
        }
        limpiarSesion()
        setEstado('login')
      })

    return () => {
      cancelado = true
    }
  }, [permitidos.join(','), reintento])

  if (estado === 'validando') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-black text-gray-300">
        Validando sesión…
      </div>
    )
  }

  if (estado === 'conexion') {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center bg-black px-6 text-center">
        <p className="mb-4 max-w-md text-gray-300">
          Error de conexión. Verifique su conexión a internet e intente nuevamente.
        </p>
        <button
          type="button"
          onClick={() => setReintento((n) => n + 1)}
          className="rounded-lg bg-white px-6 py-3 text-sm font-medium text-black transition-colors hover:bg-gray-100"
        >
          Reintentar
        </button>
      </div>
    )
  }

  if (estado === 'denegado') {
    return <Navigate to="/acceso-denegado" replace />
  }

  if (estado === 'expirada' || estado === 'login') {
    return <Navigate to="/login" replace />
  }

  return children
}
