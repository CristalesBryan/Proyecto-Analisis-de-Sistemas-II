import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { ApiError, CasoDetalle } from '../core/models';
import { InternalLayoutComponent } from '../shared/internal-layout.component';

@Component({
  selector: 'app-resolver-caso',
  imports: [InternalLayoutComponent, FormsModule, RouterLink],
  templateUrl: './resolver-caso.component.html',
})
export class ResolverCasoComponent implements OnInit {
  readonly tipos = [
    { id: 'ATENDIDO', label: 'Atendido' },
    { id: 'PARCIALMENTE_ATENDIDO', label: 'Parcialmente atendido' },
    { id: 'IMPROCEDENTE', label: 'Improcedente' },
    { id: 'SIN_RESPUESTA_CIUDADANO', label: 'Sin respuesta del ciudadano' },
  ];

  caso: CasoDetalle | null = null;
  cargando = true;
  enviando = false;
  confirmar = false;
  comentario = '';
  tipoResultado = '';
  archivo: File | null = null;
  error = '';
  exito = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly api: ApiService,
  ) {}

  get id() {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  get puedeResolver() {
    return this.caso?.estado === 'EN_PROCESO' && !this.exito;
  }

  ngOnInit() {
    void this.cargar();
  }

  elegirArchivo(event: Event) {
    const input = event.target as HTMLInputElement;
    this.archivo = input.files?.[0] ?? null;
  }

  solicitarConfirmacion() {
    this.error = '';
    if (!this.comentario.trim()) {
      this.error = 'Ingrese el comentario de resolución.';
      return;
    }
    if (!this.tipoResultado) {
      this.error = 'Seleccione el tipo de resultado.';
      return;
    }
    this.confirmar = true;
  }

  async resolver() {
    if (!this.caso || this.enviando) return;
    this.enviando = true;
    this.error = '';
    const form = new FormData();
    form.set('comentario', this.comentario.trim());
    form.set('tipoResultado', this.tipoResultado);
    if (this.archivo) form.set('archivo', this.archivo);
    try {
      const respuesta = await this.api.resolverCaso(this.caso.id, form);
      this.caso = respuesta.caso;
      this.exito = respuesta.mensaje;
      this.confirmar = false;
    } catch (error) {
      const fallo = error as ApiError;
      this.error = fallo.message;
      this.confirmar = false;
      if (fallo.codigo === 'CASO_NO_APTO_RESOLUCION' || fallo.status === 403) {
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
      if (this.caso.estado !== 'EN_PROCESO') {
        this.error = 'El caso no se encuentra en estado en proceso.';
      }
    } catch (error) {
      const fallo = error as ApiError;
      this.caso = null;
      this.error =
        fallo.status === 403
          ? 'El usuario no cuenta con permisos para resolver el caso.'
          : fallo.message;
    } finally {
      this.cargando = false;
    }
  }
}
