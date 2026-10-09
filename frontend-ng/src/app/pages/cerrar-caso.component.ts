import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { ApiError, CasoDetalle } from '../core/models';
import { InternalLayoutComponent } from '../shared/internal-layout.component';

@Component({
  selector: 'app-cerrar-caso',
  imports: [InternalLayoutComponent, FormsModule, RouterLink],
  templateUrl: './cerrar-caso.component.html',
})
export class CerrarCasoComponent implements OnInit {
  caso: CasoDetalle | null = null;
  cargando = true;
  enviando = false;
  confirmar = false;
  observacion = '';
  error = '';
  exito = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly api: ApiService,
  ) {}

  get id() {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  get puedeCerrar() {
    return this.caso?.estado === 'RESUELTO' && !this.exito;
  }

  ngOnInit() {
    void this.cargar();
  }

  solicitarConfirmacion() {
    this.error = '';
    if (!this.observacion.trim()) {
      this.error = 'Ingrese la observación de cierre.';
      return;
    }
    this.confirmar = true;
  }

  async cerrar() {
    if (!this.caso || this.enviando) return;
    this.enviando = true;
    this.error = '';
    try {
      const respuesta = await this.api.cerrarCaso(this.caso.id, this.observacion.trim());
      this.caso = respuesta.caso;
      this.exito = respuesta.mensaje;
      this.confirmar = false;
    } catch (error) {
      const fallo = error as ApiError;
      const detalle = fallo.errores ? Object.values(fallo.errores).find((texto) => texto.trim()) : '';
      this.error = detalle || fallo.message;
      this.confirmar = false;
      if (fallo.codigo === 'CASO_NO_APTO_CIERRE' || fallo.status === 403) {
        this.caso = null;
      }
    } finally {
      this.enviando = false;
    }
  }

  private async cargar() {
    this.cargando = true;
    this.error = '';
    try {
      this.caso = await this.api.obtenerCaso(this.id);
      if (this.caso.estado !== 'RESUELTO') {
        this.error = 'El caso no se encuentra en estado resuelto.';
      }
    } catch (error) {
      const fallo = error as ApiError;
      this.caso = null;
      this.error =
        fallo.status === 403
          ? 'El usuario no cuenta con permisos para cerrar el caso.'
          : fallo.message;
    } finally {
      this.cargando = false;
    }
  }
}
