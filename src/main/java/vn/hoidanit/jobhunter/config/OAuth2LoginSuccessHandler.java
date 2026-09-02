package vn.hoidanit.jobhunter.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResLoginDTO;
import vn.hoidanit.jobhunter.repository.RoleRepository;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.constant.GenderEnum;

import java.io.IOException;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final SecurityUtil securityUtil;
    private final RoleRepository roleRepository;

    @Value("${jwt.refresh-token-validity-in-seconds}")
    private Long refreshTokenExpiration;

    // URL của Frontend để redirect về sau khi login thành công
    private static final String FRONTEND_REDIRECT_URL = "http://localhost:3000/oauth2/redirect";

    public OAuth2LoginSuccessHandler(UserService userService,
                                     @Lazy SecurityUtil securityUtil,
                                     RoleRepository roleRepository) {
        this.userService = userService;
        this.securityUtil = securityUtil;
        this.roleRepository = roleRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // Lấy thông tin từ Google
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null || email.isEmpty()) {
            response.sendRedirect(FRONTEND_REDIRECT_URL + "?error=email_not_found");
            return;
        }

        // Tìm hoặc tạo mới User trong database
        User user = userService.handleGetUserByUsername(email);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setName(name != null ? name : email);
            // Đặt password ngẫu nhiên vì đây là tài khoản OAuth2 (không đăng nhập bằng password)
            user.setPassword(UUID.randomUUID().toString());
            user.setAge(0);
            user.setGender(GenderEnum.OTHER);
            user.setAddress("");


            user = userService.handleCreateUser(user);
        }

        // Tạo JWT access token nội bộ (giống như đăng nhập bằng email/password)
        ResLoginDTO resLoginDTO = new ResLoginDTO();
        ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()
        );
        resLoginDTO.setUser(userLogin);

        String accessToken = securityUtil.createAccessToken(email, resLoginDTO);
        resLoginDTO.setAccess_token(accessToken);

        // Tạo và lưu refresh token
        String refreshToken = securityUtil.createRefreshToken(email, resLoginDTO);
        userService.updateUserToken(refreshToken, email);

        // Đặt refresh_token vào cookie
        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(refreshTokenExpiration.intValue());
        // refreshCookie.setSecure(true); // Bật lên khi deploy HTTPS
        response.addCookie(refreshCookie);

        // Redirect về frontend kèm access_token
        String redirectUrl = FRONTEND_REDIRECT_URL + "?access_token=" + accessToken;
        response.sendRedirect(redirectUrl);
    }
}
