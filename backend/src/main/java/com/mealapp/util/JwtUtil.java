package com.mealapp.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JwtUtil
 * -------
 * A minimal JWT (HS256) implementation: header.payload.signature, base64url
 * encoded, HMAC-SHA256 signed via javax.crypto (built into the JDK). Not a
 * full RFC 7519 implementation — just enough to authenticate our own API
 * without pulling in a JWT library.
 */
public final class JwtUtil {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final String secret;
    private final long expirySeconds;

    public JwtUtil(String secret, long expirySeconds) {
        this.secret = secret;
        this.expirySeconds = expirySeconds;
    }

    public String sign(Map<String, Object> claims) {
        Map<String, Object> payload = new LinkedHashMap<>(claims);
        long now = System.currentTimeMillis() / 1000;
        payload.put("iat", now);
        payload.put("exp", now + expirySeconds);

        String headerB64 = B64.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = B64.encodeToString(JsonUtil.write(payload).getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + payloadB64;
        String signature = hmacSha256(signingInput);
        return signingInput + "." + signature;
    }

    /** Returns the decoded claims map, or null if the token is missing/expired/tampered. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> verify(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = hmacSha256(signingInput);
        if (!constantTimeEquals(expectedSignature, parts[2])) return null;

        try {
            String payloadJson = new String(B64D.decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = (Map<String, Object>) JsonUtil.parse(payloadJson);
            Double exp = JsonUtil.getDouble(claims, "exp");
            if (exp == null || System.currentTimeMillis() / 1000 > exp) return null;
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return B64.encodeToString(raw);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }
}
