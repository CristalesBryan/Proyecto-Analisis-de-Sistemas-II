import { Injectable } from '@angular/core';
import { AuthService } from './auth.service';
import {
  AgenteOpcion,
  ApiError,
  AreaDependencia,
  CasoDetalle,
  CasoPublico,
  CuentaCiudadano,
  DocumentoCaso,
  FiltrosBandeja,
  ListaSeguimientos,
  PaginaCasos,
  RegistroCasoPayload,
  RegistroCasoRespuesta,
  RegistroCiudadanoPayload,
  SeguimientoCaso,
  SistemaEstado,
  UsuarioAutenticado,
} from './models';

const TIMEOUT_MS = 8000;
const RUTAS_PUBLICAS = [
  '/api/auth/login',
  '/api/auth/ciudadano/',
  '/api/casos/publico',
  '/api/sistema/estado',
  '/api/bitacora/acceso-publico',
  '/api/catalogos/',
];

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly auth: AuthService) {}

  codigoSeguimientoValido(codigo: string) {
    const valor = codigo.trim().toUpperCase();
    return /^[QRDS]-\d{4}-\d{5}$/.test(valor) || /^[A-Z0-9]{10}$/.test(valor);
  }

  getSistemaEstado(): Promise<SistemaEstado> {
    return this.apiFetch('/api/sistema/estado', { method: 'GET' }).then((res) => {
      if (!res.ok) throw new Error('Servicio no disponible');
      return res.json();
    });
  }

  async registrarAccesoPublico(accion: string, resultado = 'OK') {
    try {
      await this.apiFetch('/api/bitacora/acceso-publico', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ accion, resultado }),
      });
    } catch {
      // No bloquear la UX del portal si falla el registro de bitácora
    }
  }

  consultarCasoPublico(codigo: string) {
    return this.apiFetch(`/api/casos/publico/${encodeURIComponent(codigo)}`).then((res) =>
      this.parsearJson<CasoPublico>(res),
    );
  }

  iniciarSesion(email: string, password: string) {
    return this.apiFetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    }).then((res) => this.parsearJson<{ token: string; usuario: UsuarioAutenticado }>(res));
  }

  cerrarSesion() {
    return this.apiFetch('/api/auth/logout', { method: 'POST' }).then((res) =>
      this.parsearJson<{ mensaje: string }>(res),
    );
  }

  obtenerSesion() {
    return this.apiFetch('/api/auth/me').then((res) =>
      this.parsearJson<{ usuario: UsuarioAutenticado }>(res),
    );
  }

  iniciarRegistroCiudadano(payload: RegistroCiudadanoPayload) {
    return this.apiFetch('/api/auth/ciudadano/verificar-inicio', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      timeoutMs: 15000,
    }).then((res) => this.parsearJson<{ registroId: string; mensaje: string }>(res));
  }

  reenviarCodigoCiudadano(registroId: string) {
    return this.apiFetch('/api/auth/ciudadano/reenviar-codigo', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ registroId }),
      timeoutMs: 15000,
    }).then((res) => this.parsearJson<{ mensaje: string }>(res));
  }

  confirmarRegistroCiudadano(registroId: string, codigo: string) {
    return this.apiFetch('/api/auth/ciudadano/confirmar', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ registroId, codigo }),
    }).then((res) => this.parsearJson<{ mensaje: string }>(res));
  }

  obtenerCuentaCiudadano() {
    return this.apiFetch('/api/ciudadano/cuenta').then((res) => this.parsearJson<CuentaCiudadano>(res));
  }

  obtenerAreas() {
    return this.apiFetch('/api/catalogos/areas').then((res) => this.parsearJson<AreaDependencia[]>(res));
  }

  registrarCasoPublico(payload: RegistroCasoPayload) {
    return this.apiFetch('/api/casos/publico', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      timeoutMs: 12000,
    }).then((res) => this.parsearJson<RegistroCasoRespuesta>(res));
  }

  adjuntarDocumentosCaso(codigo: string, archivos: File[]) {
    const form = new FormData();
    for (const archivo of archivos) form.append('archivos', archivo);
    return this.apiFetch(`/api/casos/publico/${encodeURIComponent(codigo)}/documentos`, {
      method: 'POST',
      body: form,
      timeoutMs: 20000,
    }).then((res) =>
      this.parsearJson<{
        archivosSubidos: { id?: number; nombreArchivo: string }[];
        rechazados: { nombre: string; motivo: string }[];
      }>(res),
    );
  }

  listarDocumentosCaso(casoId: number) {
    return this.apiFetch(`/api/casos/${casoId}/documentos`).then((res) =>
      this.parsearJson<DocumentoCaso[]>(res),
    );
  }

  adjuntarDocumentosInterno(casoId: number, archivos: File[]) {
    const form = new FormData();
    for (const archivo of archivos) form.append('archivos', archivo);
    return this.apiFetch(`/api/casos/${casoId}/documentos`, {
      method: 'POST',
      body: form,
      timeoutMs: 20000,
    }).then((res) =>
      this.parsearJson<{
        archivosSubidos: { id: number; nombreArchivo: string }[];
        rechazados: { nombre: string; motivo: string }[];
      }>(res),
    );
  }

  listarCasos(filtros: FiltrosBandeja = {}) {
    const query = this.queryBandeja(filtros);
    return this.apiFetch(`/api/casos${query ? `?${query}` : ''}`).then((res) =>
      this.parsearJson<PaginaCasos>(res),
    );
  }

  obtenerCaso(id: number) {
    return this.apiFetch(`/api/casos/${id}`).then((res) => this.parsearJson<CasoDetalle>(res));
  }

  modificarCaso(
    id: number,
    payload: {
      nombreCiudadano?: string;
      email?: string;
      telefono?: string;
      areaDependencia?: string;
      descripcion?: string;
      denunciado?: string;
      prioridad?: string;
      motivo: string;
    },
  ) {
    return this.apiFetch(`/api/casos/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }).then((res) => this.parsearJson<{ mensaje: string; caso: CasoDetalle }>(res));
  }

  listarAgentes(area?: string) {
    const query = area ? `?area=${encodeURIComponent(area)}` : '';
    return this.apiFetch(`/api/agentes${query}`).then((res) => this.parsearJson<AgenteOpcion[]>(res));
  }

  asignarCaso(id: number, agenteId: number) {
    return this.apiFetch(`/api/casos/${id}/asignar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agenteId }),
    }).then((res) => this.parsearJson<{ mensaje: string; caso: CasoDetalle }>(res));
  }

  reasignarCaso(id: number, agenteId: number, motivo: string) {
    return this.apiFetch(`/api/casos/${id}/reasignar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agenteId, motivo }),
    }).then((res) => this.parsearJson<{ mensaje: string; caso: CasoDetalle }>(res));
  }

  cambiarEstadoCaso(id: number, nuevoEstado: string, observacion: string) {
    return this.apiFetch(`/api/casos/${id}/estado`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nuevoEstado, observacion }),
    }).then((res) => this.parsearJson<{ mensaje: string; caso: CasoDetalle }>(res));
  }

  agregarObservacionCaso(id: number, texto: string) {
    return this.apiFetch(`/api/casos/${id}/observaciones`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ texto }),
    }).then((res) => this.parsearJson<{ mensaje: string; caso: CasoDetalle }>(res));
  }

  anularCaso(id: number, justificacion: string) {
    return this.apiFetch(`/api/casos/${id}/anular`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ justificacion }),
    }).then((res) => this.parsearJson<{ mensaje: string; caso: CasoDetalle }>(res));
  }

  resolverCaso(id: number, form: FormData) {
    return this.apiFetch(`/api/casos/${id}/resolver`, {
      method: 'POST',
      body: form,
      timeoutMs: 20000,
    }).then((res) => this.parsearJson<{ mensaje: string; caso: CasoDetalle }>(res));
  }

  cerrarCaso(id: number, observacion: string) {
    return this.apiFetch(`/api/casos/${id}/cerrar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ observacion }),
    }).then((res) => this.parsearJson<{ mensaje: string; caso: CasoDetalle }>(res));
  }

  async descargarDocumentoCaso(casoId: number, docId: number, nombre: string) {
    const res = await this.apiFetch(`/api/casos/${casoId}/documentos/${docId}`);
    if (!res.ok) throw new Error('No fue posible descargar el documento.');
    await this.descargarBlob(res, nombre);
  }

  listarSeguimientos(casoId: number, incluirInternos = true) {
    const query = incluirInternos ? '' : '?incluirInternos=false';
    return this.apiFetch(`/api/casos/${casoId}/seguimientos${query}`).then((res) =>
      this.parsearJson<ListaSeguimientos>(res),
    );
  }

  listarSeguimientosPublicos(codigo: string) {
    return this.apiFetch(`/api/casos/publico/${encodeURIComponent(codigo)}/seguimientos`).then((res) =>
      this.parsearJson<{
        codigoSeguimiento: string;
        avancePorcentaje: number;
        seguimientos: SeguimientoCaso[];
      }>(res),
    );
  }

  registrarSeguimiento(casoId: number, form: FormData) {
    return this.apiFetch(`/api/casos/${casoId}/seguimientos`, {
      method: 'POST',
      body: form,
      timeoutMs: 20000,
    }).then((res) =>
      this.parsearJson<{
        mensaje: string;
        avisoCorreo: string | null;
        seguimiento: SeguimientoCaso;
        caso: CasoDetalle;
        seguimientos: SeguimientoCaso[];
      }>(res),
    );
  }

  registrarProrroga(casoId: number, diasHabiles: number, justificacion: string) {
    return this.apiFetch(`/api/casos/${casoId}/prorroga`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ diasHabiles, justificacion }),
    }).then((res) => this.parsearJson<{ mensaje: string; caso: CasoDetalle }>(res));
  }

  escalarCaso(casoId: number, motivo: string) {
    return this.apiFetch(`/api/casos/${casoId}/escalar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ motivo }),
    }).then((res) => this.parsearJson<{ mensaje: string; caso: CasoDetalle }>(res));
  }

  async descargarEvidenciaSeguimiento(casoId: number, seguimientoId: number, nombre: string) {
    const res = await this.apiFetch(`/api/casos/${casoId}/seguimientos/${seguimientoId}/documento`);
    if (!res.ok) throw new Error('No fue posible descargar la evidencia.');
    await this.descargarBlob(res, nombre);
  }

  private queryBandeja(filtros: FiltrosBandeja) {
    const params = new URLSearchParams();
    for (const [clave, valor] of Object.entries(filtros)) {
      if (valor === undefined || valor === '' || valor === false) continue;
      params.set(clave, String(valor));
    }
    return params.toString();
  }

  private esRutaPublica(input: string) {
    return RUTAS_PUBLICAS.some((ruta) => input.includes(ruta));
  }

  private errorDeConexion(): ApiError {
    const error = new Error(
      'Error de conexión. Verifique su conexión a internet e intente nuevamente.',
    ) as ApiError;
    error.conexion = true;
    return error;
  }

  private async parsearJson<T>(res: Response): Promise<T> {
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      const error = new Error(
        body.mensaje || body.error || 'No fue posible completar la solicitud.',
      ) as ApiError;
      error.status = res.status;
      error.codigo = body.codigo;
      error.errores = body.errores;
      error.codigoExistente = body.codigoExistente;
      throw error;
    }
    return body as T;
  }

  private async apiFetch(input: string, init: RequestInit & { timeoutMs?: number } = {}) {
    const { timeoutMs = TIMEOUT_MS, ...fetchInit } = init;
    const headers = new Headers(fetchInit.headers);
    const token = this.auth.getToken();
    if (token && !headers.has('Authorization') && !this.esRutaPublica(input)) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    if (fetchInit.body instanceof FormData) {
      headers.delete('Content-Type');
    }

    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), timeoutMs);

    try {
      const res = await fetch(input, {
        ...fetchInit,
        headers,
        signal: fetchInit.signal ?? controller.signal,
      });

      if (res.status === 401 && !this.esRutaPublica(input)) {
        const body = await res.clone().json().catch(() => ({}));
        if (body.codigo === 'TOKEN_INVALIDO') {
          this.auth.marcarSesionExpirada();
        }
      }

      return res;
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw this.errorDeConexion();
      }
      if (error instanceof TypeError) {
        throw this.errorDeConexion();
      }
      throw error;
    } finally {
      window.clearTimeout(timeout);
    }
  }

  private async descargarBlob(res: Response, nombre: string) {
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    enlace.click();
    URL.revokeObjectURL(url);
  }
}
