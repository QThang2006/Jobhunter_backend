package vn.hoidanit.jobhunter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.response.email.ResEmailInterview;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.service.EmailService;
import vn.hoidanit.jobhunter.service.ResumeService;
import vn.hoidanit.jobhunter.service.SubscriberService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.constant.ResumeStateEnum;

@RestController
@RequestMapping("/api/v1")
public class EmailController {
    @org.springframework.beans.factory.annotation.Autowired
    private vn.hoidanit.jobhunter.service.AccessService access;

    private final EmailService emailService;
    private final SubscriberService subscriberService;
    private final ResumeRepository resumeRepository;
    private final ResumeService resumeService;

    public EmailController(
            EmailService emailService,
            SubscriberService subscriberService,
            ResumeRepository resumeRepository,
            ResumeService resumeService
    ) {
        this.emailService = emailService;
        this.subscriberService = subscriberService;
        this.resumeRepository = resumeRepository;
        this.resumeService = resumeService;
    }

    @GetMapping("/email")
    @ApiMessage("send simple email")
    public String sendSimpleEmail(){
        subscriberService.sendSubscribersEmailJobs();
        return "ok";
    }

    @PostMapping({"/email/approve", "/email/schedule-interview"})
    public ResponseEntity<ResEmailInterview> sendApprovalEmail(@RequestBody ResEmailInterview reqInterview) {
        // Lấy thông tin resume từ DB
        Resume resume = resumeRepository.findById(reqInterview.getResumeId())
                .orElseThrow(() -> new RuntimeException("Resume not found"));

        access.requireCompany(resume.getJob().getCompany());
        // Khi lên lịch phỏng vấn: Chuyển trạng thái sang REVIEWING (đang phỏng vấn/xem xét)
        // và lưu thông tin ngày giờ, hình thức phỏng vấn vào DB
        resume.setStatus(ResumeStateEnum.REVIEWING);
        resume.setInterviewDate(reqInterview.getInterviewDate());
        resume.setInterviewTime(reqInterview.getInterviewTime());
        resume.setInterviewType(reqInterview.getInterviewType());
        resume.setNote(reqInterview.getNote());

        if ("ONLINE_MEET".equalsIgnoreCase(reqInterview.getInterviewType())) {
            // Tự động hóa qua n8n Workflow: Tạo Google Meet & gửi thư mời
            String meetLink = emailService.triggerN8nInterviewMeet(
                    resume.getId(),
                    resume.getEmail(),
                    resume.getUser() != null ? resume.getUser().getName() : "Ứng viên",
                    resume.getJob() != null && resume.getJob().getCompany() != null ? resume.getJob().getCompany().getName() : "Tiệm Bánh",
                    resume.getJob() != null ? resume.getJob().getName() : "Vị trí ứng tuyển",
                    reqInterview.getInterviewDate(),
                    reqInterview.getInterviewTime(),
                    reqInterview.getNote()
            );

            if (meetLink != null && !meetLink.isBlank()) {
                resume.setMeetLink(meetLink);
                reqInterview.setMeetLink(meetLink);
            } else {
                // Fallback nếu n8n chưa bật: gửi email trực tiếp qua SMTP
                resume.setMeetLink(null);
                emailService.sendInterviewInvitationEmail(
                        resume.getJob().getCompany().getId().toString(),
                        resume.getEmail(),
                        resume.getUser() != null ? resume.getUser().getName() : "Ứng viên",
                        resume.getJob().getCompany().getName(),
                        resume.getJob().getName(),
                        reqInterview.getInterviewDate(),
                        reqInterview.getInterviewTime()
                );
            }
        } else {
            // Phỏng vấn / Thử việc trực tiếp tại tiệm: Gửi email truyền thống qua SMTP
            resume.setMeetLink(null);
            emailService.sendInterviewInvitationEmail(
                    resume.getJob().getCompany().getId().toString(),
                    resume.getEmail(),
                    resume.getUser() != null ? resume.getUser().getName() : "Ứng viên",
                    resume.getJob().getCompany().getName(),
                    resume.getJob().getName(),
                    reqInterview.getInterviewDate(),
                    reqInterview.getInterviewTime()
            );
        }

        // Lưu vào DB và broadcast thông báo real-time qua WebSocket
        resumeRepository.save(resume);
        resumeService.update(resume);

        return ResponseEntity.ok().body(reqInterview);
    }
}
