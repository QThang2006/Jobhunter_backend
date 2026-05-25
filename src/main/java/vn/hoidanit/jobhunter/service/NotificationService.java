package vn.hoidanit.jobhunter.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import vn.hoidanit.jobhunter.domain.Notification;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.notification.ResNotificationDTO;
import vn.hoidanit.jobhunter.repository.NotificationRepository;
import vn.hoidanit.jobhunter.util.constant.ResumeStateEnum;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }


    public void createResumeNotification(Resume resume) {
        User user = resume.getUser();
        if (user == null) return;

        ResumeStateEnum status = resume.getStatus();
        String title;
        String body;
        String jobName = (resume.getJob() != null) ? resume.getJob().getName() : "vị trí ứng tuyển";
        String companyName = (resume.getJob() != null && resume.getJob().getCompany() != null)
                ? resume.getJob().getCompany().getName() : "công ty";

        switch (status) {
            case APPROVED:
                title = "Hồ sơ được chấp nhận! 🎉";
                body = companyName + " đã chấp nhận đơn ứng tuyển " + jobName + " của bạn. Chúc mừng!";
                break;
            case REJECTED:
                title = "Rất tiếc về lần này";
                body = companyName + " không thể tiến hành với đơn ứng tuyển " + jobName
                        + " của bạn lần này. Đừng nản lòng, hãy thử cơ hội khác!";
                break;
            case REVIEWING:
                title = "Hồ sơ đang được xem xét";
                body = companyName + " đang trong quá trình xem xét hồ sơ " + jobName + " của bạn.";
                break;
            default:
                title = "Đơn ứng tuyển đã được cập nhật";
                body = "Trạng thái hồ sơ " + jobName + " của bạn vừa được cập nhật.";
        }

        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setBody(body);
        notification.setStatus(status);
        notification.setRead(false);
        notification.setUser(user);
        notification.setResume(resume);

        notificationRepository.save(notification);

        messagingTemplate.convertAndSend(
                "/topic/notifications/" + user.getEmail(),
                "NEW_NOTIFICATION"
        );
    }

    public ResultPaginationDTO getNotificationsByUser(User user, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(page.getNumber() + 1);
        mt.setPageSize(page.getSize());
        mt.setPages(page.getTotalPages());
        mt.setTotal(page.getTotalElements());

        List<ResNotificationDTO> list = page.getContent()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        rs.setMeta(mt);
        rs.setResult(list);
        return rs;
    }

    public boolean markAsRead(Long notificationId, User user) {
        Optional<Notification> opt = notificationRepository.findById(notificationId);
        if (opt.isEmpty()) return false;

        Notification notification = opt.get();
        // Chỉ cho phép đánh dấu notification của chính mình
        if (!notification.getUser().getId().equals(user.getId())) return false;

        notification.setRead(true);
        notificationRepository.save(notification);
        return true;
    }

    public void markAllAsRead(User user) {
        notificationRepository.markAllAsReadByUser(user);
    }

    public long countUnread(User user) {
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    private ResNotificationDTO toDTO(Notification n) {
        ResNotificationDTO dto = new ResNotificationDTO();
        dto.setId(n.getId());
        dto.setTitle(n.getTitle());
        dto.setBody(n.getBody());
        dto.setStatus(n.getStatus() != null ? n.getStatus().name() : null);
        dto.setRead(n.isRead());
        dto.setCreatedAt(n.getCreatedAt());
        dto.setResumeId(n.getResume() != null ? n.getResume().getId() : null);
        return dto;
    }
}
