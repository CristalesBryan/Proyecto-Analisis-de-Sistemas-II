import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { etiquetaEstado } from '../core/etiquetas';
import { CuentaCiudadano } from '../core/models';

@Component({
  selector: 'app-ciudadano-cuenta',
  imports: [RouterLink],
  templateUrl: './ciudadano-cuenta.component.html',
})
export class CiudadanoCuentaComponent implements OnInit, OnDestroy {
  cuenta: CuentaCiudadano | null = null;
  error = '';
  readonly etiquetaEstado = etiquetaEstado;
  private cancelado = false;

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  get usuario() {
    return this.auth.getUser();
  }

  get perfil() {
    return this.cuenta?.perfil;
  }

  ngOnInit() {
    this.api
      .obtenerCuentaCiudadano()
      .then((data) => {
        if (!this.cancelado) this.cuenta = data;
      })
      .catch((err: Error) => {
        if (!this.cancelado) this.error = err.message || 'No fue posible cargar su cuenta.';
      });
  }

  ngOnDestroy() {
    this.cancelado = true;
  }

  async logout() {
    try {
      if (this.auth.getToken()) await this.api.cerrarSesion();
    } catch {
      // La sesión local se limpia de todos modos.
    }
    this.auth.limpiarSesion();
    await this.router.navigateByUrl('/');
  }
}
