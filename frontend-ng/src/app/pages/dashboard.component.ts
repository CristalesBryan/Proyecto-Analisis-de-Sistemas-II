import { Component, OnDestroy, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { etiquetaEstado } from '../core/etiquetas';
import { ApiError, ResumenDashboard, Rol } from '../core/models';
import { InternalLayoutComponent } from '../shared/internal-layout.component';

const REFRESCO_MS = 5 * 60 * 1000;

@Component({
  selector: 'app-dashboard',
  imports: [InternalLayoutComponent, RouterLink],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit, OnDestroy {
  readonly etiquetaEstado = etiquetaEstado;
  readonly periodos = [
    { valor: 'hoy', nombre: 'Hoy' },
    { valor: '7d', nombre: '7 días' },
    { valor: '30d', nombre: '30 días' },
  ];

  periodo = '30d';
  resumen: ResumenDashboard | null = null;
  cargando = true;
  error = '';
  private temporizador = 0;

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
  ) {}

  get rol(): Rol {
    return this.auth.getUser()?.rol ?? 'AGENTE';
  }

  get bandeja() {
    return this.auth.destinosPorRol[this.rol];
  }

  get reportes() {
    return this.rol === 'ADMIN' ? '/admin/reportes' : '/supervisor/reportes';
  }

  get titulo() {
    if (this.rol === 'ADMIN') return 'Tablero de la institución';
    if (this.rol === 'SUPERVISOR') return 'Tablero del área';
    return 'Tablero de mis casos';
  }

  get sinDatos() {
    const datos = this.resumen;
    if (!datos || datos.errorIndicadores) return false;
    const recibidos = datos.recibidos ?? 0;
    const anulados = datos.anulados ?? 0;
    return datos.abiertos === 0 && datos.vencidos === 0 && recibidos === 0 && datos.resueltos === 0 && anulados === 0;
  }

  ngOnInit() {
    void this.cargar();
    this.temporizador = window.setInterval(() => void this.cargar(true), REFRESCO_MS);
  }

  ngOnDestroy() {
    window.clearInterval(this.temporizador);
  }

  elegirPeriodo(periodo: string) {
    this.periodo = periodo;
    void this.cargar();
  }

  consulta(extra: Record<string, string | undefined> = {}) {
    const params: Record<string, string> = {};
    if (this.rol === 'AGENTE') params['soloAsignados'] = 'true';
    for (const [clave, valor] of Object.entries(extra)) {
      if (valor) params[clave] = valor;
    }
    return params;
  }

  ancho(total: number) {
    const maximo = Math.max(1, ...(this.resumen?.porEstado.map((item) => item.total) ?? [1]));
    return Math.round((total / maximo) * 100);
  }

  async cargar(silencioso = false) {
    if (!silencioso) this.cargando = this.resumen == null;
    this.error = '';
    try {
      this.resumen = await this.api.consultarDashboard(this.periodo);
    } catch (err) {
      const apiError = err as ApiError;
      this.error = apiError.message || 'No fue posible consultar el tablero.';
    } finally {
      this.cargando = false;
    }
  }
}
