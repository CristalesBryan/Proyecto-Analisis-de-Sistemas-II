import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ArrowLeft, Eye, EyeOff, LoaderCircle, ShieldCheck } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { iniciarSesion, obtenerSesion, type ApiError } from '../services/api'
import {
  EMAIL_REGEX,
  consumirAvisoSesionExpirada,
  destinoDeRol,
  getToken,
  getUser,
  guardarSesion,
  limpiarSesion,
} from '../services/auth'

const VIDEO_URL =
  'https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260403_050628_c4e32401-fab4-4a27-b7a8-6e9291cd5959.mp4'

const MENSAJE_SESION_EXPIRADA =
  'Su sesión ha expirado. Por favor inicie sesión nuevamente.'

export function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [mostrarPassword, setMostrarPassword] = useState(false)
  const [enviado, setEnviado] = useState(false)
  const [emailTocado, setEmailTocado] = useState(false)
  const [passwordTocado, setPasswordTocado] = useState(false)
  const [cargando, setCargando] = useState(false)
  const [errorServidor, setErrorServidor] = useState('')
  const [errorConexion, setErrorConexion] = useState(false)
  const [avisoExpirada, setAvisoExpirada] = useState(false)
  const [sacudir, setSacudir] = useState(false)
  const [entradaListo, setEntradaListo] = useState(false)

  const emailValido = useMemo(() => EMAIL_REGEX.test(email.trim()), [email])
  const passwordValida = password.length > 0
  const formularioValido = emailValido && passwordValida

  useEffect(() => {
    if (consumirAvisoSesionExpirada()) {
      setAvisoExpirada(true)
      setErrorServidor(MENSAJE_SESION_EXPIRADA)
    }

    const token = getToken()
    const usuario = getUser()
    if (!token || !usuario) return

    obtenerSesion()
      .then(() => navigate(destinoDeRol(usuario.rol), { replace: true }))
      .catch(() => limpiarSesion())
  }, [navigate])

  function activarSacudida() {
    setSacudir(false)
    window.requestAnimationFrame(() => setSacudir(true))
  }

  async function autenticar() {
    setErrorServidor('')
    setErrorConexion(false)
    setAvisoExpirada(false)
    setCargando(true)

    try {
      const respuesta = await iniciarSesion(email.trim(), password)
      guardarSesion(respuesta.token, respuesta.usuario)
      navigate(destinoDeRol(respuesta.usuario.rol), { replace: true })
    } catch (error) {
      const apiError = error as ApiError
      const mensaje =
        apiError instanceof Error
          ? apiError.message
          : 'Error de conexión. Verifique su conexión a internet e intente nuevamente.'

      setErrorServidor(mensaje)
      setErrorConexion(Boolean(apiError.conexion))

      if (apiError.status === 401) {
        activarSacudida()
      }
    } finally {
      setCargando(false)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setEnviado(true)
    if (!formularioValido) return
    await autenticar()
  }

  return (
    <main className="relative flex min-h-screen overflow-hidden bg-black px-6 py-6 md:px-12 lg:px-16">
      <video
        className="absolute inset-0 h-full w-full object-cover"
        src={VIDEO_URL}
        autoPlay
        loop
        muted
        playsInline
        aria-hidden="true"
      />
      <div className="relative z-10 mx-auto flex w-full max-w-6xl flex-col">
        <header className="flex items-center justify-between">
          <Link to="/" className="flex items-baseline gap-2">
            <span className="text-2xl font-semibold tracking-tight">QRDS</span>
            <span className="hidden text-xs text-gray-300 sm:inline">Municipalidad</span>
          </Link>
          <Link
            to="/"
            className="flex items-center gap-2 text-sm text-gray-300 transition-colors hover:text-white"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            Volver al portal
          </Link>
        </header>

        <div className="flex flex-1 items-center justify-center py-12">
          <section
            className={`liquid-glass w-full max-w-[420px] rounded-xl border border-white/20 p-6 shadow-2xl sm:p-8 ${
              entradaListo ? '' : 'login-card'
            } ${sacudir ? 'login-shake' : ''}`}
            onAnimationEnd={(event) => {
              if (event.animationName === 'login-enter') setEntradaListo(true)
              if (event.animationName === 'login-shake') setSacudir(false)
            }}
          >
            <div className="mb-8">
              <div className="mb-5 flex h-11 w-11 items-center justify-center rounded-lg border border-white/20">
                <ShieldCheck className="h-5 w-5" aria-hidden="true" />
              </div>
              <p className="mb-2 text-sm text-gray-300">Acceso para personal autorizado</p>
              <h1 className="text-3xl font-normal tracking-[-0.04em]">Iniciar sesión</h1>
              <p className="mt-3 text-sm leading-relaxed text-gray-300">
                Ingresa con tus credenciales institucionales para gestionar casos.
              </p>
            </div>

            <form noValidate onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label htmlFor="email" className="mb-2 block text-sm text-gray-200">
                  Correo electrónico
                </label>
                <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="usuario@municipalidad.gt"
                  autoComplete="email"
                  aria-label="Correo electrónico"
                  onBlur={() => setEmailTocado(true)}
                  aria-invalid={(enviado || emailTocado) && !emailValido}
                  aria-describedby={(enviado || emailTocado) && !emailValido ? 'email-error' : undefined}
                  className={`w-full rounded-lg border bg-black/40 px-4 py-3 text-sm text-white outline-none transition placeholder:text-gray-500 focus:ring-2 focus:ring-white/30 ${
                    (enviado || emailTocado) && !emailValido
                      ? 'border-red-400/80'
                      : 'border-white/20 focus:border-white/40'
                  }`}
                />
                {(enviado || emailTocado) && !emailValido && (
                  <p id="email-error" className="mt-2 text-xs text-red-200">
                    Ingresa un correo electrónico válido.
                  </p>
                )}
              </div>

              <div>
                <label htmlFor="password" className="mb-2 block text-sm text-gray-200">
                  Contraseña
                </label>
                <div className="relative">
                  <input
                    id="password"
                    type={mostrarPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    onBlur={() => setPasswordTocado(true)}
                    autoComplete="current-password"
                    aria-label="Contraseña"
                    aria-invalid={(enviado || passwordTocado) && !passwordValida}
                    aria-describedby={
                      (enviado || passwordTocado) && !passwordValida ? 'password-error' : undefined
                    }
                    className={`w-full rounded-lg border bg-black/40 px-4 py-3 pr-12 text-sm text-white outline-none transition placeholder:text-gray-500 focus:ring-2 focus:ring-white/30 ${
                      (enviado || passwordTocado) && !passwordValida
                        ? 'border-red-400/80'
                        : 'border-white/20 focus:border-white/40'
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setMostrarPassword((actual) => !actual)}
                    className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-gray-300 transition-colors hover:text-white"
                    aria-label={mostrarPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  >
                    {mostrarPassword ? (
                      <EyeOff className="h-4 w-4" aria-hidden="true" />
                    ) : (
                      <Eye className="h-4 w-4" aria-hidden="true" />
                    )}
                  </button>
                </div>
                {(enviado || passwordTocado) && !passwordValida && (
                  <p id="password-error" className="mt-2 text-xs text-red-200">
                    Ingresa tu contraseña.
                  </p>
                )}
              </div>

              {errorServidor && (
                <div
                  className={`rounded-lg px-4 py-3 text-sm ${
                    avisoExpirada
                      ? 'border border-amber-300/30 bg-amber-950/40 text-amber-100'
                      : 'border border-red-300/30 bg-red-950/40 text-red-100'
                  }`}
                  role="alert"
                >
                  {errorServidor}
                </div>
              )}

              <button
                type="submit"
                disabled={cargando || !formularioValido}
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-white px-4 py-3 text-sm font-medium text-black transition-colors hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {cargando && <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />}
                {cargando ? 'Verificando acceso…' : 'Iniciar sesión'}
              </button>

              {errorConexion && (
                <button
                  type="button"
                  onClick={() => void autenticar()}
                  disabled={cargando || !formularioValido}
                  className="w-full rounded-lg border border-white/20 px-4 py-3 text-sm text-white transition-colors hover:bg-white hover:text-black disabled:opacity-50"
                >
                  Reintentar
                </button>
              )}
            </form>

            <p className="mt-6 text-center">
              <Link
                to="/"
                className="inline-flex items-center gap-2 text-sm text-gray-300 transition-colors hover:text-white"
              >
                <ArrowLeft className="h-4 w-4" aria-hidden="true" />
                Volver al portal
              </Link>
            </p>
          </section>
        </div>
      </div>
    </main>
  )
}
