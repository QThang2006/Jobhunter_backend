package vn.hoidanit.jobhunter.config;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.service.UserService;

import java.util.Collections;

@Component("userDetailService")
public class UserDetailsCustom implements UserDetailsService {

    private final UserService userService;


    public UserDetailsCustom(UserService userService) {
        this.userService = userService;

    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.handleGetUserByUsername(username);
        if(user == null){
            throw new UsernameNotFoundException("username/password ko hop le");
        }
        System.out.println("Found user: " + user.getEmail());
        System.out.println("User password in DB: " + user.getPassword());

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
