import type { Rol, UsuarioAutenticado } from './api'

const TOKEN_KEY = 'qrds_token'
const USER_KEY = 'qrds_usuario'
const SESION_EXPIRADA_KEY = 'qrds_sesion_expirada'

export const destinosPorRol: Record<Rol, string> = {
  ADMIN: '/admin/casos',
  SUPERVISOR: '/supervisor/casos',
  AGENTE: '/agente/casos',
}

/** Validación de correo institucional (RN-02 / RFC 5322 práctico). */
export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function esRol(valor: string): valor is Rol {
  return valor === 'ADMIN' || valor === 'SUPERVISOR' || valor === 'AGENTE'
}

export function destinoDeRol(rol: string) {
  return esRol(rol) ? destinosPorRol[rol] : '/acceso-denegado'
}

export function guardarSesion(token: string, usuario: UsuarioAutenticado) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(usuario))
}

export function limpiarSesion() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUser(): UsuarioAutenticado | null {
  const crudo = localStorage.getItem(USER_KEY)
  if (!crudo) return null
  try {
    const usuario = JSON.parse(crudo) as UsuarioAutenticado
    if (!usuario?.email || !esRol(usuario.rol)) return null
    return usuario
  } catch {
    limpiarSesion()
    return null
  }
}

export function isAuthenticated() {
  return Boolean(getToken() && getUser())
}

export function hasRole(rol: Rol) {
  return getUser()?.rol === rol
}

export function marcarSesionExpirada() {
  sessionStorage.setItem(SESION_EXPIRADA_KEY, '1')
  limpiarSesion()
}

export function consumirAvisoSesionExpirada() {
  const marcada = sessionStorage.getItem(SESION_EXPIRADA_KEY) === '1'
  sessionStorage.removeItem(SESION_EXPIRADA_KEY)
  return marcada
}
