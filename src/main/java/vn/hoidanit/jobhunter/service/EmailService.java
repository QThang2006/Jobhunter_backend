package vn.hoidanit.jobhunter.service;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.apache.catalina.LifecycleState;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.repository.JobRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class EmailService {
    private final MailSender mailSender;
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;
    private final JobRepository jobRepository;

    public EmailService(MailSender mailSender, JavaMailSender javaMailSender, SpringTemplateEngine templateEngine, JobRepository jobRepository) {
        this.mailSender = mailSender;
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.jobRepository = jobRepository;
    }

    public void sendSimpleEmail(String otp,String email){
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);
        msg.setSubject("otp");
        msg.setText("otp cua ban la: "+otp);
        mailSender.send(msg);
    }

    public void sendEmailSync(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        // Prepare message using a Spring helper
        MimeMessage mimeMessage = this.javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content, isHtml);
            this.javaMailSender.send(mimeMessage);
        } catch (MailException | MessagingException e) {
            System.out.println("ERROR SEND EMAIL: " + e);
        }
    }

    @Async
    public void sendEmailFromTemplateSync(String to, String subject, String templateName,String username, Object value) {
        Context context = new Context();
        context.setVariable("name", username);
        context.setVariable("jobs",value);
        String content = this.templateEngine.process(templateName, context);
        this.sendEmailSync(to, subject, content, false, true);
    }

    @Async
    public void sendInterviewInvitationEmail(
            String id,
            String toEmail,           // Email ứng viên
            String candidateName,     // Tên ứng viên
            String companyName,       // Tên công ty
            String jobName,           // Tên vị trí
            String interviewDate,     // Ngày PV (DD/MM/YYYY)
            String interviewTime      // Giờ PV (HH:mm)
    ) {
        Context context = new Context();
        context.setVariable("detailId", id);
        context.setVariable("candidateName", candidateName);
        context.setVariable("companyName", companyName);
        context.setVariable("jobName", jobName);
        context.setVariable("interviewDate", interviewDate);
        context.setVariable("interviewTime", interviewTime);

        String content = this.templateEngine.process("interview", context);

        String subject = "🎉 Thông báo lịch phỏng vấn - " + companyName;
        this.sendEmailSync(toEmail, subject, content, false, true);
    }


}
