package gt.municipalidad.qrds.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.exception.ErrorResponse;
import gt.municipalidad.qrds.util.HttpRequests;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private final int loginPorMinuto;
    private final int registrosPorVentana;
    private final long ventanaRegistroMs;
    private final int consultasPorMinuto;
    private final ObjectMapper objectMapper;
    private final Map<String, Deque<Long>> ventanas = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${qrds.login.rate-limit-por-minuto}") int loginPorMinuto,
            @Value("${qrds.registro.rate-limit}") int registrosPorVentana,
            @Value("${qrds.registro.ventana-minutos}") int ventanaRegistroMinutos,
            @Value("${qrds.consulta.rate-limit-por-minuto}") int consultasPorMinuto,
            ObjectMapper objectMapper) {
        this.loginPorMinuto = loginPorMinuto;
        this.registrosPorVentana = registrosPorVentana;
        this.ventanaRegistroMs = ventanaRegistroMinutos * 60_000L;
        this.consultasPorMinuto = consultasPorMinuto;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return regla(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Regla regla = regla(request);
        String clave = regla.nombre() + ":" + HttpRequests.ipCliente(request);
        long ahora = Instant.now().toEpochMilli();
        long desde = ahora - regla.ventanaMs();
        Deque<Long> marcas = ventanas.computeIfAbsent(clave, key -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && marcas.peekFirst() < desde) {
                marcas.pollFirst();
            }
            if (marcas.size() >= regla.limite()) {
                response.setStatus(429);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(), ErrorResponse.de(regla.mensaje(), "RATE_LIMIT"));
                return;
            }
            marcas.addLast(ahora);
        }
        filterChain.doFilter(request, response);
    }

    private Regla regla(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        if (path == null) {
            return null;
        }
        String metodo = request.getMethod();
        if ("POST".equalsIgnoreCase(metodo) && path.endsWith("/api/auth/login")) {
            return new Regla(
                    "login",
                    loginPorMinuto,
                    60_000,
                    "Demasiados intentos de inicio de sesión. Espere un minuto e intente nuevamente.");
        }
        if ("POST".equalsIgnoreCase(metodo) && path.endsWith("/api/casos/publico")) {
            return new Regla(
                    "registro",
                    registrosPorVentana,
                    ventanaRegistroMs,
                    "Demasiados registros desde esta dirección. Intente más tarde.");
        }
        if ("POST".equalsIgnoreCase(metodo)
                && (path.endsWith("/api/auth/ciudadano/verificar-inicio")
                        || path.endsWith("/api/auth/ciudadano/confirmar"))) {
            return new Regla(
                    "registro-ciudadano",
                    registrosPorVentana,
                    ventanaRegistroMs,
                    "Demasiados intentos de registro desde esta dirección. Intente más tarde.");
        }
        if ("GET".equalsIgnoreCase(metodo) && esConsultaPublica(path)) {
            return new Regla(
                    "consulta",
                    consultasPorMinuto,
                    60_000,
                    "Demasiadas consultas desde esta dirección. Intente más tarde.");
        }
        return null;
    }

    private boolean esConsultaPublica(String path) {
        return path.matches(".*/api/casos/publico/[^/]+(/seguimientos)?");
    }

    private record Regla(String nombre, int limite, long ventanaMs, String mensaje) {
    }
}
