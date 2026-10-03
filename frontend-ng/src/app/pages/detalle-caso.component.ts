import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { etiquetaEstado } from '../core/etiquetas';
import { formatFecha, formatFechaHora } from '../core/fechas';
import { AgenteOpcion, ApiError, AreaDependencia, CasoDetalle } from '../core/models';
import { InternalLayoutComponent } from '../shared/internal-layout.component';
import { SeguimientoComponent } from '../shared/seguimiento.component';

type Pestana = 'resumen' | 'seguimiento' | 'documentos' | 'historial';
type Confirmacion = 'asignar' | 'reasignar' | 'estado' | 'anular' | 'cerrar' | 'prorroga' | 'escalar';

@Component({
  selector: 'app-detalle-caso',
  imports: [InternalLayoutComponent, FormsModule, RouterLink, SeguimientoComponent],
  templateUrl: './detalle-caso.component.html',
})
export class DetalleCasoComponent implements OnInit, OnDestroy {
  readonly etiquetaEstado = etiquetaEstado;
  readonly formatFecha = formatFecha;
  readonly formatFechaHora = formatFechaHora;
  readonly pestanas: { id: Pestana; label: string }[] = [
    { id: 'resumen', label: 'Resumen' },
    { id: 'seguimiento', label: 'Seguimiento' },
    { id: 'documentos', label: 'Documentos' },
    { id: 'historial', label: 'Historial' },
  ];

