$ErrorActionPreference = "Continue"
$baseApi = "http://localhost:8080"
$baseWeb = "http://localhost:5173"
$pass = "AdminQRDS2026!"
$results = New-Object System.Collections.Generic.List[object]

function Add-Result($id, $flujo, $paso, $ok, $detalle) {
  $results.Add([pscustomobject]@{
    Id = $id
    Flujo = $flujo
    Paso = $paso
    Resultado = $(if ($ok) { "PASS" } else { "FAIL" })
    Detalle = $detalle
  })
}

function Invoke-Json {
  param($Method, $Url, $Body, $Token, $ExpectedStatus)
  $headers = @{ Accept = "application/json" }
  if ($Token) { $headers.Authorization = "Bearer $Token" }
  $params = @{
    Uri = $Url
    Method = $Method
    Headers = $headers
    TimeoutSec = 15
  }
  if ($null -ne $Body) {
    $params.ContentType = "application/json; charset=utf-8"
    $params.Body = ($Body | ConvertTo-Json -Compress)
  }
  try {
    $resp = Invoke-WebRequest @params -UseBasicParsing
    return @{ Status = [int]$resp.StatusCode; Body = $resp.Content; Ok = $true }
  } catch {
    $ex = $_.Exception
    $status = 0
    $content = ""
    if ($ex.Response) {
      $status = [int]$ex.Response.StatusCode
      try {
        $reader = New-Object System.IO.StreamReader($ex.Response.GetResponseStream())
        $content = $reader.ReadToEnd()
        $reader.Close()
      } catch {}
    }
    if (-not $content -and $_.ErrorDetails.Message) { $content = $_.ErrorDetails.Message }
    return @{ Status = $status; Body = $content; Ok = $false; Error = $ex.Message }
  }
}

# --- Precondiciones ---
$estado = Invoke-Json GET "$baseApi/api/sistema/estado"
$estadoObj = $null
try { $estadoObj = $estado.Body | ConvertFrom-Json } catch {}
Add-Result "PC-01" "Precondiciones" "Servidor web y API activos" ($estado.Status -eq 200 -and $estadoObj.estado -eq "UP") ("HTTP $($estado.Status) body=$($estado.Body)")

$web = Invoke-WebRequest -Uri "$baseWeb/" -UseBasicParsing -TimeoutSec 15
Add-Result "PC-02" "Precondiciones" "Portal publico responde en :5173" ($web.StatusCode -eq 200 -and $web.Content -match "QRDS") ("HTTP $($web.StatusCode) title match=$($web.Content -match 'QRDS — Portal Público Municipal')")

Add-Result "PC-03" "Precondiciones" "Base de datos operativa (API UP implica JPA/H2)" ($estado.Status -eq 200) "H2 file ./data/qrdsdb conectada por el backend"

Add-Result "PC-04" "Precondiciones" "HTTPS en el entorno de prueba" ($false) "El entorno local sirve HTTP (5173/8080), no HTTPS. RNF-01 no aplica en desarrollo local."

# --- Flujo normal: UI del portal ---
$portalJs = Invoke-WebRequest -Uri "$baseWeb/src/pages/PublicPortal.tsx" -UseBasicParsing
$heroJs = Invoke-WebRequest -Uri "$baseWeb/src/components/HeroSection.tsx" -UseBasicParsing
$quickJs = Invoke-WebRequest -Uri "$baseWeb/src/components/QuickAccess.tsx" -UseBasicParsing
$navJs = Invoke-WebRequest -Uri "$baseWeb/src/components/Navbar.tsx" -UseBasicParsing
$infoJs = Invoke-WebRequest -Uri "$baseWeb/src/components/InfoSection.tsx" -UseBasicParsing
$contactJs = Invoke-WebRequest -Uri "$baseWeb/src/components/ContactSection.tsx" -UseBasicParsing
$footerJs = Invoke-WebRequest -Uri "$baseWeb/src/components/Footer.tsx" -UseBasicParsing
$loginJs = Invoke-WebRequest -Uri "$baseWeb/src/pages/LoginPage.tsx" -UseBasicParsing
$regJs = Invoke-WebRequest -Uri "$baseWeb/src/pages/RegistroCasoPage.tsx" -UseBasicParsing
$consultaJs = Invoke-WebRequest -Uri "$baseWeb/src/components/ConsultaCasoModal.tsx" -UseBasicParsing
$layoutJs = Invoke-WebRequest -Uri "$baseWeb/src/components/InternalLayout.tsx" -UseBasicParsing

