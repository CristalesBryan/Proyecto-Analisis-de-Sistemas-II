import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService } from '../core/api.service';
import { EMAIL_REGEX } from '../core/auth.service';
import { formatFechaHora } from '../core/fechas';
import { ApiError, AreaDependencia, RegistroCasoRespuesta } from '../core/models';
import { RecaptchaComponent } from '../shared/recaptcha.component';

type TipoCaso = 'Q' | 'R' | 'D' | 'S';

const TIPOS: { codigo: TipoCaso; nombre: string; plazo: string; descripcion: string }[] = [
  { codigo: 'Q', nombre: 'Queja', plazo: '15 días hábiles', descripcion: 'Insatisfacción por el servicio recibido.' },
  { codigo: 'R', nombre: 'Reclamo', plazo: '20 días hábiles', descripcion: 'Solicitud de revisión de una decisión o omisión.' },
  { codigo: 'D', nombre: 'Denuncia', plazo: '30 días hábiles', descripcion: 'Posible irregularidad o incumplimiento de normas.' },
  { codigo: 'S', nombre: 'Sugerencia', plazo: '30 días hábiles', descripcion: 'Propuesta para mejorar servicios o atención.' },
];

const MAX_ARCHIVOS = 5;
const MAX_BYTES = 5 * 1024 * 1024;
const EXTENSIONES = new Set(['pdf', 'jpg', 'jpeg', 'png', 'docx']);

function tiposDesdeConsulta(valor: string | null): TipoCaso[] {
  const clave = (valor || '').trim().toUpperCase();
  if (clave === 'Q') return ['Q'];
  if (clave === 'S') return ['S'];
  if (clave === 'R') return ['R'];
  if (clave === 'D') return ['D'];
  if (clave === 'RD' || clave === 'R,D') return ['R', 'D'];
  return ['Q', 'R', 'D', 'S'];
}

@Component({
  selector: 'app-registro-caso',
  imports: [FormsModule, RouterLink, RecaptchaComponent],
  templateUrl: './registro-caso.component.html',
})
export class RegistroCasoComponent implements OnInit, OnDestroy {
  @ViewChild('captcha') captcha?: RecaptchaComponent;

