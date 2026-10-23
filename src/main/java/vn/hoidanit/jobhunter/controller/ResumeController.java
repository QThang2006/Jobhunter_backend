package vn.hoidanit.jobhunter.controller;

import com.turkraft.springfilter.boot.Filter;
import com.turkraft.springfilter.builder.FilterBuilder;
import com.turkraft.springfilter.converter.FilterSpecificationConverter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqQuickApplyDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResCreateResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResFetchResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResUpdateResumeDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.ResumeService;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class ResumeController {
    @org.springframework.beans.factory.annotation.Autowired
    private vn.hoidanit.jobhunter.service.AccessService access;


    private final ResumeService resumeService;
    private final UserService userService;

    @Autowired
    private FilterSpecificationConverter filterSpecificationConverter;

    @Autowired
    private FilterBuilder fiterBuilder;

    public ResumeController(ResumeService resumeService, UserService userService) {
        this.resumeService = resumeService;
        this.userService = userService;
    }

    @PostMapping("/resumes")
    @ApiMessage("created a resume")
    public ResponseEntity<ResCreateResumeDTO> create(@Valid @RequestBody Resume resume) throws IdInvalidException {

        return ResponseEntity.status(HttpStatus.CREATED).body(resumeService.create(resume));
    }

    @PostMapping("/resumes/quick-apply")
    @ApiMessage("Ứng tuyển nhanh qua link rải")
    public ResponseEntity<ResCreateResumeDTO> quickApply(@Valid @RequestBody ReqQuickApplyDTO req) throws IdInvalidException {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.resumeService.quickApply(req));
    }

    @PutMapping("/resumes")
    @ApiMessage("update a resume")
    public ResponseEntity<ResUpdateResumeDTO> update(@RequestBody Resume resume) throws IdInvalidException {
        Optional<Resume> resumeOptional = resumeService.fetchById(resume.getId());
        if(resumeOptional.isEmpty()){
            throw new IdInvalidException("resume with id = " + resume.getId() + "not exist");
        }

        Resume reqResume = resumeOptional.get();
        access.requireCompany(reqResume.getJob().getCompany());
        if (resume.getStatus() == null) throw new IdInvalidException("Resume status is required");
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        User currentUser = userService.handleGetUserByUsername(email);
        if (currentUser != null && currentUser.getCompany() != null) {
            Long userCompanyId = currentUser.getCompany().getId();
            if (reqResume.getJob() == null || reqResume.getJob().getCompany() == null
                    || !userCompanyId.equals(reqResume.getJob().getCompany().getId())) {
                throw new IdInvalidException("Bạn không có quyền cập nhật hồ sơ của công ty khác");
            }
        }
        reqResume.setStatus(resume.getStatus());
        if (resume.getStatus() == vn.hoidanit.jobhunter.util.constant.ResumeStateEnum.APPROVED 
                || resume.getStatus() == vn.hoidanit.jobhunter.util.constant.ResumeStateEnum.REJECTED) {
            reqResume.setInterviewDate(null);
            reqResume.setInterviewTime(null);
            reqResume.setInterviewType(null);
            reqResume.setNote(null);
            reqResume.setMeetLink(null);
        } else {
            if (resume.getInterviewDate() != null) reqResume.setInterviewDate(resume.getInterviewDate());
            if (resume.getInterviewTime() != null) reqResume.setInterviewTime(resume.getInterviewTime());
            if (resume.getInterviewType() != null) reqResume.setInterviewType(resume.getInterviewType());
            if (resume.getNote() != null) reqResume.setNote(resume.getNote());
            if (resume.getMeetLink() != null) reqResume.setMeetLink(resume.getMeetLink());
        }

        return ResponseEntity.ok().body(resumeService.update(reqResume));
    }

    @DeleteMapping("/resumes/{id}")
    @ApiMessage("delete a resume by id")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) throws IdInvalidException {
        Optional<Resume> reqResumeOptional = resumeService.fetchById(id);
        if(reqResumeOptional.isEmpty()){
            throw new IdInvalidException("Resume voi id = " + id + " ko ton tai");
        }

        Resume reqResume = reqResumeOptional.get();
        access.requireCompany(reqResume.getJob().getCompany());
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        User currentUser = userService.handleGetUserByUsername(email);
        if (currentUser != null && currentUser.getCompany() != null) {
            Long userCompanyId = currentUser.getCompany().getId();
            if (reqResume.getJob() == null || reqResume.getJob().getCompany() == null
                    || !userCompanyId.equals(reqResume.getJob().getCompany().getId())) {
                throw new IdInvalidException("Bạn không có quyền xóa hồ sơ của công ty khác");
            }
        }

        resumeService.delete(id);

        return ResponseEntity.ok().body(null);
    }

    @GetMapping("/resumes/{id}")
    @ApiMessage("Fetch a resume by id")
    public ResponseEntity<ResFetchResumeDTO> fetchById(@PathVariable("id") long id) throws IdInvalidException {
        Optional<Resume> reqResumeOptional = resumeService.fetchById(id);
        if(reqResumeOptional.isEmpty()){
            throw new IdInvalidException("Resume voi id = " + id + " ko ton tai");
        }

        Resume reqResume = reqResumeOptional.get();
        access.requireCompany(reqResume.getJob().getCompany());
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        User currentUser = userService.handleGetUserByUsername(email);
        if (currentUser != null && currentUser.getCompany() != null) {
            Long userCompanyId = currentUser.getCompany().getId();
            if (reqResume.getJob() == null || reqResume.getJob().getCompany() == null
                    || !userCompanyId.equals(reqResume.getJob().getCompany().getId())) {
                throw new IdInvalidException("Bạn không có quyền xem hồ sơ của công ty khác");
            }
        }

        return ResponseEntity.ok().body(resumeService.getResume(reqResume));
    }

    @GetMapping("/resumes")
    @ApiMessage("get jobs with pagination")
    public ResponseEntity<ResultPaginationDTO> getAllJob(
            @Filter Specification<Resume> spec,
            Pageable pageable ) {

        access.requireCompany(access.currentUser().getCompany());
        Specification<Resume> finalSpec = spec;
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        User currentUser = userService.handleGetUserByUsername(email);
        if (currentUser != null && currentUser.getCompany() != null) {
            Long companyId = currentUser.getCompany().getId();
            Specification<Resume> companySpec = (root, query, cb) ->
                    cb.equal(root.get("job").get("company").get("id"), companyId);
            finalSpec = (finalSpec == null) ? companySpec : finalSpec.and(companySpec);
        }

        return ResponseEntity.status(HttpStatus.OK).body(resumeService.fetchAllResume(finalSpec, pageable));
    }

    @PostMapping("/resumes/by-user")
    @ApiMessage("get list resume by user")
    public ResponseEntity<ResultPaginationDTO> fetchResumeByUser(Pageable pageable ) {

        return ResponseEntity.status(HttpStatus.OK).body(resumeService.fetchResumeByUser(pageable));
    }
}
