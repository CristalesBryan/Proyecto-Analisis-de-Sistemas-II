import { forwardRef, useImperativeHandle, useRef } from 'react'
import ReCAPTCHA from 'react-google-recaptcha'

const SITE_KEY = import.meta.env.VITE_RECAPTCHA_SITE_KEY ?? ''

export type RecaptchaV2Handle = {
  reset: () => void
}

type RecaptchaV2Props = {
  onToken: (token: string | null) => void
  onExpirado?: () => void
  onError?: () => void
}

export const RecaptchaV2 = forwardRef<RecaptchaV2Handle, RecaptchaV2Props>(
  function RecaptchaV2({ onToken, onExpirado, onError }, ref) {
    const widget = useRef<ReCAPTCHA>(null)

    useImperativeHandle(ref, () => ({
      reset() {
        widget.current?.reset()
        onToken(null)
      },
    }))

    if (!SITE_KEY) {
      return (
        <p className="text-sm text-amber-100" role="alert">
          Falta configurar VITE_RECAPTCHA_SITE_KEY para mostrar la verificación.
        </p>
      )
    }

    return (
      <div className="recaptcha-v2 overflow-x-auto">
        <ReCAPTCHA
          ref={widget}
          sitekey={SITE_KEY}
          theme="dark"
          hl="es"
          onChange={(valor) => onToken(valor)}
          onExpired={() => {
            onToken(null)
            onExpirado?.()
          }}
          onErrored={() => {
            onToken(null)
            onError?.()
          }}
        />
      </div>
    )
  },
)
