package vn.hoidanit.jobhunter.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.constant.WorkShiftStatusEnum;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "work_shifts")
@Getter
@Setter
public class WorkShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate shiftDate;

    private String shiftName; // Ca Sáng, Ca Chiều, Ca Tối, Ca Đêm, Full-time...

    private String startTime; // "06:00"

    private String endTime; // "14:00"

    @Column(columnDefinition = "TEXT")
    private String note; // Ghi chú nhiệm vụ ca làm

    @Enumerated(EnumType.STRING)
    private WorkShiftStatusEnum status = WorkShiftStatusEnum.ASSIGNED;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user; // Nhân viên đảm nhận

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company; // Tiệm bánh

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_id")
    private Job job; // Vị trí làm việc nếu có

    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;

    @PrePersist
    public void handleBeforeCreate() {
        this.createdBy = SecurityUtil.getCurrentUserLogin().orElse("");
        this.createdAt = Instant.now();
        if (this.status == null) {
            this.status = WorkShiftStatusEnum.ASSIGNED;
        }
    }

    @PreUpdate
    public void handleBeforeUpdate() {
        this.updatedBy = SecurityUtil.getCurrentUserLogin().orElse("");
        this.updatedAt = Instant.now();
    }
}
