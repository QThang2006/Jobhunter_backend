package vn.hoidanit.jobhunter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.chatGPT.JobSearchCriteria;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class OpenAIService {

    @Value("${groq.api.key}")
    private String apiKey;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @jakarta.annotation.PostConstruct
    public void init() {
        String keyStatus = (apiKey == null || apiKey.equals("YOUR_GROQ_API_KEY_HERE"))
                ? "DEFAULT_KEY (Check application.properties!)"
                : apiKey.substring(0, Math.min(apiKey.length(), 6)) + "***";
        System.out.println(">>> GROQ AI READY. Key: " + keyStatus);
        System.out.println(">>> Model: llama-3.3-70b-versatile (Latest & Reliable)");
    }

    // --- FUNCTION 1: EXTRACT CRITERIA ---
    public JobSearchCriteria extractCriteria(String question) {
        String todayDate = LocalDate.now().toString();

        String prompt = """
            [IDENTITY]
            Bạn là Chuyên gia Tuyển dụng Cấp cao (Senior Technical Recruiter) của hệ thống JobHunter. 
            Nhiệm vụ của bạn là phân tích câu hỏi của người dùng và chuyển đổi nó thành một đối tượng JSON tìm kiếm chính xác.
            
            [CONTEXT]
            - Hôm nay là ngày: %s
            - Đối tượng mục tiêu: Lập trình viên & Nhà tuyển dụng tại Việt Nam.
            
            [DEEP ANALYSIS RULES]
            1. KỸ NĂNG (SKILLS): Khớp với danh mục hệ thống.
                - "Frontend", "Giao diện" -> ["FRONTEND", "REACT.JS"].
                - "Backend", "Hệ thống" -> ["BACKEND", "JAVA SPRING", "NEST.JS"].
                - "Fullstack" -> ["FULLSTACK"].
                - "Java Spring", "Spring Boot" -> ["JAVA SPRING"].
                - "React", "ReactJS" -> ["REACT.JS"].
                - "Vue" -> ["VUE.JS"].
                - MỞ RỘNG LOGIC: Nếu user dùng từ khóa vai trò, hãy thêm các công nghệ liên quan.\s
                     VD: "Web dev" -> ["HTML", "CSS", "Javascript", "React"].
                     VD: "Cầy Java" -> ["JAVA",...].
                - LUÔN chuyển về chữ IN HOA khi có thể để khớp Database.
            2. ĐỊA ĐIỂM (LOCATION): Chuẩn hóa về mã DATABASE viết hoa.
               - "SG", "Sài Gòn", "Thành phố mang tên Bác", "Hồ Chí Minh" -> "HOCHIMINH".
               - "HN", "Thủ đô", "Hà Nội" -> "HANOI".
               - "ĐN", "Đà Thành", "Đà Nẵng" -> "DANANG".
               - Các tỉnh khác -> "OTHER".
               - nếu ko đề cập tới địa điểm thì ko cần fill vào null là được .
            3. LƯƠNG (SALARY): Đưa về con số VNĐ (Double).
               - "10 củ", "10 triệu", "10tr" -> 10000000.
               - "1k đô", "1000$" -> 25000000.
               - "Lương nghìn đô" -> salaryMin = 25000000.
               - "15-20 triệu" -> lấy mốc thấp nhất: salaryMin = 15000000.
            4. CẤP BẬC (LEVEL): Khớp chính xác với [INTERN, FRESHER, JUNIOR, MIDDLE, SENIOR].
               - "Sinh viên", "Thực tập" -> "INTERN".
               - "Mới tốt nghiệp", "0 kinh nghiệm" -> "FRESHER".
               - "Đã có kinh nghiệm", "2 năm" -> "JUNIOR".
               - "3-5 năm", "Cứng" -> "MIDDLE".
               - "Expert", "Lead", "Quản lý" -> "SENIOR".
            5. PHÂN TÍCH THỜI GIAN (QUAN TRỌNG):
               - "postedWithinDays": Dùng cho câu hỏi về độ mới của bài đăng (VD: "mới đăng 3 ngày", "vừa up").
               - "jobStartDateAfter": Dùng cho thời gian bắt đầu tuyển dụng (startDate). VD: "tuyển sau tết", "mở từ tháng 5".
               - "jobEndDateBefore": Dùng cho hạn chót ứng tuyển (endDate). VD: "còn hạn đến cuối tháng", "hết hạn trước tháng 6".
            6. TỪ KHÓA MÔ TẢ (DESCRIPTION KEYWORDS):
               - Trích xuất các yêu cầu hoặc phúc lợi đặc biệt người dùng mong muốn.
               - VD: "có bảo hiểm", "lương tháng 13", "môi trường trẻ", "đi du lịch", "làm Remote" -> ["bảo hiểm", "lương tháng 13", "môi trường", "du lịch", "remote"].
            7. SẮP XẾP (SORTBY):
               - "Lương cao nhất", "tiền nhiều" -> sortBy = "salaryDesc".
               - "Mới nhất", "gần đây" -> sortBy = "newest".

            [OUTPUT FORMAT - JSON ONLY]
            {
              "skills": ["string"],
              "location": "string",
              "salaryMin": 0.0,
              "experienceLevel": "string",
              "postedWithinDays": 0,
              "jobStartDateAfter": "YYYY-MM-DD",
              "jobEndDateBefore": "YYYY-MM-DD",
              "descriptionKeywords": ["string"],
              "companyName": "string",
              "sortBy": "salaryDesc" | "newest" | null
            }

            USER QUERY: "%s"
            """.formatted(todayDate, question);

        try {
            String jsonResponse = callAI(prompt, true);
            jsonResponse = cleanJson(jsonResponse);
            return objectMapper.readValue(jsonResponse, JobSearchCriteria.class);
        } catch (Exception e) {
            e.printStackTrace();
            return new JobSearchCriteria();
        }
    }

    // --- FUNCTION 2: GENERATE CONVERSATIONAL REPLY ---
    public String generateConversationalReply(String userQuestion, List<Job> foundJobs, JobSearchCriteria criteria) {
        StringBuilder jobContext = new StringBuilder();
        if (!foundJobs.isEmpty()) {
            jobContext.append("Danh sách công việc tìm thấy:\n");
            foundJobs.stream().limit(5).forEach(j -> {
                jobContext.append("- %s tại %s (Lương: %,.0f VNĐ)\n".formatted(j.getName(), j.getLocation(), j.getSalary()));
            });
        }

        String prompt = """
            IDENTITY: Bạn là Recruitment Assistant thân thiện của JobHunter.
            TASK: Phản hồi lại người dùng dựa trên kết quả tìm kiếm thực tế.
            
            USER QUESTION: "%s"
            RESULT COUNT: %d cơ hội.
            CONTEXT:
            %s
            
            QUY TẮC PHẢN HỒI:
            1. Ngôn ngữ: Tiếng Việt, lịch sự, chuyên nghiệp nhưng vẫn gần gũi.
            2. Nếu có kết quả: Tóm tắt ngắn gọn những gì bạn đã tìm thấy (VD: "Tôi đã lọc được 3 vị trí Java lương cao tại HCM cho bạn...") và mời họ xem chi tiết bên dưới.
            3. Nếu không có kết quả: Nói lời xin lỗi khéo léo và gợi ý họ thay đổi tiêu chí (VD: mở rộng địa điểm hoặc giảm yêu cầu lương).
            4. KHÔNG liệt kê lại toàn bộ danh sách (vì đã có giao diện hiển thị), chỉ cần viết lời dẫn thu hút.
            """.formatted(userQuestion, foundJobs.size(), jobContext.toString());

        return callAI(prompt, false); // Trả về dạng văn bản (không phải JSON)
    }

    // --- FUNCTION 3: NO RESULT REPLY (SIMPLE MODE) ---
    public String generateNoResultReply(JobSearchCriteria criteria) {
        return "Hiện tại hệ thống chưa có công việc nào khớp với tiêu chí này.";
    }

    private String callAI(String promptText, boolean jsonMode) {
        try {
            // 1. Chuẩn bị Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey); // Groq dùng Bearer token

            // 2. Chuẩn bị Body (Groq/OpenAI Standard)
            Map<String, Object> message = Map.of("role", "user", "content", promptText);

            Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("model", "llama-3.3-70b-versatile"); // Model mới nằm ở đây
            requestBody.put("messages", List.of(message));

            if (jsonMode) {
                requestBody.put("response_format", Map.of("type", "json_object"));
            }

            // 3. Gọi API
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            Map<String, Object> response = restTemplate.postForObject(GROQ_URL, entity, Map.class);

            // 4. Parse Response (OpenAI Format)
            if (response == null || !response.containsKey("choices")) {
                return jsonMode ? "{}" : "Hệ thống AI đang bận...";
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> responseContent = (Map<String, Object>) firstChoice.get("message");

            return (String) responseContent.get("content");

        } catch (Exception e) {
            System.err.println(">>> AI API Error: " + e.getMessage());
            return jsonMode ? "{}" : "Hệ thống AI đang bận xử lý...";
        }
    }

    private String cleanJson(String raw) {
        if (raw == null) return "{}";
        String cleaned = raw.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }
}

