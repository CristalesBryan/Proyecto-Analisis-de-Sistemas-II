package gt.municipalidad.qrds.util;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpRequests {

    private HttpRequests() {
    }

    public static String ipCliente(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        return agent == null ? "" : agent;
    }
}
