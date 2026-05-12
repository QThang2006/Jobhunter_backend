package vn.hoidanit.jobhunter.domain.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqChangePasswordDTO {
    private String email;
    private String oldpass;
    private String newpass;
}