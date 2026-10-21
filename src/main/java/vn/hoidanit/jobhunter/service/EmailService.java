package vn.hoidanit.jobhunter.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import vn.hoidanit.jobhunter.domain.response.email.EmailValidationResponse;
import vn.hoidanit.jobhunter.repository.JobRepository;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Service
public class EmailService {
    private final MailSender mailSender;
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;
    private final JobRepository jobRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${n8n.webhook.interview-meet-url}")
    private String n8nWebhookUrl;

    public EmailService(MailSender mailSender, JavaMailSender javaMailSender, SpringTemplateEngine templateEngine, JobRepository jobRepository) {
        this.mailSender = mailSender;
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.jobRepository = jobRepository;
    }

    private final String API_KEY = "a48f032cc416409581555b1fba7ad04c";
    private final String URL = "https://emailreputation.abstractapi.com/v1/?api_key=" + API_KEY + "&email=";

    public boolean checkEmailExist(String email) {
        try {
            String finalUrl = URL + email;
            EmailValidationResponse response = restTemplate.getForObject(finalUrl, EmailValidationResponse.class);

            if (response != null && response.getEmailDeliverability() != null) {
                boolean result = response.getEmailDeliverability().isSmtpValid();
                System.out.println(">>> [ABSTRACT API SUCCESS] Email: " + email + " -> isSmtpValid: " + result);
                return result;
            }
        } catch (Exception e) {
            System.err.println(">>> [ABSTRACT API CRASH] Lỗi kết nối API hoặc quá giới hạn 429: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        return false;
    }

    public void sendSimpleEmail(String otp,String email){
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("quocthangngo2006@gmail.com");
        msg.setTo(email);
        msg.setSubject("otp");
        msg.setText("otp cua ban la: "+otp);
        mailSender.send(msg);
    }

    public void sendEmailSync(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        MimeMessage mimeMessage = this.javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            message.setFrom("quocthangngo2006@gmail.com");
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

        String subject = "🥐 Thư mời thử việc tại tiệm bánh - " + companyName;
        this.sendEmailSync(toEmail, subject, content, false, true);
    }

    /**
     * Kích hoạt Workflow n8n để tạo lịch Google Meet & tự động gửi mail cho ứng viên
     */
    public String triggerN8nInterviewMeet(
            Long resumeId,
            String candidateEmail,
            String candidateName,
            String companyName,
            String jobTitle,
            String interviewDate,
            String interviewTime,
            String note
    ) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("resumeId", resumeId);
            payload.put("candidateEmail", candidateEmail);
            payload.put("candidateName", candidateName);
            payload.put("companyName", companyName);
            payload.put("jobTitle", jobTitle);
            payload.put("interviewDate", interviewDate);
            payload.put("interviewTime", interviewTime);
            payload.put("note", note != null ? note : "Phỏng vấn trực tuyến qua Google Meet");
            payload.put("durationMinutes", 45);

            // Tính sẵn startDateTime và endDateTime chuẩn ISO 8601 (+07:00) cho Google Calendar
            try {
                String[] dParts = interviewDate.contains("/") ? interviewDate.split("/") : interviewDate.split("-");
                String day = String.format("%02d", Integer.parseInt(dParts[0].trim()));
                String month = String.format("%02d", Integer.parseInt(dParts[1].trim()));
                String year = dParts[2].trim();
                String[] tParts = interviewTime.split(":");
                int hour = Integer.parseInt(tParts[0].trim());
                int minute = Integer.parseInt(tParts[1].trim());

                java.time.LocalDate localDate = java.time.LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
                java.time.LocalTime localTime = java.time.LocalTime.of(hour, minute);
                java.time.ZonedDateTime startZoned = java.time.ZonedDateTime.of(localDate, localTime, java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
                java.time.ZonedDateTime endZoned = startZoned.plusMinutes(45);

                String startIso = startZoned.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                String endIso = endZoned.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

                payload.put("startDateTime", startIso);
                payload.put("endDateTime", endIso);
            } catch (Exception ex) {
                System.err.println(">>> [DATE PARSE WARN] Khong the parse ISO date: " + ex.getMessage());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(n8nWebhookUrl, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object meetLink = response.getBody().get("meetLink");
                if (meetLink != null) {
                    System.out.println(">>> [N8N SUCCESS] Đã tạo link Google Meet thành công: " + meetLink);
                    return meetLink.toString();
                }
            }
        } catch (Exception e) {
            System.err.println(">>> [N8N WEBHOOK ERROR] Lỗi khi gọi webhook n8n: " + e.getMessage());
        }
        return null;
    }
}