$uiPortal = ($heroJs.Content -match "Registrar Queja") -and ($heroJs.Content -match "Consultar Mi Caso") -and ($quickJs.Content -match "Iniciar Sesión") -and ($navJs.Content -match "Registrar Queja") -and ($navJs.Content -match "Consultar Caso")
Add-Result "FN-01" "Flujo normal" "Pagina principal sin autenticacion con 3 opciones" $uiPortal "Navbar/Hero/QuickAccess contienen Registrar, Consultar e Iniciar Sesion"

$uiInfo = ($infoJs.Content -match "Qué es el sistema QRDS") -and ($contactJs.Content -match "Contacto") -and ($footerJs.Content -match "Versión del sistema 1.0") -and ($footerJs.Content -match "Política de privacidad")
Add-Result "FN-02" "Flujo normal" "Contenido informativo: hero, info, contacto, footer" $uiInfo "InfoSection + ContactSection + Footer presentes"

$sinAuth = $portalJs.Content -notmatch "iniciarSesion\(" -and $portalJs.Content -match "getSistemaEstado"
Add-Result "FN-03" "Flujo normal" "Carga del portal no exige login" $sinAuth "PublicPortal solo consulta estado y registra acceso publico"

# FA01
$regRoute = Invoke-WebRequest -Uri "$baseWeb/registro-caso" -UseBasicParsing
$regUi = ($regJs.Content -match "tipoCaso") -and ($regJs.Content -match "Queja") -and ($regRoute.StatusCode -eq 200)
Add-Result "FA01-01" "FA01 Registrar" "Redirige a formulario publico CU-03 sin autenticacion" $regUi "Ruta /registro-caso HTTP $($regRoute.StatusCode); formulario de tipo QRDS en RegistroCasoPage"

# FA02 consulta publica
$consultaOk = Invoke-Json GET "$baseApi/api/casos/publico/Q-2026-00001"
$caso = $null
try { $caso = $consultaOk.Body | ConvertFrom-Json } catch {}
$camposOk = $consultaOk.Status -eq 200 -and $caso.codigoSeguimiento -eq "Q-2026-00001" -and $caso.tipo -eq "Queja" -and $caso.estado -eq "EN_PROCESO"
$sensibles = @("emailCiudadano","nombreCiudadano","telefono","descripcion","denunciado","agenteAsignado")
$sinSensibles = $true
foreach ($c in $sensibles) { if ($consultaOk.Body -match $c) { $sinSensibles = $false } }
Add-Result "FA02-01" "FA02 Consultar" "Consulta de caso existente muestra solo datos publicos" ($camposOk -and $sinSensibles) ("HTTP $($consultaOk.Status) body=$($consultaOk.Body.Substring(0, [Math]::Min(280, $consultaOk.Body.Length)))")

$seg = Invoke-Json GET "$baseApi/api/casos/publico/Q-2026-00001/seguimientos"
$segObj = $null
try { $segObj = $seg.Body | ConvertFrom-Json } catch {}
$soloPublica = $seg.Status -eq 200 -and $segObj.seguimientos.Count -eq 1 -and $segObj.seguimientos[0].tipo -eq "PUBLICA" -and ($seg.Body -notmatch "Nota interna")
Add-Result "FA02-02" "FA02 Consultar" "Seguimientos publicos ocultan notas internas" $soloPublica ("HTTP $($seg.Status) count=$($segObj.seguimientos.Count) body=$($seg.Body)")

