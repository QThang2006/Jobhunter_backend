package vn.hoidanit.jobhunter.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.NotificationRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public CompanyService(
            CompanyRepository companyRepository,
            UserRepository userRepository,
            JobRepository jobRepository,
            ResumeRepository resumeRepository,
            NotificationRepository notificationRepository,
            SimpMessagingTemplate simpMessagingTemplate) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.resumeRepository = resumeRepository;
        this.notificationRepository = notificationRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public Company handleCreateCompany(Company c) {
        this.simpMessagingTemplate.convertAndSend("/topic/companies", "REFRESH");
        return this.companyRepository.save(c);
    }

    public ResultPaginationDTO handleGetCompany(Specification<Company> spec, Pageable pageable) {
        Page<Company> pageCompany = this.companyRepository.findAll(spec, pageable);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageCompany.getNumber() + 1);
        mt.setPageSize(pageCompany.getSize());
        mt.setPages(pageCompany.getTotalPages());
        mt.setTotal(pageCompany.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(pageCompany.getContent());

        return rs;
    }

    public ResultPaginationDTO handleGetCompanyForAdmin(Specification<Company> spec, Pageable pageable) {
        Optional<String> currentUserLogin = SecurityUtil.getCurrentUserLogin();

        if (currentUserLogin.isPresent()) {
            String email = currentUserLogin.get();
            User currentUser = this.userRepository.findByEmail(email);

            if (currentUser != null && currentUser.getCompany() != null) {
                Long companyId = currentUser.getCompany().getId();
                Specification<Company> companyIdSpec = (root, query, cb) ->
                        cb.equal(root.get("id"), companyId);
                spec = (spec == null) ? companyIdSpec : spec.and(companyIdSpec);
            }
        }

        Page<Company> pageCompany = this.companyRepository.findAll(spec, pageable);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageCompany.getNumber() + 1);
        mt.setPageSize(pageCompany.getSize());
        mt.setPages(pageCompany.getTotalPages());
        mt.setTotal(pageCompany.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(pageCompany.getContent());

        return rs;
    }

    public Company handleUpdateCompany(Company resCompany){
        Optional<Company> companyOptional = companyRepository.findById(resCompany.getId());
        if(companyOptional.isPresent()){
            Company res = companyOptional.get();
            res.setLogo(resCompany.getLogo());
            res.setName(resCompany.getName());
            res.setDescription(resCompany.getDescription());
            res.setAddress(resCompany.getAddress());
            this.simpMessagingTemplate.convertAndSend("/topic/companies", "REFRESH");
            return companyRepository.save(res);
        }
        return null;
    }

    @Transactional
    public void handleDeleteCompany(Long id){
        Optional<Company> companyOptional = companyRepository.findById(id);
        if(companyOptional.isPresent()){
            Company com = companyOptional.get();

            // 1. Reset user company and role to null (khách vãng lai)
            List<User> users = userRepository.findByCompany(com);
            if(users != null && !users.isEmpty()){
                users.forEach(item -> {
                    item.setCompany(null);
                    item.setRole(null);
                });
                userRepository.saveAll(users);
            }

            // 2. Tìm tất cả tin tuyển dụng (jobs) thuộc công ty này
            List<Job> jobs = jobRepository.findByCompany(com);
            if(jobs != null && !jobs.isEmpty()){
                // 3. Tìm và xóa toàn bộ hồ sơ (resumes) ứng tuyển vào các jobs này
                List<Resume> resumes = resumeRepository.findByJobIn(jobs);
                if(resumes != null && !resumes.isEmpty()){
                    // Ngắt liên kết thông báo liên quan tới resume tránh lỗi ràng buộc khoá ngoại
                    notificationRepository.nullifyResumeIn(resumes);
                    // Broadcast cập nhật lại cho từng ứng viên nộp hồ sơ
                    resumes.stream()
                            .map(Resume::getUser)
                            .filter(u -> u != null && u.getId() != null)
                            .map(User::getId)
                            .distinct()
                            .forEach(userId -> {
                                this.simpMessagingTemplate.convertAndSend("/topic/resumes/" + userId, "REFRESH");
                            });
                    resumeRepository.deleteAll(resumes);
                }
                // 4. Xóa tất cả jobs
                jobRepository.deleteAll(jobs);
            }

            // 5. Xóa công ty
            companyRepository.delete(com);
        }

        // 6. Broadcast cập nhật realtime tới tất cả các bảng liên quan
        this.simpMessagingTemplate.convertAndSend("/topic/companies", "REFRESH");
        this.simpMessagingTemplate.convertAndSend("/topic/users", "REFRESH");
        this.simpMessagingTemplate.convertAndSend("/topic/jobs", "REFRESH");
        this.simpMessagingTemplate.convertAndSend("/topic/resumes", "REFRESH");
    }

    public Optional<Company> findById(long id){
        return companyRepository.findById(id);
    }
}