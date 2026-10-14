import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { etiquetaEstado } from '../core/etiquetas';
import { formatFechaHora } from '../core/fechas';
import { AgenteOpcion, ApiError, AreaDependencia, CasoResumen, FiltrosBandeja, ResultadoReasignacion } from '../core/models';
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
  agentes: AgenteOpcion[] = [];
  seleccion = new Set<number>();
  agenteMasivo = '';
  motivoMasivo = '';
  notificarMasivo = true;
  resultadosMasivo: ResultadoReasignacion[] = [];
  enviandoMasivo = false;
  total = 0;
  paginas = 1;
  pagina = 1;
  cargando = true;
  error = '';

  private readonly alVolver = () => this.cargar();

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
    private readonly route: ActivatedRoute,
  ) {}

  get usuario() {
    return this.auth.getUser();
  }

  get titulo() {
    return this.usuario?.rol === 'AGENTE' ? 'Casos de mi área' : 'Bandeja de casos';
  }

  get esAdmin() {
    return this.usuario?.rol === 'ADMIN';
  }

  get casosSeleccionados() {
    return this.casos.filter((caso) => this.seleccion.has(caso.id));
  }

  get origenMasivo() {
    return this.casosSeleccionados[0]?.agenteNombre || '';
  }

  get agentesMasivos() {
    const origen = this.casosSeleccionados[0]?.agenteAsignadoId;
    return this.agentes.filter((agente) => agente.id !== origen);
  }

  get masivoListo() {
    const largo = this.motivoMasivo.trim().length;
    return this.seleccion.size > 0 && this.seleccion.size <= 50 && !!this.agenteMasivo && largo >= 50 && largo <= 1000;
  }

  ngOnInit() {
    const consulta = this.route.snapshot.queryParamMap;
    this.filtros = {
      ...this.filtros,
      estado: consulta.get('estado') || undefined,
      desde: consulta.get('desde') || undefined,
      hasta: consulta.get('hasta') || undefined,
      vencido: consulta.get('vencido') === 'true',
      abiertos: consulta.get('abiertos') === 'true',
      soloAsignados: consulta.get('soloAsignados') === 'true',
    };
    void this.api.obtenerAreas().then((areas) => (this.areas = areas)).catch(() => (this.areas = []));
    if (this.esAdmin) {
      void this.api.listarAgentes().then((agentes) => (this.agentes = agentes)).catch(() => (this.agentes = []));
    }
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

  quitarFiltroDelTablero() {
    this.filtros = { ...this.filtros, vencido: false, abiertos: false, soloAsignados: false, page: 1 };
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

  puedeMarcar(caso: CasoResumen) {
    if (!caso.agenteAsignadoId || caso.estado === 'CERRADO' || caso.estado === 'ANULADO') {
      return false;
    }
    const primero = this.casosSeleccionados[0];
    return !primero || primero.agenteAsignadoId === caso.agenteAsignadoId || this.seleccion.has(caso.id);
  }

  alternar(caso: CasoResumen, marcado: boolean) {
    if (marcado && !this.puedeMarcar(caso)) {
      this.error = 'Seleccione casos del mismo agente de origen.';
      return;
    }
    const siguiente = new Set(this.seleccion);
    if (marcado) siguiente.add(caso.id);
    else siguiente.delete(caso.id);
    this.seleccion = siguiente;
    this.resultadosMasivo = [];
    if (siguiente.size === 0) this.agenteMasivo = '';
    else if (!this.agenteMasivo) this.agenteMasivo = String(this.agentesMasivos[0]?.id || '');
  }

  async reasignarSeleccion() {
    if (!this.masivoListo) return;
    this.enviandoMasivo = true;
    this.error = '';
    try {
      const respuesta = await this.api.reasignarCasos(
        [...this.seleccion],
        Number(this.agenteMasivo),
        this.motivoMasivo,
        this.notificarMasivo,
      );
      this.resultadosMasivo = respuesta.resultados;
      this.seleccion = new Set();
      this.motivoMasivo = '';
      this.cargar();
    } catch (err) {
      const apiError = err as ApiError;
      this.error = apiError.message;
    } finally {
      this.enviandoMasivo = false;
    }
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
        this.seleccion = new Set([...this.seleccion].filter((id) => respuesta.content.some((caso) => caso.id === id)));
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
