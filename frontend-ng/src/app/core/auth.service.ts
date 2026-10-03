import { Injectable } from '@angular/core';
import { Rol, UsuarioAutenticado } from './models';

const TOKEN_KEY = 'qrds_token';
const USER_KEY = 'qrds_usuario';
const SESION_EXPIRADA_KEY = 'qrds_sesion_expirada';

export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly destinosPorRol: Record<Rol, string> = {
    ADMIN: '/admin/casos',
    SUPERVISOR: '/supervisor/casos',
    AGENTE: '/agente/casos',
    CIUDADANO: '/ciudadano',
  };

  readonly inicioPorRol: Record<Rol, string> = {
    ADMIN: '/admin/dashboard',
    SUPERVISOR: '/supervisor/dashboard',
    AGENTE: '/agente/casos',
    CIUDADANO: '/ciudadano',
  };

  esRol(valor: string): valor is Rol {
    return valor === 'ADMIN' || valor === 'SUPERVISOR' || valor === 'AGENTE' || valor === 'CIUDADANO';
  }

  destinoDeRol(rol: string) {
    return this.esRol(rol) ? this.destinosPorRol[rol] : '/acceso-denegado';
  }

  guardarSesion(token: string, usuario: UsuarioAutenticado) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(usuario));
  }

  limpiarSesion() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  getToken() {
    return localStorage.getItem(TOKEN_KEY);
  }

  getUser(): UsuarioAutenticado | null {
    const crudo = localStorage.getItem(USER_KEY);
    if (!crudo) return null;
    try {
      const usuario = JSON.parse(crudo) as UsuarioAutenticado;
      if (!usuario?.email || !this.esRol(usuario.rol)) return null;
      return usuario;
    } catch {
      this.limpiarSesion();
      return null;
    }
  }

  marcarSesionExpirada() {
    sessionStorage.setItem(SESION_EXPIRADA_KEY, '1');
    this.limpiarSesion();
  }

  consumirAvisoSesionExpirada() {
    const marcada = sessionStorage.getItem(SESION_EXPIRADA_KEY) === '1';
    sessionStorage.removeItem(SESION_EXPIRADA_KEY);
    return marcada;
  }
}