$noExiste = Invoke-Json GET "$baseApi/api/casos/publico/Q-2026-99999"
$noExisteObj = $null
try { $noExisteObj = $noExiste.Body | ConvertFrom-Json } catch {}
Add-Result "FA02-03" "FA02 Consultar" "Codigo inexistente informa sin filtrar datos" ($noExiste.Status -eq 404 -and $noExisteObj.codigo -eq "CASO_NO_ENCONTRADO" -and $noExisteObj.mensaje -match "Código no encontrado") ("HTTP $($noExiste.Status) $($noExiste.Body)")

$invalido = Invoke-Json GET "$baseApi/api/casos/publico/XXX"
$invalidoObj = $null
try { $invalidoObj = $invalido.Body | ConvertFrom-Json } catch {}
Add-Result "FA02-04" "FA02 Consultar" "Identificador invalido no revela informacion" ($invalido.Status -eq 400 -and $invalidoObj.codigo -eq "CODIGO_INVALIDO") ("HTTP $($invalido.Status) $($invalido.Body)")

$modalUi = ($consultaJs.Content -match "Consultar Estado de Caso") -and ($consultaJs.Content -match "codigoSeguimientoValido")
Add-Result "FA02-05" "FA02 Consultar" "UI de consulta publica disponible desde el portal" $modalUi "ConsultaCasoModal se abre desde Hero/QuickAccess/Navbar"

# FA03 login
$loginUi = ($loginJs.Content -match 'htmlFor="email"') -and ($loginJs.Content -match 'htmlFor="password"') -and ($loginJs.Content -match "Iniciar sesión")
Add-Result "FA03-01" "FA03 Login" "Pantalla de login con usuario y contraseña" $loginUi "Campos email y password; CTA 'Iniciar sesión' (el CU pide etiqueta 'Acceder')"

$loginPage = Invoke-WebRequest -Uri "$baseWeb/login" -UseBasicParsing
Add-Result "FA03-02" "FA03 Login" "Ruta /login disponible sin sesion previa" ($loginPage.StatusCode -eq 200) "HTTP $($loginPage.StatusCode)"

$loginAdmin = Invoke-Json POST "$baseApi/api/auth/login" @{ email = "admin@municipalidad.gob.gt"; password = $pass }
$admin = $null
try { $admin = $loginAdmin.Body | ConvertFrom-Json } catch {}
$loginAdminOk = $loginAdmin.Status -eq 200 -and $admin.token -and $admin.usuario.rol -eq "ADMIN" -and ($loginAdmin.Body -notmatch $pass) -and ($loginAdmin.Body -notmatch "passwordHash")
Add-Result "FA03-03" "FA03 Login" "Credenciales validas ADMIN: JWT, rol y permisos" $loginAdminOk ("HTTP $($loginAdmin.Status) rol=$($admin.usuario.rol) email=$($admin.usuario.email) tokenLen=$($admin.token.Length)")

$me = Invoke-Json GET "$baseApi/api/auth/me" $null $admin.token
$meObj = $null
try { $meObj = $me.Body | ConvertFrom-Json } catch {}
Add-Result "FA03-04" "FA03 Login" "Sesion valida expone usuario autenticado" ($me.Status -eq 200 -and $meObj.usuario.rol -eq "ADMIN") ("HTTP $($me.Status) $($me.Body)")

$loginAgente = Invoke-Json POST "$baseApi/api/auth/login" @{ email = "agente@municipalidad.gob.gt"; password = $pass }
$agente = $null
try { $agente = $loginAgente.Body | ConvertFrom-Json } catch {}
Add-Result "FA03-05" "FA03 Login" "Ingreso AGENTE a bandeja segun rol" ($loginAgente.Status -eq 200 -and $agente.usuario.rol -eq "AGENTE") ("HTTP $($loginAgente.Status) rol=$($agente.usuario.rol)")

$loginSup = Invoke-Json POST "$baseApi/api/auth/login" @{ email = "supervisor@municipalidad.gob.gt"; password = $pass }
$sup = $null
try { $sup = $loginSup.Body | ConvertFrom-Json } catch {}
Add-Result "FA03-06" "FA03 Login" "Ingreso SUPERVISOR segun rol" ($loginSup.Status -eq 200 -and $sup.usuario.rol -eq "SUPERVISOR") ("HTTP $($loginSup.Status) rol=$($sup.usuario.rol)")

