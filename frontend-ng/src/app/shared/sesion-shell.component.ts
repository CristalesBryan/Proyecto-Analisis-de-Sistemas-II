import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ApiError, Rol } from '../core/models';

@Component({
  selector: 'app-sesion-shell',
  imports: [RouterOutlet],
  templateUrl: './sesion-shell.component.html',
})
export class SesionShellComponent implements OnInit {
  estado: 'validando' | 'ok' | 'conexion' = 'validando';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly api: ApiService,
    private readonly auth: AuthService,
  ) {}

  ngOnInit() {
    this.validar();
  }

  reintentar() {
    this.validar();
  }

  private validar() {
    const token = this.auth.getToken();
    if (!token) {
      void this.router.navigateByUrl('/login');
      return;
    }
    const permitidos = (this.route.snapshot.data['roles'] as Rol[] | undefined) ?? [];
    this.estado = 'validando';
    this.api
      .obtenerSesion()
      .then(({ usuario }) => {
        this.auth.guardarSesion(token, usuario);
        if (!permitidos.includes(usuario.rol)) {
          void this.router.navigateByUrl('/acceso-denegado');
          return;
        }
        this.estado = 'ok';
      })
      .catch((error: ApiError) => {
        if (error?.codigo === 'TOKEN_INVALIDO' || error?.status === 401) {
          this.auth.marcarSesionExpirada();
          void this.router.navigateByUrl('/login');
          return;
        }
        if (error?.conexion) {
          this.estado = 'conexion';
          return;
        }
        this.auth.limpiarSesion();
        void this.router.navigateByUrl('/login');
      });
  }
}
