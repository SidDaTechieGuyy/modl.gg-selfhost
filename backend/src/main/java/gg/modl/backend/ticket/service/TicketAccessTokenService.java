package gg.modl.backend.ticket.service;

import gg.modl.backend.server.data.Server;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class TicketAccessTokenService {
    private final byte[] secret;
    @Value("${BASE_URL:http://localhost:8080}")
    private String baseUrl;
    public TicketAccessTokenService(@Value("#{@instanceSecret.value}") String secret) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("SESSION_SECRET must have at least 32 characters");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }
    public String issueToken(Server server, String ticketId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(("ticket-access\n" + server.getId() + "\n" + ticketId).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Cannot sign ticket access", e); }
    }
    public String accessLink(Server server, String ticketId) {
        return baseUrl + "/ticket/" + org.springframework.web.util.UriUtils.encodePathSegment(ticketId, StandardCharsets.UTF_8)
            + "?token=" + issueToken(server, ticketId);
    }

    public boolean validateToken(Server server, String ticketId, String token) {
        if (token == null || token.length() != 43) return false;
        return MessageDigest.isEqual(issueToken(server, ticketId).getBytes(StandardCharsets.US_ASCII), token.getBytes(StandardCharsets.US_ASCII));
    }
    public boolean validateAppealCreateToken(Server server, String punishmentId, String uuid, String token) {
        return validateToken(server, "appeal-create:" + punishmentId + ":" + (uuid == null ? "" : uuid), token);
    }

    public void grantAccess(Server server, String id, String resource, jakarta.servlet.http.HttpServletResponse response) {
        var cookie = new jakarta.servlet.http.Cookie(resource + "_auth_" + id, issueToken(server, id));
        cookie.setPath("/");
        cookie.setMaxAge(30 * 24 * 60 * 60);
        cookie.setSecure(baseUrl != null && baseUrl.startsWith("https://"));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
