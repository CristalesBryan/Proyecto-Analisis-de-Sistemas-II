import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Rol } from '../core/models';
import { InternalLayoutComponent } from '../shared/internal-layout.component';

const CONTENIDO: Record<Rol, { titulo: string; descripcion: string }> = {
  ADMIN: {
    titulo: 'Panel de administración',
    descripcion: 'Administración de usuarios, parámetros, reportes y bitácoras del sistema.',
  },
  SUPERVISOR: {
    titulo: 'Panel de supervisión',
    descripcion: 'Gestión de casos, asignaciones y reportes de su área institucional.',
  },
  AGENTE: {
    titulo: 'Casos de mi área',
    descripcion: 'Atención de los casos asignados y de los nuevos registros pendientes de su dependencia.',
  },
  CIUDADANO: {
    titulo: 'Mi cuenta',
    descripcion: 'Consulta tus datos y el seguimiento de tus casos registrados.',
  },
};

@Component({
  selector: 'app-dashboard',
  imports: [InternalLayoutComponent, RouterLink],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent {
  constructor(private readonly auth: AuthService) {}

  get usuario() {
    return this.auth.getUser();
  }

  get rol(): Rol {
    return this.usuario?.rol ?? 'AGENTE';
  }

  get contenido() {
    return CONTENIDO[this.rol];
  }

  get bandeja() {
    return this.auth.destinosPorRol[this.rol];
  }
}
