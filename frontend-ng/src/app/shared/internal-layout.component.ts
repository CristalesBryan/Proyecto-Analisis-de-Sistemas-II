import { Component } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Rol } from '../core/models';

@Component({
  selector: 'app-internal-layout',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './internal-layout.component.html',
})
export class InternalLayoutComponent {
  constructor(
    private readonly auth: AuthService,
    private readonly api: ApiService,
    private readonly router: Router,
  ) {}

  get usuario() {
    return this.auth.getUser();
  }

  get rol(): Rol | undefined {
    return this.usuario?.rol;
  }

  get bandeja() {
    return this.rol ? this.auth.destinosPorRol[this.rol] : '/login';
  }

  get inicio() {
    return this.rol ? this.auth.inicioPorRol[this.rol] : '/login';
  }

  async logout() {
    try {
      if (this.auth.getToken()) await this.api.cerrarSesion();
    } catch {
      // La limpieza local debe suceder incluso si el token ya venció.
    }
    this.auth.limpiarSesion();
    void this.router.navigateByUrl('/');
  }
}
