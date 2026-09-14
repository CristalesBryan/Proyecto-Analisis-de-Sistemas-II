import { useEffect, useMemo, useState, type FormEvent } from 'react'
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Copy,
  FileWarning,
  Lightbulb,
  LoaderCircle,
  Megaphone,
  Paperclip,
  RefreshCw,
  Scale,
  ShieldAlert,
  Upload,
  X,
} from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { EMAIL_REGEX } from '../services/auth'
import {
  adjuntarDocumentosCaso,
  obtenerAreas,
  obtenerCaptcha,
  registrarCasoPublico,
  type ApiError,
  type AreaDependencia,
  type RegistroCasoRespuesta,
} from '../services/api'
import { formatFechaHora } from '../lib/fechas'

const VIDEO_URL =
  'https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260403_050628_c4e32401-fab4-4a27-b7a8-6e9291cd5959.mp4'

const TIPOS = [
  {
    codigo: 'Q' as const,
    nombre: 'Queja',
    plazo: '15 días hábiles',
    descripcion: 'Insatisfacción por el servicio recibido.',
    icon: FileWarning,
  },
  {
    codigo: 'R' as const,
    nombre: 'Reclamo',
    plazo: '20 días hábiles',
    descripcion: 'Solicitud de revisión de una decisión o omisión.',
    icon: Scale,
  },
  {
    codigo: 'D' as const,
    nombre: 'Denuncia',
    plazo: '30 días hábiles',
    descripcion: 'Posible irregularidad o incumplimiento de normas.',
    icon: ShieldAlert,
  },
  {
    codigo: 'S' as const,
    nombre: 'Sugerencia',
    plazo: '30 días hábiles',
    descripcion: 'Propuesta para mejorar servicios o atención.',
    icon: Lightbulb,
  },
]

const MAX_ARCHIVOS = 5
const MAX_BYTES = 5 * 1024 * 1024
const EXTENSIONES = new Set(['pdf', 'jpg', 'jpeg', 'png', 'docx'])
const INPUT =
  'w-full rounded-lg border bg-black/40 px-4 py-3 text-sm text-white outline-none transition placeholder:text-gray-500 focus:ring-2 focus:ring-white/30'

type TipoCaso = 'Q' | 'R' | 'D' | 'S'

function tiposDesdeConsulta(valor: string | null): TipoCaso[] {
  const clave = (valor || '').trim().toUpperCase()
  if (clave === 'Q') return ['Q']
  if (clave === 'S') return ['S']
  if (clave === 'R') return ['R']
  if (clave === 'D') return ['D']
  if (clave === 'RD' || clave === 'R,D') return ['R', 'D']
  return ['Q', 'R', 'D', 'S']
}

function claseCampo(invalido: boolean, bloqueado = false) {
  return `${INPUT} ${invalido ? 'border-red-400/80' : 'border-white/20 focus:border-white/40'} ${
    bloqueado ? 'cursor-not-allowed opacity-50' : ''
  }`
}

function extensionDe(nombre: string) {
  return nombre.toLowerCase().split('.').pop() || ''
}

function validarArchivo(file: File) {
  if (!EXTENSIONES.has(extensionDe(file.name))) {
    return 'Formato no permitido. Use PDF, JPG, PNG o DOCX.'
  }
  if (file.size > MAX_BYTES) return 'El archivo supera el máximo de 5 MB.'
  return null
}

