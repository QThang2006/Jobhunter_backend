package vn.hoidanit.jobhunter;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TestEncode {
    public static void main(String[] args) {
        String encoded = new BCryptPasswordEncoder().encode("123456");
        System.out.println(encoded);
    }
}
