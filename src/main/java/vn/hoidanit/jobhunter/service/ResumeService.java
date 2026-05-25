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
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.*;
import vn.hoidanit.jobhunter.domain.response.resume.ResCreateResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResFetchResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResUpdateResumeDTO;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;

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

    public ResumeService(SimpMessagingTemplate simpMessagingTemplate,
                         ResumeRepository resumeRepository,
                         UserRepository userRepository,
                         JobRepository jobRepository,
                         @Lazy NotificationService notificationService) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.notificationService = notificationService;
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
        Resume resume1 = resumeRepository.save(resume);
        this.simpMessagingTemplate.convertAndSend("/topic/resumes", "REFRESH");

        ResCreateResumeDTO res = new ResCreateResumeDTO();
        res.setId(resume1.getId());
        res.setCreatedAt(resume1.getCreatedAt());
        res.setCreatedBy(resume1.getCreatedBy());

        return res;

    }

    public Optional<Resume> fetchById(long id) {
        return resumeRepository.findById(id);
    }

    public List<Resume> fetchByEmail(String email,Job Job){
        return   resumeRepository.findByEmailAndJob(email,Job);
    }

    public ResUpdateResumeDTO update(Resume resume) {
        Resume resume1 = resumeRepository.save(resume);
        ResUpdateResumeDTO res = new ResUpdateResumeDTO();
        res.setUpdateAt(resume1.getUpdatedAt());
        res.setUpdateBy(resume1.getUpdatedBy());
        this.simpMessagingTemplate.convertAndSend("/topic/resumes", "REFRESH");

        notificationService.createResumeNotification(resume1);

        return res;
    }

    public ResFetchResumeDTO getResume(Resume resume) {
        ResFetchResumeDTO res = new ResFetchResumeDTO();
        res.setId(resume.getId());
        res.setStatus(resume.getStatus());
        res.setEmail(resume.getEmail());
        res.setUrl(resume.getUrl());
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