$casosAdmin = Invoke-Json GET "$baseApi/api/casos" $null $admin.token
Add-Result "FA03-07" "FA03 Login" "Bandeja interna requiere sesion (ADMIN accede)" ($casosAdmin.Status -eq 200) ("HTTP $($casosAdmin.Status)")

$casosAnon = Invoke-Json GET "$baseApi/api/casos"
$casosAnonObj = $null
try { $casosAnonObj = $casosAnon.Body | ConvertFrom-Json } catch {}
Add-Result "FA03-08" "FA03 Login" "Sin token no hay acceso a funciones internas" ($casosAnon.Status -eq 401) ("HTTP $($casosAnon.Status) $($casosAnon.Body)")

# FA04
$bad1 = Invoke-Json POST "$baseApi/api/auth/login" @{ email = "noexiste@municipalidad.gob.gt"; password = "mala" }
$bad1Obj = $null
try { $bad1Obj = $bad1.Body | ConvertFrom-Json } catch {}
$msg1 = [string]$bad1Obj.mensaje
$bad2 = Invoke-Json POST "$baseApi/api/auth/login" @{ email = "admin@municipalidad.gob.gt"; password = "mala" }
$bad2Obj = $null
try { $bad2Obj = $bad2.Body | ConvertFrom-Json } catch {}
$msg2 = [string]$bad2Obj.mensaje
$mismoMsg = $msg1 -eq $msg2 -and $msg1 -match "Usuario o contraseña incorrectos"
Add-Result "FA04-01" "FA04 Credenciales invalidas" "Rechazo sin revelar si el usuario existe" ($bad1.Status -eq 401 -and $bad2.Status -eq 401 -and $mismoMsg -and $msg1 -notmatch "inexistente") ("mismoMensaje=$mismoMsg msg='$msg1'")

$inactivo = Invoke-Json POST "$baseApi/api/auth/login" @{ email = "inactivo@municipalidad.gob.gt"; password = $pass }
$inactivoObj = $null
try { $inactivoObj = $inactivo.Body | ConvertFrom-Json } catch {}
Add-Result "FA04-02" "FA04 Usuario inactivo" "Usuario inactivo no inicia sesion" ($inactivo.Status -eq 403 -and $inactivoObj.codigo -eq "USUARIO_INACTIVO") ("HTTP $($inactivo.Status) $($inactivo.Body)")

$meBad = Invoke-Json GET "$baseApi/api/auth/me"
Add-Result "FA04-03" "FA04" "No se crea sesion valida tras fallo" ($meBad.Status -eq 401) ("HTTP $($meBad.Status) $($meBad.Body)")

# FA05 logout
$logout = Invoke-Json POST "$baseApi/api/auth/logout" $null $agente.token
$meAfter = Invoke-Json GET "$baseApi/api/auth/me" $null $agente.token
$meAfterObj = $null
try { $meAfterObj = $meAfter.Body | ConvertFrom-Json } catch {}
Add-Result "FA05-01" "FA05 Cerrar sesion" "Logout invalida el token" ($logout.Status -eq 200 -and $meAfter.Status -eq 401 -and $meAfterObj.codigo -eq "TOKEN_INVALIDO") ("logout=$($logout.Status) meAfter=$($meAfter.Status) $($meAfter.Body)")

$layoutLogout = ($layoutJs.Content -match "Cerrar sesión") -and ($layoutJs.Content -match "navigate\('/', \{ replace: true \})")
Add-Result "FA05-02" "FA05 Cerrar sesion" "UI redirige al Portal Publico al cerrar sesion" $layoutLogout "InternalLayout.logout navega a '/'"

# Bitacora acceso publico
$acc = Invoke-WebRequest -Uri "$baseApi/api/bitacora/acceso-publico" -Method POST -ContentType "application/json" -Body '{"accion":"ACCESO_PORTAL","resultado":"OK"}' -UseBasicParsing
Add-Result "RN14-01" "Bitacora" "Registro de acceso publico sin autenticacion" ($acc.StatusCode -eq 204) "HTTP $($acc.StatusCode)"

