package vn.hoidanit.jobhunter.domain.request;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ForgotPasswordRequest {
    private String email;
}
