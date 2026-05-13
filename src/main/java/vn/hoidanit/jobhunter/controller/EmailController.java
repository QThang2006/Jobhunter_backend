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
//        emailService.sendEmailSync("quocthangngo2006@gmail.com","test email","<h1> <b> hello </b> <h1>",false,true);
//        emailService.sendEmailFromTemplateSync("quocthangngo2006@gmail.com","test email","test");
        subscriberService.sendSubscribersEmailJobs();
        return "ok";
    }

    @PostMapping("/email/approve")
    public ResponseEntity<Void> sendApprovalEmail(@RequestBody ResEmailInterview reqInterview) {
        // Lấy thông tin resume từ DB
        Resume resume = resumeRepository.findById(reqInterview.getResumeId())
                .orElseThrow(() -> new RuntimeException("Resume not found"));

        // Gửi email
        emailService.sendInterviewInvitationEmail(
                resume.getJob().getCompany().getId().toString(),
                resume.getEmail(),                        // to
                resume.getUser().getName(),               // candidateName
                resume.getJob().getCompany().getName(),            // companyName
                resume.getJob().getName(),                // jobName
                reqInterview.getInterviewDate(),                   // interviewDate
                reqInterview.getInterviewTime()                    // interviewTime
        );

        return ResponseEntity.ok().body(null);
    }
}
