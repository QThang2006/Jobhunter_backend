package vn.hoidanit.jobhunter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.chatGPT.ChatResponse;
import vn.hoidanit.jobhunter.domain.chatGPT.JobSearchCriteria;
import vn.hoidanit.jobhunter.repository.JobRepository;

import java.util.List;

@Service
public class ChatService {
    private final JobRepository jobRepository;
    private final OpenAIService openAIService;
    private final JobService jobService;
    private final ObjectMapper objectMapper = new ObjectMapper(); // Thêm để in log đẹp

    public ChatService(JobRepository jobRepository, OpenAIService openAIService, JobService jobService) {
        this.jobRepository = jobRepository;
        this.openAIService = openAIService;
        this.jobService = jobService;
    }

    public ChatResponse processUserQuestion(String userQuestion) {
        // 1. Bước 1: Gọi AI để bóc tách tiêu chí tìm kiếm (JSON)
        JobSearchCriteria criteria = openAIService.extractCriteria(userQuestion);
        try {
            System.out.println(">>> AI Analysis Result: \n" +
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(criteria));
        } catch (Exception e) {}

        // 2. Bước 2: Tìm kiếm trong Database
        Specification<Job> spec = jobService.filterJobs(criteria);
        List<Job> foundJobs = jobRepository.findAll(spec);

        // Logic Fallback (Nới lỏng nếu rỗng)
        if (foundJobs.isEmpty() && (criteria.getSalaryMin() != null || criteria.getSkills() != null)) {
            criteria.setSalaryMin(null);
            foundJobs = jobRepository.findAll(jobService.filterJobs(criteria));
        }

        // 3. Bước 3: Gọi AI lần 2 để tự viết câu trả lời dựa trên kết quả thật
        String botReply = openAIService.generateConversationalReply(userQuestion, foundJobs, criteria);

        return new ChatResponse(botReply, foundJobs);
    }

}
