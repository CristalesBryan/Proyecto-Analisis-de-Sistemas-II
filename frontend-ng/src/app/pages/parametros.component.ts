import { Component, OnInit } from '@angular/core';
import { ApiService } from '../core/api.service';
import { ApiError, AreaParametro, ConfiguracionParametros, ParametroItem } from '../core/models';
import { InternalLayoutComponent } from '../shared/internal-layout.component';

type Confirmacion =
  | { tipo: 'parametro'; item: ParametroItem; nuevo: string }
  | { tipo: 'restaurar'; grupo: string; nombre: string }
  | { tipo: 'recalcular'; clave: string; etiqueta: string; dias: string }
  | { tipo: 'area'; area: AreaParametro }
  | { tipo: 'eliminar-area'; area: AreaParametro }
  | { tipo: 'feriado'; fecha: string; descripcion: string }
  | { tipo: 'eliminar-feriado'; fecha: string };

@Component({
  selector: 'app-parametros',
  imports: [InternalLayoutComponent],
  templateUrl: './parametros.component.html',
})
export class ParametrosComponent implements OnInit {
  configuracion: ConfiguracionParametros | null = null;
  grupo = 'plazos';
  borradores: Record<string, string> = {};
  codigoArea = '';
  nombreArea = '';
  fechaFeriado = '';
  descripcionFeriado = '';
  confirmacion: Confirmacion | null = null;
  claveInvalida = '';
  error = '';
  exito = '';
  advertencia = '';
  cargando = true;
  enviando = false;

  constructor(private readonly api: ApiService) {}

  ngOnInit() {
    void this.cargar();
  }

  get puedeEditar() {
    return this.configuracion?.puedeEditar === true;
  }

  get grupoActual() {
    return this.configuracion?.grupos.find((item) => item.codigo === this.grupo) ?? null;
  }

  async cargar() {
    this.cargando = true;
    this.error = '';
    try {
      this.configuracion = await this.api.consultarParametros();
      this.borradores = {};
      for (const grupo of this.configuracion.grupos) {
        for (const item of grupo.parametros) this.borradores[item.clave] = item.valor;
      }
      if (!this.grupoActual && this.configuracion.grupos[0]) this.grupo = this.configuracion.grupos[0].codigo;
    } catch (err) {
      this.error = (err as ApiError).message;
    } finally {
      this.cargando = false;
    }
  }

  valor(event: Event) {
    return (event.target as HTMLInputElement).value;
  }

  pedirParametro(item: ParametroItem) {
    const nuevo = (this.borradores[item.clave] || '').trim();
    this.claveInvalida = '';
    this.error = '';
    if (nuevo === item.valor) return;
    this.confirmacion = { tipo: 'parametro', item, nuevo };
  }

  pedirRestaurar() {
    if (!this.grupoActual) return;
    this.confirmacion = { tipo: 'restaurar', grupo: this.grupoActual.codigo, nombre: this.grupoActual.nombre };
  }

  pedirRecalcular(item: ParametroItem) {
    this.confirmacion = {
      tipo: 'recalcular',
      clave: item.clave,
      etiqueta: item.etiqueta,
      dias: this.borradores[item.clave] || item.valor,
    };
  }

  pedirArea(area: AreaParametro) {
    this.confirmacion = { tipo: 'area', area: { ...area } };
  }

  pedirEliminarArea(area: AreaParametro) {
    this.confirmacion = { tipo: 'eliminar-area', area };
  }

  pedirFeriado() {
    this.error = '';
    if (!this.fechaFeriado || this.descripcionFeriado.trim().length < 3) {
      this.error = 'Indique la fecha y una descripción de al menos 3 caracteres.';
      return;
    }
    this.confirmacion = {
      tipo: 'feriado',
      fecha: this.fechaFeriado,
      descripcion: this.descripcionFeriado.trim(),
    };
  }

