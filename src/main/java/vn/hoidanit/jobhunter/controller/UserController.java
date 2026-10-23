package vn.hoidanit.jobhunter.controller;

import com.turkraft.springfilter.boot.Filter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqChangePasswordDTO;
import vn.hoidanit.jobhunter.domain.response.ResUpdateUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResCreateUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.EmailService;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    @org.springframework.beans.factory.annotation.Autowired
    private vn.hoidanit.jobhunter.service.AccessService access;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserController(UserService userService, PasswordEncoder passwordEncoder,EmailService emailService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @PostMapping("/users")
    public ResponseEntity<ResCreateUserDTO> createNewUser(@Valid  @RequestBody User user) throws IdInvalidException {
        if (!access.isAdmin(access.currentUser())) throw new org.springframework.security.access.AccessDeniedException("Only administrators can create accounts with roles");
        user.setId(null);
        boolean isEmailExistInSystem = userService.isEmailExist(user.getEmail());
        if(isEmailExistInSystem){
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

    @DeleteMapping("/users/{id}")
    @ApiMessage("Delete a user")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) throws IdInvalidException {
        if (!access.isAdmin(access.currentUser())) throw new org.springframework.security.access.AccessDeniedException("Only administrators can access other accounts");
        User currentUser = userService.fetchUserById(id);
        if(currentUser==null){
            throw new IdInvalidException("user with id = "+id+" not exist");
        }
        userService.handleDeleteUser(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ResUserDTO> fetchUserById(@PathVariable Long id) throws IdInvalidException {
        if (!access.isAdmin(access.currentUser())) throw new org.springframework.security.access.AccessDeniedException("Only administrators can access other accounts");
        User currentUser = userService.fetchUserById(id);
        if(currentUser==null){
            throw new IdInvalidException("user with id = "+id+" not exist");
        }
        return ResponseEntity.ok(userService.convertToResUserDTO(currentUser));
    }

    @GetMapping("/users")
    @ApiMessage("fetch all users")
    public ResponseEntity<ResultPaginationDTO> getAllUser(@Filter Specification<User> spec, Pageable pageable){

        if (!access.isAdmin(access.currentUser())) throw new org.springframework.security.access.AccessDeniedException("Only administrators can list accounts");
        return ResponseEntity.status(HttpStatus.OK).body(userService.fetchAllUser(spec,pageable));
    }

    @PutMapping("/users")
    public ResponseEntity<ResUpdateUserDTO> updateUser(@RequestBody User user) throws IdInvalidException {
        if (!access.isAdmin(access.currentUser())) throw new org.springframework.security.access.AccessDeniedException("Use the personal profile endpoint");
        User currentUser = userService.handleUpdate(user);
        if(currentUser==null){
            throw new IdInvalidException("user with id = "+user.getId()+" not exist");
        }
        return ResponseEntity.status(HttpStatus.OK).body(userService.convertToResUpdateUserDTO(currentUser));
    }

    @GetMapping("/users/me")
    public ResponseEntity<ResUserDTO> profile() {
        return ResponseEntity.ok(userService.convertToResUserDTO(access.currentUser()));
    }

    @PutMapping("/users/me")
    public ResponseEntity<ResUpdateUserDTO> updateProfile(@RequestBody User request) {
        return ResponseEntity.ok(userService.convertToResUpdateUserDTO(
                userService.updateProfile(request, access.currentUser())));
    }

    @PostMapping("/users/change-password")
    @ApiMessage("Change password")
    public ResponseEntity<String> changePassword(@RequestBody ReqChangePasswordDTO request) throws IdInvalidException {
        // Tìm user theo email
        User currentUser = access.currentUser();
        if (currentUser == null) {
            throw new IdInvalidException("User không tồn tại");
        }
        // Kiểm tra mật khẩu cũ
        if (!this.passwordEncoder.matches(request.getOldpass(), currentUser.getPassword())) {
            throw new IdInvalidException("Mật khẩu cũ không chính xác");
        }
        // Mã hóa và cập nhật mật khẩu mới
        if (request.getNewpass() == null || request.getNewpass().length() < 6) {
            throw new IdInvalidException("Password must contain at least 6 characters");
        }
        String newHashPassword = this.passwordEncoder.encode(request.getNewpass());
        this.userService.changePassword(currentUser, newHashPassword);
        return ResponseEntity.ok().body("Đổi mật khẩu thành công");
    }
}
