import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { etiquetaEstado } from '../core/etiquetas';
import { formatFecha, formatFechaHora } from '../core/fechas';
import { AgenteOpcion, ApiError, AreaDependencia, CasoDetalle, EventoHistorial, VistaCiudadano } from '../core/models';
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
  notificarReasignacion = true;
  actualizarAreaCaso = false;
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
  arrastrando = false;
  avisoDocs = '';
  justificacionCarga = '';
  justificacionBaja = '';
  documentoBaja: number | null = null;
  colaDocs: { nombre: string; estado: 'cargando' | 'listo' | 'error'; motivo: string }[] = [];
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
  readonly filtrosHistorial = [
    { id: '', label: 'Todos' },
    { id: 'ESTADOS', label: 'Estados' },
    { id: 'SEGUIMIENTOS', label: 'Seguimientos' },
    { id: 'DOCUMENTOS', label: 'Documentos' },
    { id: 'ASIGNACIONES', label: 'Asignaciones' },
    { id: 'RESOLUCION', label: 'Resolución' },
  ];
  filtroHistorial = '';
  buscaHistorial = '';
  eventosHistorial: EventoHistorial[] = [];
  totalHistorial = 0;
  paginaHistorial = 0;
  avisoHistorial = '';
  cargandoHistorial = false;
  eventoAbierto: number | null = null;
  vistaCiudadano: VistaCiudadano | null = null;
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

  get puedeReasignar() {
    return (
      this.puedeAsignar &&
      !!this.caso?.agenteAsignadoId &&
      this.caso.estado !== 'CERRADO' &&
      this.caso.estado !== 'ANULADO'
    );
  }

  get agentesParaReasignar() {
    return this.agentes.filter((agente) => agente.id !== this.caso?.agenteAsignadoId);
  }

  get agenteDestino() {
    return this.agentes.find((agente) => String(agente.id) === this.agenteId);
  }

  get reasignacionLista() {
    const largo = this.motivo.trim().length;
    return !!this.agenteDestino && largo >= 50 && largo <= 1000;
  }

  get puedeAnular() {
    return this.usuario?.rol === 'ADMIN';
  }

  get puedeCerrar() {
    return this.usuario?.permisos?.includes('CASOS_CERRAR') === true;
  }

  get puedePlazo() {
    return this.usuario?.rol === 'ADMIN' || this.usuario?.rol === 'SUPERVISOR';
  }

  get bandeja() {
    return this.usuario ? this.auth.destinosPorRol[this.usuario.rol] : '/';
  }

  get rutaBitacora() {
    return this.usuario?.rol === 'ADMIN' ? '/admin/bitacoras' : '/supervisor/bitacoras';
  }

  get finalizado() {
    return this.caso?.estado === 'RESUELTO' || this.caso?.estado === 'CERRADO' || this.caso?.estado === 'ANULADO';
  }

  get puedeAdjuntar() {
    if (!this.caso) return false;
    if (this.caso.estado === 'CERRADO' || this.caso.estado === 'ANULADO') {
      return this.usuario?.rol === 'ADMIN';
    }
    return true;
  }

  get exigeJustificacionCarga() {
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
    if (this.confirmar === 'reasignar') {
      const extracto = this.motivo.trim().slice(0, 140);
      const destino = this.agenteDestino?.nombre || 'el agente seleccionado';
      return `Reasignar ${codigo} de ${this.caso.agenteNombre} a ${destino}. El estado no cambia. Justificación: ${extracto}`;
    }
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

  marcado(event: Event) {
    return (event.target as HTMLInputElement).checked;
  }

  get prorrogaLista() {
    const dias = Number(this.diasProrroga);
    return Number.isInteger(dias) && dias >= 1 && dias <= 15 && this.justificacionProrroga.trim().length >= 20;
  }

  prepararCambioEstado() {
    this.error = '';
    this.sincronizarNuevoEstado();
    this.confirmar = 'estado';
  }

  prepararProrroga() {
    this.error = '';
    if (!this.prorrogaLista) {
      this.error = this.justificacionProrroga.trim().length < 20
        ? 'La justificación debe tener al menos 20 caracteres.'
        : 'La prórroga debe ser de 1 a 15 días hábiles.';
      return;
    }
    this.confirmar = 'prorroga';
  }

  private sincronizarNuevoEstado() {
    const opciones = this.transicionesVisibles;
    if (!opciones.includes(this.nuevoEstado)) {
      this.nuevoEstado = opciones[0] || '';
    }
  }

  archivos(event: Event) {
    return (event.target as HTMLInputElement).files;
  }

  cambiarPestana(siguiente: Pestana) {
    this.pestana = siguiente;
    const destino = siguiente === 'seguimiento' ? `/casos/${this.casoId}/seguimiento` : `/casos/${this.casoId}`;
    if (this.router.url !== destino) void this.router.navigateByUrl(destino);
    if (siguiente === 'historial') void this.cargarHistorial(0);
  }

  async cargarHistorial(pagina = 0) {
    if (!Number.isFinite(this.casoId)) return;
    this.cargandoHistorial = true;
    this.avisoHistorial = '';
    this.paginaHistorial = pagina;
    try {
      const resultado = await this.api.consultarHistorial(
        this.casoId,
        this.filtroHistorial,
        this.buscaHistorial,
        pagina,
      );
      this.eventosHistorial = resultado.eventos;
      this.totalHistorial = resultado.total;
      this.avisoHistorial = resultado.aviso || '';
      this.eventoAbierto = null;
    } catch (error) {
      this.eventosHistorial = [];
      this.totalHistorial = 0;
      this.error = error instanceof Error ? error.message : 'No fue posible consultar el historial.';
    } finally {
      this.cargandoHistorial = false;
    }
  }

  elegirFiltro(categoria: string) {
    this.filtroHistorial = categoria;
    void this.cargarHistorial(0);
  }

  abrirEvento(id: number) {
    this.eventoAbierto = this.eventoAbierto === id ? null : id;
  }

  async exportarHistorial() {
    this.error = '';
    try {
      await this.api.exportarHistorial(this.casoId, this.filtroHistorial, this.buscaHistorial);
      this.exito = 'El historial se exportó para uso interno.';
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'No fue posible exportar el historial.';
    }
  }

  async verVistaCiudadano() {
    this.error = '';
    if (this.vistaCiudadano) {
      this.vistaCiudadano = null;
      return;
    }
    try {
      this.vistaCiudadano = await this.api.vistaCiudadano(this.casoId);
    } catch (error) {
      this.vistaCiudadano = null;
      this.error = error instanceof Error ? error.message : 'No fue posible abrir la vista ciudadana.';
    }
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
      this.sincronizarNuevoEstado();
      if (this.puedeAsignar) {
        const lista = await this.api.listarAgentes(this.usuario?.rol === 'ADMIN' ? undefined : detalle.area);
        this.agentes = lista;
        const disponibles = lista.filter((agente) => agente.id !== detalle.agenteAsignadoId);
        this.agenteId = String(disponibles[0]?.id || lista[0]?.id || '');
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
        respuesta = await this.api.reasignarCaso(
          this.caso.id,
          Number(this.agenteId),
          this.motivo,
          this.notificarReasignacion,
          this.actualizarAreaCaso,
        );
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
      const accion = this.confirmar;
      this.caso = respuesta.caso;
      this.sincronizarNuevoEstado();
      this.exito = respuesta.mensaje;
      this.confirmar = null;
      this.motivo = '';
      this.actualizarAreaCaso = false;
      if ((accion === 'asignar' || accion === 'reasignar') && this.puedeAsignar && this.caso) {
        this.agentes = await this.api.listarAgentes(this.usuario?.rol === 'ADMIN' ? undefined : this.caso.area);
      }
      if (this.puedeAsignar && this.caso) {
        const disponibles = this.agentes.filter((agente) => agente.id !== this.caso?.agenteAsignadoId);
        this.agenteId = String(disponibles[0]?.id || '');
      }
      if (this.pestana === 'historial') {
        await this.cargarHistorial(0);
      }
      this.observacionEstado = '';
      this.justificacion = '';
    } catch (err) {
      const apiError = err as ApiError;
      const detalle = apiError.errores ? Object.values(apiError.errores).find((texto) => texto.trim()) : '';
      this.error = apiError.conexion
        ? 'Error al gestionar el caso. Verifique su conexión e intente nuevamente.'
        : detalle || apiError.message;
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

  prepararSoltar(event: DragEvent) {
    event.preventDefault();
    this.arrastrando = true;
  }

  salirSoltar(event: DragEvent) {
    event.preventDefault();
    this.arrastrando = false;
  }

  soltarDocumentos(event: DragEvent) {
    event.preventDefault();
    this.arrastrando = false;
    const lista = event.dataTransfer?.files;
    if (lista) void this.cargarLista(Array.from(lista));
  }

  async subirDocumentos(event: Event) {
    const input = event.target as HTMLInputElement;
    const lista = input.files ? Array.from(input.files) : [];
    input.value = '';
    if (lista.length === 0) return;
    await this.cargarLista(lista);
  }

  private async cargarLista(archivos: File[]) {
    if (!this.caso || archivos.length === 0 || !this.puedeAdjuntar) return;
    if (this.exigeJustificacionCarga && this.justificacionCarga.trim().length < 10) {
      this.avisoDocs = 'Indique una justificación de al menos 10 caracteres para autorizar la carga.';
      return;
    }
    this.subiendoDoc = true;
    this.avisoDocs = '';
    this.error = '';
    this.exito = '';
    this.colaDocs = archivos.map((archivo) => ({ nombre: archivo.name, estado: 'cargando', motivo: '' }));
    let cargados = 0;
    try {
      for (let i = 0; i < archivos.length; i += 1) {
        try {
          const respuesta = await this.api.adjuntarDocumentosInterno(
            this.caso.id,
            [archivos[i]],
            this.exigeJustificacionCarga ? this.justificacionCarga.trim() : undefined,
          );
          if (respuesta.rechazados.length > 0) {
            this.colaDocs[i] = {
              nombre: archivos[i].name,
              estado: 'error',
              motivo: respuesta.rechazados[0].motivo,
            };
          } else {
            this.colaDocs[i] = { nombre: archivos[i].name, estado: 'listo', motivo: '' };
            cargados += 1;
          }
        } catch (err) {
          this.colaDocs[i] = {
            nombre: archivos[i].name,
            estado: 'error',
            motivo: (err as ApiError).message || 'No fue posible almacenar el archivo.',
          };
        }
      }
      const documentos = await this.api.listarDocumentosCaso(this.casoId);
      this.caso = { ...this.caso, documentos };
      if (cargados > 0) {
        this.exito = 'Documento cargado y asociado al caso.';
        this.justificacionCarga = '';
      }
    } finally {
      this.subiendoDoc = false;
    }
  }

  abrirBaja(docId: number) {
    this.documentoBaja = this.documentoBaja === docId ? null : docId;
    this.justificacionBaja = '';
  }

  async confirmarBaja(docId: number) {
    if (!this.caso || this.justificacionBaja.trim().length < 10) return;
    this.error = '';
    try {
      const respuesta = await this.api.darBajaDocumento(this.caso.id, docId, this.justificacionBaja.trim());
      const documentos = await this.api.listarDocumentosCaso(this.casoId);
      this.caso = { ...this.caso, documentos };
      this.exito = respuesta.mensaje;
      this.documentoBaja = null;
      this.justificacionBaja = '';
    } catch (err) {
      this.error = (err as ApiError).message;
    }
  }

  formatoTamano(bytes: number) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  etiquetaTipo(mime: string, nombre: string) {
    if (mime.includes('pdf') || nombre.toLowerCase().endsWith('.pdf')) return 'PDF';
    if (mime.includes('png') || nombre.toLowerCase().endsWith('.png')) return 'PNG';
    if (mime.includes('word') || nombre.toLowerCase().endsWith('.docx')) return 'DOCX';
    if (mime.includes('jpeg') || /\.jpe?g$/i.test(nombre)) return 'JPG';
    return mime;
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
