package gt.municipalidad.qrds.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.municipalidad.qrds.exception.ErrorResponse;
import gt.municipalidad.qrds.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, UsuarioRepository usuarioRepository, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();
        try {
            Claims claims = jwtService.parsear(token);
            Long userId = jwtService.userId(claims);
            var usuario = usuarioRepository.findById(userId).orElse(null);
            if (usuario == null || !usuario.isActivo() || usuario.getTokenVersion() != jwtService.tokenVersion(claims)) {
                if (esRutaPublica(request)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                escribir401(response);
                return;
            }
            var principal = new UsuarioPrincipal(usuario);
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            if (esRutaPublica(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            escribir401(response);
        }
    }

    private boolean esRutaPublica(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        if (path == null) {
            return false;
        }
        return path.equals("/api/sistema/estado")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/ciudadano/verificar-inicio")
                || path.equals("/api/auth/ciudadano/confirmar")
                || path.equals("/api/bitacora/acceso-publico")
                || path.startsWith("/api/catalogos/")
                || path.equals("/api/casos/captcha")
                || path.equals("/api/casos/publico")
                || path.startsWith("/api/casos/publico/")
                || path.startsWith("/h2-console");
    }

    private void escribir401(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ErrorResponse.de(
                        "Su sesión ha expirado. Por favor inicie sesión nuevamente.",
                        "TOKEN_INVALIDO"));
    }
}
