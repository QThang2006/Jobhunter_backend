package vn.hoidanit.jobhunter.service;

import com.turkraft.springfilter.converter.FilterSpecification;
import com.turkraft.springfilter.converter.FilterSpecificationConverter;
import com.turkraft.springfilter.parser.FilterParser;
import com.turkraft.springfilter.parser.node.FilterNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqQuickApplyDTO;
import vn.hoidanit.jobhunter.domain.response.*;
import vn.hoidanit.jobhunter.domain.response.resume.ResCreateResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResFetchResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResUpdateResumeDTO;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.RoleRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.constant.ResumeStateEnum;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ResumeService {
    @Autowired
    private FilterParser filterParser;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private FilterSpecificationConverter filterSpecificationConverter;

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final NotificationService notificationService;

    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;

    public ResumeService(SimpMessagingTemplate simpMessagingTemplate,
                         ResumeRepository resumeRepository,
                         UserRepository userRepository,
                         JobRepository jobRepository,
                         @Lazy NotificationService notificationService,
                         @Lazy EmailService emailService,
                         @Lazy PasswordEncoder passwordEncoder,
                         RoleRepository roleRepository) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
    }

    public boolean checkResumeExistByUserAndJob(Resume resume) {

        if(resume.getUser() == null){
            return false;
        }
        Optional<User> userOptional = userRepository.findById(resume.getUser().getId());
        if(userOptional.isEmpty()){
            return false;
        }

        if(resume.getJob() == null){
            return false;
        }
        Optional<Job> jobOptional = jobRepository.findById(resume.getJob().getId());
        if(jobOptional.isEmpty()){
            return false;
        }

        return true;

    }

    public ResCreateResumeDTO create(Resume resume) {
        if (resume.getUser() != null && resume.getPhone() != null && !resume.getPhone().trim().isEmpty()) {
            Optional<User> uOpt = this.userRepository.findById(resume.getUser().getId());
            if (uOpt.isPresent()) {
                User u = uOpt.get();
                if (u.getPhone() == null || u.getPhone().isEmpty()) {
                    u.setPhone(resume.getPhone().trim());
                    this.userRepository.save(u);
                }
            }
        }

        Resume resume1 = resumeRepository.save(resume);
        // Broadcast cho admin (reload bảng tổng)
        this.simpMessagingTemplate.convertAndSend("/topic/resumes", "REFRESH");
        // Broadcast riêng cho user sở hữu resume (reload "CV của tôi")
        if (resume1.getUser() != null) {
            this.simpMessagingTemplate.convertAndSend(
                    "/topic/resumes/" + resume1.getUser().getId(), "REFRESH"
            );
        }

        try {
            if (emailService != null && resume1.getJob() != null) {
                Optional<Job> jOpt = this.jobRepository.findById(resume1.getJob().getId());
                if (jOpt.isPresent()) {
                    Job j = jOpt.get();
                    String companyName = j.getCompany() != null ? j.getCompany().getName() : "Tiệm Bánh";
                    String candidateName = resume1.getUser() != null ? resume1.getUser().getName() : "Ứng viên";
                    emailService.sendQuickApplyConfirmationEmail(
                            resume1.getEmail(),
                            candidateName,
                            companyName,
                            j.getName(),
                            false,
                            null
                    );
                }
            }
        } catch (Exception ex) {
            System.err.println(">>> [EMAIL APPLY ERROR] " + ex.getMessage());
        }

        ResCreateResumeDTO res = new ResCreateResumeDTO();
        res.setId(resume1.getId());
        res.setCreatedAt(resume1.getCreatedAt());
        res.setCreatedBy(resume1.getCreatedBy());

        return res;

    }

    public ResCreateResumeDTO quickApply(ReqQuickApplyDTO req) throws IdInvalidException {
        // 1. Kiểm tra Job
        Optional<Job> jobOpt = this.jobRepository.findById(req.getJobId());
        if (jobOpt.isEmpty()) {
            throw new IdInvalidException("Vị trí tuyển dụng với ID = " + req.getJobId() + " không tồn tại");
        }
        Job job = jobOpt.get();
        if (!job.isActive()) {
            throw new IdInvalidException("Vị trí tuyển dụng này hiện đang tạm ngưng nhận hồ sơ");
        }

        // 2. Tìm hoặc tự động tạo User ngầm
        String email = req.getEmail().trim().toLowerCase();
        User user = this.userRepository.findByEmail(email);
        boolean isNewAccount = false;
        String temporaryPassword = null;

        if (user == null) {
            isNewAccount = true;
            user = new User();
            user.setEmail(email);
            user.setName(req.getName().trim());
            if (req.getPhone() != null && !req.getPhone().trim().isEmpty()) {
                user.setPhone(req.getPhone().trim());
            }
            temporaryPassword = "BakeJob@" + (1000 + (int)(Math.random() * 9000));
            user.setPassword(this.passwordEncoder.encode(temporaryPassword));

            Role userRole = this.roleRepository.findByName("USER");
            if (userRole != null) {
                user.setRole(userRole);
            }
            user = this.userRepository.save(user);
        } else if (user.getPhone() == null && req.getPhone() != null && !req.getPhone().trim().isEmpty()) {
            user.setPhone(req.getPhone().trim());
            this.userRepository.save(user);
        }

        // 3. Tạo Resume
        Resume resume = new Resume();
        resume.setEmail(email);
        resume.setUrl(req.getUrlCV());
        resume.setUser(user);
        resume.setJob(job);
        resume.setStatus(ResumeStateEnum.PENDING);
        if (req.getPhone() != null && !req.getPhone().trim().isEmpty()) {
            resume.setPhone(req.getPhone().trim());
        }
        if (req.getNote() != null && !req.getNote().trim().isEmpty()) {
            resume.setMessage(req.getNote().trim());
        }

        Resume savedResume = this.resumeRepository.save(resume);

        // 4. Real-time broadcast
        this.simpMessagingTemplate.convertAndSend("/topic/resumes", "REFRESH");
        if (user.getId() != null) {
            this.simpMessagingTemplate.convertAndSend("/topic/resumes/" + user.getId(), "REFRESH");
        }

        // 5. Gửi email xác nhận nộp đơn và thông tin tài khoản cho ứng viên
        try {
            if (this.emailService != null) {
                String companyName = job.getCompany() != null ? job.getCompany().getName() : "Tiệm Bánh";
                System.out.println(">>> [QUICK APPLY] Đang gọi gửi email cho: " + email + ", isNewAccount=" + isNewAccount);
                this.emailService.sendQuickApplyConfirmationEmail(
                        email,
                        user.getName(),
                        companyName,
                        job.getName(),
                        isNewAccount,
                        temporaryPassword
                );
            } else {
                System.err.println(">>> [QUICK APPLY ERROR] emailService is null!");
            }
        } catch (Exception ex) {
            System.err.println(">>> [EMAIL QUICK APPLY ERROR] " + ex.getMessage());
            ex.printStackTrace();
        }

        ResCreateResumeDTO res = new ResCreateResumeDTO();
        res.setId(savedResume.getId());
        res.setCreatedAt(savedResume.getCreatedAt());
        res.setCreatedBy(savedResume.getCreatedBy());
        return res;
    }

    public Optional<Resume> fetchById(long id) {
        return resumeRepository.findById(id);
    }

    public List<Resume> fetchByEmail(String email,Job Job){
        return   resumeRepository.findByEmailAndJob(email,Job);
    }

    public ResUpdateResumeDTO update(Resume resume) {
        // Khi duyệt nhận việc chính thức (APPROVED):
        // 1. Tự động gán nhân viên vào tiệm bánh
        // 2. Xóa lịch phỏng vấn khỏi lịch tuần (vì đã phỏng vấn xong và trúng tuyển)
        // 3. Tự động gửi email chúc mừng trúng tuyển cho ứng viên
        if (resume.getStatus() == ResumeStateEnum.APPROVED) {
            resume.setInterviewDate(null);
            resume.setInterviewTime(null);
            resume.setMeetLink(null);
            resume.setInterviewType(null);
            resume.setNote(null);

            if (resume.getUser() != null && resume.getJob() != null) {
                User user = resume.getUser();
                if (resume.getJob().getCompany() != null) {
                    user.setCompany(resume.getJob().getCompany());
                    this.userRepository.save(user);
                    this.simpMessagingTemplate.convertAndSend("/topic/users/" + user.getId(), "REFRESH");
                }
            }

            try {
                String toEmail = resume.getEmail();
                String candidateName = resume.getUser() != null ? resume.getUser().getName() : "Ứng viên";
                String companyName = resume.getJob() != null && resume.getJob().getCompany() != null
                        ? resume.getJob().getCompany().getName()
                        : "Tiệm Bánh";
                String jobName = resume.getJob() != null ? resume.getJob().getName() : "Vị trí làm việc";

                if (toEmail != null && !toEmail.isBlank() && this.emailService != null) {
                    System.out.println(">>> [APPROVED EMAIL] Bắt đầu gửi email chúc mừng trúng tuyển tới: " + toEmail);
                    this.emailService.sendApprovalEmail(toEmail, candidateName, companyName, jobName);
                }
            } catch (Exception ex) {
                System.err.println(">>> [APPROVED EMAIL ERROR] Lỗi khi gửi email chúc mừng: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        // Khi từ chối hồ sơ (REJECTED): Xóa lịch phỏng vấn khỏi lịch tuần & gửi email từ chối cho ứng viên
        if (resume.getStatus() == ResumeStateEnum.REJECTED) {
            resume.setInterviewDate(null);
            resume.setInterviewTime(null);
            resume.setMeetLink(null);
            resume.setInterviewType(null);
            resume.setNote(null);

            try {
                String toEmail = resume.getEmail();
                String candidateName = resume.getUser() != null ? resume.getUser().getName() : "Ứng viên";
                String companyName = resume.getJob() != null && resume.getJob().getCompany() != null
                        ? resume.getJob().getCompany().getName()
                        : "Tiệm Bánh";
                String jobName = resume.getJob() != null ? resume.getJob().getName() : "Vị trí ứng tuyển";

                if (toEmail != null && !toEmail.isBlank() && emailService != null) {
                    emailService.sendRejectionEmail(toEmail, candidateName, companyName, jobName);
                }
            } catch (Exception ex) {
                System.err.println(">>> [REJECTION EMAIL ERROR] Lỗi khi gửi email từ chối: " + ex.getMessage());
            }
        }

        Resume resume1 = resumeRepository.save(resume);
        ResUpdateResumeDTO res = new ResUpdateResumeDTO();
        res.setUpdateAt(resume1.getUpdatedAt());
        res.setUpdateBy(resume1.getUpdatedBy());
        // Broadcast cho admin
        this.simpMessagingTemplate.convertAndSend("/topic/resumes", "REFRESH");
        // Broadcast riÃªng cho user sá»Ÿ há»¯u resume (reload "CV cá»§a tÃ´i")
        if (resume1.getUser() != null) {
            this.simpMessagingTemplate.convertAndSend(
                    "/topic/resumes/" + resume1.getUser().getId(), "REFRESH"
            );
        }

        // Gá»­i notification real-time cho user
        notificationService.createResumeNotification(resume1);

        return res;
    }

    public ResFetchResumeDTO getResume(Resume resume) {
        ResFetchResumeDTO res = new ResFetchResumeDTO();
        res.setId(resume.getId());
        res.setStatus(resume.getStatus());
        res.setEmail(resume.getEmail());
        res.setPhone(resume.getPhone());
        res.setMessage(resume.getMessage());
        res.setUrl(resume.getUrl());
        res.setMeetLink(resume.getMeetLink());
        res.setInterviewDate(resume.getInterviewDate());
        res.setInterviewTime(resume.getInterviewTime());
        res.setInterviewType(resume.getInterviewType());
        res.setNote(resume.getNote());
        res.setCreatedAt(resume.getCreatedAt());
        res.setUpdatedAt(resume.getUpdatedAt());
        res.setCreatedBy(resume.getCreatedBy());
        res.setUpdatedBy(resume.getUpdatedBy());

        if(resume.getJob() != null){
            res.setCompanyName(resume.getJob().getCompany().getName());
        }

        res.setUser(new ResFetchResumeDTO.UserResume(resume.getUser().getId(),resume.getUser().getName()));
        res.setJob(new ResFetchResumeDTO.JobResume(resume.getJob().getId(),resume.getJob().getName()));

        return res;
    }

    public ResultPaginationDTO fetchAllResume(Specification<Resume> spec, Pageable pageable){
        Page<Resume> pageUser = resumeRepository.findAll(spec,pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageUser.getNumber()+1);
        mt.setPageSize(pageUser.getSize());
        mt.setPages(pageUser.getTotalPages());
        mt.setTotal(pageUser.getTotalElements());

        List<ResFetchResumeDTO> listUser = pageUser.getContent()
                .stream().map(item -> getResume(item) )
                .collect(Collectors.toList());

        rs.setMeta(mt);
        rs.setResult(listUser);

        return  rs;
    }

    public void delete(long id){
        resumeRepository.deleteById(id);
        this.simpMessagingTemplate.convertAndSend("/topic/resumes", "REFRESH");
    }

    @Transactional
    public void deleteResumesByIds(List<Long> ids) {
        resumeRepository.deleteByIdIn(ids);
    }

    public ResultPaginationDTO fetchResumeByUser(Pageable pageable){
        String email = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "";
        FilterNode node = filterParser.parse("email='" +email+ "'");
        FilterSpecification<Resume> spec = filterSpecificationConverter.convert(node);
        Page<Resume> pageResume = resumeRepository.findAll(spec,pageable);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageResume.getNumber()+1);
        mt.setPageSize(pageResume.getSize());
        mt.setPages(pageResume.getTotalPages());
        mt.setTotal(pageResume.getTotalElements());

        List<ResFetchResumeDTO> listUser = pageResume.getContent()
                .stream().map(item -> getResume(item) )
                .collect(Collectors.toList());

        rs.setMeta(mt);
        rs.setResult(listUser);

        return  rs;

    }

}
