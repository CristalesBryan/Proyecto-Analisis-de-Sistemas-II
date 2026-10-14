import { Injectable } from '@angular/core';
import { AuthService } from './auth.service';
import {
  AgenteOpcion,
  ApiError,
  AreaDependencia,
  AreaParametro,
  ConfiguracionParametros,
  FeriadoParametro,
  InstitucionPublica,
  AreaAdmin,
  CasoDetalle,
  CasoPublico,
  PaginaHistorial,
  VistaCiudadano,
  CatalogoReporte,
  CuentaCiudadano,
  DocumentoCaso,
  FiltrosBandeja,
  ListaSeguimientos,
  EventoBitacora,
  PaginaBitacora,
  PaginaCasos,
  PaginaUsuarios,
  RegistroCasoPayload,
  ReporteGenerado,
  ReporteResumen,
  ResumenDashboard,
  RegistroCasoRespuesta,
  ResultadoReasignacion,
  RegistroCiudadanoPayload,
  SeguimientoCaso,
  SistemaEstado,
  UsuarioAccion,
  UsuarioAdmin,
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

  obtenerInstitucion() {
    return this.apiFetch('/api/catalogos/institucion').then((res) => this.parsearJson<InstitucionPublica>(res));
  }

  consultarParametros() {
    return this.apiFetch('/api/parametros').then((res) => this.parsearJson<ConfiguracionParametros>(res));
  }

  guardarParametro(clave: string, valor: string, version: number) {
    return this.apiFetch(`/api/parametros/${encodeURIComponent(clave)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ valor, version }),
    }).then((res) => this.parsearJson<{ mensaje: string; valorNuevo: string; version: number }>(res));
  }

  restaurarParametros(grupo: string) {
    return this.apiFetch(`/api/parametros/grupos/${grupo}/restaurar`, { method: 'POST' }).then((res) =>
      this.parsearJson<{ mensaje: string }>(res),
    );
  }

  crearAreaParametro(codigo: string, nombre: string) {
    return this.apiFetch('/api/parametros/areas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ codigo, nombre }),
    }).then((res) => this.parsearJson<{ mensaje: string; advertencia?: string }>(res));
  }

  actualizarAreaParametro(area: AreaParametro) {
    return this.apiFetch(`/api/parametros/areas/${area.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        codigo: area.codigo,
        nombre: area.nombre,
        activo: area.activo,
        version: area.version,
      }),
    }).then((res) => this.parsearJson<{ mensaje: string; advertencia?: string | null }>(res));
  }

  eliminarAreaParametro(id: number) {
    return this.apiFetch(`/api/parametros/areas/${id}`, { method: 'DELETE' }).then((res) => {
      if (!res.ok) return this.parsearJson(res);
      return undefined;
    });
  }

  crearFeriado(fecha: string, descripcion: string) {
    return this.apiFetch('/api/parametros/feriados', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fecha, descripcion }),
    }).then((res) => this.parsearJson<FeriadoParametro>(res));
  }

  eliminarFeriado(fecha: string) {
    return this.apiFetch(`/api/parametros/feriados/${fecha}`, { method: 'DELETE' }).then((res) => {
      if (!res.ok) return this.parsearJson(res);
      return undefined;
    });
  }

  recalcularPlazos(tipo: string) {
    return this.apiFetch('/api/parametros/plazos/recalcular', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tipo, confirmar: true }),
    }).then((res) => this.parsearJson<{ mensaje: string }>(res));
  }

  exportarParametros() {
    return this.apiFetch('/api/parametros/exportar').then(async (res) => {
      if (!res.ok) return this.parsearJson(res);
      const cuerpo = await res.text();
      const blob = new Blob([cuerpo], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = 'parametros-qrds.json';
      enlace.click();
      URL.revokeObjectURL(url);
      return undefined;
    });
  }

  importarParametros(archivo: File) {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.apiFetch('/api/parametros/importar', { method: 'POST', body: datos }).then((res) =>
      this.parsearJson<{ mensaje: string }>(res),
    );
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

  adjuntarDocumentosInterno(casoId: number, archivos: File[], justificacion?: string) {
    const form = new FormData();
    for (const archivo of archivos) form.append('archivos', archivo);
    if (justificacion) form.append('justificacion', justificacion);
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

  catalogoReportes() {
    return this.apiFetch('/api/reportes/catalogo').then((res) => this.parsearJson<CatalogoReporte[]>(res));
  }

  generarReporte(payload: {
    tipo: string;
    formato: 'PANTALLA' | 'PDF' | 'XLSX';
    filtros: {
      fechaDesde: string;
      fechaHasta: string;
      tipoCaso?: string;
      estado?: string;
      area?: string;
      agenteId?: number | null;
      incluirAnulados: boolean;
    };
  }) {
    return this.apiFetch('/api/reportes/generar', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      timeoutMs: 30000,
    }).then((res) => this.parsearJson<ReporteGenerado>(res));
  }

  listarReportes() {
    return this.apiFetch('/api/reportes/jobs').then((res) => this.parsearJson<ReporteResumen[]>(res));
  }

  areasAdministracion() {
    return this.apiFetch('/api/usuarios/catalogos/areas').then((res) => this.parsearJson<AreaAdmin[]>(res));
  }

  listarUsuarios(filtros: {
    q?: string;
    rol?: string;
    area?: string;
    activo?: string;
    page?: number;
    size?: number;
  }) {
    const params = new URLSearchParams();
    for (const [clave, valor] of Object.entries(filtros)) {
      if (valor === undefined || valor === '') continue;
      params.set(clave, String(valor));
    }
    const query = params.toString();
    return this.apiFetch(`/api/usuarios${query ? `?${query}` : ''}`).then((res) =>
      this.parsearJson<PaginaUsuarios>(res),
    );
  }

  crearUsuario(payload: {
    nombre: string;
    email: string;
    rol: string;
    areaId: number | null;
    password?: string;
    generarPassword?: boolean;
    activo: boolean;
  }) {
    return this.apiFetch('/api/usuarios', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }).then((res) => this.parsearJson<UsuarioAccion>(res));
  }

  actualizarUsuario(
    id: number,
    payload: { nombre: string; email: string; rol: string; areaId: number | null; activo: boolean },
  ) {
    return this.apiFetch(`/api/usuarios/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }).then((res) => this.parsearJson<UsuarioAccion>(res));
  }

  restablecerContrasena(id: number, nuevaPassword?: string) {
    return this.apiFetch(`/api/usuarios/${id}/reset-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(nuevaPassword ? { nuevaPassword } : {}),
    }).then((res) => this.parsearJson<UsuarioAccion>(res));
  }

  cambiarContrasenaPropia(actual: string, nueva: string, confirmacion: string) {
    return this.apiFetch('/api/usuarios/me/cambiar-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ actual, nueva, confirmacion }),
    }).then((res) =>
      this.parsearJson<{ mensaje: string; token: string; usuario: UsuarioAutenticado }>(res),
    );
  }

  obtenerReporte(id: number) {
    return this.apiFetch(`/api/reportes/jobs/${id}`).then((res) => this.parsearJson<ReporteGenerado>(res));
  }

  async descargarReporte(id: number, nombre: string) {
    const res = await this.apiFetch(`/api/reportes/jobs/${id}/descarga`, { timeoutMs: 30000 });
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      const error = new Error(body.mensaje || 'No fue posible descargar el reporte.') as ApiError;
      error.status = res.status;
      error.codigo = body.codigo;
      throw error;
    }
    const dispuesto = res.headers.get('Content-Disposition') || '';
    const coincidencia = dispuesto.match(/filename="([^"]+)"/);
    await this.descargarBlob(res, coincidencia?.[1] || nombre);
  }

  listarBitacoras(filtros: {
    desde: string;
    hasta: string;
    dominio?: string;
    tipo?: string;
    usuarioId?: string;
    caso?: string;
    ip?: string;
    resultado?: string;
    page?: number;
  }) {
    const params = new URLSearchParams();
    for (const [clave, valor] of Object.entries(filtros)) {
      if (valor === undefined || valor === '') continue;
      params.set(clave, String(valor));
    }
    return this.apiFetch(`/api/bitacoras?${params.toString()}`).then((res) =>
      this.parsearJson<PaginaBitacora>(res),
    );
  }

  detalleBitacora(clave: string) {
    const separador = clave.indexOf(':');
    const dominio = clave.slice(0, separador);
    const id = clave.slice(separador + 1);
    return this.apiFetch(`/api/bitacoras/${dominio}/${id}`).then((res) => this.parsearJson<EventoBitacora>(res));
  }

  async exportarBitacora(
    filtros: { desde: string; hasta: string; dominio?: string; tipo?: string; usuarioId?: number | null; caso?: string; ip?: string; resultado?: string },
    formato: 'PDF' | 'XLSX',
  ) {
    const res = await this.apiFetch('/api/bitacoras/exportar', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filtros, formato }),
      timeoutMs: 30000,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      const error = new Error(body.mensaje || 'No fue posible exportar la bitácora.') as ApiError;
      error.status = res.status;
      error.codigo = body.codigo;
      throw error;
    }
    const dispuesto = res.headers.get('Content-Disposition') || '';
    const coincidencia = dispuesto.match(/filename="([^"]+)"/);
    await this.descargarBlob(res, coincidencia?.[1] || (formato === 'PDF' ? 'auditoria.pdf' : 'auditoria.xlsx'));
  }

  consultarDashboard(periodo: string) {
    return this.apiFetch(`/api/dashboard/resumen?periodo=${encodeURIComponent(periodo)}`).then((res) =>
      this.parsearJson<ResumenDashboard>(res),
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

  consultarHistorial(casoId: number, categoria = '', q = '', page = 0) {
    const params = new URLSearchParams();
    if (categoria) params.set('categoria', categoria);
    if (q.trim()) params.set('q', q.trim());
    params.set('page', String(page));
    return this.apiFetch(`/api/casos/${casoId}/historial?${params.toString()}`).then((res) =>
      this.parsearJson<PaginaHistorial>(res),
    );
  }

  vistaCiudadano(casoId: number) {
    return this.apiFetch(`/api/casos/${casoId}/historial/vista-ciudadano`).then((res) =>
      this.parsearJson<VistaCiudadano>(res),
    );
  }

  async exportarHistorial(casoId: number, categoria = '', q = '') {
    const params = new URLSearchParams();
    if (categoria) params.set('categoria', categoria);
    if (q.trim()) params.set('q', q.trim());
    const query = params.toString();
    const res = await this.apiFetch(`/api/casos/${casoId}/historial/export.pdf${query ? `?${query}` : ''}`);
    if (!res.ok) throw new Error('No fue posible exportar el historial.');
    const dispuesto = res.headers.get('Content-Disposition') || '';
    const coincidencia = dispuesto.match(/filename="([^"]+)"/);
    await this.descargarBlob(res, coincidencia?.[1] || 'historial.pdf');
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

  reasignarCaso(
    id: number,
    agenteId: number,
    motivo: string,
    notificar = true,
    actualizarArea = false,
  ) {
    return this.apiFetch(`/api/casos/${id}/reasignar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agenteId, motivo, notificar, actualizarArea }),
    }).then((res) => this.parsearJson<{ mensaje: string; caso: CasoDetalle }>(res));
  }

  reasignarCasos(ids: number[], agenteId: number, motivo: string, notificar = true) {
    return this.apiFetch('/api/casos/reasignar-masivo', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids, agenteId, motivo, notificar }),
    }).then((res) => this.parsearJson<{ resultados: ResultadoReasignacion[] }>(res));
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

  darBajaDocumento(casoId: number, docId: number, justificacion: string) {
    return this.apiFetch(`/api/casos/${casoId}/documentos/${docId}`, {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ justificacion }),
    }).then((res) => this.parsearJson<{ mensaje: string }>(res));
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
