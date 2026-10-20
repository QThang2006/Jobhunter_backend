package vn.hoidanit.jobhunter.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.WorkShift;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WorkShiftRepository extends JpaRepository<WorkShift, Long>, JpaSpecificationExecutor<WorkShift> {
    Page<WorkShift> findByUserOrderByShiftDateDescStartTimeAsc(User user, Pageable pageable);

    List<WorkShift> findByUserAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
            User user, LocalDate fromDate, LocalDate toDate);

    List<WorkShift> findByCompanyAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
            Company company, LocalDate fromDate, LocalDate toDate);

    Page<WorkShift> findByCompanyOrderByShiftDateDesc(Company company, Pageable pageable);
}