export function RegistroCasoPage() {
  const [searchParams] = useSearchParams()
  const tipoQuery = searchParams.get('tipo')
  const tiposPermitidos = useMemo(() => tiposDesdeConsulta(tipoQuery), [tipoQuery])
  const tipoFijo = tiposPermitidos.length === 1
  const [paso, setPaso] = useState(tipoFijo ? 2 : 1)
  const [tipoCaso, setTipoCaso] = useState<TipoCaso | ''>(tipoFijo ? tiposPermitidos[0] : '')
  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [telefono, setTelefono] = useState('')
  const [area, setArea] = useState('')
  const [descripcion, setDescripcion] = useState('')
  const [denunciado, setDenunciado] = useState('')
  const [esAnonimo, setEsAnonimo] = useState(false)
  const [archivos, setArchivos] = useState<File[]>([])
  const [erroresArchivo, setErroresArchivo] = useState<string[]>([])
  const [aceptaPrivacidad, setAceptaPrivacidad] = useState(false)
  const [areas, setAreas] = useState<AreaDependencia[]>([])
  const [captchaId, setCaptchaId] = useState('')
  const [captchaPregunta, setCaptchaPregunta] = useState('')
  const [captchaRespuesta, setCaptchaRespuesta] = useState('')
  const [enviado, setEnviado] = useState(false)
  const [cargando, setCargando] = useState(false)
  const [errorServidor, setErrorServidor] = useState('')
  const [errorConexion, setErrorConexion] = useState(false)
  const [duplicado, setDuplicado] = useState<string | null>(null)
  const [confirmacion, setConfirmacion] = useState<RegistroCasoRespuesta | null>(null)
  const [avisoAdjuntos, setAvisoAdjuntos] = useState('')
  const [copiado, setCopiado] = useState(false)

  const anonimo = esAnonimo
  const tiposVisibles = TIPOS.filter((tipo) => tiposPermitidos.includes(tipo.codigo))
  const tituloRegistro =
    tiposPermitidos.length === 1
      ? `Registrar ${TIPOS.find((tipo) => tipo.codigo === tiposPermitidos[0])?.nombre.toLowerCase()}`
      : tiposPermitidos.length === 2
        ? 'Registrar reclamo o denuncia'
        : 'Registrar caso'

  useEffect(() => {
    if (tipoFijo) {
      const unico = tiposPermitidos[0]
      setTipoCaso(unico)
      setPaso((actual) => (actual === 1 ? 2 : actual))
      return
    }
    setTipoCaso((actual) => (actual && tiposPermitidos.includes(actual) ? actual : ''))
  }, [tipoFijo, tipoQuery, tiposPermitidos])

  useEffect(() => {
    void obtenerAreas()
      .then(setAreas)
      .catch(() => setAreas([]))
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

  useEffect(() => {
    if (paso === 3 && !captchaId) void cargarCaptcha()
  }, [paso, captchaId])

  const erroresPaso2 = useMemo(() => {
    const errores: Record<string, string> = {}
    if (!anonimo && (nombre.trim().length < 2 || nombre.trim().length > 150)) {
      errores.nombre = 'El nombre debe tener entre 2 y 150 caracteres.'
    }
    if (!anonimo && !EMAIL_REGEX.test(email.trim())) {
      errores.email = 'Ingrese un correo electrónico válido.'
    }
    const digitos = telefono.replace(/\D/g, '')
    if (!anonimo && digitos && (digitos.length < 8 || digitos.length > 15)) {
      errores.telefono = 'El teléfono debe tener entre 8 y 15 dígitos.'
    }
    if (!anonimo && !area) errores.area = 'Seleccione un área o dependencia.'
    if (descripcion.trim().length < 50 || descripcion.trim().length > 2000) {
      errores.descripcion = 'La descripción debe tener entre 50 y 2000 caracteres.'
    }
    if (!anonimo && tipoCaso === 'D' && denunciado.trim().length > 150) {
      errores.denunciado = 'El nombre del denunciado no puede superar 150 caracteres.'
    }
    return errores
  }, [anonimo, nombre, email, telefono, area, descripcion, tipoCaso, denunciado])

  const erroresPaso3 = useMemo(() => {
    const errores: Record<string, string> = {}
    if (!aceptaPrivacidad) {
      errores.privacidad = 'Debe aceptar el aviso de privacidad para enviar el caso.'
    }
    if (!captchaRespuesta.trim()) {
      errores.captcha = 'Resuelva la verificación para continuar.'
    }
    return errores
  }, [aceptaPrivacidad, captchaRespuesta])

  function irPaso2() {
    setEnviado(false)
    if (!tipoCaso) {
      setEnviado(true)
      return
    }
    setPaso(2)
  }

  function irPaso3() {
    setEnviado(true)
    if (Object.keys(erroresPaso2).length > 0) return
    setEnviado(false)
    setPaso(3)
  }

  function agregarArchivos(lista: FileList | null) {
    if (!lista) return
    const nuevos = [...archivos]
    const errores: string[] = []
    for (const file of Array.from(lista)) {
      if (nuevos.length >= MAX_ARCHIVOS) {
        errores.push(`${file.name}: máximo 5 archivos por caso.`)
        continue
      }
      const motivo = validarArchivo(file)
      if (motivo) {
        errores.push(`${file.name}: ${motivo}`)
        continue
      }
      if (nuevos.some((item) => item.name === file.name && item.size === file.size)) continue
      nuevos.push(file)
    }
    setArchivos(nuevos)
    setErroresArchivo(errores)
  }

  async function enviar(forzarRegistro = false) {
    setEnviado(true)
    setErrorServidor('')
    setErrorConexion(false)
    setDuplicado(null)
    if (!tipoCaso || Object.keys(erroresPaso2).length > 0 || Object.keys(erroresPaso3).length > 0) {
      return
    }

    setCargando(true)
    try {
      const respuesta = await registrarCasoPublico({
        tipoCaso,
        nombreCiudadano: anonimo ? '' : nombre.trim(),
        email: anonimo ? '' : email.trim(),
        telefono: anonimo ? '' : telefono.replace(/\D/g, ''),
        areaDependencia: anonimo ? '' : area,
        descripcion: descripcion.trim(),
        denunciado: anonimo ? undefined : denunciado.trim() || undefined,
        esAnonimo: anonimo,
        aceptaPrivacidad,
        captchaId,
        captchaRespuesta,
        forzarRegistro,
      })

      if (!anonimo && archivos.length > 0) {
        try {
          const adjuntos = await adjuntarDocumentosCaso(respuesta.codigoSeguimiento, archivos)
          if (adjuntos.rechazados.length > 0) {
            setAvisoAdjuntos(
              `El caso se registró, pero algunos archivos no se adjuntaron: ${adjuntos.rechazados
                .map((item) => item.nombre)
                .join(', ')}.`,
            )
          }
        } catch {
          setAvisoAdjuntos(
            'El caso se registró, pero no fue posible guardar los documentos adjuntos.',
          )
        }
      }

      setConfirmacion(respuesta)
    } catch (error) {
      const apiError = error as ApiError
      if (apiError.codigo === 'CASO_SIMILAR' && apiError.codigoExistente) {
        setDuplicado(apiError.codigoExistente)
        return
      }
      setErrorServidor(
        apiError.conexion
          ? 'Error al registrar su caso. Verifique su conexión e intente nuevamente.'
          : apiError.message,
      )
      setErrorConexion(Boolean(apiError.conexion))
      if (apiError.codigo === 'CAPTCHA_INVALIDO') void cargarCaptcha()
    } finally {
      setCargando(false)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await enviar(false)
  }

  async function copiarCodigo() {
    if (!confirmacion) return
    await navigator.clipboard.writeText(confirmacion.codigoSeguimiento)
    setCopiado(true)
    window.setTimeout(() => setCopiado(false), 2000)
  }

  const tipoSeleccionado = TIPOS.find((item) => item.codigo === tipoCaso)

  return (
    <main className="relative min-h-screen overflow-hidden bg-black px-6 py-6 md:px-12 lg:px-16">
      <video
        className="absolute inset-0 h-full w-full object-cover"
        src={VIDEO_URL}
        autoPlay
        loop
        muted
        playsInline
        aria-hidden="true"
      />

      <div className="relative z-10 mx-auto flex min-h-[calc(100vh-3rem)] w-full max-w-6xl flex-col">
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

        <div className="flex flex-1 items-center justify-center py-10">
          <section className="login-card liquid-glass w-full max-w-2xl rounded-xl border border-white/20 p-6 shadow-2xl sm:p-8">
            {confirmacion ? (
              <Confirmacion
                confirmacion={confirmacion}
                avisoAdjuntos={avisoAdjuntos}
                copiado={copiado}
                onCopiar={() => void copiarCodigo()}
                onNuevo={() => {
                  setConfirmacion(null)
                  setPaso(tipoFijo ? 2 : 1)
                  setTipoCaso(tipoFijo ? tiposPermitidos[0] : '')
                  setNombre('')
                  setEmail('')
                  setTelefono('')
                  setArea('')
                  setDescripcion('')
                  setDenunciado('')
                  setEsAnonimo(false)
                  setArchivos([])
                  setErroresArchivo([])
                  setAceptaPrivacidad(false)
                  setCaptchaId('')
                  setCaptchaPregunta('')
                  setCaptchaRespuesta('')
                  setEnviado(false)
                  setErrorServidor('')
                  setAvisoAdjuntos('')
                  setCopiado(false)
                }}
              />
            ) : (
              <>
                <p className="mb-2 text-sm text-gray-300">Registro público · sin autenticación</p>
                <h1 className="text-3xl font-normal tracking-[-0.04em]">{tituloRegistro}</h1>
                <p className="mt-3 text-sm leading-relaxed text-gray-300">
                  {tipoFijo
                    ? 'Completa los datos del caso. Recibirás un código de seguimiento al finalizar.'
                    : tiposPermitidos.length === 2
                      ? 'Elige si presentarás un reclamo o una denuncia. Recibirás un código de seguimiento al finalizar.'
                      : 'Presenta una queja, reclamo, denuncia o sugerencia. Recibirás un código de seguimiento al finalizar.'}
                </p>

                <ol className="mt-8 grid grid-cols-3 gap-2 text-xs sm:text-sm" aria-label="Progreso del registro">
                  {['Tipo', 'Datos', 'Adjuntos'].map((etiqueta, index) => {
                    const numero = index + 1
                    const activo = paso === numero
                    const completo = paso > numero
                    return (
                      <li
                        key={etiqueta}
                        className={`rounded-lg border px-3 py-2 text-center ${
                          activo
                            ? 'border-white/50 text-white'
                            : completo
                              ? 'border-white/20 text-white'
                              : 'border-white/10 text-gray-400'
                        }`}
                      >
                        {numero}. {etiqueta}
                      </li>
                    )
                  })}
                </ol>

                <form noValidate onSubmit={handleSubmit} className="mt-8 space-y-6">
                  {paso === 1 && (
                    <fieldset>
                      <legend className="mb-4 text-sm text-gray-200">
                        Selecciona el tipo de caso
                      </legend>
                      <div className="grid gap-3 sm:grid-cols-2">
                        {tiposVisibles.map((tipo) => {
                          const Icon = tipo.icon
                          const seleccionado = tipoCaso === tipo.codigo
                          return (
                            <button
                              key={tipo.codigo}
                              type="button"
                              onClick={() => {
                                setTipoCaso(tipo.codigo)
                                if (tipo.codigo !== 'D') {
                                  setDenunciado('')
                                }
                              }}
                              className={`rounded-xl border p-4 text-left transition-colors ${
                                seleccionado
                                  ? 'border-white bg-white/10'
                                  : 'border-white/20 hover:border-white/40'
                              }`}
                              aria-pressed={seleccionado}
                            >
                              <Icon className="mb-3 h-5 w-5" aria-hidden="true" />
                              <p className="font-medium">{tipo.nombre}</p>
                              <p className="mt-2 text-sm leading-relaxed text-gray-300">
                                {tipo.descripcion}
                              </p>
                              <p className="mt-3 text-xs text-gray-400">Plazo: {tipo.plazo}</p>
                            </button>
                          )
                        })}
                      </div>
                      {enviado && !tipoCaso && (
                        <p className="mt-3 text-xs text-red-200">Seleccione un tipo de caso.</p>
                      )}
                    </fieldset>
                  )}

                  {paso === 2 && (
                    <div className="space-y-5">
                      <label className="flex items-start gap-3 text-sm text-gray-200">
                        <input
                          type="checkbox"
                          checked={esAnonimo}
                          onChange={(event) => {
                            const valor = event.target.checked
                            setEsAnonimo(valor)
                            if (valor) {
                              setNombre('')
                              setEmail('')
                              setTelefono('')
                              setArea('')
                              setDenunciado('')
                              setArchivos([])
                              setErroresArchivo([])
                            }
                          }}
                          className="mt-1 h-4 w-4 accent-white"
                        />
                        <span>
                          Presentar este caso de forma anónima. Se bloquearán nombre, correo,
                          teléfono, área y demás datos personales; solo quedará activa la
                          descripción del caso y no se enviará confirmación por correo.
                        </span>
                      </label>

                      <div>
                        <label htmlFor="nombre" className="mb-2 block text-sm text-gray-200">
                          Nombre completo
                        </label>
                        <input
                          id="nombre"
                          value={nombre}
                          onChange={(event) => setNombre(event.target.value)}
                          className={claseCampo(enviado && Boolean(erroresPaso2.nombre), anonimo)}
                          aria-invalid={enviado && Boolean(erroresPaso2.nombre)}
                          autoComplete="name"
                          disabled={anonimo}
                        />
                        {enviado && erroresPaso2.nombre && (
                          <p className="mt-2 text-xs text-red-200">{erroresPaso2.nombre}</p>
                        )}
                      </div>

                      <div>
                        <label htmlFor="correo" className="mb-2 block text-sm text-gray-200">
                          Correo electrónico
                        </label>
                        <input
                          id="correo"
                          type="email"
                          value={email}
                          onChange={(event) => setEmail(event.target.value)}
                          className={claseCampo(enviado && Boolean(erroresPaso2.email), anonimo)}
                          aria-invalid={enviado && Boolean(erroresPaso2.email)}
                          autoComplete="email"
                          placeholder="ciudadano@correo.com"
                          disabled={anonimo}
                        />
                        {enviado && erroresPaso2.email && (
                          <p className="mt-2 text-xs text-red-200">{erroresPaso2.email}</p>
                        )}
                      </div>

                      <div>
                        <label htmlFor="telefono" className="mb-2 block text-sm text-gray-200">
                          Teléfono (opcional)
                        </label>
                        <input
                          id="telefono"
                          type="tel"
                          value={telefono}
                          onChange={(event) => setTelefono(event.target.value)}
                          className={claseCampo(enviado && Boolean(erroresPaso2.telefono), anonimo)}
                          aria-invalid={enviado && Boolean(erroresPaso2.telefono)}
                          autoComplete="tel"
                          disabled={anonimo}
                        />
                        {enviado && erroresPaso2.telefono && (
                          <p className="mt-2 text-xs text-red-200">{erroresPaso2.telefono}</p>
                        )}
                      </div>

                      <div>
                        <label htmlFor="area" className="mb-2 block text-sm text-gray-200">
                          Área / dependencia
                        </label>
                        <select
                          id="area"
                          value={area}
                          onChange={(event) => setArea(event.target.value)}
                          className={`${claseCampo(enviado && Boolean(erroresPaso2.area), anonimo)} bg-black`}
                          aria-invalid={enviado && Boolean(erroresPaso2.area)}
                          disabled={anonimo}
                        >
                          <option value="">Seleccione una opción</option>
                          {areas.map((item) => (
                            <option key={item.codigo} value={item.codigo}>
                              {item.nombre}
                            </option>
                          ))}
                        </select>
                        {enviado && erroresPaso2.area && (
                          <p className="mt-2 text-xs text-red-200">{erroresPaso2.area}</p>
                        )}
                      </div>

                      {tipoCaso === 'D' && (
                        <div>
                          <label htmlFor="denunciado" className="mb-2 block text-sm text-gray-200">
                            Funcionario o área denunciada (opcional)
                          </label>
                          <input
                            id="denunciado"
                            value={denunciado}
                            onChange={(event) => setDenunciado(event.target.value)}
                            className={claseCampo(enviado && Boolean(erroresPaso2.denunciado), anonimo)}
                            maxLength={150}
                            disabled={anonimo}
                          />
                          {enviado && erroresPaso2.denunciado && (
                            <p className="mt-2 text-xs text-red-200">{erroresPaso2.denunciado}</p>
                          )}
                        </div>
                      )}

                      <div>
                        <div className="mb-2 flex items-center justify-between">
                          <label htmlFor="descripcion" className="text-sm text-gray-200">
                            Descripción del caso
                          </label>
                          <span className="text-xs text-gray-400">{descripcion.length}/2000</span>
                        </div>
                        <textarea
                          id="descripcion"
                          value={descripcion}
                          onChange={(event) => setDescripcion(event.target.value)}
                          rows={6}
                          maxLength={2000}
                          className={claseCampo(enviado && Boolean(erroresPaso2.descripcion))}
                          aria-invalid={enviado && Boolean(erroresPaso2.descripcion)}
                          placeholder="Describe los hechos con el mayor detalle posible (mínimo 50 caracteres)."
                        />
                        {enviado && erroresPaso2.descripcion && (
                          <p className="mt-2 text-xs text-red-200">{erroresPaso2.descripcion}</p>
                        )}
                      </div>
                    </div>
                  )}

                  {paso === 3 && (
                    <div className="space-y-5">
                      <div className="rounded-lg border border-white/15 bg-black/30 p-4 text-sm text-gray-300">
                        <p>
                          <span className="text-white">{tipoSeleccionado?.nombre}</span>
                          {area ? ` · ${areas.find((item) => item.codigo === area)?.nombre || area}` : ''}
                        </p>
                        <p className="mt-1 line-clamp-2">{descripcion}</p>
                      </div>

                      <div>
                        <p className="mb-2 text-sm text-gray-200">Documentos de soporte (opcional)</p>
                        {anonimo ? (
                          <p className="rounded-lg border border-white/15 bg-black/30 px-4 py-3 text-sm text-gray-400">
                            En un registro anónimo no se adjuntan documentos para no identificar al
                            ciudadano.
                          </p>
                        ) : (
                          <>
                        <label className="flex cursor-pointer flex-col items-center rounded-xl border border-dashed border-white/20 px-4 py-8 text-center transition-colors hover:border-white/40">
                          <Upload className="mb-3 h-5 w-5" aria-hidden="true" />
                          <span className="text-sm">PDF, JPG, PNG o DOCX · máximo 5 MB · hasta 5 archivos</span>
                          <input
                            type="file"
                            className="sr-only"
                            multiple
                            accept=".pdf,.jpg,.jpeg,.png,.docx,application/pdf,image/jpeg,image/png,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            onChange={(event) => {
                              agregarArchivos(event.target.files)
                              event.target.value = ''
                            }}
                          />
                        </label>
                        {archivos.length > 0 && (
                          <ul className="mt-3 space-y-2">
                            {archivos.map((archivo) => (
                              <li
                                key={`${archivo.name}-${archivo.size}`}
                                className="flex items-center justify-between rounded-lg border border-white/15 px-3 py-2 text-sm"
                              >
                                <span className="flex items-center gap-2 truncate">
                                  <Paperclip className="h-4 w-4 shrink-0" aria-hidden="true" />
                                  <span className="truncate">{archivo.name}</span>
                                </span>
                                <button
                                  type="button"
                                  onClick={() =>
                                    setArchivos((actuales) => actuales.filter((item) => item !== archivo))
                                  }
                                  className="p-1 text-gray-400 hover:text-white"
                                  aria-label={`Quitar ${archivo.name}`}
                                >
                                  <X className="h-4 w-4" />
                                </button>
                              </li>
                            ))}
                          </ul>
                        )}
                        {erroresArchivo.map((error) => (
                          <p key={error} className="mt-2 text-xs text-red-200">
                            {error}
                          </p>
                        ))}
                          </>
                        )}
                      </div>

                      <div>
                        <label className="flex items-start gap-3 text-sm text-gray-200">
                          <input
                            type="checkbox"
                            checked={aceptaPrivacidad}
                            onChange={(event) => setAceptaPrivacidad(event.target.checked)}
                            className="mt-1 h-4 w-4 accent-white"
                            aria-invalid={enviado && Boolean(erroresPaso3.privacidad)}
                          />
                          <span>
                            Acepto el aviso de privacidad. Mis datos se usarán únicamente para
                            gestionar este caso conforme a la legislación vigente, y no serán
                            publicados en la consulta pública.
                          </span>
                        </label>
                        {enviado && erroresPaso3.privacidad && (
                          <p className="mt-2 text-xs text-red-200">{erroresPaso3.privacidad}</p>
                        )}
                      </div>

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
                          className={claseCampo(enviado && Boolean(erroresPaso3.captcha))}
                          inputMode="numeric"
                          autoComplete="off"
                        />
                        {enviado && erroresPaso3.captcha && (
                          <p className="mt-2 text-xs text-red-200">{erroresPaso3.captcha}</p>
                        )}
                      </div>
                    </div>
                  )}

                  {errorServidor && (
                    <div
                      className="rounded-lg border border-red-300/30 bg-red-950/40 px-4 py-3 text-sm text-red-100"
                      role="alert"
                    >
                      {errorServidor}
                    </div>
                  )}

                  <div className="flex flex-wrap gap-3">
                    {paso > 1 && !(tipoFijo && paso === 2) && (
                      <button
                        type="button"
                        onClick={() => {
                          setEnviado(false)
                          setPaso((actual) => actual - 1)
                        }}
                        className="inline-flex items-center gap-2 rounded-lg border border-white/20 px-5 py-3 text-sm text-white transition-colors hover:bg-white hover:text-black"
                      >
                        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
                        Atrás
                      </button>
                    )}
                    {paso === 1 && (
                      <button
                        type="button"
                        onClick={irPaso2}
                        className="ml-auto inline-flex items-center gap-2 rounded-lg bg-white px-5 py-3 text-sm font-medium text-black transition-colors hover:bg-gray-100"
                      >
                        Continuar
                        <ArrowRight className="h-4 w-4" aria-hidden="true" />
                      </button>
                    )}
                    {paso === 2 && (
                      <button
                        type="button"
                        onClick={irPaso3}
                        className="ml-auto inline-flex items-center gap-2 rounded-lg bg-white px-5 py-3 text-sm font-medium text-black transition-colors hover:bg-gray-100"
                      >
                        Continuar
                        <ArrowRight className="h-4 w-4" aria-hidden="true" />
                      </button>
                    )}
                    {paso === 3 && (
                      <button
                        type="submit"
                        disabled={cargando}
                        className="ml-auto inline-flex items-center gap-2 rounded-lg bg-white px-5 py-3 text-sm font-medium text-black transition-colors hover:bg-gray-100 disabled:opacity-60"
                      >
                        {cargando && <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />}
                        {cargando ? 'Enviando caso…' : 'Enviar caso'}
                      </button>
                    )}
                  </div>

                  {errorConexion && (
                    <button
                      type="button"
                      onClick={() => void enviar(false)}
                      disabled={cargando}
                      className="w-full rounded-lg border border-white/20 px-4 py-3 text-sm text-white transition-colors hover:bg-white hover:text-black"
                    >
                      Reintentar
                    </button>
                  )}
                </form>
              </>
            )}
          </section>
        </div>
      </div>

      {duplicado && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4">
          <div className="liquid-glass w-full max-w-md rounded-xl border border-white/20 p-6">
            <Megaphone className="mb-4 h-5 w-5" aria-hidden="true" />
            <h2 className="text-xl font-medium">Posible caso similar</h2>
            <p className="mt-3 text-sm leading-relaxed text-gray-300">
              Detectamos que puede tener un caso similar registrado recientemente. Código:{' '}
              <span className="text-white">{duplicado}</span>. ¿Desea continuar con un nuevo
              registro?
            </p>
            <div className="mt-6 flex flex-wrap gap-3">
              <button
                type="button"
                onClick={() => {
                  setDuplicado(null)
                  void enviar(true)
                }}
                className="rounded-lg bg-white px-4 py-3 text-sm font-medium text-black hover:bg-gray-100"
              >
                Continuar registro
              </button>
              <button
                type="button"
                onClick={() => setDuplicado(null)}
                className="rounded-lg border border-white/20 px-4 py-3 text-sm hover:bg-white hover:text-black"
              >
                Revisar datos
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  )
}

function Confirmacion({
  confirmacion,
  avisoAdjuntos,
  copiado,
  onCopiar,
  onNuevo,
}: {
  confirmacion: RegistroCasoRespuesta
  avisoAdjuntos: string
  copiado: boolean
  onCopiar: () => void
  onNuevo: () => void
}) {
  return (
    <div>
      <div className="mb-5 flex h-11 w-11 items-center justify-center rounded-lg border border-white/20">
        <Check className="h-5 w-5" aria-hidden="true" />
      </div>
      <p className="mb-2 text-sm text-gray-300">Registro exitoso · {confirmacion.tipo}</p>
      <h1 className="text-3xl font-normal tracking-[-0.04em]">Guarda tu código</h1>
      <p className="mt-3 text-sm leading-relaxed text-gray-300">
        Tu caso quedó en estado {confirmacion.estado}. Úsalo para consultar el avance desde el
        portal, sin crear una cuenta.
      </p>

      <div className="mt-8 rounded-xl border border-white/20 bg-black/30 px-5 py-4">
        <p className="text-xs uppercase tracking-[0.18em] text-gray-400">Código de seguimiento</p>
        <div className="mt-2 flex items-center justify-between gap-3">
          <p className="text-2xl font-medium tracking-wide">{confirmacion.codigoSeguimiento}</p>
          <button
            type="button"
            onClick={onCopiar}
            className="rounded-lg border border-white/20 p-2 text-gray-300 hover:text-white"
            aria-label="Copiar código de seguimiento"
          >
            {copiado ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
          </button>
        </div>
      </div>

      <ul className="mt-6 space-y-2 text-sm text-gray-300">
        <li>Fecha de registro: {formatFechaHora(confirmacion.fechaRegistro)}</li>
        <li>Plazo estimado de respuesta: {confirmacion.plazoEstimado}</li>
      </ul>

      {confirmacion.esAnonimo || !confirmacion.correoEnviado ? (
        <p className="mt-6 rounded-lg border border-amber-300/30 bg-amber-950/40 px-4 py-3 text-sm text-amber-100">
          {confirmacion.esAnonimo
            ? `No se envió correo porque el caso es anónimo. Su código de caso es: ${confirmacion.codigoSeguimiento}. Guárdelo para dar seguimiento.`
            : `No fue posible enviar la confirmación por correo. Su código de caso es: ${confirmacion.codigoSeguimiento}. Guárdelo para dar seguimiento.`}
        </p>
      ) : (
        <p className="mt-6 text-sm text-gray-300">
          Enviamos una confirmación a tu correo con el código y el plazo estimado.
        </p>
      )}

      {avisoAdjuntos && (
        <p className="mt-4 rounded-lg border border-amber-300/30 bg-amber-950/40 px-4 py-3 text-sm text-amber-100">
          {avisoAdjuntos}
        </p>
      )}

      <div className="mt-8 flex flex-wrap gap-3">
        <Link
          to="/"
          className="rounded-lg bg-white px-5 py-3 text-sm font-medium text-black hover:bg-gray-100"
        >
          Volver al portal
        </Link>
        <button
          type="button"
          onClick={onNuevo}
          className="rounded-lg border border-white/20 px-5 py-3 text-sm hover:bg-white hover:text-black"
        >
          Registrar otro caso
        </button>
      </div>
    </div>
  )
}
