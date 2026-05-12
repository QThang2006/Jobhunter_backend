package vn.hoidanit.jobhunter.domain.request;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class VerifyOTPRequest {
    private String email;
    private String otp;
}