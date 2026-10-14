import { Component } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ApiError } from '../core/models';
import { InternalLayoutComponent } from '../shared/internal-layout.component';

@Component({
  selector: 'app-perfil',
  imports: [FormsModule, RouterLink, InternalLayoutComponent, NgTemplateOutlet],
  templateUrl: './perfil.component.html',
})
export class PerfilComponent {
  actual = '';
  nueva = '';
  confirmacion = '';
  errores: Record<string, string> = {};
  error = '';
  mensaje = '';
  cargando = false;

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  get usuario() {
    return this.auth.getUser();
  }

  get obligatorio() {
    return Boolean(this.usuario?.forzarCambioPassword);
  }

  get ciudadano() {
    return this.usuario?.rol === 'CIUDADANO';
  }

  async guardar() {
    this.errores = {};
    this.error = '';
    this.mensaje = '';
    if (!this.actual) this.errores['actual'] = 'La contraseña actual es obligatoria.';
    if (!this.claveValida(this.nueva)) {
      this.errores['nueva'] = 'La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula y un dígito.';
    }
    if (this.nueva !== this.confirmacion) {
      this.errores['confirmacion'] = 'La confirmación no coincide con la nueva contraseña.';
    }
    if (Object.keys(this.errores).length > 0) return;
    this.cargando = true;
    try {
      const respuesta = await this.api.cambiarContrasenaPropia(this.actual, this.nueva, this.confirmacion);
      this.auth.guardarSesion(respuesta.token, respuesta.usuario);
      this.mensaje = respuesta.mensaje;
      this.actual = '';
      this.nueva = '';
      this.confirmacion = '';
      await this.router.navigateByUrl(this.auth.destinoDeRol(respuesta.usuario.rol));
    } catch (error) {
      const apiError = error as ApiError;
      this.errores = apiError.errores ?? {};
      if (!apiError.errores) this.error = error instanceof Error ? error.message : 'No fue posible actualizar la contraseña.';
    } finally {
      this.cargando = false;
    }
  }

  private claveValida(valor: string) {
    return valor.length >= 8 && /[A-Z]/.test(valor) && /[a-z]/.test(valor) && /\d/.test(valor);
  }
}
