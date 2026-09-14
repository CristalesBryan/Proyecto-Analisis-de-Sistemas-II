package gt.municipalidad.qrds.security;

import gt.municipalidad.qrds.entity.Usuario;
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
    private final long expirationHours;

    public JwtService(
            @Value("${qrds.jwt.secret}") String secret,
            @Value("${qrds.jwt.expiration-hours}") long expirationHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    public String generar(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plusSeconds(expirationHours * 3600);
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
