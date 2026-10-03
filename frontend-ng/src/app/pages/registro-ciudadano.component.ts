import { Component, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { EMAIL_REGEX } from '../core/auth.service';
import { ApiError } from '../core/models';
import { RecaptchaComponent } from '../shared/recaptcha.component';

const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,}$/;
const DPI_REGEX = /^\d{13}$/;

@Component({
  selector: 'app-registro-ciudadano',
  imports: [FormsModule, RouterLink, RecaptchaComponent],
  templateUrl: './registro-ciudadano.component.html',
})
export class RegistroCiudadanoComponent {
  @ViewChild('captcha') captcha?: RecaptchaComponent;

  paso: 1 | 2 = 1;
  nombre = '';
  email = '';
  telefono = '';
  dpi = '';
  password = '';
  confirmarPassword = '';
  mostrarPassword = false;
  aceptaPrivacidad = false;
  recaptchaToken = '';
  errorCaptcha = '';
  codigo = '';
  registroId = '';
  enviado = false;
  cargando = false;
  errorServidor = '';
  erroresApi: Record<string, string> = {};

  constructor(
    private readonly api: ApiService,
    private readonly router: Router,
  ) {}

  get errores(): Record<string, string> {
    const lista: Record<string, string> = {};
    if (this.nombre.trim().length < 2) lista['nombre'] = 'Ingrese su nombre completo.';
    if (!EMAIL_REGEX.test(this.email.trim())) lista['email'] = 'Ingrese un correo electrónico válido.';
    const tel = this.telefono.replace(/\D/g, '');
    if (tel && (tel.length < 8 || tel.length > 15)) lista['telefono'] = 'El teléfono debe tener entre 8 y 15 dígitos.';
    if (!DPI_REGEX.test(this.dpi.replace(/\D/g, ''))) lista['dpi'] = 'Ingrese un DPI o CUI de 13 dígitos.';
    if (!PASSWORD_REGEX.test(this.password)) {
      lista['password'] = 'Use 10 caracteres o más, con mayúscula, minúscula, número y símbolo.';
    }
    if (this.password !== this.confirmarPassword) lista['confirmarPassword'] = 'Las contraseñas no coinciden.';
    if (!this.aceptaPrivacidad) lista['privacidad'] = 'Debe aceptar el aviso de privacidad.';
    if (!this.recaptchaToken.trim()) lista['captcha'] = 'Complete la verificación «No soy un robot».';
    return lista;
  }

  reiniciarCaptcha(mensaje = '') {
    this.captcha?.reset();
    this.recaptchaToken = '';
    this.errorCaptcha = mensaje;
  }

  onToken(token: string) {
    this.recaptchaToken = token || '';
    if (token) this.errorCaptcha = '';
  }

  async enviarDatos(event: Event) {
    event.preventDefault();
    this.enviado = true;
    this.errorServidor = '';
    this.erroresApi = {};
    if (Object.keys(this.errores).length > 0) return;
    this.cargando = true;
    try {
      const respuesta = await this.api.iniciarRegistroCiudadano({
        nombre: this.nombre.trim(),
        email: this.email.trim(),
        telefono: this.telefono.replace(/\D/g, ''),
        dpi: this.dpi.replace(/\D/g, ''),
        password: this.password,
        confirmarPassword: this.confirmarPassword,
        aceptaPrivacidad: this.aceptaPrivacidad,
        recaptchaToken: this.recaptchaToken,
      });
      this.registroId = respuesta.registroId;
      this.paso = 2;
      this.enviado = false;
    } catch (error) {
      const apiError = error as ApiError;
      this.errorServidor = apiError.message;
      if (apiError.errores) this.erroresApi = apiError.errores;
      this.reiniciarCaptcha(
        apiError.codigo === 'CAPTCHA_INVALIDO' ||
          apiError.codigo === 'CAPTCHA_NO_DISPONIBLE' ||
          apiError.codigo === 'CAPTCHA_NO_CONFIGURADO'
          ? apiError.message || 'La verificación expiró o falló. Márquela de nuevo.'
          : 'Vuelva a completar la verificación «No soy un robot».',
      );
    } finally {
      this.cargando = false;
    }
  }

  async confirmarCodigo(event: Event) {
    event.preventDefault();
    this.enviado = true;
    this.errorServidor = '';
    if (!this.codigo.trim()) return;
    this.cargando = true;
    try {
      await this.api.confirmarRegistroCiudadano(this.registroId, this.codigo.trim());
      await this.router.navigateByUrl('/login');
    } catch (error) {
      this.errorServidor = (error as ApiError).message;
    } finally {
      this.cargando = false;
    }
  }
}
