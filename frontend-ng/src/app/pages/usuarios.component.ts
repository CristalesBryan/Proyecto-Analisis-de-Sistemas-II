import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { formatFechaHora } from '../core/fechas';
import { ApiError, AreaAdmin, UsuarioAdmin } from '../core/models';
import { InternalLayoutComponent } from '../shared/internal-layout.component';

type Confirmacion = {
  titulo: string;
  texto: string;
  accion: () => Promise<void>;
};

@Component({
  selector: 'app-usuarios',
  imports: [InternalLayoutComponent, FormsModule],
  templateUrl: './usuarios.component.html',
})
export class UsuariosComponent implements OnInit {
  usuarios: UsuarioAdmin[] = [];
  areas: AreaAdmin[] = [];
  total = 0;
  paginas = 0;
  pagina = 0;
  readonly tamano = 20;
  consulta = '';
  rol = '';
  area = '';
  activo = '';
  vista: 'lista' | 'ficha' = 'lista';
  modo: 'alta' | 'edicion' | 'ver' = 'alta';
  usuarioId: number | null = null;
  nombre = '';
  email = '';
  rolFormulario = 'AGENTE';
  areaId = '';
  activoFormulario = true;
  password = '';
  generarPassword = false;
  errores: Record<string, string> = {};
  mensaje = '';
  temporal = '';
  error = '';
  cargando = false;
  confirmacion: Confirmacion | null = null;
  readonly formatFechaHora = formatFechaHora;
  readonly roles = [
    { valor: 'ADMIN', etiqueta: 'Administrador' },
    { valor: 'SUPERVISOR', etiqueta: 'Supervisor' },
    { valor: 'AGENTE', etiqueta: 'Agente' },
  ];

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService,
  ) {}

  ngOnInit() {
    void this.iniciar();
  }

  get rolSesion() {
    return this.auth.getUser()?.rol;
  }

  get esSupervisor() {
    return this.rolSesion === 'SUPERVISOR';
  }

  get areaObligatoria() {
    return this.rolFormulario === 'SUPERVISOR' || this.rolFormulario === 'AGENTE';
  }

  async iniciar() {
    this.cargando = true;
    this.error = '';
    try {
      this.areas = await this.api.areasAdministracion();
      if (this.esSupervisor && this.areas[0]) {
        this.areaId = String(this.areas[0].id);
        this.rolFormulario = 'AGENTE';
      }
      await this.cargar();
    } catch (error) {
      this.error = this.mensajeDe(error);
    } finally {
      this.cargando = false;
    }
  }

  async cargar(pagina = this.pagina) {
    this.cargando = true;
    this.error = '';
    try {
      const resultado = await this.api.listarUsuarios({
        q: this.consulta.trim(),
        rol: this.esSupervisor ? 'AGENTE' : this.rol,
        area: this.esSupervisor ? '' : this.area,
        activo: this.activo,
        page: pagina,
        size: this.tamano,
      });
      this.usuarios = resultado.content;
      this.total = resultado.totalElements;
      this.paginas = resultado.totalPages;
      this.pagina = resultado.page;
    } catch (error) {
      this.error = this.mensajeDe(error);
    } finally {
      this.cargando = false;
    }
  }

  buscar() {
    void this.cargar(0);
  }

  limpiarFiltros() {
    this.consulta = '';
    this.rol = '';
    this.area = '';
    this.activo = '';
    void this.cargar(0);
  }

  nuevo() {
    this.vista = 'ficha';
    this.modo = 'alta';
    this.usuarioId = null;
    this.nombre = '';
    this.email = '';
    this.rolFormulario = 'AGENTE';
    this.areaId = this.esSupervisor && this.areas[0] ? String(this.areas[0].id) : '';
    this.activoFormulario = true;
    this.password = '';
    this.generarPassword = false;
    this.errores = {};
    this.mensaje = '';
    this.temporal = '';
    this.error = '';
  }

  ver(usuario: UsuarioAdmin) {
    this.llenar(usuario, 'ver');
  }

  editar(usuario: UsuarioAdmin) {
    this.llenar(usuario, 'edicion');
  }

  volver() {
    this.vista = 'lista';
    this.temporal = '';
    void this.cargar();
  }

  async guardar() {
    this.errores = this.validarLocal();
    if (Object.keys(this.errores).length > 0) return;
    if (this.modo === 'edicion' && this.rolOriginal && this.rolOriginal !== this.rolFormulario) {
      this.confirmacion = {
        titulo: 'Cambiar rol',
        texto: 'El cambio de rol modifica el acceso del usuario y cierra su sesión actual.',
        accion: () => this.enviar(),
      };
      return;
    }
    await this.enviar();
  }

  pedirEstado(usuario: UsuarioAdmin, activo: boolean) {
    this.confirmacion = {
      titulo: activo ? 'Reactivar usuario' : 'Desactivar usuario',
      texto: activo
        ? `${usuario.nombre} podrá iniciar sesión de nuevo.`
        : `${usuario.nombre} no podrá iniciar sesión. Los casos asignados se conservan.`,
      accion: () => this.cambiarEstado(usuario, activo),
    };
  }

  pedirReset(usuario: UsuarioAdmin) {
    this.confirmacion = {
      titulo: 'Restablecer contraseña',
      texto: `Se generará una contraseña temporal para ${usuario.nombre}. Deberá cambiarla en el siguiente ingreso.`,
      accion: () => this.restablecer(usuario),
    };
  }

  async confirmar() {
    const pendiente = this.confirmacion;
    this.confirmacion = null;
    if (!pendiente) return;
    await pendiente.accion();
  }

  cancelarConfirmacion() {
    this.confirmacion = null;
  }

  etiquetaRol(rol: string) {
    return this.roles.find((item) => item.valor === rol)?.etiqueta ?? rol;
  }

  private rolOriginal = '';

  private llenar(usuario: UsuarioAdmin, modo: 'ver' | 'edicion') {
    this.vista = 'ficha';
    this.modo = modo;
    this.usuarioId = usuario.id;
    this.nombre = usuario.nombre;
    this.email = usuario.email;
    this.rolFormulario = usuario.rol;
    this.rolOriginal = usuario.rol;
    this.areaId = usuario.areaId == null ? '' : String(usuario.areaId);
    this.activoFormulario = usuario.activo;
    this.password = '';
    this.generarPassword = false;
    this.errores = {};
    this.mensaje = '';
    this.temporal = '';
    this.error = '';
  }

  private validarLocal() {
    const errores: Record<string, string> = {};
    const nombre = this.nombre.trim();
    if (nombre.length < 2 || nombre.length > 100) {
      errores['nombre'] = 'El nombre debe tener entre 2 y 100 caracteres.';
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email.trim())) {
      errores['email'] = 'Ingrese un correo electrónico válido.';
    }
    if (this.areaObligatoria && !this.areaId) {
      errores['areaId'] = 'El área es obligatoria para este rol.';
    }
    if (this.modo === 'alta' && !this.generarPassword && !this.claveValida(this.password)) {
      errores['password'] = 'La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula y un dígito.';
    }
    return errores;
  }

  private claveValida(valor: string) {
    return valor.length >= 8 && /[A-Z]/.test(valor) && /[a-z]/.test(valor) && /\d/.test(valor);
  }

  private async enviar() {
    this.cargando = true;
    this.error = '';
    this.mensaje = '';
    this.temporal = '';
    try {
      const payload = {
        nombre: this.nombre.trim(),
        email: this.email.trim(),
        rol: this.rolFormulario,
        areaId: this.areaId ? Number(this.areaId) : null,
        activo: this.activoFormulario,
      };
      const respuesta =
        this.modo === 'alta'
          ? await this.api.crearUsuario({
              ...payload,
              password: this.generarPassword ? undefined : this.password,
              generarPassword: this.generarPassword,
            })
          : await this.api.actualizarUsuario(this.usuarioId!, payload);
      this.mensaje = respuesta.mensaje;
      this.temporal = respuesta.contrasenaTemporal ?? '';
      this.vista = 'lista';
      await this.cargar(this.modo === 'alta' ? 0 : this.pagina);
    } catch (error) {
      const apiError = error as ApiError;
      this.errores = apiError.errores ?? {};
      if (!apiError.errores) this.error = this.mensajeDe(error);
    } finally {
      this.cargando = false;
    }
  }

  private async cambiarEstado(usuario: UsuarioAdmin, activo: boolean) {
    this.cargando = true;
    this.error = '';
    try {
      const respuesta = await this.api.actualizarUsuario(usuario.id, {
        nombre: usuario.nombre,
        email: usuario.email,
        rol: usuario.rol,
        areaId: usuario.areaId,
        activo,
      });
      this.mensaje = respuesta.mensaje;
      await this.cargar();
    } catch (error) {
      this.error = this.mensajeDe(error);
    } finally {
      this.cargando = false;
    }
  }

  private async restablecer(usuario: UsuarioAdmin) {
    this.cargando = true;
    this.error = '';
    try {
      const respuesta = await this.api.restablecerContrasena(usuario.id);
      this.mensaje = respuesta.mensaje;
      this.temporal = respuesta.contrasenaTemporal ?? '';
      await this.cargar();
    } catch (error) {
      this.error = this.mensajeDe(error);
    } finally {
      this.cargando = false;
    }
  }

  private mensajeDe(error: unknown) {
    return error instanceof Error ? error.message : 'No fue posible completar la operación. Intente nuevamente.';
  }
}
