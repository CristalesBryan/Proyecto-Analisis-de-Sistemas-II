import { Component } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { InternalLayoutComponent } from '../shared/internal-layout.component';

@Component({
  selector: 'app-resolver-caso',
  imports: [InternalLayoutComponent, RouterLink],
  templateUrl: './resolver-caso.component.html',
})
export class ResolverCasoComponent {
  constructor(private readonly route: ActivatedRoute) {}

  get id() {
    return this.route.snapshot.paramMap.get('id');
  }
}
