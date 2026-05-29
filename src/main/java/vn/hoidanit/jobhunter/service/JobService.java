package vn.hoidanit.jobhunter.service;

import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.*;
import vn.hoidanit.jobhunter.domain.chatGPT.JobSearchCriteria;
import vn.hoidanit.jobhunter.domain.response.job.ResCreateJobDTO;
import vn.hoidanit.jobhunter.domain.response.job.ResUpdateJobDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.*;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class JobService {
    private final JobRepository jobRepository;
    private final SkillRepository skillRepository;
    private final CompanyRepository companyRepository;
    private final ResumeRepository resumeRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserRepository userRepository;

    public JobService(JobRepository jobRepository, SkillRepository skillRepository, CompanyRepository companyRepository, ResumeRepository resumeRepository, SimpMessagingTemplate simpMessagingTemplate, UserRepository userRepository) {
        this.jobRepository = jobRepository;
        this.skillRepository = skillRepository;
        this.companyRepository = companyRepository;
        this.resumeRepository = resumeRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.userRepository = userRepository;
    }

    public ResCreateJobDTO create(Job j) {
        if(j.getSkills() != null ){
            List<Long> reqSkills = j.getSkills()
                    .stream()
                    .map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = skillRepository.findByIdIn(reqSkills);
            j.setSkills(dbSkills);
        }

        if(j.getCompany() != null){
            Optional<Company> companyOptional = companyRepository.findById(j.getCompany().getId());
            if(companyOptional.isPresent()){
                j.setCompany(companyOptional.get());
            }
        }

        Job currentJob = jobRepository.save(j);

        ResCreateJobDTO dto = new ResCreateJobDTO();
        dto.setId(currentJob.getId());
        dto.setCreatedAt(currentJob.getCreatedAt());
        dto.setCreatedBy(currentJob.getCreatedBy());
        dto.setName(currentJob.getName());
        dto.setActive(currentJob.isActive());
        dto.setLevel(currentJob.getLevel());
        dto.setLocation(currentJob.getLocation());
        dto.setSalary(currentJob.getSalary());
        dto.setQuantity(currentJob.getQuantity());
        dto.setEndDate(currentJob.getEndDate());
        dto.setStartDate(currentJob.getStartDate());

        if(currentJob.getSkills() != null){
            List<String> skills = currentJob.getSkills()
                    .stream()
                    .map(item -> item.getName())
                    .collect(Collectors.toList());
            dto.setSkills(skills);
        }

        simpMessagingTemplate.convertAndSend("/topic/jobs", "REFRESH");

        return dto;
    }

    public Optional<Job> fetchJobById(long id){
        return  jobRepository.findById(id);
    }


    public ResUpdateJobDTO update(Job j, Job jobInDB) throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "" ;
        User user = userRepository.findByEmail(email);
        boolean a=true;
        if (user.getRole().getName().equals("SUPER_ADMIN")){
            a=false;
        }
        if(a) {
            if (!user.getCompany().getName().equals(j.getCompany().getName())) {
                throw new IdInvalidException("bạn ko có quyền này");
            }
        }
        if(j.getSkills() != null ){
            List<Long> reqSkills = j.getSkills()
                    .stream()
                    .map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = skillRepository.findByIdIn(reqSkills);
            jobInDB.setSkills(dbSkills);
        }

        if(j.getCompany() != null){
            Optional<Company> companyOptional = companyRepository.findById(j.getCompany().getId());
            if(companyOptional.isPresent()){
                jobInDB.setCompany(companyOptional.get());
            }
        }

        //update
        jobInDB.setName(j.getName());
        jobInDB.setActive(j.isActive());
        jobInDB.setLevel(j.getLevel());
        jobInDB.setLocation(j.getLocation());
        jobInDB.setSalary(j.getSalary());
        jobInDB.setStartDate(j.getStartDate());
        jobInDB.setEndDate(j.getEndDate());
        jobInDB.setQuantity(j.getQuantity());

        Job currentJob = jobRepository.save(jobInDB);

        //convert response
        ResUpdateJobDTO dto = new ResUpdateJobDTO();
        dto.setId(currentJob.getId());
        dto.setCreatedAt(currentJob.getCreatedAt());
        dto.setCreatedBy(currentJob.getCreatedBy());
        dto.setName(currentJob.getName());
        dto.setActive(currentJob.isActive());
        dto.setLevel(currentJob.getLevel());
        dto.setLocation(currentJob.getLocation());
        dto.setSalary(currentJob.getSalary());
        dto.setQuantity(currentJob.getQuantity());
        dto.setEndDate(currentJob.getEndDate());
        dto.setStartDate(currentJob.getStartDate());

        if(currentJob.getSkills() != null){
            List<String> skills = currentJob.getSkills()
                    .stream()
                    .map(item -> item.getName())
                    .collect(Collectors.toList());
            dto.setSkills(skills);
        }
        simpMessagingTemplate.convertAndSend("/topic/jobs", "REFRESH");
        return dto;
    }

    @Transactional
    public void delete(long id, List<Long> resumeId) {
        resumeRepository.deleteByIdIn(resumeId);
        jobRepository.deleteById(id);
        simpMessagingTemplate.convertAndSend("/topic/jobs", "REFRESH");
    }



    public ResultPaginationDTO fetchAllJobs(Specification<Job> spec, Pageable pageable) {

        // Lấy email user đang đăng nhập
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Tìm user
        User currentUser = userRepository.findByEmail(email);

        // Nếu user thuộc công ty
        if (currentUser.getCompany() != null) {

            Long companyId = currentUser.getCompany().getId();

            // Spec lọc theo company
            Specification<Job> companySpec = (root, query, cb) ->
                    cb.equal(root.get("company").get("id"), companyId);

            // Gộp spec cũ + spec company
            spec = spec == null
                    ? companySpec
                    : spec.and(companySpec);
        }

        Page<Job> pageJob = jobRepository.findAll(spec, pageable);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageJob.getNumber() + 1);
        mt.setPageSize(pageJob.getSize());
        mt.setPages(pageJob.getTotalPages());
        mt.setTotal(pageJob.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(pageJob.getContent());

        return rs;
    }

    public static Specification<Job> filterJobs(JobSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            query.distinct(true);

            // 0. LUÔN LỌC CÔNG VIỆC ĐANG HOẠT ĐỘNG
            predicates.add(cb.isTrue(root.get("active")));

            // 1. KỸ NĂNG (Lọc CHẶT CHẼ trong bảng liên kết job_skill - sử dụng Subquery)
            if (criteria.getSkills() != null && !criteria.getSkills().isEmpty()) {
                for (String s : criteria.getSkills()) {
                    // Tạo một subquery để tìm ID của những Job có chứa skill này
                    Subquery<Long> subquery = query.subquery(Long.class);
                    Root<Job> subRoot = subquery.from(Job.class);
                    Join<Object, Object> subSkillJoin = subRoot.join("skills");

                    subquery.select(subRoot.get("id"))
                            .where(cb.like(cb.lower(subSkillJoin.get("name")), "%" + s.toLowerCase() + "%"));

                    // Chỉ lấy những Job có ID nằm trong danh sách của subquery
                    predicates.add(root.get("id").in(subquery));
                }
            }

            // 2. ĐỊA ĐIỂM (Hỗ trợ tìm kiếm theo vùng miền)
            if (criteria.getLocation() != null && !criteria.getLocation().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("location")), "%" + criteria.getLocation().toLowerCase() + "%"));
            }

            // 3. LƯƠNG (Chỉ lọc nếu user có con số cụ thể)
            if (criteria.getSalaryMin() != null && criteria.getSalaryMin() > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("salary"), criteria.getSalaryMin()));
            }

            // 4. LEVEL (Enum Mapping)
            if (criteria.getExperienceLevel() != null) {
                predicates.add(cb.equal(root.get("level").as(String.class), criteria.getExperienceLevel().toUpperCase()));
            }

            // 5. CÔNG TY (Join mạnh mẽ)
            if (criteria.getCompanyName() != null && !criteria.getCompanyName().isEmpty()) {
                Join<Object, Object> companyJoin = root.join("company", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(companyJoin.get("name")), "%" + criteria.getCompanyName().toLowerCase() + "%"));
            }

            // 6. TỪ KHÓA TRONG MÔ TẢ (Phúc lợi, yêu cầu thêm)
            if (criteria.getDescriptionKeywords() != null && !criteria.getDescriptionKeywords().isEmpty()) {
                for (String keyword : criteria.getDescriptionKeywords()) {
                    predicates.add(cb.like(cb.lower(root.get("description")), "%" + keyword.toLowerCase() + "%"));
                }
            }

            // 8. THỜI GIAN TUYỂN DỤNG (startDate & endDate)
            if (criteria.getJobEndDateBefore() != null) {
                try {
                    // Job phải bắt đầu TRƯỚC KHI mốc kết thúc của user tới
                    Instant criteriaEnd = LocalDate.parse(criteria.getJobEndDateBefore()).atStartOfDay(ZoneId.systemDefault()).toInstant();
                    predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), criteriaEnd));
                } catch (Exception e) {}
            }
            if (criteria.getJobStartDateAfter() != null) {
                try {
                    // Job phải kết thúc SAU KHI mốc bắt đầu của user bắt đầu
                    Instant criteriaStart = LocalDate.parse(criteria.getJobStartDateAfter()).atStartOfDay(ZoneId.systemDefault()).toInstant();
                    predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), criteriaStart));
                } catch (Exception e) {}
            }

            // 9. SẮP XẾP (Sorting Logic)
            if (criteria.getSortBy() != null) {
                if (criteria.getSortBy().equalsIgnoreCase("salaryDesc")) {
                    query.orderBy(cb.desc(root.get("salary")));
                } else if (criteria.getSortBy().equalsIgnoreCase("newest")) {
                    query.orderBy(cb.desc(root.get("createdAt")));
                }
            } else {
                query.orderBy(cb.desc(root.get("createdAt")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

}
