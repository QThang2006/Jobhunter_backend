package vn.hoidanit.jobhunter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieFactory {
    @Value("${app.auth.cookie-secure:true}")
    private boolean secure;
    @Value("${jwt.refresh-token-validity-in-seconds}")
    private long lifetime;

    public ResponseCookie refresh(String token) {
        return ResponseCookie.from("refresh_token", token == null ? "" : token)
                .httpOnly(true).secure(secure).sameSite(secure ? "None" : "Lax")
                .path("/").maxAge(token == null ? 0 : lifetime).build();
    }
}
