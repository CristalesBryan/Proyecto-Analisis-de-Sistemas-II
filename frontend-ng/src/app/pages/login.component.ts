import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService, EMAIL_REGEX } from '../core/auth.service';
import { ApiError } from '../core/models';

const MENSAJE_SESION_EXPIRADA = 'Su sesión ha expirado. Por favor inicie sesión nuevamente.';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.component.html',
})
export class LoginComponent implements OnInit {
  email = '';
  password = '';
  mostrarPassword = false;
  enviado = false;
  emailTocado = false;
  passwordTocado = false;
  cargando = false;
  errorServidor = '';
  errorConexion = false;
  avisoExpirada = false;
  sacudir = false;

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  ngOnInit() {
    this.email = '';
    this.password = '';
    if (this.auth.consumirAvisoSesionExpirada()) {
      this.avisoExpirada = true;
      this.errorServidor = MENSAJE_SESION_EXPIRADA;
    }
    const token = this.auth.getToken();
    const usuario = this.auth.getUser();
    if (!token || !usuario) return;
    this.api
      .obtenerSesion()
      .then(() => this.router.navigateByUrl(this.auth.destinoDeRol(usuario.rol)))
      .catch(() => this.auth.limpiarSesion());
  }

  get emailValido() {
    return EMAIL_REGEX.test(this.email.trim());
  }

  get passwordValida() {
    return this.password.length > 0;
  }

  get formularioValido() {
    return this.emailValido && this.passwordValida;
  }

  activarSacudida() {
    this.sacudir = false;
    window.requestAnimationFrame(() => (this.sacudir = true));
  }

  async autenticar() {
    this.errorServidor = '';
    this.errorConexion = false;
    this.avisoExpirada = false;
    this.cargando = true;
    try {
      const respuesta = await this.api.iniciarSesion(this.email.trim(), this.password);
      this.auth.guardarSesion(respuesta.token, respuesta.usuario);
      await this.router.navigateByUrl(this.auth.destinoDeRol(respuesta.usuario.rol));
    } catch (error) {
      const apiError = error as ApiError;
      this.errorServidor =
        apiError instanceof Error
          ? apiError.message
          : 'Error de conexión. Verifique su conexión a internet e intente nuevamente.';
      this.errorConexion = Boolean(apiError.conexion);
      if (apiError.status === 401) this.activarSacudida();
    } finally {
      this.cargando = false;
    }
  }

  async enviar(event: Event) {
    event.preventDefault();
    this.enviado = true;
    if (!this.formularioValido) return;
    await this.autenticar();
  }
}
