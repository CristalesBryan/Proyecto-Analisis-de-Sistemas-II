import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { formatFechaHora } from '../core/fechas';
import { ApiError, EventoBitacora, UsuarioAdmin } from '../core/models';
import { InternalLayoutComponent } from '../shared/internal-layout.component';

function fechaLocal(fecha = new Date()) {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

@Component({
  selector: 'app-bitacoras',
  imports: [InternalLayoutComponent, FormsModule],
  templateUrl: './bitacoras.component.html',
})
export class BitacorasComponent implements OnInit {
  readonly formatFechaHora = formatFechaHora;
  readonly dominios = [
    { valor: '', nombre: 'Todos' },
    { valor: 'ACCESO', nombre: 'Accesos' },
    { valor: 'PUBLICO', nombre: 'Consultas públicas' },
    { valor: 'CASO', nombre: 'Casos' },
    { valor: 'DOCUMENTO', nombre: 'Documentos' },
    { valor: 'USUARIO', nombre: 'Usuarios' },
    { valor: 'REPORTE', nombre: 'Reportes' },
    { valor: 'NOTIFICACION', nombre: 'Notificaciones' },
    { valor: 'AUDITORIA', nombre: 'Auditoría' },
    { valor: 'PARAMETROS', nombre: 'Parámetros' },
  ];

  desde = fechaLocal();
  hasta = fechaLocal();
  dominio = '';
  tipo = '';
  usuarioId = '';
  caso = '';
  ip = '';
  resultado = '';
  pagina = 0;
  totalPaginas = 0;
  total = 0;
  mensaje = '';
  error = '';
  cargando = false;
  exportando = false;
  eventos: EventoBitacora[] = [];
  detalle: EventoBitacora | null = null;
  usuarios: UsuarioAdmin[] = [];

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
    private readonly route: ActivatedRoute,
  ) {}

  ngOnInit() {
    const caso = this.route.snapshot.queryParamMap.get('caso');
    if (caso) this.caso = caso;
    void this.cargarUsuarios();
    void this.buscar(0);
  }

  get yo() {
    return this.auth.getUser()?.id ?? '';
  }

  async buscar(pagina = 0) {
    this.cargando = true;
    this.error = '';
    this.mensaje = '';
    this.detalle = null;
    this.pagina = pagina;
    try {
      const resultado = await this.api.listarBitacoras({
        desde: this.desde,
        hasta: this.hasta,
        dominio: this.dominio,
        tipo: this.tipo.trim(),
        usuarioId: this.usuarioId,
        caso: this.caso.trim(),
        ip: this.ip.trim(),
        resultado: this.resultado.trim(),
        page: pagina,
      });
      this.eventos = resultado.content;
      this.total = resultado.totalElements;
      this.totalPaginas = resultado.totalPages;
      this.mensaje = resultado.mensaje || '';
    } catch (error) {
      this.eventos = [];
      this.total = 0;
      this.totalPaginas = 0;
      this.error = error instanceof Error ? error.message : 'No fue posible consultar la bitácora.';
    } finally {
      this.cargando = false;
    }
  }

  async abrir(evento: EventoBitacora) {
    this.error = '';
    try {
      this.detalle = await this.api.detalleBitacora(evento.clave);
    } catch (error) {
      this.detalle = null;
      this.error = error instanceof Error ? error.message : 'No fue posible abrir el evento.';
    }
  }

  async exportar(formato: 'PDF' | 'XLSX') {
    this.exportando = true;
    this.error = '';
    try {
      await this.api.exportarBitacora(
        {
          desde: this.desde,
          hasta: this.hasta,
          dominio: this.dominio || undefined,
          tipo: this.tipo.trim() || undefined,
          usuarioId: this.usuarioId ? Number(this.usuarioId) : null,
          caso: this.caso.trim() || undefined,
          ip: this.ip.trim() || undefined,
          resultado: this.resultado.trim() || undefined,
        },
        formato,
      );
    } catch (error) {
      const api = error as ApiError;
      this.error = api.message || 'No fue posible exportar la bitácora.';
    } finally {
      this.exportando = false;
    }
  }

  private async cargarUsuarios() {
    try {
      const pagina = await this.api.listarUsuarios({ page: 0, size: 100 });
      this.usuarios = pagina.content;
    } catch {
      this.usuarios = [];
    }
  }
}
