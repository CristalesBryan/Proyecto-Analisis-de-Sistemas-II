import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { formatFechaHora } from '../core/fechas';
import { ApiError, CasoDetalle, SeguimientoCaso, TipoSeguimiento } from '../core/models';

const ABIERTOS = new Set(['EN_REVISION', 'EN_PROCESO']);
const NOTA_EXCEPCIONAL = new Set(['CERRADO', 'ANULADO']);

@Component({
  selector: 'app-seguimiento',
  imports: [FormsModule],
  templateUrl: './seguimiento.component.html',
})
export class SeguimientoComponent implements OnChanges {
  @Input({ required: true }) caso!: CasoDetalle;
  @Output() casoActualizado = new EventEmitter<CasoDetalle>();
  @Output() error = new EventEmitter<string>();
  @Output() exito = new EventEmitter<string>();

  items: SeguimientoCaso[] = [];
  cargando = true;
  enviando = false;
  tipo: TipoSeguimiento = 'PUBLICA';
  titulo = '';
  descripcion = '';
  porcentaje = '';
  notificar = false;
  padreId = '';
  justificacion = '';
  archivo: File | null = null;
  readonly formatFechaHora = formatFechaHora;
  private casoCargado?: number;

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
  ) {}

  get abierto() {
    return ABIERTOS.has(this.caso.estado);
  }

  get excepcional() {
    return NOTA_EXCEPCIONAL.has(this.caso.estado) && this.auth.getUser()?.rol === 'ADMIN';
  }

  get puedeRegistrar() {
    return this.caso.estado !== 'RESUELTO' && (this.abierto || this.excepcional);
  }

  ngOnChanges(changes: SimpleChanges) {
    if (this.excepcional) this.tipo = 'INTERNA';
    if (changes['caso'] && this.caso?.id !== this.casoCargado) {
      this.porcentaje = '';
      this.casoCargado = this.caso.id;
      this.cargar();
    }
  }

  elegirArchivo(event: Event) {
    const input = event.target as HTMLInputElement;
    this.archivo = input.files?.[0] || null;
  }

  async descargar(item: SeguimientoCaso) {
    if (!item.adjunto) return;
    try {
      await this.api.descargarEvidenciaSeguimiento(this.caso.id, item.id, item.adjunto.nombreArchivo);
    } catch (err) {
      this.error.emit((err as Error).message);
    }
  }

  async enviar(event: Event) {
    event.preventDefault();
    if (this.titulo.trim().length < 5 || this.titulo.trim().length > 120) {
      this.error.emit('El título debe tener entre 5 y 120 caracteres.');
      return;
    }
    if (this.descripcion.trim().length < 20 || this.descripcion.trim().length > 2000) {
      this.error.emit('La descripción debe tener entre 20 y 2000 caracteres.');
      return;
    }
    if (this.tipo === 'CORRECCION' && !this.padreId) {
      this.error.emit('La corrección debe referenciar un seguimiento existente.');
      return;
    }
    if (this.excepcional && this.justificacion.trim().length < 20) {
      this.error.emit('En casos finalizados justifique la nota interna (mín. 20 caracteres).');
      return;
    }
    if (this.archivo && this.archivo.size > 5 * 1024 * 1024) {
      this.error.emit('El archivo supera el máximo de 5 MB.');
      return;
    }

    const form = new FormData();
    form.append('tipo', this.excepcional ? 'INTERNA' : this.tipo);
    form.append('titulo', this.titulo.trim());
    form.append('descripcion', this.descripcion.trim());
    if (this.porcentaje !== '') form.append('porcentajeAvance', this.porcentaje);
    form.append(
      'notificarCiudadano',
      String(this.tipo === 'PUBLICA' && this.notificar && !this.caso.esAnonimo && this.abierto),
    );
    if (this.tipo === 'CORRECCION' && this.padreId) form.append('seguimientoPadreId', this.padreId);
    if (this.excepcional) form.append('justificacionExcepcional', this.justificacion.trim());
    if (this.archivo) form.append('archivo', this.archivo);

    this.enviando = true;
    try {
      const respuesta = await this.api.registrarSeguimiento(this.caso.id, form);
      this.items = respuesta.seguimientos;
      this.casoActualizado.emit(respuesta.caso);
      const base = respuesta.avisoCorreo ? `${respuesta.mensaje} ${respuesta.avisoCorreo}` : respuesta.mensaje;
      this.exito.emit(
        respuesta.caso.avancePorcentaje === 100
          ? `${base} El 100% de avance no resuelve el caso. Use Proceder a resolver.`
          : base,
      );
      this.titulo = '';
      this.descripcion = '';
      this.porcentaje = '';
      this.justificacion = '';
      this.archivo = null;
      this.notificar = false;
      this.padreId = '';
    } catch (err) {
      this.error.emit((err as ApiError).message);
    } finally {
      this.enviando = false;
    }
  }

  private cargar() {
    this.cargando = true;
    this.api
      .listarSeguimientos(this.caso.id)
      .then((respuesta) => {
        this.items = respuesta.seguimientos;
        if (respuesta.caso) this.casoActualizado.emit(respuesta.caso);
      })
      .catch((err: ApiError) => this.error.emit(err.message))
      .finally(() => {
        this.cargando = false;
      });
  }
}
