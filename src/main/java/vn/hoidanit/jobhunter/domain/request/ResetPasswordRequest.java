package vn.hoidanit.jobhunter.domain.request;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ResetPasswordRequest {
    private String email;
    private String otp;
    private String newPass;
}