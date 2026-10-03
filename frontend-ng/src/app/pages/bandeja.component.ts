import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { etiquetaEstado } from '../core/etiquetas';
import { formatFechaHora } from '../core/fechas';
import { ApiError, AreaDependencia, CasoResumen, FiltrosBandeja } from '../core/models';
import { InternalLayoutComponent } from '../shared/internal-layout.component';

@Component({
  selector: 'app-bandeja',
  imports: [InternalLayoutComponent, FormsModule, RouterLink],
  templateUrl: './bandeja.component.html',
})
export class BandejaComponent implements OnInit, OnDestroy {
  readonly estados = ['RECIBIDO', 'EN_REVISION', 'EN_PROCESO', 'RESUELTO', 'CERRADO', 'ANULADO'];
  readonly tipos = [
    { codigo: 'Q', nombre: 'Queja' },
    { codigo: 'R', nombre: 'Reclamo' },
    { codigo: 'D', nombre: 'Denuncia' },
    { codigo: 'S', nombre: 'Sugerencia' },
  ];
  readonly etiquetaEstado = etiquetaEstado;
  readonly formatFechaHora = formatFechaHora;

  filtros: FiltrosBandeja = { page: 1, size: 20, orden: 'fechaRegistro', direccion: 'desc' };
  casos: CasoResumen[] = [];
  areas: AreaDependencia[] = [];
  total = 0;
  paginas = 1;
  pagina = 1;
  cargando = true;
  error = '';

  private readonly alVolver = () => this.cargar();

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
  ) {}

  get usuario() {
    return this.auth.getUser();
  }

  get titulo() {
    return this.usuario?.rol === 'AGENTE' ? 'Casos de mi área' : 'Bandeja de casos';
  }

  ngOnInit() {
    void this.api.obtenerAreas().then((areas) => (this.areas = areas)).catch(() => (this.areas = []));
    window.addEventListener('focus', this.alVolver);
    this.cargar();
  }

  ngOnDestroy() {
    window.removeEventListener('focus', this.alVolver);
  }

  actualizar(clave: keyof FiltrosBandeja, valor: string | boolean) {
    this.filtros = { ...this.filtros, [clave]: valor, page: 1 };
    this.cargar();
  }

  ordenar(campo: string) {
    this.filtros = {
      ...this.filtros,
      orden: campo,
      direccion: this.filtros.orden === campo && this.filtros.direccion === 'desc' ? 'asc' : 'desc',
    };
    this.cargar();
  }

  irPagina(delta: number) {
    this.filtros = { ...this.filtros, page: this.pagina + delta };
    this.cargar();
  }

  texto(event: Event) {
    return (event.target as HTMLInputElement).value;
  }

  marcado(event: Event) {
    return (event.target as HTMLInputElement).checked;
  }

  dias(cantidad: number) {
    const n = Math.abs(cantidad);
    return `${n} día${n === 1 ? '' : 's'} hábil${n === 1 ? '' : 'es'}`;
  }

  destino(caso: CasoResumen) {
    return caso.estado === 'EN_REVISION' || caso.estado === 'EN_PROCESO'
      ? ['/casos', caso.id, 'seguimiento']
      : ['/casos', caso.id];
  }

  cargar() {
    this.cargando = true;
    this.error = '';
    this.api
      .listarCasos(this.filtros)
      .then((respuesta) => {
        this.casos = respuesta.content;
        this.total = respuesta.totalElements;
        this.paginas = respuesta.totalPages;
        this.pagina = respuesta.page;
      })
      .catch((err: ApiError) => {
        this.error = err.conexion
          ? 'Error al gestionar el caso. Verifique su conexión e intente nuevamente.'
          : err.message;
      })
      .finally(() => {
        this.cargando = false;
      });
  }
}
