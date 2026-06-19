package vn.hoidanit.jobhunter.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ForgotPasswordRequest;
import vn.hoidanit.jobhunter.domain.request.ReqLoginDTO;
import vn.hoidanit.jobhunter.domain.request.ResetPasswordRequest;
import vn.hoidanit.jobhunter.domain.request.VerifyOTPRequest;
import vn.hoidanit.jobhunter.domain.response.ResCreateUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResLoginDTO;
import vn.hoidanit.jobhunter.repository.RoleRepository;
import vn.hoidanit.jobhunter.service.EmailService;
import vn.hoidanit.jobhunter.service.RoleService;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityUtil securityUtil;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final EmailService emailService;

    @Value("${jwt.refresh-token-validity-in-seconds}")
    private Long refreshTokenExpiration;

    public AuthController(UserService userService, AuthenticationManager authenticationManager, SecurityUtil securityUtil, PasswordEncoder passwordEncoder, RoleRepository roleRepository, EmailService emailService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.securityUtil = securityUtil;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.emailService = emailService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ResLoginDTO> login(@Valid @RequestBody ReqLoginDTO reqLoginDTO) {

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(reqLoginDTO.getUsername(), reqLoginDTO.getPassword());


        Authentication authentication = authenticationManager.authenticate(authenticationToken);


        SecurityContextHolder.getContext().setAuthentication(authentication);

        ResLoginDTO res = new ResLoginDTO();
        User user = userService.handleGetUserByUsername(reqLoginDTO.getUsername());

        ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()
        );
        res.setUser(userLogin);

        String access_token = securityUtil.createAccessToken(authentication.getName(),res);
        res.setAccess_token(access_token);

        String refresh_token = securityUtil.createRefreshToken(reqLoginDTO.getUsername(), res);
        userService.updateUserToken(refresh_token, reqLoginDTO.getUsername());

        ResponseCookie responseCookie = ResponseCookie
                .from("refresh_token", refresh_token)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshTokenExpiration)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(res);
    }

    @GetMapping("/auth/account")
    @ApiMessage("fetch account")
    public ResponseEntity<ResLoginDTO.UserGetAccount> getAccount(){
        String email = SecurityUtil.getCurrentUserLogin().isPresent()
                ? SecurityUtil.getCurrentUserLogin().get()
                : "";
        User currentUserDB = userService.handleGetUserByUsername(email);
        Role role = currentUserDB.getRole();


        ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin();
        ResLoginDTO.UserGetAccount userGetAccount = new ResLoginDTO.UserGetAccount();
        if(currentUserDB != null){
            userLogin.setId(currentUserDB.getId());
            userLogin.setName(currentUserDB.getName());
            userLogin.setEmail(currentUserDB.getEmail());
            if (role != null && role.isActive()) {
                userLogin.setRole(currentUserDB.getRole());
            } else {
                userLogin.setRole(null);
            }
            userGetAccount.setUser(userLogin);
        }
        return ResponseEntity.ok(userGetAccount);
    }

    @GetMapping("/auth/refresh")
    @ApiMessage("Get user by refresh token")
    public ResponseEntity<ResLoginDTO> getRefreshToken(
            @CookieValue(name = "refresh_token", defaultValue = "abc") String refresh_token) throws IdInvalidException {

        if(refresh_token.equals("abc")){
            throw new IdInvalidException("ko co refresh Token cookie");
        }
        Jwt decodedToken = securityUtil.checkValidRefreshToken(refresh_token);
        String email = decodedToken.getSubject();

        User currentUser = userService.getUserByRefreshTokenAndEmail(refresh_token,email);
        if(currentUser == null){
            throw new IdInvalidException("Refresh Token ko hop le");
        }

        ResLoginDTO res = new ResLoginDTO();
        User user = userService.handleGetUserByUsername(email);

        ResLoginDTO.UserLogin userLogin = new ResLoginDTO.UserLogin(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()
        );
        res.setUser(userLogin);

        String access_token = securityUtil.createAccessToken(email,res);
        res.setAccess_token(access_token);

        String new_refresh_token = securityUtil.createRefreshToken(email, res);
        userService.updateUserToken(new_refresh_token,email);

        ResponseCookie responseCookie = ResponseCookie
                .from("refresh_token", refresh_token)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshTokenExpiration)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(res);
    }

    @PostMapping("/auth/logout")
    @ApiMessage("Logout User")
    public ResponseEntity<Void> logout() throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin().isPresent()
                ? SecurityUtil.getCurrentUserLogin().get()
                : "";
        if(email.equals("")){
            throw new IdInvalidException("Access Token ko hop le");
        }
        userService.updateUserToken("",email);
        ResponseCookie deleteCookie = ResponseCookie
                .from("refresh_token", null)
                .httpOnly(true)
                .path("/")
                .secure(true)
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,deleteCookie.toString())
                .body(null);
    }

    @PostMapping("/auth/register")
    @ApiMessage("register a user")
    public ResponseEntity<ResCreateUserDTO> register(@Valid  @RequestBody User user) throws IdInvalidException {
        boolean isEmailExist1 = userService.isEmailExist(user.getEmail());
        if(isEmailExist1){
            throw new IdInvalidException("Email: "+user.getEmail()+" da ton tai");
        }
        boolean isEmailExist = emailService.checkEmailExist(user.getEmail());
        if(!isEmailExist){
            throw new IdInvalidException("Email ko chinh xac");
        }
        String hashPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashPassword);
        User user1 = userService.handleCreateUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.convertToResCreatedUserDTO(user1));
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody ForgotPasswordRequest req) {
        userService.sendOTP(req.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/auth/verify-otp")
    public ResponseEntity<Boolean> verifyOTP(@RequestBody VerifyOTPRequest req) {
        boolean isValid = userService.verifyOTP(req.getEmail(), req.getOtp());
        return ResponseEntity.ok(isValid);
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest req) {
        userService.resetPassword(req.getEmail(), req.getOtp(), req.getNewPass());
        return ResponseEntity.ok().build();
    }

}
