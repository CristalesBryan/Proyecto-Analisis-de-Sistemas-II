package gt.municipalidad.qrds.security;

import gt.municipalidad.qrds.entity.Usuario;
import gt.municipalidad.qrds.service.ParametroService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final ParametroService parametroService;

    public JwtService(
            @Value("${qrds.jwt.secret}") String secret,
            ParametroService parametroService) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parametroService = parametroService;
    }

    public String generar(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plusSeconds(parametroService.horasSesion() * 3600L);
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("userId", usuario.getId())
                .claim("email", usuario.getEmail())
                .claim("rol", usuario.getRol().name())
                .claim("nombre", usuario.getNombre())
                .claim("tokenVersion", usuario.getTokenVersion())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expira))
                .signWith(key)
                .compact();
    }

    public Claims parsear(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long userId(Claims claims) {
        Object valor = claims.get("userId");
        if (valor instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(valor));
    }

    public int tokenVersion(Claims claims) {
        Object valor = claims.get("tokenVersion");
        if (valor instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
