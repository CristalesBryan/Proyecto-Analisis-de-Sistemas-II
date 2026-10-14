import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { etiquetaEstado } from '../core/etiquetas';
import { formatFechaHora } from '../core/fechas';
import {
  AgenteOpcion,
  ApiError,
  AreaDependencia,
  CatalogoReporte,
  ReporteGenerado,
  ReporteResumen,
} from '../core/models';
import { InternalLayoutComponent } from '../shared/internal-layout.component';

const ESTADOS = ['RECIBIDO', 'EN_REVISION', 'EN_PROCESO', 'RESUELTO', 'CERRADO', 'ANULADO'];
const TIPOS = [
  { codigo: 'Q', nombre: 'Queja' },
  { codigo: 'R', nombre: 'Reclamo' },
  { codigo: 'D', nombre: 'Denuncia' },
  { codigo: 'S', nombre: 'Sugerencia' },
];

function fechaLocal(fecha = new Date()) {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

@Component({
  selector: 'app-reportes',
  imports: [InternalLayoutComponent, FormsModule],
  templateUrl: './reportes.component.html',
})
export class ReportesComponent implements OnInit, OnDestroy {
  catalogo: CatalogoReporte[] = [];
  areas: AreaDependencia[] = [];
  agentes: AgenteOpcion[] = [];
  historial: ReporteResumen[] = [];
  resultado: ReporteGenerado | null = null;
  tipo = 'RPT-01';
  formato: 'PANTALLA' | 'PDF' | 'XLSX' = 'PANTALLA';
  fechaDesde = `${new Date().getFullYear()}-01-01`;
  fechaHasta = fechaLocal();
  tipoCaso = '';
  estado = '';
  area = '';
  agenteId = '';
  incluirAnulados = false;
  cargando = false;
  error = '';
  pagina = 0;
  readonly tamano = 10;
  readonly estados = ESTADOS;
  readonly tipos = TIPOS;
  readonly etiquetaEstado = etiquetaEstado;
  readonly formatFechaHora = formatFechaHora;
  private espera: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
  ) {}

  ngOnInit() {
    void this.cargarInicial();
  }

  ngOnDestroy() {
    if (this.espera) clearTimeout(this.espera);
  }

  get rol() {
    return this.auth.getUser()?.rol;
  }

  get areaUsuario() {
    return this.auth.getUser()?.areaDependencia;
  }

  get seleccionado() {
    return this.catalogo.find((item) => item.codigo === this.tipo);
  }

  get filasPagina() {
    const filas = this.resultado?.filas ?? [];
    return filas.slice(this.pagina * this.tamano, (this.pagina + 1) * this.tamano);
  }

  get totalPaginas() {
    const total = this.resultado?.filas.length ?? 0;
    return Math.max(1, Math.ceil(total / this.tamano));
  }

  get agentesVisibles() {
    if (this.rol !== 'ADMIN' || !this.area) return this.agentes;
    return this.agentes.filter((agente) => agente.areaDependencia === this.area);
  }

  seleccionar(codigo: string) {
    this.tipo = codigo;
    if (codigo === 'RPT-08') this.incluirAnulados = true;
  }

  limpiar() {
    this.fechaDesde = `${new Date().getFullYear()}-01-01`;
    this.fechaHasta = fechaLocal();
    this.tipoCaso = '';
    this.estado = '';
    this.area = '';
    this.agenteId = '';
    this.incluirAnulados = this.tipo === 'RPT-08';
    this.formato = 'PANTALLA';
    this.resultado = null;
    this.error = '';
    this.pagina = 0;
  }

  async generar() {
    this.error = '';
    this.cargando = true;
    this.pagina = 0;
    try {
      const respuesta = await this.api.generarReporte({
        tipo: this.tipo,
        formato: this.formato,
        filtros: {
          fechaDesde: this.fechaDesde,
          fechaHasta: this.fechaHasta,
          tipoCaso: this.tipoCaso || undefined,
          estado: this.estado || undefined,
          area: this.rol === 'ADMIN' ? this.area || undefined : undefined,
          agenteId: this.agenteId ? Number(this.agenteId) : null,
          incluirAnulados: this.tipo === 'RPT-08' || this.incluirAnulados,
        },
      });
      this.resultado = respuesta;
      if (respuesta.asincrono) this.esperar(respuesta.id);
      await this.refrescarHistorial();
    } catch (error) {
      this.error = (error as ApiError).message;
    } finally {
      this.cargando = false;
    }
  }

  async descargar(reporte: { id: number; tipo: string; formato: string }) {
    this.error = '';
    try {
      await this.api.descargarReporte(reporte.id, `reporte-${reporte.tipo}.${reporte.formato === 'PDF' ? 'pdf' : 'xlsx'}`);
      await this.refrescarHistorial();
    } catch (error) {
      this.error = (error as ApiError).message;
    }
  }

  anterior() {
    if (this.pagina > 0) this.pagina--;
  }

  siguiente() {
    if (this.pagina + 1 < this.totalPaginas) this.pagina++;
  }

  private async cargarInicial() {
    try {
      const [catalogo, historial] = await Promise.all([this.api.catalogoReportes(), this.api.listarReportes()]);
      this.catalogo = catalogo;
      this.historial = historial;
      if (this.rol === 'ADMIN') this.areas = await this.api.obtenerAreas();
      this.agentes = await this.api.listarAgentes(this.rol === 'SUPERVISOR' ? this.areaUsuario || undefined : undefined);
    } catch (error) {
      this.error = (error as ApiError).message;
    }
  }

  private async refrescarHistorial() {
    this.historial = await this.api.listarReportes();
  }

  private esperar(id: number) {
    if (this.espera) clearTimeout(this.espera);
    this.espera = setTimeout(() => void this.consultarJob(id), 1500);
  }

  private async consultarJob(id: number) {
    try {
      const job = await this.api.obtenerReporte(id);
      if (this.resultado?.id === id) this.resultado = job;
      if (job.estado === 'PENDIENTE' || job.estado === 'PROCESANDO') {
        this.esperar(id);
        return;
      }
      await this.refrescarHistorial();
    } catch (error) {
      this.error = (error as ApiError).message;
    }
  }
}
