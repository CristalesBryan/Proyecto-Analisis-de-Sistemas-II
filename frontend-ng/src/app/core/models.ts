export type SistemaEstado = {
  estado: 'UP' | 'DOWN';
  mensaje?: string;
};

export type SeguimientoPublico = {
  id: number;
  tipo: string;
  titulo: string;
  descripcion: string;
  porcentajeAvance: number | null;
  creadoEn: string;
};

export type CasoPublico = {
  codigoSeguimiento: string;
  tipoCaso?: string;
  tipo: string;
  estado: string;
  fechaRegistro: string;
  ultimaActualizacion?: string;
  avancePorcentaje?: number;
  seguimientos?: SeguimientoPublico[];
};

export type Rol = 'ADMIN' | 'SUPERVISOR' | 'AGENTE' | 'CIUDADANO';

export type UsuarioAutenticado = {
  id: number;
  userId: number;
  nombre: string;
  email: string;
  telefono?: string | null;
  dpi?: string | null;
  rol: Rol;
  areaDependencia?: string | null;
  permisos?: string[];
  forzarCambioPassword?: boolean;
};

export type ApiError = Error & {
  status?: number;
  codigo?: string;
  conexion?: boolean;
  errores?: Record<string, string>;
  codigoExistente?: string;
};

export type RegistroCiudadanoPayload = {
  nombre: string;
  email: string;
  telefono: string;
  dpi: string;
  password: string;
  confirmarPassword: string;
  aceptaPrivacidad: boolean;
  recaptchaToken: string;
};

export type CuentaCiudadano = {
  perfil: {
    id: number;
    nombre: string;
    email: string;
    telefono?: string | null;
    dpi?: string | null;
    rol: Rol;
    creadoEn: string;
    emailVerificado: boolean;
  };
  casos: {
    codigoSeguimiento: string;
    tipo: string;
    estado: string;
    fechaRegistro: string;
    ultimaActualizacion?: string;
    avancePorcentaje: number;
    areaDependencia?: string | null;
  }[];
};

export type AreaDependencia = {
  codigo: string;
  nombre: string;
};

export type RegistroCasoPayload = {
  tipoCaso: 'Q' | 'R' | 'D' | 'S';
  nombreCiudadano: string;
  email: string;
  telefono: string;
  areaDependencia?: string;
  descripcion: string;
  denunciado?: string;
  esAnonimo: boolean;
  aceptaPrivacidad: boolean;
  recaptchaToken: string;
  forzarRegistro?: boolean;
};

export type RegistroCasoRespuesta = {
  codigoSeguimiento: string;
  mensaje: string;
  fechaRegistro: string;
  tipoCaso: string;
  tipo: string;
  estado: string;
  plazoEstimado: string;
  correoEnviado: boolean;
  esAnonimo: boolean;
};

export type DocumentoCaso = {
  id: number;
  nombreArchivo: string;
  tipoMime: string;
  tamanioBytes: number;
  subidoEn: string;
  origen?: string;
  subidoPorNombre?: string | null;
};

export type PlazoCaso = {
  diasRestantes: number | null;
  semaforo: 'verde' | 'amarillo' | 'rojo' | 'gris' | string;
  vencido: boolean;
};

export type CasoResumen = {
  id: number;
  codigoSeguimiento: string;
  tipoCaso: string;
  tipo: string;
  ciudadano: string;
  area: string;
  areaNombre: string;
  estado: string;
  fechaRegistro: string;
  agenteAsignadoId: number | null;
  agenteNombre: string;
  prioridad: string;
  avancePorcentaje?: number;
  escalado?: boolean;
  fechaLimiteRespuesta?: string | null;
  plazo?: PlazoCaso;
};

export type CasoDetalle = CasoResumen & {
  nombreCiudadano: string | null;
  emailCiudadano: string | null;
  telefono: string | null;
  esAnonimo: boolean;
  descripcion: string;
  denunciado: string | null;
  transicionesPermitidas: string[];
  fechaUltimaActualizacion: string;
  documentos: DocumentoCaso[];
  observaciones: {
    id: number;
    texto: string;
    creadoEn: string;
    usuarioNombre: string;
  }[];
  historial: {
    tipoEvento: string;
    estadoAnterior: string | null;
    estadoNuevo: string | null;
    descripcion: string;
    fechaHora: string;
    usuarioNombre: string;
  }[];
  fechaProrroga?: string | null;
};

export type EventoHistorial = {
  id: number;
  tipo: string;
  categoria: string;
  titulo: string;
  resumen: string;
  detalle: string;
  fechaHora: string;
  autor: string;
  interno: boolean;
  documentoId: number | null;
};

export type PaginaHistorial = {
  eventos: EventoHistorial[];
  page: number;
  size: number;
  total: number;
  parcial: boolean;
  aviso: string | null;
};

export type VistaCiudadano = {
  codigoSeguimiento: string;
  tipo: string;
  estado: string;
  fechaRegistro: string;
  ultimaActualizacion: string;
  avancePorcentaje: number;
  seguimientos: {
    id: number;
    tipo: string;
    titulo: string;
    descripcion: string;
    porcentajeAvance: number | null;
    creadoEn: string;
  }[];
};

