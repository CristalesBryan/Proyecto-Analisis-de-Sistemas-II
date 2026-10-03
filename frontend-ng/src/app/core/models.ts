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
  documentos: {
    id: number;
    nombreArchivo: string;
    tipoMime: string;
    tamanioBytes: number;
    subidoEn: string;
  }[];
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
};

export type FiltrosBandeja = {
  estado?: string;
  tipo?: string;
  area?: string;
  codigo?: string;
  desde?: string;
  hasta?: string;
  sinAsignar?: boolean;
  orden?: string;
  direccion?: 'asc' | 'desc';
  page?: number;
  size?: number;
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

export type ListaSeguimientos = {
  caso?: CasoDetalle;
  seguimientos: SeguimientoCaso[];
  codigoSeguimiento?: string;
  avancePorcentaje?: number;
};
