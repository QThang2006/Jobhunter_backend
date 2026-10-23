package vn.hoidanit.jobhunter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import vn.hoidanit.jobhunter.config.AuthCookieFactory;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResLoginDTO;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerTests {
    @Test
    void refreshPersistsAndReturnsSameNewTokenWithCrossSiteCookie() throws Exception {
        UserService users = mock(UserService.class);
        SecurityUtil security = mock(SecurityUtil.class);
        AuthController controller = new AuthController(users, null, security, null, null, null);
        AuthCookieFactory cookies = new AuthCookieFactory();
        ReflectionTestUtils.setField(cookies, "secure", true);
        ReflectionTestUtils.setField(cookies, "lifetime", 3600L);
        ReflectionTestUtils.setField(controller, "cookies", cookies);
        User user = new User(); user.setId(1L); user.setEmail("user@example.test");
        when(security.checkValidRefreshToken("old-token")).thenReturn(Jwt.withTokenValue("old-token")
                .header("alg", "HS512").subject(user.getEmail()).build());
        when(users.getUserByRefreshTokenAndEmail("old-token", user.getEmail())).thenReturn(user);
        when(users.handleGetUserByUsername(user.getEmail())).thenReturn(user);
        when(security.createRefreshToken(eq(user.getEmail()), any())).thenReturn("new-token");
        when(security.createAccessToken(eq(user.getEmail()), any())).thenReturn("access-token");
        ResponseEntity<ResLoginDTO> response = controller.getRefreshToken("old-token");
        String cookie = response.getHeaders().getFirst("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("refresh_token=new-token"));
        assertTrue(cookie.contains("SameSite=None"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("HttpOnly"));
        verify(users).updateUserToken("new-token", user.getEmail());
    }

    @Test
    void logoutCookieUsesSameScopeAndLocalCookiesUseLax() {
        AuthCookieFactory cookies = new AuthCookieFactory();
        ReflectionTestUtils.setField(cookies, "secure", false);
        ReflectionTestUtils.setField(cookies, "lifetime", 3600L);
        assertEquals("Lax", cookies.refresh("token").getSameSite());
        assertFalse(cookies.refresh("token").isSecure());
        assertEquals(0, cookies.refresh(null).getMaxAge().getSeconds());
        assertEquals("/", cookies.refresh(null).getPath());
    }
}
