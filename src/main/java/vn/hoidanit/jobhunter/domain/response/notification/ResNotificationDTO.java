package vn.hoidanit.jobhunter.domain.response.notification;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ResNotificationDTO {
    private Long id;
    private String title;
    private String body;
    private String status;   // "APPROVED" | "REJECTED" | "REVIEWING" | "PENDING"
    private boolean isRead;
    private Instant createdAt;
    private Long resumeId;
}
