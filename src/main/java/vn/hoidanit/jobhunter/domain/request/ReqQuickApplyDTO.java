package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqQuickApplyDTO {
    @NotNull(message = "jobId không được để trống")
    private Long jobId;

    @NotBlank(message = "Họ tên không được để trống")
    private String name;

    @NotBlank(message = "Email không được để trống")
    private String email;

    private String phone;

    @NotBlank(message = "File CV không được để trống")
    private String urlCV;

    private String note;
}
