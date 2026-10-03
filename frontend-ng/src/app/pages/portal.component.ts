import { Component, OnDestroy, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { etiquetaEstado } from '../core/etiquetas';
import { formatFecha, formatFechaHora } from '../core/fechas';
import { ApiError, CasoPublico, SeguimientoPublico } from '../core/models';

@Component({
  selector: 'app-portal',
  imports: [RouterLink],
  templateUrl: './portal.component.html',
})
export class PortalComponent implements OnInit, OnDestroy {
  disponible: boolean | null = null;
  modalOpen = false;
  codigo = '';
  loading = false;
  error: string | null = null;
  resultado: CasoPublico | null = null;
  seguimientos: SeguimientoPublico[] = [];
  anio = new Date().getFullYear();
  readonly etiquetaEstado = etiquetaEstado;
  readonly formatFecha = formatFecha;
  readonly formatFechaHora = formatFechaHora;

  private cancelado = false;

  readonly tipos = [
    { nombre: 'Queja', texto: 'Manifestación de insatisfacción por un servicio municipal.' },
    { nombre: 'Reclamo', texto: 'Solicitud formal ante un derecho no atendido o mal ejecutado.' },
    { nombre: 'Denuncia', texto: 'Reporte de irregularidades o hechos que requieren investigación.' },
    { nombre: 'Sugerencia', texto: 'Propuesta ciudadana para mejorar servicios o procesos.' },
  ];

  readonly pasos = [
    'Registra tu caso y obtén un código de seguimiento único (ej. Q-2026-00001).',
    'El sistema asigna y gestiona el caso según plazos institucionales.',
    'Consulta el estado en cualquier momento con tu código.',
    'Recibe la resolución y el cierre del caso por los canales configurados.',
  ];

  readonly contactos = [
    { label: 'Dirección', value: 'Palacio Municipal, Centro Cívico, Ciudad' },
    { label: 'Teléfono', value: '(502) 2222-0000' },
    { label: 'Correo', value: 'qrds@municipalidad.gob.gt' },
    { label: 'Horario presencial', value: 'Lunes a viernes, 8:00 – 16:00' },
  ];

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
  ) {}

  ngOnInit() {
    void this.boot();
  }

  ngOnDestroy() {
    this.cancelado = true;
  }

  get usuario() {
    return this.auth.getUser();
  }

  get enlaceSesion() {
    const usuario = this.usuario;
    if (!usuario) return '/login';
    if (usuario.rol === 'CIUDADANO') return '/ciudadano';
    return this.auth.destinosPorRol[usuario.rol];
  }

  get textoSesion() {
    const usuario = this.usuario;
    if (!usuario) return 'Iniciar Sesión';
    return usuario.rol === 'CIUDADANO' ? 'Mi cuenta' : 'Entrar';
  }

  alEscribirCodigo(event: Event) {
    this.codigo = (event.target as HTMLInputElement).value.toUpperCase();
  }

  openConsulta() {
    this.modalOpen = true;
    void this.api.registrarAccesoPublico('CONSULTAR_CASO', 'OK');
  }

  cerrarConsulta() {
    this.codigo = '';
    this.error = null;
    this.resultado = null;
    this.seguimientos = [];
    this.modalOpen = false;
  }

  async consultar(event: Event) {
    event.preventDefault();
    const valor = this.codigo.trim().toUpperCase();
    this.error = null;
    this.resultado = null;
    this.seguimientos = [];
    if (!this.api.codigoSeguimientoValido(valor)) {
      this.error = 'Ingrese un código válido. Ejemplo: Q-2026-00001.';
      return;
    }
    this.loading = true;
    try {
      const data = await this.api.consultarCasoPublico(valor);
      this.resultado = data;
      if (Array.isArray(data.seguimientos)) {
        this.seguimientos = data.seguimientos;
      } else {
        try {
          const linea = await this.api.listarSeguimientosPublicos(valor);
          this.seguimientos = linea.seguimientos;
          this.resultado = { ...data, avancePorcentaje: linea.avancePorcentaje };
        } catch {
          this.seguimientos = [];
        }
      }
    } catch (err) {
      const fallo = err as ApiError;
      this.error =
        fallo.codigo === 'CODIGO_INVALIDO'
          ? 'Los datos ingresados no permiten realizar la consulta.'
          : fallo.message || 'Código no encontrado. Verifique el número e intente nuevamente.';
    } finally {
      this.loading = false;
    }
  }

  private async boot() {
    try {
      const estado = await this.api.getSistemaEstado();
      if (this.cancelado) return;
      if (estado.estado === 'UP') {
        this.disponible = true;
        await this.api.registrarAccesoPublico('ACCESO_PORTAL', 'OK');
      } else {
        this.disponible = false;
      }
    } catch {
      if (!this.cancelado) this.disponible = false;
    }
  }
}