  readonly pasos = ['Tipo', 'Datos', 'Adjuntos'];
  readonly formatFechaHora = formatFechaHora;
  paso = 1;
  tipoCaso: TipoCaso | '' = '';
  tiposPermitidos: TipoCaso[] = ['Q', 'R', 'D', 'S'];
  nombre = '';
  email = '';
  telefono = '';
  area = '';
  descripcion = '';
  denunciado = '';
  esAnonimo = false;
  archivos: File[] = [];
  erroresArchivo: string[] = [];
  aceptaPrivacidad = false;
  areas: AreaDependencia[] = [];
  recaptchaToken = '';
  errorCaptcha = '';
  enviado = false;
  cargando = false;
  errorServidor = '';
  errorConexion = false;
  duplicado: string | null = null;
  confirmacion: RegistroCasoRespuesta | null = null;
  avisoAdjuntos = '';
  copiado = false;
  private sub?: Subscription;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly api: ApiService,
  ) {}

  ngOnInit() {
    this.sub = this.route.queryParamMap.subscribe((params) => this.aplicarTipo(params.get('tipo')));
    void this.api.obtenerAreas().then((areas) => (this.areas = areas)).catch(() => (this.areas = []));
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  get tipoFijo() {
    return this.tiposPermitidos.length === 1;
  }

  get tiposVisibles() {
    return TIPOS.filter((tipo) => this.tiposPermitidos.includes(tipo.codigo));
  }

  get tituloRegistro() {
    if (this.tiposPermitidos.length === 1) {
      const nombre = TIPOS.find((tipo) => tipo.codigo === this.tiposPermitidos[0])?.nombre.toLowerCase();
      return `Registrar ${nombre}`;
    }
    if (this.tiposPermitidos.length === 2) return 'Registrar reclamo o denuncia';
    return 'Registrar caso';
  }

  get tipoSeleccionado() {
    return TIPOS.find((item) => item.codigo === this.tipoCaso);
  }

  get areaNombre() {
    return this.areas.find((item) => item.codigo === this.area)?.nombre || this.area;
  }

  get erroresPaso2(): Record<string, string> {
    const errores: Record<string, string> = {};
    if (!this.esAnonimo && (this.nombre.trim().length < 2 || this.nombre.trim().length > 150)) {
      errores['nombre'] = 'El nombre debe tener entre 2 y 150 caracteres.';
    }
    if (!this.esAnonimo && !EMAIL_REGEX.test(this.email.trim())) {
      errores['email'] = 'Ingrese un correo electrónico válido.';
    }
    const digitos = this.telefono.replace(/\D/g, '');
    if (!this.esAnonimo && digitos && (digitos.length < 8 || digitos.length > 15)) {
      errores['telefono'] = 'El teléfono debe tener entre 8 y 15 dígitos.';
    }
    if (!this.esAnonimo && !this.area) errores['area'] = 'Seleccione un área o dependencia.';
    if (this.descripcion.trim().length < 50 || this.descripcion.trim().length > 2000) {
      errores['descripcion'] = 'La descripción debe tener entre 50 y 2000 caracteres.';
    }
    if (!this.esAnonimo && this.tipoCaso === 'D' && this.denunciado.trim().length > 150) {
      errores['denunciado'] = 'El nombre del denunciado no puede superar 150 caracteres.';
    }
    return errores;
  }

  get erroresPaso3(): Record<string, string> {
    const errores: Record<string, string> = {};
    if (!this.aceptaPrivacidad) errores['privacidad'] = 'Debe aceptar el aviso de privacidad para enviar el caso.';
    if (!this.recaptchaToken.trim()) errores['captcha'] = 'Complete la verificación «No soy un robot».';
    return errores;
  }

  valor(event: Event) {
    return (event.target as HTMLInputElement).value;
  }

  marcado(event: Event) {
    return (event.target as HTMLInputElement).checked;
  }

  elegirTipo(codigo: TipoCaso) {
    this.tipoCaso = codigo;
    if (codigo !== 'D') this.denunciado = '';
  }

  cambiarAnonimo(valor: boolean) {
    this.esAnonimo = valor;
    if (valor) {
      this.nombre = '';
      this.email = '';
      this.telefono = '';
      this.area = '';
      this.denunciado = '';
      this.archivos = [];
      this.erroresArchivo = [];
    }
  }

  irPaso2() {
    this.enviado = false;
    if (!this.tipoCaso) {
      this.enviado = true;
      return;
    }
    this.paso = 2;
  }

  irPaso3() {
    this.enviado = true;
    if (Object.keys(this.erroresPaso2).length > 0) return;
    this.enviado = false;
    this.paso = 3;
  }

  atras() {
    this.enviado = false;
    this.paso -= 1;
  }

  agregarArchivos(event: Event) {
    const input = event.target as HTMLInputElement;
    const lista = input.files;
    input.value = '';
    if (!lista) return;
    const nuevos = [...this.archivos];
    const errores: string[] = [];
    for (const file of Array.from(lista)) {
      if (nuevos.length >= MAX_ARCHIVOS) {
        errores.push(`${file.name}: máximo 5 archivos por caso.`);
        continue;
      }
      const motivo = this.validarArchivo(file);
      if (motivo) {
        errores.push(`${file.name}: ${motivo}`);
        continue;
      }
      if (nuevos.some((item) => item.name === file.name && item.size === file.size)) continue;
      nuevos.push(file);
    }
    this.archivos = nuevos;
    this.erroresArchivo = errores;
  }

  quitarArchivo(archivo: File) {
    this.archivos = this.archivos.filter((item) => item !== archivo);
  }

  onToken(token: string) {
    this.recaptchaToken = token || '';
    if (token) this.errorCaptcha = '';
  }

  reiniciarCaptcha(mensaje = '') {
    this.captcha?.reset();
    this.recaptchaToken = '';
    this.errorCaptcha = mensaje;
  }

  continuarDuplicado() {
    this.duplicado = null;
    void this.enviar(true);
  }

  async enviar(forzarRegistro = false) {
    this.enviado = true;
    this.errorServidor = '';
    this.errorConexion = false;
    this.duplicado = null;
    if (!this.tipoCaso || Object.keys(this.erroresPaso2).length > 0 || Object.keys(this.erroresPaso3).length > 0) {
      return;
    }
    this.cargando = true;
    try {
      const respuesta = await this.api.registrarCasoPublico({
        tipoCaso: this.tipoCaso,
        nombreCiudadano: this.esAnonimo ? '' : this.nombre.trim(),
        email: this.esAnonimo ? '' : this.email.trim(),
        telefono: this.esAnonimo ? '' : this.telefono.replace(/\D/g, ''),
        areaDependencia: this.esAnonimo ? '' : this.area,
        descripcion: this.descripcion.trim(),
        denunciado: this.esAnonimo ? undefined : this.denunciado.trim() || undefined,
        esAnonimo: this.esAnonimo,
        aceptaPrivacidad: this.aceptaPrivacidad,
        recaptchaToken: this.recaptchaToken,
        forzarRegistro,
      });
      if (!this.esAnonimo && this.archivos.length > 0) {
        try {
          const adjuntos = await this.api.adjuntarDocumentosCaso(respuesta.codigoSeguimiento, this.archivos);
          if (adjuntos.rechazados.length > 0) {
            this.avisoAdjuntos = `El caso se registró, pero algunos archivos no se adjuntaron: ${adjuntos.rechazados
              .map((item) => item.nombre)
              .join(', ')}.`;
          }
        } catch {
          this.avisoAdjuntos = 'El caso se registró, pero no fue posible guardar los documentos adjuntos.';
        }
      }
      this.confirmacion = respuesta;
    } catch (error) {
      const apiError = error as ApiError;
      if (apiError.codigo === 'CASO_SIMILAR' && apiError.codigoExistente) {
        this.duplicado = apiError.codigoExistente;
        return;
      }
      this.errorServidor = apiError.conexion
        ? 'Error al registrar su caso. Verifique su conexión e intente nuevamente.'
        : apiError.message;
      this.errorConexion = Boolean(apiError.conexion);
      if (
        apiError.codigo === 'CAPTCHA_INVALIDO' ||
        apiError.codigo === 'CAPTCHA_NO_DISPONIBLE' ||
        apiError.codigo === 'CAPTCHA_NO_CONFIGURADO'
      ) {
        this.reiniciarCaptcha(apiError.message || 'La verificación expiró o falló. Márquela de nuevo.');
      }
    } finally {
      this.cargando = false;
    }
  }

  async enviarFormulario(event: Event) {
    event.preventDefault();
    await this.enviar(false);
  }

  async copiarCodigo() {
    if (!this.confirmacion) return;
    await navigator.clipboard.writeText(this.confirmacion.codigoSeguimiento);
    this.copiado = true;
    window.setTimeout(() => (this.copiado = false), 2000);
  }

  nuevo() {
    this.confirmacion = null;
    this.paso = this.tipoFijo ? 2 : 1;
    this.tipoCaso = this.tipoFijo ? this.tiposPermitidos[0] : '';
    this.nombre = '';
    this.email = '';
    this.telefono = '';
    this.area = '';
    this.descripcion = '';
    this.denunciado = '';
    this.esAnonimo = false;
    this.archivos = [];
    this.erroresArchivo = [];
    this.aceptaPrivacidad = false;
    this.reiniciarCaptcha();
    this.enviado = false;
    this.errorServidor = '';
    this.avisoAdjuntos = '';
    this.copiado = false;
  }

  private aplicarTipo(valor: string | null) {
    this.tiposPermitidos = tiposDesdeConsulta(valor);
    if (this.tipoFijo) {
      this.tipoCaso = this.tiposPermitidos[0];
      if (this.paso === 1) this.paso = 2;
      return;
    }
    if (!this.tipoCaso || !this.tiposPermitidos.includes(this.tipoCaso)) this.tipoCaso = '';
  }

  private validarArchivo(file: File) {
    const extension = file.name.toLowerCase().split('.').pop() || '';
    if (!EXTENSIONES.has(extension)) return 'Formato no permitido. Use PDF, JPG, PNG o DOCX.';
    if (file.size > MAX_BYTES) return 'El archivo supera el máximo de 5 MB.';
    return null;
  }
}
