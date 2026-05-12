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
        boolean isIdExist = resumeService.checkResumeExistByUserAndJob(resume);
        if(!isIdExist){
            throw new IdInvalidException("User or job ko ton tai");
        }
        String email = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "" ;
        List<Resume> resumeDB = resumeService.fetchByEmail(email,resume.getJob());
        if(resumeDB.size()!=0){
            boolean isValid=false;
            isValid = resumeDB.stream().anyMatch(item -> {
                Instant createdAt = item.getCreatedAt();
                Instant start = item.getJob().getStartDate();
                Instant end = item.getJob().getEndDate();

                return createdAt.isAfter(start) && createdAt.isBefore(end);
            });
            if (isValid){
                throw new IdInvalidException("bạn đã nộp cv cho đợt này hãy chờ hr duyệt cv or đợt sau");
            }
        }


        return ResponseEntity.status(HttpStatus.CREATED).body(resumeService.create(resume));
    }

    @PutMapping("/resumes")
    @ApiMessage("update a resume")
    public ResponseEntity<ResUpdateResumeDTO> update(@RequestBody Resume resume) throws IdInvalidException {
        Optional<Resume> resumeOptional = resumeService.fetchById(resume.getId());
        if(resumeOptional.isEmpty()){
            throw new IdInvalidException("resume with id = " + resume.getId() + "not exist");
        }

        Resume reqResume = resumeOptional.get();
        reqResume.setStatus(resume.getStatus());

        return ResponseEntity.ok().body(resumeService.update(reqResume));
    }

    @DeleteMapping("/resumes/{id}")
    @ApiMessage("delete a resume by id")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) throws IdInvalidException {
        Optional<Resume> reqResumeOptional = resumeService.fetchById(id);
        if(reqResumeOptional.isEmpty()){
            throw new IdInvalidException("Resume voi id = " + id + " ko ton tai");
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

        return ResponseEntity.ok().body(resumeService.getResume(reqResumeOptional.get()));
    }

    @GetMapping("/resumes")
    @ApiMessage("get jobs with pagination")
    public ResponseEntity<ResultPaginationDTO> getAllJob(
            @Filter Specification<Resume> spec,
            Pageable pageable ) {

        List<Long> arrJobIds = null;
        String email = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "" ;
        User currentUser = userService.handleGetUserByUsername(email);
        if(currentUser != null){
            Company userCompany = currentUser.getCompany();
            if(userCompany != null){
                List<Job> companyJobs = userCompany.getJobs();
                if(companyJobs != null && companyJobs.size() > 0){
                    arrJobIds = companyJobs.stream().map(x -> x.getId())
                            .collect(Collectors.toList());
                }
            }
        }

        Specification<Resume> finalSpec = spec; // Mặc định chỉ dùng spec từ filter frontend

        // Chỉ khi nào arrJobIds CÓ DỮ LIỆU (tức là HR của công ty) thì mới lọc thêm
        if (arrJobIds != null && !arrJobIds.isEmpty()) {
            Specification<Resume> jobInSpec = filterSpecificationConverter.convert(fiterBuilder.field("job")
                    .in(fiterBuilder.input(arrJobIds)).get());
            finalSpec = finalSpec.and(jobInSpec);
        }

        return ResponseEntity.status(HttpStatus.OK).body(resumeService.fetchAllResume(finalSpec, pageable));
    }

    @PostMapping("/resumes/by-user")
    @ApiMessage("get list resume by user")
    public ResponseEntity<ResultPaginationDTO> fetchResumeByUser(Pageable pageable ) {

        return ResponseEntity.status(HttpStatus.OK).body(resumeService.fetchResumeByUser(pageable));
    }
}