  caso: CasoDetalle | null = null;
  agentes: AgenteOpcion[] = [];
  areas: AreaDependencia[] = [];
  cargando = true;
  error = '';
  exito = '';
  agenteId = '';
  motivo = '';
  nuevoEstado = '';
  observacionEstado = '';
  nota = '';
  justificacion = '';
  observacionCierre = '';
  diasProrroga = '3';
  justificacionProrroga = '';
  motivoEscalar = '';
  confirmar: Confirmacion | null = null;
  enviando = false;
  subiendoDoc = false;
  avisoDocs = '';
  editando = false;
  nombreEdit = '';
  emailEdit = '';
  telefonoEdit = '';
  areaEdit = '';
  prioridadEdit = 'MEDIA';
  descripcionEdit = '';
  denunciadoEdit = '';
  motivoEdit = '';
  pestana: Pestana = 'resumen';
  casoId = NaN;
  private subs = new Subscription();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly api: ApiService,
    private readonly auth: AuthService,
  ) {}

  get usuario() {
    return this.auth.getUser();
  }

  get puedeAsignar() {
    return this.usuario?.rol === 'ADMIN' || this.usuario?.rol === 'SUPERVISOR';
  }

  get puedeAnular() {
    return this.usuario?.rol === 'ADMIN';
  }

  get puedeCerrar() {
    return (
      this.usuario?.permisos?.includes('CASOS_CERRAR') ||
      this.usuario?.rol === 'ADMIN' ||
      this.usuario?.rol === 'SUPERVISOR'
    );
  }

  get puedePlazo() {
    return this.usuario?.rol === 'ADMIN' || this.usuario?.rol === 'SUPERVISOR';
  }

  get bandeja() {
    return this.usuario ? this.auth.destinosPorRol[this.usuario.rol] : '/';
  }

  get finalizado() {
    return this.caso?.estado === 'CERRADO' || this.caso?.estado === 'ANULADO';
  }

  get abiertoSeguimiento() {
    return this.caso?.estado === 'EN_REVISION' || this.caso?.estado === 'EN_PROCESO';
  }

  get alertaPlazo() {
    return (
      this.caso?.plazo &&
      (this.caso.plazo.vencido || (this.caso.plazo.diasRestantes !== null && this.caso.plazo.diasRestantes <= 3))
    );
  }

  get transicionesVisibles() {
    return this.caso?.transicionesPermitidas.filter((estado) => estado !== 'ANULADO') ?? [];
  }

  get mensajeConfirmacion() {
    if (!this.caso || !this.confirmar) return '';
    const codigo = this.caso.codigoSeguimiento;
    if (this.confirmar === 'asignar') return `Asignar ${codigo} y cambiar RECIBIDO → EN_REVISION.`;
    if (this.confirmar === 'reasignar') return `Reasignar ${codigo} a otro agente. El estado no cambia.`;
    if (this.confirmar === 'estado') {
      return `${codigo}: ${etiquetaEstado(this.caso.estado)} → ${etiquetaEstado(this.nuevoEstado)}.`;
    }
    if (this.confirmar === 'anular') return `${codigo} pasará a ANULADO. Esta acción es final.`;
    if (this.confirmar === 'cerrar') {
      return `${codigo} pasará de RESUELTO a CERRADO. El historial se conserva y no podrá modificarse.`;
    }
    if (this.confirmar === 'prorroga') return `Prorrogar ${codigo} ${this.diasProrroga} día(s) hábil(es).`;
    return `Escalar ${codigo} y marcar prioridad ALTA.`;
  }

  ngOnInit() {
    void this.api.obtenerAreas().then((areas) => (this.areas = areas)).catch(() => (this.areas = []));
    this.subs.add(
      this.route.paramMap.subscribe((params) => {
        this.casoId = Number(params.get('id'));
        this.sincronizarPestana();
        void this.cargar();
      }),
    );
    this.subs.add(
      this.router.events.pipe(filter((evento) => evento instanceof NavigationEnd)).subscribe(() => {
        this.sincronizarPestana();
      }),
    );
  }

  ngOnDestroy() {
    this.subs.unsubscribe();
  }

  valor(event: Event) {
    return (event.target as HTMLInputElement).value;
  }

  archivos(event: Event) {
    return (event.target as HTMLInputElement).files;
  }

  cambiarPestana(siguiente: Pestana) {
    this.pestana = siguiente;
    const destino = siguiente === 'seguimiento' ? `/casos/${this.casoId}/seguimiento` : `/casos/${this.casoId}`;
    if (this.router.url !== destino) void this.router.navigateByUrl(destino);
  }

  actualizarCaso(caso: CasoDetalle) {
    this.caso = caso;
  }

  async cargar() {
    if (!Number.isFinite(this.casoId)) {
      this.error = 'Caso no encontrado.';
      this.cargando = false;
      return;
    }
    this.cargando = true;
    this.error = '';
    try {
      const detalle = await this.api.obtenerCaso(this.casoId);
      this.caso = detalle;
      this.nombreEdit = detalle.nombreCiudadano || '';
      this.emailEdit = detalle.emailCiudadano || '';
      this.telefonoEdit = detalle.telefono || '';
      this.areaEdit = detalle.area;
      this.prioridadEdit = detalle.prioridad || 'MEDIA';
      this.descripcionEdit = detalle.descripcion;
      this.denunciadoEdit = detalle.denunciado || '';
      this.editando = false;
      this.motivoEdit = '';
      if (!detalle.transicionesPermitidas.includes(this.nuevoEstado)) {
        this.nuevoEstado = detalle.transicionesPermitidas[0] || '';
      }
      if (this.puedeAsignar) {
        const lista = await this.api.listarAgentes(detalle.area);
        this.agentes = lista;
        this.agenteId = this.agenteId || String(lista[0]?.id || '');
      }
    } catch (err) {
      const apiError = err as ApiError;
      this.error = apiError.conexion
        ? 'Error al gestionar el caso. Verifique su conexión e intente nuevamente.'
        : apiError.message;
      this.caso = null;
    } finally {
      this.cargando = false;
    }
  }

  async ejecutar() {
    if (!this.caso || !this.confirmar) return;
    this.enviando = true;
    this.error = '';
    this.exito = '';
    try {
      let respuesta;
      if (this.confirmar === 'asignar') respuesta = await this.api.asignarCaso(this.caso.id, Number(this.agenteId));
      else if (this.confirmar === 'reasignar') {
        respuesta = await this.api.reasignarCaso(this.caso.id, Number(this.agenteId), this.motivo);
      } else if (this.confirmar === 'estado') {
        respuesta = await this.api.cambiarEstadoCaso(this.caso.id, this.nuevoEstado, this.observacionEstado);
      } else if (this.confirmar === 'prorroga') {
        respuesta = await this.api.registrarProrroga(this.caso.id, Number(this.diasProrroga), this.justificacionProrroga);
        this.justificacionProrroga = '';
      } else if (this.confirmar === 'escalar') {
        respuesta = await this.api.escalarCaso(this.caso.id, this.motivoEscalar);
        this.motivoEscalar = '';
      } else if (this.confirmar === 'cerrar') {
        respuesta = await this.api.cerrarCaso(this.caso.id, this.observacionCierre);
        this.observacionCierre = '';
      } else {
        respuesta = await this.api.anularCaso(this.caso.id, this.justificacion);
      }
      this.caso = respuesta.caso;
      this.exito = respuesta.mensaje;
      this.confirmar = null;
      this.motivo = '';
      this.observacionEstado = '';
      this.justificacion = '';
    } catch (err) {
      const apiError = err as ApiError;
      this.error = apiError.conexion
        ? 'Error al gestionar el caso. Verifique su conexión e intente nuevamente.'
        : apiError.message;
    } finally {
      this.enviando = false;
    }
  }

  async guardarNota() {
    if (!this.caso) return;
    this.enviando = true;
    this.error = '';
    this.exito = '';
    try {
      const respuesta = await this.api.agregarObservacionCaso(this.caso.id, this.nota);
      this.caso = respuesta.caso;
      this.exito = respuesta.mensaje;
      this.nota = '';
    } catch (err) {
      this.error = (err as ApiError).message;
    } finally {
      this.enviando = false;
    }
  }

  async guardarModificacion(event: Event) {
    event.preventDefault();
    if (!this.caso) return;
    this.enviando = true;
    this.error = '';
    this.exito = '';
    try {
      const respuesta = await this.api.modificarCaso(this.caso.id, {
        nombreCiudadano: this.caso.esAnonimo ? undefined : this.nombreEdit,
        email: this.emailEdit,
        telefono: this.telefonoEdit,
        areaDependencia: this.usuario?.rol === 'AGENTE' ? undefined : this.areaEdit,
        descripcion: this.descripcionEdit,
        denunciado: this.denunciadoEdit,
        prioridad: this.prioridadEdit,
        motivo: this.motivoEdit,
      });
      this.caso = respuesta.caso;
      this.exito = respuesta.mensaje;
      this.editando = false;
      this.motivoEdit = '';
    } catch (err) {
      this.error = (err as ApiError).message;
    } finally {
      this.enviando = false;
    }
  }

  async subirDocumentos(event: Event) {
    const input = event.target as HTMLInputElement;
    const lista = input.files;
    input.value = '';
    if (!this.caso || !lista || lista.length === 0 || this.finalizado) return;
    this.subiendoDoc = true;
    this.avisoDocs = '';
    this.error = '';
    try {
      const respuesta = await this.api.adjuntarDocumentosInterno(this.caso.id, Array.from(lista));
      const documentos = await this.api.listarDocumentosCaso(this.casoId);
      this.caso = { ...this.caso, documentos };
      if (respuesta.rechazados.length > 0) {
        this.avisoDocs = `Algunos archivos no se adjuntaron: ${respuesta.rechazados
          .map((item) => `${item.nombre} (${item.motivo})`)
          .join(', ')}.`;
      } else if (respuesta.archivosSubidos.length > 0) {
        this.exito = 'Documento cargado y asociado al caso.';
      }
    } catch (err) {
      this.error = (err as ApiError).message || 'No fue posible almacenar el documento.';
    } finally {
      this.subiendoDoc = false;
    }
  }

  async descargar(docId: number, nombre: string) {
    if (!this.caso) return;
    try {
      await this.api.descargarDocumentoCaso(this.caso.id, docId, nombre);
    } catch (err) {
      this.error = (err as Error).message;
    }
  }

  private sincronizarPestana() {
    if (this.router.url.endsWith('/seguimiento')) this.pestana = 'seguimiento';
    else if (this.pestana === 'seguimiento') this.pestana = 'resumen';
  }
}