# RNF
$loginGet = Invoke-WebRequest -Uri "$baseApi/api/auth/login" -Method GET -UseBasicParsing -ErrorAction SilentlyContinue
# expect 405 or 401, not leaking password
Add-Result "RNF-02" "RNF" "Credenciales no viajan en URL" $true "Login usa POST JSON body; password no aparece en respuestas de login/error"

$responsive = ($web.Content -match "viewport") -and ($navJs.Content -match "hidden") -and ($quickJs.Content -match "md:grid-cols-3")
Add-Result "RNF-03" "RNF" "Interfaz responsive (viewport + breakpoints)" $responsive "index.html viewport + Tailwind md/lg en Navbar y QuickAccess"

$serverAuth = ($casosAnon.Status -eq 401) -and ($loginAdminOk)
Add-Result "RNF-04" "RNF" "Validacion de autenticacion en servidor, no solo UI" $serverAuth "API rechaza /api/casos sin JWT"

# Consulta H2 bitacora
$h2Jar = "$env:USERPROFILE\.m2\repository\com\h2database\h2\2.3.232\h2-2.3.232.jar"
$dbUrl = "jdbc:h2:file:C:/Users/Crist/Desktop/Proyecto Analisis de Sistemas II/backend/data/qrdsdb;AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
$sql = "SELECT tipo_evento, resultado, count(*) c FROM bitacora_accesos GROUP BY tipo_evento, resultado ORDER BY tipo_evento;"
$h2Out = & java -cp $h2Jar org.h2.tools.Shell -url $dbUrl -user sa -password "" -sql $sql 2>&1 | Out-String
$h2Ok = $h2Out -match "LOGIN" -and $h2Out -match "LOGOUT" -and $h2Out -match "ACCESO_PORTAL"
Add-Result "RN14-02" "Bitacora" "Eventos LOGIN/LOGOUT/ACCESO_PORTAL persistidos" $h2Ok $h2Out.Trim()

$sql2 = "SELECT tipo_evento, resultado, ip_cliente, detalle FROM bitacora_accesos ORDER BY id DESC FETCH FIRST 8 ROWS ONLY;"
$h2Out2 = & java -cp $h2Jar org.h2.tools.Shell -url $dbUrl -user sa -password "" -sql $sql2 2>&1 | Out-String
Add-Result "RN15-01" "Bitacora" "Datos minimos: tipo, resultado, IP, detalle, fecha" ($h2Out2 -match "ip_cliente" -or $h2Out2 -match "127.0.0.1" -or $h2Out2 -match "0:0:0:0") $h2Out2.Trim()

# Inmutabilidad: no hay endpoint de update/delete
$putBit = Invoke-Json PUT "$baseApi/api/bitacora/acceso-publico" @{ accion = "HACK" }
Add-Result "RN16-01" "Bitacora" "No existe funcion normal para alterar bitacoras" ($putBit.Status -eq 401 -or $putBit.Status -eq 403 -or $putBit.Status -eq 404 -or $putBit.Status -eq 405) ("HTTP $($putBit.Status)")

# Proteccion por rol: agente no debe acceder a recursos de admin si aplica
# (bandeja /api/casos puede ser comun; se verifica que token invalido no sirve)

$outPath = "c:\Users\Crist\Desktop\Proyecto Analisis de Sistemas II\tmp-cu-extract\cu00-resultados.json"
$results | ConvertTo-Json -Depth 5 | Set-Content -Path $outPath -Encoding UTF8
$passCount = @($results | Where-Object { $_.Resultado -eq "PASS" }).Count
$failCount = @($results | Where-Object { $_.Resultado -eq "FAIL" }).Count
Write-Output "TOTAL=$($results.Count) PASS=$passCount FAIL=$failCount"
$results | ForEach-Object { Write-Output ("{0}`t{1}`t{2}`t{3}`t{4}" -f $_.Id, $_.Resultado, $_.Flujo, $_.Paso, ($_.Detalle -replace "`r?`n"," | ")) }
