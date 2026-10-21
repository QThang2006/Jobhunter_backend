package vn.hoidanit.jobhunter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Resume;

import java.util.List;

public interface ResumeRepository extends JpaRepository<Resume,Long>, JpaSpecificationExecutor<Resume> {
    Long deleteByIdIn(List<Long> id);
    List<Resume> findByEmailAndJob(String email, Job JobId);
    List<Resume> findByJobIn(List<Job> jobs);

    @Modifying
    @Transactional
    @Query("UPDATE Resume r SET r.interviewDate = null, r.interviewTime = null, r.meetLink = null, r.interviewType = null, r.note = null WHERE r.status IN (vn.hoidanit.jobhunter.util.constant.ResumeStateEnum.APPROVED, vn.hoidanit.jobhunter.util.constant.ResumeStateEnum.REJECTED)")
    void cleanInterviewSchedulesForApprovedAndRejected();
}