  textoConfirmacion() {
    const pendiente = this.confirmacion;
    if (!pendiente) return '';
    if (pendiente.tipo === 'parametro') {
      return `${pendiente.item.etiqueta}: ${pendiente.item.valor} → ${pendiente.nuevo}. El cambio queda en la bitácora.`;
    }
    if (pendiente.tipo === 'restaurar') {
      return `Restaurar ${pendiente.nombre} a los valores iniciales. El cambio queda en la bitácora.`;
    }
    if (pendiente.tipo === 'recalcular') {
      return `Recalcular los casos abiertos de ${pendiente.etiqueta} con ${pendiente.dias} días hábiles. Los plazos ya guardados de esos casos se reemplazan.`;
    }
    if (pendiente.tipo === 'area') {
      const impacto = pendiente.area.casos + pendiente.area.usuarios > 0 && !pendiente.area.activo
        ? ' El área tiene casos o usuarios: quedará inactiva y no recibirá casos nuevos.'
        : '';
      return `${pendiente.area.codigo}: ${pendiente.area.nombre}. Activa: ${pendiente.area.activo ? 'sí' : 'no'}.${impacto}`;
    }
    if (pendiente.tipo === 'eliminar-area') {
      return `Eliminar el área ${pendiente.area.codigo}. No tiene casos ni usuarios.`;
    }
    if (pendiente.tipo === 'feriado') {
      return `Agregar ${pendiente.fecha} como día no laborable. Los plazos ya calculados no cambian.`;
    }
    return `Quitar el día no laborable ${pendiente.fecha}.`;
  }

  async confirmar() {
    const pendiente = this.confirmacion;
    if (!pendiente) return;
    this.enviando = true;
    this.error = '';
    this.exito = '';
    this.advertencia = '';
    this.claveInvalida = '';
    try {
      if (pendiente.tipo === 'parametro') {
        const respuesta = await this.api.guardarParametro(pendiente.item.clave, pendiente.nuevo, pendiente.item.version);
        this.exito = respuesta.mensaje;
      } else if (pendiente.tipo === 'restaurar') {
        const respuesta = await this.api.restaurarParametros(pendiente.grupo);
        this.exito = respuesta.mensaje;
      } else if (pendiente.tipo === 'recalcular') {
        const tipo = pendiente.clave.split('.')[1] || 'Q';
        const respuesta = await this.api.recalcularPlazos(tipo);
        this.exito = respuesta.mensaje;
      } else if (pendiente.tipo === 'area') {
        const respuesta = await this.api.actualizarAreaParametro(pendiente.area);
        this.exito = respuesta.mensaje;
        this.advertencia = respuesta.advertencia || '';
      } else if (pendiente.tipo === 'eliminar-area') {
        await this.api.eliminarAreaParametro(pendiente.area.id);
        this.exito = 'El área se eliminó.';
      } else if (pendiente.tipo === 'feriado') {
        await this.api.crearFeriado(pendiente.fecha, pendiente.descripcion);
        this.fechaFeriado = '';
        this.descripcionFeriado = '';
        this.exito = 'El día no laborable quedó registrado.';
      } else {
        await this.api.eliminarFeriado(pendiente.fecha);
        this.exito = 'El día no laborable se quitó.';
      }
      this.confirmacion = null;
      await this.cargar();
    } catch (err) {
      const api = err as ApiError;
      this.error = api.errores?.['valor'] || api.errores?.['codigo'] || api.errores?.['fecha'] || api.message;
      if (pendiente.tipo === 'parametro') this.claveInvalida = pendiente.item.clave;
    } finally {
      this.enviando = false;
    }
  }

  async crearArea() {
    this.error = '';
    try {
      const respuesta = await this.api.crearAreaParametro(this.codigoArea, this.nombreArea);
      this.codigoArea = '';
      this.nombreArea = '';
      this.exito = respuesta.mensaje;
      await this.cargar();
    } catch (err) {
      const api = err as ApiError;
      this.error = api.errores?.['codigo'] || api.errores?.['nombre'] || api.message;
    }
  }

  async importar(event: Event) {
    const archivo = (event.target as HTMLInputElement).files?.[0];
    if (!archivo) return;
    this.error = '';
    try {
      const respuesta = await this.api.importarParametros(archivo);
      this.exito = respuesta.mensaje;
      await this.cargar();
    } catch (err) {
      this.error = (err as ApiError).message;
    }
  }

  async exportar() {
    this.error = '';
    try {
      await this.api.exportarParametros();
      this.exito = 'La paramétría se exportó.';
    } catch (err) {
      this.error = (err as ApiError).message;
    }
  }
}
