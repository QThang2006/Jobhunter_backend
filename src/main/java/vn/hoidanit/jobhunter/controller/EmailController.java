package vn.hoidanit.jobhunter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.response.email.ResEmailInterview;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.service.EmailService;
import vn.hoidanit.jobhunter.service.SubscriberService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;

@RestController
@RequestMapping("/api/v1")
public class EmailController {
    private final EmailService emailService;
    private final SubscriberService subscriberService;
    private final ResumeRepository resumeRepository;

    public EmailController(EmailService emailService, SubscriberService subscriberService, ResumeRepository resumeRepository) {
        this.emailService = emailService;
        this.subscriberService = subscriberService;
        this.resumeRepository = resumeRepository;
    }

    @GetMapping("/email")
    @ApiMessage("send simple email")
    public String sendSimpleEmail(){
        subscriberService.sendSubscribersEmailJobs();
        return "ok";
    }

    @PostMapping("/email/approve")
    public ResponseEntity<ResEmailInterview> sendApprovalEmail(@RequestBody ResEmailInterview reqInterview) {
        // Lấy thông tin resume từ DB
        Resume resume = resumeRepository.findById(reqInterview.getResumeId())
                .orElseThrow(() -> new RuntimeException("Resume not found"));

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
                resumeRepository.save(resume);
                reqInterview.setMeetLink(meetLink);
            } else {
                // Fallback nếu n8n chưa bật: gửi email trực tiếp qua SMTP
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
            // Thử việc / phỏng vấn trực tiếp tại tiệm: Gửi email truyền thống
            emailService.sendInterviewInvitationEmail(
                    resume.getJob().getCompany().getId().toString(),
                    resume.getEmail(),                        // to
                    resume.getUser() != null ? resume.getUser().getName() : "Ứng viên", // candidateName
                    resume.getJob().getCompany().getName(),            // companyName
                    resume.getJob().getName(),                // jobName
                    reqInterview.getInterviewDate(),                   // interviewDate
                    reqInterview.getInterviewTime()                    // interviewTime
            );
        }

        return ResponseEntity.ok().body(reqInterview);
    }
}
