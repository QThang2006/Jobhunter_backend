package vn.hoidanit.jobhunter.service;


import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.Subscriber;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResCreateUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResUpdateUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.SubscriberRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;


import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service

public class UserService {
    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final RoleService roleService;
    private final SubscriberRepository subscriberRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final EmailService emailService;
    @Lazy
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, CompanyService companyService, RoleService roleService, SubscriberRepository subscriberRepository, SimpMessagingTemplate simpMessagingTemplate, EmailService emailService, @Lazy PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.companyService = companyService;
        this.roleService = roleService;
        this.subscriberRepository = subscriberRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.emailService = emailService;

        this.passwordEncoder = passwordEncoder;
    }

    public User handleCreateUser(User user){
        if(user.getCompany() != null){
            Optional<Company> companyOptionalCreated = companyService.findById(user.getCompany().getId());
            user.setCompany(companyOptionalCreated.isPresent() ? companyOptionalCreated.get() : null);
        }

        if(user.getRole() != null){
            Role role = roleService.fetchById(user.getRole().getId());
            user.setRole(role != null ? role : null);
        }
        this.simpMessagingTemplate.convertAndSend("/topic/users", "REFRESH");
        return userRepository.save(user);
    }

    @Transactional
    public void handleDeleteUser(Long id){
        User user = fetchUserById(id);
        Subscriber subscriber = subscriberRepository.findByEmail(user.getEmail());
        subscriberRepository.delete(subscriber);
        userRepository.deleteById(id);
        this.simpMessagingTemplate.convertAndSend("/topic/users", "REFRESH");
    }

    public User fetchUserById(Long id){
        Optional<User> userOptional = userRepository.findById(id);
        if(userOptional.isPresent()){
            return userOptional.get();
        }else return null;
    }

    public ResultPaginationDTO fetchAllUser(Specification<User> spec, Pageable pageable){
        Page<User> pageUser = userRepository.findAll(spec,pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageUser.getNumber()+1);
        mt.setPageSize(pageUser.getSize());
        mt.setPages(pageUser.getTotalPages());
        mt.setTotal(pageUser.getTotalElements());

        List<ResUserDTO> listUser = pageUser.getContent()
                .stream().map(item -> convertToResUserDTO(item))
                .collect(Collectors.toList());

        rs.setMeta(mt);
        rs.setResult(listUser);

        return  rs;
    }

    public boolean isEmailExist(String email){
        return userRepository.existsByEmail(email);
    }

    public User handleUpdate(User user){
        User userUpdate = fetchUserById(user.getId());
        if(userUpdate != null){
            userUpdate.setName(user.getName());
            userUpdate.setEmail(user.getEmail());

            if(user.getCompany() != null && user.getCompany().getId() != null) {
                Optional<Company> companyOptional = companyService.findById(user.getCompany().getId());
                userUpdate.setCompany(companyOptional.orElse(null));
            }

            // Update role nếu có
            if(user.getRole() != null && user.getRole().getId() != null) {
                Role role = roleService.fetchById(user.getRole().getId());
                userUpdate.setRole(role);
            }

            userUpdate = userRepository.save(userUpdate);
        }
        this.simpMessagingTemplate.convertAndSend("/topic/users", "REFRESH");
        return userUpdate;
    }


    public User handleGetUserByUsername(String username){
        return  userRepository.findByEmail(username);
    }

    public ResCreateUserDTO convertToResCreatedUserDTO(User user){
        ResCreateUserDTO res = new ResCreateUserDTO();
        ResCreateUserDTO.CompanyUser com = new ResCreateUserDTO.CompanyUser();
        res.setCreatedAt(user.getCreatedAt());
        res.setAge(user.getAge());
        res.setId(user.getId());
        res.setName(user.getName());
        res.setEmail(user.getEmail());
        res.setGender(user.getGender());
        res.setAddress(user.getAddress());

        if(user.getCompany() != null){
            com.setId(user.getCompany().getId());
            com.setName(user.getCompany().getName());
            res.setCompany(com);
        }
        return res;
    }

    public ResUserDTO convertToResUserDTO(User user){
        ResUserDTO res = new ResUserDTO();
        ResUserDTO.CompanyUser com = new ResUserDTO.CompanyUser();
        ResUserDTO.RoleUser role = new ResUserDTO.RoleUser();

        res.setCreatedAt(user.getCreatedAt());
        res.setAge(user.getAge());
        res.setId(user.getId());
        res.setName(user.getName());
        res.setEmail(user.getEmail());
        res.setGender(user.getGender());
        res.setAddress(user.getAddress());
        res.setUpdateAt(user.getUpdatedAt());

        if(user.getCompany() != null){
            com.setId(user.getCompany().getId());
            com.setName(user.getCompany().getName());
            res.setCompany(com);
        }

        if(user.getRole() != null){
            role.setId(user.getRole().getId());
            role.setName(user.getRole().getName());
            res.setRole(role);
        }

        return res;
    }

    public ResUpdateUserDTO convertToResUpdateUserDTO(User user){
        ResUpdateUserDTO res = new ResUpdateUserDTO();
        ResUpdateUserDTO.CompanyUser com = new ResUpdateUserDTO.CompanyUser();
        res.setAge(user.getAge());
        res.setId(user.getId());
        res.setName(user.getName());
        res.setGender(user.getGender());
        res.setAddress(user.getAddress());
        res.setUpdateAt(user.getUpdatedAt());

        if(user.getCompany() != null){
            com.setId(user.getCompany().getId());
            com.setName(user.getCompany().getName());
            res.setCompany(com);
        }

        return res;
    }

    public void updateUserToken(String token, String email){
        User currentUser = handleGetUserByUsername(email);
        if(currentUser != null){
            currentUser.setRefreshToken(token);
            userRepository.save(currentUser);
        }
    }

    public User getUserByRefreshTokenAndEmail(String token, String email){
        return userRepository.findByRefreshTokenAndEmail(token,email);
    }

    public void sendOTP(String email) {
        User user = userRepository.findByEmail(email);
        if (user != null) {
            String otp = String.valueOf((int)((Math.random() * 900000) + 100000)); // 6 digits
            user.setOtp(otp);
            userRepository.save(user);
            emailService.sendSimpleEmail(otp,email);
        }
    }

    public boolean verifyOTP(String email, String otp) {
        User user = userRepository.findByEmail(email);
        if (user != null && user.getOtp() != null) {
            return user.getOtp().equals(otp);
        }
        return false;
    }

    public void resetPassword(String email, String otp, String newPass) {
        if (verifyOTP(email, otp)) {
            User user = userRepository.findByEmail(email);
            // Trong dự án thật, bạn nên dùng PasswordEncoder.encode(newPass)
            newPass = passwordEncoder.encode(newPass);
            user.setPassword(newPass);
            user.setOtp(null); // Xóa OTP sau khi dùng
            userRepository.save(user);
        }
    }

}