export type PaginaCasos = {
  content: CasoResumen[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AgenteOpcion = {
  id: number;
  nombre: string;
  email: string;
  areaDependencia: string;
  areaNombre: string;
  casosAbiertos: number;
};

export type ResultadoReasignacion = {
  casoId: number;
  codigo: string | null;
  aplicado: boolean;
  mensaje: string;
};

export type CatalogoReporte = {
  codigo: string;
  nombre: string;
  descripcion: string;
  formatos: string[];
};

export type FiltrosReporte = {
  fechaDesde: string;
  fechaHasta: string;
  tipoCaso?: string;
  estado?: string;
  area?: string;
  agenteId?: number | null;
  incluirAnulados?: boolean;
};

export type ReporteGenerado = {
  id: number;
  tipo: string;
  nombre: string;
  formato: string;
  estado: string;
  cantidadFilas: number;
  asincrono: boolean;
  reutilizado: boolean;
  mensaje: string;
  columnas: string[];
  filas: string[][];
  creadoEn: string;
  expiraEn: string | null;
  descargable: boolean;
};

export type ReporteResumen = {
  id: number;
  tipo: string;
  nombre: string;
  formato: string;
  estado: string;
  cantidadFilas: number | null;
  creadoEn: string;
  expiraEn: string | null;
  descargable: boolean;
  errorMensaje: string | null;
};

export type FiltrosBandeja = {
  estado?: string;
  tipo?: string;
  area?: string;
  codigo?: string;
  desde?: string;
  hasta?: string;
  sinAsignar?: boolean;
  vencido?: boolean;
  abiertos?: boolean;
  soloAsignados?: boolean;
  orden?: string;
  direccion?: 'asc' | 'desc';
  page?: number;
  size?: number;
};

export type ResumenDashboard = {
  periodo: string;
  desde: string;
  hasta: string;
  alcance: string;
  puedeReportes: boolean;
  abiertos: number;
  vencidos: number;
  recibidos: number | null;
  resueltos: number;
  diasPromedio: number | null;
  anulados: number | null;
  porEstado: { estado: string; total: number }[];
  cargaAgentes: { nombre: string; abiertos: number }[] | null;
  topVencidos: { id: number; codigo: string; estado: string; fechaLimite: string; diasAtraso: number }[];
  errorIndicadores: string | null;
  errorEstados: string | null;
  errorCarga: string | null;
  errorVencidos: string | null;
};

export type TipoSeguimiento = 'PUBLICA' | 'INTERNA' | 'CORRECCION';

export type SeguimientoCaso = {
  id: number;
  tipo: TipoSeguimiento;
  titulo: string;
  descripcion: string;
  porcentajeAvance: number | null;
  creadoEn: string;
  notificado: boolean;
  seguimientoPadreId: number | null;
  usuarioNombre?: string;
  usuarioId?: number;
  adjunto: { id: number; nombreArchivo: string } | null;
};

export type AreaAdmin = {
  id: number;
  codigo: string;
  nombre: string;
};

export type UsuarioAdmin = {
  id: number;
  nombre: string;
  email: string;
  rol: 'ADMIN' | 'SUPERVISOR' | 'AGENTE';
  areaId: number | null;
  areaCodigo: string | null;
  areaNombre: string | null;
  activo: boolean;
  forzarCambioPassword: boolean;
  creadoEn: string;
  actualizadoEn: string;
};

export type PaginaUsuarios = {
  content: UsuarioAdmin[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type UsuarioAccion = {
  mensaje: string;
  usuario: UsuarioAdmin;
  contrasenaTemporal?: string | null;
};

export type EventoBitacora = {
  clave: string;
  dominio: string;
  id: number;
  fechaHora: string;
  tipoEvento: string;
  usuario: string;
  usuarioId: number | null;
  caso: string | null;
  casoId: number | null;
  descripcion: string | null;
  estadoAnterior: string | null;
  estadoNuevo: string | null;
  ipCliente: string | null;
  resultado: string | null;
  userAgent: string | null;
};

export type PaginaBitacora = {
  content: EventoBitacora[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  mensaje: string | null;
};

export type ListaSeguimientos = {
  caso?: CasoDetalle;
  seguimientos: SeguimientoCaso[];
  codigoSeguimiento?: string;
  avancePorcentaje?: number;
};

export type ParametroItem = {
  clave: string;
  etiqueta: string;
  valor: string;
  ayuda: string;
  tipo: string;
  version: number;
};

export type GrupoParametros = {
  codigo: string;
  nombre: string;
  ayuda: string;
  parametros: ParametroItem[];
};

export type AreaParametro = {
  id: number;
  codigo: string;
  nombre: string;
  activo: boolean;
  version: number;
  casos: number;
  usuarios: number;
};

export type FeriadoParametro = {
  fecha: string;
  descripcion: string;
};

export type ConfiguracionParametros = {
  grupos: GrupoParametros[];
  areas: AreaParametro[];
  feriados: FeriadoParametro[];
  puedeEditar: boolean;
};

export type InstitucionPublica = {
  nombre: string;
  direccion: string;
  telefono: string;
  correo: string;
  horario: string;
  plazos: { codigo: string; etiqueta: string; diasHabiles: number; texto: string }[];
};
