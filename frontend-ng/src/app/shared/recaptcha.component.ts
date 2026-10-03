import { AfterViewInit, Component, ElementRef, EventEmitter, Output, ViewChild } from '@angular/core';
import { environment } from '../../environments/environment';

declare global {
  interface Window {
    grecaptcha?: {
      ready: (cb: () => void) => void;
      render: (el: HTMLElement, options: Record<string, unknown>) => number;
      reset: (id?: number) => void;
    };
  }
}

@Component({
  selector: 'app-recaptcha',
  templateUrl: './recaptcha.component.html',
})
export class RecaptchaComponent implements AfterViewInit {
  @ViewChild('host') host?: ElementRef<HTMLElement>;
  @Output() token = new EventEmitter<string>();
  @Output() expirado = new EventEmitter<void>();
  @Output() fallo = new EventEmitter<void>();

  readonly siteKey = environment.recaptchaSiteKey;
  private widgetId?: number;
  private intentos = 0;

  ngAfterViewInit() {
    if (this.siteKey) this.intentarRender();
  }

  reset() {
    if (this.widgetId !== undefined && window.grecaptcha) {
      window.grecaptcha.reset(this.widgetId);
    }
    this.token.emit('');
  }

  private intentarRender() {
    const api = window.grecaptcha;
    const el = this.host?.nativeElement;
    if (!api || !el) {
      if (this.intentos < 40) {
        this.intentos += 1;
        window.setTimeout(() => this.intentarRender(), 200);
      }
      return;
    }
    api.ready(() => {
      const destino = this.host?.nativeElement;
      if (!destino || this.widgetId !== undefined) return;
      this.widgetId = api.render(destino, {
        sitekey: this.siteKey,
        theme: 'light',
        hl: 'es',
        callback: (valor: string) => this.token.emit(valor),
        'expired-callback': () => {
          this.token.emit('');
          this.expirado.emit();
        },
        'error-callback': () => {
          this.token.emit('');
          this.fallo.emit();
        },
      });
    });
  }
}
