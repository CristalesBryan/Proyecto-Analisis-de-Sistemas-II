import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-acceso-denegado',
  imports: [RouterLink],
  templateUrl: './acceso-denegado.component.html',
})
export class AccesoDenegadoComponent {
  constructor(
    private readonly auth: AuthService,
    private readonly api: ApiService,
    private readonly router: Router,
  ) {}

  get usuario() {
    return this.auth.getUser();
  }

  get destino() {
    return this.usuario ? this.auth.destinoDeRol(this.usuario.rol) : '/';
  }

  async logout() {
    try {
      if (this.auth.getToken()) await this.api.cerrarSesion();
    } catch {
      // La limpieza local debe suceder incluso si el token ya venció.
    }
    this.auth.limpiarSesion();
    await this.router.navigateByUrl('/');
  }
}
