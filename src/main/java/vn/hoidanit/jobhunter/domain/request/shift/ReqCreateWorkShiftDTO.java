package vn.hoidanit.jobhunter.domain.request.shift;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ReqCreateWorkShiftDTO {
    @NotNull(message = "Nhân viên không được để trống")
    private Long userId;

    @NotNull(message = "Tiệm bánh không được để trống")
    private Long companyId;

    private Long jobId;

    @NotNull(message = "Ngày làm việc không được để trống")
    private LocalDate shiftDate;

    private String shiftName;
    private String startTime;
    private String endTime;
    private String note;
}
