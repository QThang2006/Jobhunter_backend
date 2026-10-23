package vn.hoidanit.jobhunter.service;

import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.*;
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
    @org.springframework.beans.factory.annotation.Autowired
    private vn.hoidanit.jobhunter.service.AccessService access;

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
        Optional<String> currentUserLogin = SecurityUtil.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            User currentUser = userRepository.findByEmail(currentUserLogin.get());
            if (currentUser != null && currentUser.getCompany() != null) {
                j.setCompany(currentUser.getCompany());
            }
        }

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

        access.requireCompany(j.getCompany());
        j.setId(null);
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
        access.requireCompany(jobInDB.getCompany());
        String email = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "" ;
        User user = userRepository.findByEmail(email);
        if (user != null && user.getCompany() != null) {
            if (jobInDB.getCompany() == null || !user.getCompany().getId().equals(jobInDB.getCompany().getId())) {
                throw new IdInvalidException("Bạn không có quyền sửa đổi việc làm của công ty khác");
            }
            j.setCompany(user.getCompany());
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
        jobInDB.setDescription(j.getDescription());
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
    public void delete(long id, List<Long> resumeId) throws IdInvalidException {
        Optional<String> currentUserLogin = SecurityUtil.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            User currentUser = userRepository.findByEmail(currentUserLogin.get());
            if (currentUser != null && currentUser.getCompany() != null) {
                Optional<Job> jOpt = jobRepository.findById(id);
                if (jOpt.isPresent() && (jOpt.get().getCompany() == null || !currentUser.getCompany().getId().equals(jOpt.get().getCompany().getId()))) {
                    throw new IdInvalidException("Bạn không có quyền xóa việc làm của công ty khác");
                }
            }
        }

        access.requireCompany(jobRepository.findById(id).orElseThrow().getCompany());
        resumeRepository.deleteByIdIn(resumeId);
        jobRepository.deleteById(id);
        simpMessagingTemplate.convertAndSend("/topic/jobs", "REFRESH");
    }



    public ResultPaginationDTO fetchAllJobs(Specification<Job> spec, Pageable pageable) {
        // Luôn lọc công việc đang hoạt động (active = true) cho người dùng public/client
        Specification<Job> activeSpec = (root, query, cb) -> cb.isTrue(root.get("active"));
        spec = (spec == null) ? activeSpec : spec.and(activeSpec);

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

    public Job updateActiveStatus(long id, boolean active) {
        Optional<Job> jobOptional = jobRepository.findById(id);
        if (jobOptional.isPresent()) {
            Job job = jobOptional.get();
            access.requireCompany(job.getCompany());

            Optional<String> currentUserLogin = SecurityUtil.getCurrentUserLogin();
            if (currentUserLogin.isPresent()) {
                User currentUser = userRepository.findByEmail(currentUserLogin.get());
                if (currentUser != null && currentUser.getCompany() != null) {
                    if (job.getCompany() == null || !currentUser.getCompany().getId().equals(job.getCompany().getId())) {
                        return null;
                    }
                }
            }

            job.setActive(active);
            Job updated = jobRepository.save(job);
            this.simpMessagingTemplate.convertAndSend("/topic/jobs", "REFRESH");
            return updated;
        }
        return null;
    }

    public ResultPaginationDTO fetchAllJobsForAdmin(Specification<Job> spec, Pageable pageable) {
        access.requireCompany(access.currentUser().getCompany());
        Optional<String> currentUserLogin = SecurityUtil.getCurrentUserLogin();

        if (currentUserLogin.isPresent()) {
            String email = currentUserLogin.get();
            User currentUser = userRepository.findByEmail(email);

        // Nếu user thuộc công ty
        if (currentUser.getCompany() != null) {
                
                    Long companyId = currentUser.getCompany().getId();

            // Spec lọc theo company
                    Specification<Job> companySpec = (root, query, cb) ->
                            cb.equal(root.get("company").get("id"), companyId);

                    spec = (spec == null) ? companySpec : spec.and(companySpec);
            }
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

}
