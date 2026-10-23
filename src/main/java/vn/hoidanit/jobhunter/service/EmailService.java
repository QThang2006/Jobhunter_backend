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

    @Value("${abstract.api-key:}")
    private String emailValidationKey;

    public boolean checkEmailExist(String email) {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return false;
        if (emailValidationKey == null || emailValidationKey.isBlank()) return true;
        try {
            String finalUrl = org.springframework.web.util.UriComponentsBuilder
                    .fromHttpUrl("https://emailreputation.abstractapi.com/v1/")
                    .queryParam("api_key", emailValidationKey).queryParam("email", email)
                    .build().encode().toUriString();
            EmailValidationResponse response = restTemplate.getForObject(finalUrl, EmailValidationResponse.class);

            if (response != null && response.getEmailDeliverability() != null) {
                boolean result = response.getEmailDeliverability().isSmtpValid();
                System.out.println(">>> [ABSTRACT API SUCCESS] Email: " + email + " -> isSmtpValid: " + result);
                return result;
            }
        } catch (Exception e) {
            System.err.println("Email validation provider unavailable");
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
        System.out.println(">>> [EMAIL SYNC] Chuẩn bị gửi email tới: " + to + " | Tiêu đề: " + subject);
        MimeMessage mimeMessage = this.javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            message.setFrom("quocthangngo2006@gmail.com", "BakeJobs");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content, isHtml);
            this.javaMailSender.send(mimeMessage);
            System.out.println(">>> [EMAIL SYNC SUCCESS] Đã gửi email thành công tới: " + to);
        } catch (Exception e) {
            System.err.println(">>> [EMAIL SYNC ERROR] Gửi email thất bại tới: " + to + " | Lỗi: " + e.getMessage());
            e.printStackTrace();
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

    @Async
    public void sendRejectionEmail(
            String toEmail,
            String candidateName,
            String companyName,
            String jobName
    ) {
        String subject = "🥐 Thông báo kết quả ứng tuyển vị trí " + jobName + " - " + companyName;

        String htmlContent = "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='utf-8'></head>"
                + "<body style='font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif; background-color: #f9fafb; margin: 0; padding: 24px; color: #111827;'>"
                + "  <div style='max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e5e7eb; padding: 32px 28px; box-shadow: 0 2px 10px rgba(0,0,0,0.04);'>"
                + "    <div style='text-align: center; padding-bottom: 20px; border-bottom: 1px solid #f3f4f6;'>"
                + "      <div style='font-size: 28px;'>🥐</div>"
                + "      <h2 style='font-size: 20px; font-weight: 700; color: #111827; margin: 8px 0 4px;'>Thông Báo Kết Quả Ứng Tuyển</h2>"
                + "      <p style='font-size: 14px; color: #6b7280; margin: 0;'>Đơn vị tuyển dụng: <strong>" + companyName + "</strong></p>"
                + "    </div>"
                + "    <div style='padding-top: 20px; font-size: 14.5px; line-height: 1.6; color: #374151;'>"
                + "      <p>Xin chào <strong>" + candidateName + "</strong>,</p>"
                + "      <p>Lời đầu tiên, <strong>" + companyName + "</strong> xin gửi lời cảm ơn chân thành đến bạn vì đã dành thời gian và sự quan tâm đến vị trí <strong>" + jobName + "</strong> tại tiệm bánh của chúng tôi.</p>"
                + "      <p>Sau khi xem xét kỹ lưỡng hồ sơ và đối chiếu với yêu cầu hiện tại, chúng tôi rất tiếc phải thông báo rằng hiện tại chưa thể đồng hành cùng bạn trong đợt tuyển dụng lần này.</p>"
                + "      <p>Quyết định này hoàn toàn không phản ánh năng lực hay tiềm năng của bạn, mà chỉ dựa trên sự phù hợp với các tiêu chí đặc thù của tiệm bánh ở thời điểm hiện tại.</p>"
                + "      <p>Chúng tôi đã lưu giữ thông tin của bạn trong hệ thống và sẽ chủ động liên hệ lại khi có cơ hội việc làm mới phù hợp hơn.</p>"
                + "      <p>Chúc bạn luôn tràn đầy nhiệt huyết và sớm tìm được công việc như ý trong ngành bánh!</p>"
                + "      <p style='margin-top: 24px;'>Trân trọng,<br><strong>Bộ phận Tuyển dụng • " + companyName + "</strong></p>"
                + "    </div>"
                + "    <div style='text-align: center; font-size: 12px; color: #9ca3af; margin-top: 28px; border-top: 1px solid #f3f4f6; padding-top: 16px;'>"
                + "      © " + java.time.Year.now().getValue() + " " + companyName + " • Nền tảng kết nối việc làm BakeJobs"
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";

        this.sendEmailSync(toEmail, subject, htmlContent, false, true);
    }

    @Async
    public void sendApprovalEmail(
            String toEmail,
            String candidateName,
            String companyName,
            String jobName
    ) {
        String subject = "🎉【BakeJobs】Chúc mừng bạn đã trúng tuyển vị trí " + jobName + " - " + companyName;

        String htmlContent = "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='utf-8'></head>"
                + "<body style='font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif; background-color: #f9fafb; margin: 0; padding: 24px; color: #111827;'>"
                + "  <div style='max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e5e7eb; padding: 32px 28px; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>"
                + "    <div style='text-align: center; padding-bottom: 20px; border-bottom: 2px solid #f3f4f6;'>"
                + "      <div style='font-size: 32px;'>🎉🥖</div>"
                + "      <h2 style='font-size: 22px; font-weight: 700; color: #16a34a; margin: 8px 0 4px;'>Chúc Mừng Bạn Đã Trúng Tuyển!</h2>"
                + "      <p style='font-size: 14px; color: #6b7280; margin: 0;'>Đơn vị tiếp nhận: <strong>" + companyName + "</strong></p>"
                + "    </div>"
                + "    <div style='padding-top: 24px; font-size: 15px; line-height: 1.6; color: #374151;'>"
                + "      <p>Xin chào <strong>" + candidateName + "</strong>,</p>"
                + "      <p>Đại diện <strong>" + companyName + "</strong>, chúng tôi rất vui mừng thông báo rằng bạn đã hoàn thành xuất sắc đợt phỏng vấn và chính thức <strong>trúng tuyển</strong> vào vị trí <strong>" + jobName + "</strong> tại tiệm bánh của chúng tôi!</p>"
                + "      <div style='background-color: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; padding: 16px 20px; margin: 20px 0;'>"
                + "        <p style='margin: 0 0 6px 0; color: #166534; font-weight: 600;'>🥖 Chào mừng bạn gia nhập tiệm bánh " + companyName + "!</p>"
                + "        <p style='margin: 0; color: #15803d; font-size: 13.5px;'>Tài khoản của bạn trên hệ thống BakeJobs đã được liên kết với chi nhánh tiệm bánh. Bạn có thể đăng nhập để theo dõi phân ca làm việc từ quản lý.</p>"
                + "      </div>"
                + "      <p>Bộ phận nhân sự / Quản lý của tiệm sẽ sớm liên hệ trực tiếp với bạn qua Số điện thoại / Email để hướng dẫn thủ tục nhận việc và sắp xếp ca làm việc đầu tiên.</p>"
                + "      <p style='margin-top: 24px;'>Chúc bạn sẽ có những trải nghiệm làm việc tràn đầy nhiệt huyết và cùng tiệm bánh tạo nên những mẻ bánh thơm ngon nhất!<br><br>Trân trọng,<br><strong>Bộ phận Tuyển dụng • " + companyName + "</strong></p>"
                + "    </div>"
                + "    <div style='text-align: center; font-size: 12px; color: #9ca3af; margin-top: 28px; border-top: 1px solid #f3f4f6; padding-top: 16px;'>"
                + "      © " + java.time.Year.now().getValue() + " " + companyName + " • Nền tảng kết nối việc làm BakeJobs"
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";

        this.sendEmailSync(toEmail, subject, htmlContent, false, true);
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

            System.out.println(">>> [N8N CALL] Dang goi webhook: " + n8nWebhookUrl);
            ResponseEntity<Map> response = restTemplate.postForEntity(n8nWebhookUrl, entity, Map.class);
            System.out.println(">>> [N8N RESPONSE] Status: " + response.getStatusCode() + ", Body: " + response.getBody());
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object meetLink = response.getBody().get("meetLink");
                if (meetLink != null) {
                    System.out.println(">>> [N8N SUCCESS] Đã tạo link Google Meet thành công: " + meetLink);
                    return meetLink.toString();
                }
            }
        } catch (Exception e) {
            System.err.println(">>> [N8N WEBHOOK ERROR] Lỗi khi gọi webhook n8n: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Gửi email xác nhận nộp hồ sơ ứng tuyển nhanh (từ link rải) kèm thông tin tài khoản nếu mới tạo
     */
    @Async
    public void sendQuickApplyConfirmationEmail(
            String toEmail,
            String candidateName,
            String companyName,
            String jobTitle,
            boolean isNewAccount,
            String temporaryPassword
    ) {
        System.out.println(">>> [EMAIL QUICK APPLY] Bắt đầu xử lý gửi email cho: " + toEmail + ", isNewAccount=" + isNewAccount);
        String subject = "【BakeJobs】Xác nhận hồ sơ ứng tuyển vị trí " + jobTitle + " - " + companyName;

        String accountInfoHtml = "";
        if (isNewAccount && temporaryPassword != null) {
            accountInfoHtml = "<div style='background-color: #f3f4f6; border-radius: 8px; padding: 16px; margin: 20px 0;'>"
                    + "<p style='margin: 0 0 8px 0; font-weight: 600; color: #1f2937;'>🔑 Tài khoản theo dõi tiến độ ứng tuyển:</p>"
                    + "<p style='margin: 4px 0; color: #4b5563;'>• Tên đăng nhập: <strong>" + toEmail + "</strong></p>"
                    + "<p style='margin: 4px 0; color: #4b5563;'>• Mật khẩu tạm thời: <strong style='color: #2563eb;'>" + temporaryPassword + "</strong></p>"
                    + "<p style='margin: 8px 0 0 0; font-size: 13px; color: #6b7280;'>* Bạn có thể đăng nhập vào BakeJobs để cập nhật thông tin và theo dõi lịch phỏng vấn / ca làm việc.</p>"
                    + "</div>";
        }

        String htmlContent = "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 20px; background-color: #f9fafb;'>"
                + "  <div style='max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e5e7eb; padding: 32px; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>"
                + "    <div style='text-align: center; border-bottom: 2px solid #f3f4f6; padding-bottom: 20px; margin-bottom: 24px;'>"
                + "      <h2 style='color: #1677ff; margin: 0; font-size: 22px;'>🥖 BakeJobs • Kết Nối Việc Làm Bánh</h2>"
                + "      <p style='color: #6b7280; font-size: 14px; margin-top: 6px;'>Xác nhận hồ sơ ứng tuyển thành công</p>"
                + "    </div>"
                + "    <div>"
                + "      <p>Xin chào <strong>" + candidateName + "</strong>,</p>"
                + "      <p>Hồ sơ ứng tuyển của bạn cho vị trí <strong style='color: #1677ff;'>" + jobTitle + "</strong> tại tiệm bánh <strong style='color: #059669;'>" + companyName + "</strong> đã được tiếp nhận thành công!</p>"
                + accountInfoHtml
                + "      <p>Bộ phận nhân sự của <strong>" + companyName + "</strong> sẽ sớm xem xét hồ sơ và liên hệ phỏng vấn qua Email / Số điện thoại của bạn.</p>"
                + "      <p style='margin-top: 24px;'>Chúc bạn một ngày làm việc tràn đầy năng lượng!<br>Trân trọng,<br><strong>Đội ngũ BakeJobs & " + companyName + "</strong></p>"
                + "    </div>"
                + "    <div style='text-align: center; font-size: 12px; color: #9ca3af; margin-top: 28px; border-top: 1px solid #f3f4f6; padding-top: 16px;'>"
                + "      © " + java.time.Year.now().getValue() + " " + companyName + " • Nền tảng tuyển dụng ngành bánh"
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";

        this.sendEmailSync(toEmail, subject, htmlContent, false, true);
    }
}
