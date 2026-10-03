import { Routes } from '@angular/router';
import { AccesoDenegadoComponent } from './pages/acceso-denegado.component';
import { BandejaComponent } from './pages/bandeja.component';
import { CiudadanoCuentaComponent } from './pages/ciudadano-cuenta.component';
import { DashboardComponent } from './pages/dashboard.component';
import { DetalleCasoComponent } from './pages/detalle-caso.component';
import { LoginComponent } from './pages/login.component';
import { PortalComponent } from './pages/portal.component';
import { RegistroCasoComponent } from './pages/registro-caso.component';
import { RegistroCiudadanoComponent } from './pages/registro-ciudadano.component';
import { ResolverCasoComponent } from './pages/resolver-caso.component';
import { SesionShellComponent } from './shared/sesion-shell.component';

const personal = ['ADMIN', 'SUPERVISOR', 'AGENTE'] as const;

export const routes: Routes = [
  { path: '', component: PortalComponent },
  { path: 'login', component: LoginComponent },
  { path: 'registro-ciudadano', component: RegistroCiudadanoComponent },
  { path: 'registro-caso', component: RegistroCasoComponent },
  { path: 'acceso-denegado', component: AccesoDenegadoComponent },
  {
    path: 'ciudadano',
    component: SesionShellComponent,
    data: { roles: ['CIUDADANO'] },
    children: [{ path: '', component: CiudadanoCuentaComponent }],
  },
  {
    path: 'admin',
    component: SesionShellComponent,
    data: { roles: ['ADMIN'] },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'casos' },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'casos', component: BandejaComponent },
    ],
  },
  {
    path: 'supervisor',
    component: SesionShellComponent,
    data: { roles: ['SUPERVISOR'] },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'casos' },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'casos', component: BandejaComponent },
    ],
  },
  {
    path: 'agente',
    component: SesionShellComponent,
    data: { roles: ['AGENTE'] },
    children: [{ path: 'casos', component: BandejaComponent }],
  },
  {
    path: 'casos/:id',
    component: SesionShellComponent,
    data: { roles: [...personal] },
    children: [
      { path: '', component: DetalleCasoComponent },
      { path: 'seguimiento', component: DetalleCasoComponent },
      { path: 'resolver', component: ResolverCasoComponent },
    ],
  },
];
