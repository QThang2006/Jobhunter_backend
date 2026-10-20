package vn.hoidanit.jobhunter.domain.request.shift;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ReqTransferWorkShiftDTO {
    @NotNull(message = "Mã ca làm việc không được để trống")
    private Long shiftId;

    @NotNull(message = "Nhân viên nhận ca mới không được để trống")
    private Long newUserId;

    private LocalDate newShiftDate; // Tùy chọn đổi ngày làm
    private String reason;          // Lý do chuyển ca
}
