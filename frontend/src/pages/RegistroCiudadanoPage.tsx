import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ArrowLeft, Eye, EyeOff, LoaderCircle, RefreshCw, ShieldCheck } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import {
  confirmarRegistroCiudadano,
  iniciarRegistroCiudadano,
  obtenerCaptcha,
  type ApiError,
} from '../services/api'
import { EMAIL_REGEX } from '../services/auth'

const VIDEO_URL =
  'https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260403_050628_c4e32401-fab4-4a27-b7a8-6e9291cd5959.mp4'

const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,}$/
const DPI_REGEX = /^\d{13}$/

const campo =
  'w-full rounded-lg border bg-black/40 px-4 py-3 text-sm text-white outline-none transition placeholder:text-gray-500 focus:border-white/40 focus:ring-2 focus:ring-white/30'

export function RegistroCiudadanoPage() {
  const navigate = useNavigate()
  const [paso, setPaso] = useState<1 | 2>(1)
  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [telefono, setTelefono] = useState('')
  const [dpi, setDpi] = useState('')
  const [password, setPassword] = useState('')
  const [confirmarPassword, setConfirmarPassword] = useState('')
  const [mostrarPassword, setMostrarPassword] = useState(false)
  const [aceptaPrivacidad, setAceptaPrivacidad] = useState(false)
  const [captchaId, setCaptchaId] = useState('')
  const [captchaPregunta, setCaptchaPregunta] = useState('')
  const [captchaRespuesta, setCaptchaRespuesta] = useState('')
  const [codigo, setCodigo] = useState('')
  const [registroId, setRegistroId] = useState('')
  const [enviado, setEnviado] = useState(false)
  const [cargando, setCargando] = useState(false)
  const [errorServidor, setErrorServidor] = useState('')
  const [erroresApi, setErroresApi] = useState<Record<string, string>>({})

  useEffect(() => {
    void cargarCaptcha()
  }, [])

  async function cargarCaptcha() {
    try {
      const reto = await obtenerCaptcha()
      setCaptchaId(reto.captchaId)
      setCaptchaPregunta(reto.pregunta)
      setCaptchaRespuesta('')
    } catch {
      setCaptchaPregunta('No fue posible cargar la verificación. Intente refrescar.')
    }
  }

  const errores = useMemo(() => {
    const lista: Record<string, string> = {}
    if (nombre.trim().length < 2) lista.nombre = 'Ingrese su nombre completo.'
    if (!EMAIL_REGEX.test(email.trim())) lista.email = 'Ingrese un correo electrónico válido.'
    const tel = telefono.replace(/\D/g, '')
    if (tel && (tel.length < 8 || tel.length > 15)) lista.telefono = 'El teléfono debe tener entre 8 y 15 dígitos.'
    if (!DPI_REGEX.test(dpi.replace(/\D/g, ''))) lista.dpi = 'Ingrese un DPI o CUI de 13 dígitos.'
    if (!PASSWORD_REGEX.test(password)) {
      lista.password = 'Use 10 caracteres o más, con mayúscula, minúscula, número y símbolo.'
    }
    if (password !== confirmarPassword) lista.confirmarPassword = 'Las contraseñas no coinciden.'
    if (!aceptaPrivacidad) lista.privacidad = 'Debe aceptar el aviso de privacidad.'
    if (!captchaRespuesta.trim()) lista.captcha = 'Resuelva la verificación para continuar.'
    return lista
  }, [nombre, email, telefono, dpi, password, confirmarPassword, aceptaPrivacidad, captchaRespuesta])

  async function enviarDatos(event: FormEvent) {
    event.preventDefault()
    setEnviado(true)
    setErrorServidor('')
    setErroresApi({})
    if (Object.keys(errores).length > 0) return
    setCargando(true)
    try {
      const respuesta = await iniciarRegistroCiudadano({
        nombre: nombre.trim(),
        email: email.trim(),
        telefono: telefono.replace(/\D/g, ''),
        dpi: dpi.replace(/\D/g, ''),
        password,
        confirmarPassword,
        aceptaPrivacidad,
        captchaId,
        captchaRespuesta,
      })
      setRegistroId(respuesta.registroId)
      setPaso(2)
      setEnviado(false)
    } catch (error) {
      const apiError = error as ApiError
      setErrorServidor(apiError.message)
      if (apiError.errores) setErroresApi(apiError.errores)
      void cargarCaptcha()
    } finally {
      setCargando(false)
    }
  }

  async function confirmarCodigo(event: FormEvent) {
    event.preventDefault()
    setEnviado(true)
    setErrorServidor('')
    if (!codigo.trim()) return
    setCargando(true)
    try {
      await confirmarRegistroCiudadano(registroId, codigo.trim())
      navigate('/login', { replace: true })
    } catch (error) {
      const apiError = error as ApiError
      setErrorServidor(apiError.message)
    } finally {
      setCargando(false)
    }
  }

  function clase(error?: string) {
    return `${campo} ${enviado && error ? 'border-red-400/80' : 'border-white/20'}`
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
            to="/login"
            className="flex items-center gap-2 text-sm text-gray-300 transition-colors hover:text-white"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            Ir a iniciar sesión
          </Link>
        </header>

        <div className="flex flex-1 items-center justify-center py-12">
          <section className="liquid-glass login-card w-full max-w-[480px] rounded-xl border border-white/20 p-6 shadow-2xl sm:p-8">
            <div className="mb-8">
              <div className="mb-5 flex h-11 w-11 items-center justify-center rounded-lg border border-white/20">
                <ShieldCheck className="h-5 w-5" aria-hidden="true" />
              </div>
              <p className="mb-2 text-sm text-gray-300">Cuenta de ciudadano</p>
              <h1 className="text-3xl font-normal tracking-[-0.04em]">
                {paso === 1 ? 'Crear cuenta' : 'Verificar correo'}
              </h1>
              <p className="mt-3 text-sm leading-relaxed text-gray-300">
                {paso === 1
                  ? 'La cuenta se crea con verificación humana, contraseña segura, DPI y aviso de privacidad.'
                  : 'Ingrese el código de seis dígitos enviado a su correo para activar la cuenta. En desarrollo, el código también aparece en la consola del backend.'}
              </p>
            </div>

            {paso === 1 ? (
              <form noValidate autoComplete="off" onSubmit={(event) => void enviarDatos(event)} className="space-y-4">
                <Campo
                  id="nombre"
                  label="Nombre completo"
                  value={nombre}
                  onChange={setNombre}
                  autoComplete="off"
                  className={clase(errores.nombre || erroresApi.nombre)}
                  error={enviado ? errores.nombre || erroresApi.nombre : ''}
                />
                <Campo
                  id="correo-registro"
                  label="Correo electrónico"
                  type="text"
                  value={email}
                  onChange={setEmail}
                  autoComplete="off"
                  className={clase(errores.email || erroresApi.email)}
                  error={enviado ? errores.email || erroresApi.email : ''}
                />
                <Campo
                  id="telefono"
                  label="Teléfono"
                  value={telefono}
                  onChange={setTelefono}
                  autoComplete="off"
                  className={clase(errores.telefono || erroresApi.telefono)}
                  error={enviado ? errores.telefono || erroresApi.telefono : ''}
                />
                <Campo
                  id="dpi"
                  label="DPI / CUI"
                  value={dpi}
                  onChange={setDpi}
                  autoComplete="off"
                  className={clase(errores.dpi || erroresApi.dpi)}
                  error={enviado ? errores.dpi || erroresApi.dpi : ''}
                />
                <div>
                  <label htmlFor="password" className="mb-2 block text-sm text-gray-200">
                    Contraseña
                  </label>
                  <div className="relative">
                    <input
                      id="password"
                      name="qrds-clave-nueva"
                      type={mostrarPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      autoComplete="new-password"
                      readOnly
                      onFocus={(event) => event.currentTarget.removeAttribute('readonly')}
                      data-1p-ignore
                      data-lpignore="true"
                      className={`${clase(errores.password)} pr-12`}
                    />
                    <button
                      type="button"
                      onClick={() => setMostrarPassword((actual) => !actual)}
                      className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-gray-300 hover:text-white"
                      aria-label={mostrarPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    >
                      {mostrarPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {enviado && errores.password && (
                    <p className="mt-2 text-xs text-red-200">{errores.password}</p>
                  )}
                </div>
                <Campo
                  id="confirmar"
                  label="Confirmar contraseña"
                  type="password"
                  value={confirmarPassword}
                  onChange={setConfirmarPassword}
                  autoComplete="new-password"
                  className={clase(errores.confirmarPassword)}
                  error={enviado ? errores.confirmarPassword : ''}
                />
                <label className="flex items-start gap-3 text-sm text-gray-200">
                  <input
                    type="checkbox"
                    checked={aceptaPrivacidad}
                    onChange={(event) => setAceptaPrivacidad(event.target.checked)}
                    className="mt-1"
                  />
                  <span>
                    Acepto el aviso de privacidad. Mis datos se usarán para identificar mi cuenta y
                    consultar mis casos.
                  </span>
                </label>
                {enviado && errores.privacidad && (
                  <p className="text-xs text-red-200">{errores.privacidad}</p>
                )}
                <div>
                  <div className="mb-2 flex items-center justify-between">
                    <label htmlFor="captcha" className="text-sm text-gray-200">
                      Verificación {captchaPregunta && `· ${captchaPregunta}`}
                    </label>
                    <button
                      type="button"
                      onClick={() => void cargarCaptcha()}
                      className="rounded-lg p-1 text-gray-300 hover:text-white"
                      aria-label="Refrescar verificación"
                    >
                      <RefreshCw className="h-4 w-4" />
                    </button>
                  </div>
                  <input
                    id="captcha"
                    value={captchaRespuesta}
                    onChange={(event) => setCaptchaRespuesta(event.target.value)}
                    className={clase(errores.captcha)}
                    inputMode="numeric"
                    autoComplete="off"
                  />
                  {enviado && errores.captcha && (
                    <p className="mt-2 text-xs text-red-200">{errores.captcha}</p>
                  )}
                </div>
                {errorServidor && (
                  <div className="rounded-lg border border-red-300/30 bg-red-950/40 px-4 py-3 text-sm text-red-100" role="alert">
                    {errorServidor}
                  </div>
                )}
                <button
                  type="submit"
                  disabled={cargando}
                  className="flex w-full items-center justify-center gap-2 rounded-lg bg-white px-4 py-3 text-sm font-medium text-black transition-colors hover:bg-gray-100 disabled:opacity-50"
                >
                  {cargando && <LoaderCircle className="h-4 w-4 animate-spin" />}
                  {cargando ? 'Validando…' : 'Continuar'}
                </button>
              </form>
            ) : (
              <form noValidate onSubmit={(event) => void confirmarCodigo(event)} className="space-y-5">
                <Campo
                  id="codigo"
                  label="Código de verificación"
                  value={codigo}
                  onChange={setCodigo}
                  className={clase(!codigo.trim() ? 'codigo' : '')}
                  error={enviado && !codigo.trim() ? 'Ingrese el código de seis dígitos.' : ''}
                />
                {errorServidor && (
                  <div className="rounded-lg border border-red-300/30 bg-red-950/40 px-4 py-3 text-sm text-red-100" role="alert">
                    {errorServidor}
                  </div>
                )}
                <button
                  type="submit"
                  disabled={cargando}
                  className="flex w-full items-center justify-center gap-2 rounded-lg bg-white px-4 py-3 text-sm font-medium text-black transition-colors hover:bg-gray-100 disabled:opacity-50"
                >
                  {cargando && <LoaderCircle className="h-4 w-4 animate-spin" />}
                  {cargando ? 'Activando cuenta…' : 'Activar cuenta'}
                </button>
              </form>
            )}
          </section>
        </div>
      </div>
    </main>
  )
}

function Campo({
  id,
  label,
  value,
  onChange,
  className,
  error,
  type = 'text',
  autoComplete = 'off',
}: {
  id: string
  label: string
  value: string
  onChange: (valor: string) => void
  className: string
  error?: string
  type?: string
  autoComplete?: string
}) {
  return (
    <div>
      <label htmlFor={id} className="mb-2 block text-sm text-gray-200">
        {label}
      </label>
      <input
        id={id}
        name={`qrds-${id}`}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
        autoCorrect="off"
        autoCapitalize="none"
        spellCheck={false}
        readOnly
        onFocus={(event) => event.currentTarget.removeAttribute('readonly')}
        data-1p-ignore
        data-lpignore="true"
        className={className}
      />
      {error && <p className="mt-2 text-xs text-red-200">{error}</p>}
    </div>
  )
}
