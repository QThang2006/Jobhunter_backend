package vn.hoidanit.jobhunter.domain.response.shift;

import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.util.constant.WorkShiftStatusEnum;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
public class ResWorkShiftDTO {
    private Long id;
    private LocalDate shiftDate;
    private String shiftName;
    private String startTime;
    private String endTime;
    private String note;
    private WorkShiftStatusEnum status;

    private UserShift user;
    private CompanyShift company;
    private JobShift job;

    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;

    @Getter
    @Setter
    public static class UserShift {
        private Long id;
        private String name;
        private String email;
    }

    @Getter
    @Setter
    public static class CompanyShift {
        private Long id;
        private String name;
        private String logo;
    }

    @Getter
    @Setter
    public static class JobShift {
        private Long id;
        private String name;
    }
}
