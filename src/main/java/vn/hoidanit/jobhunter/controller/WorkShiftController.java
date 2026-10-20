package vn.hoidanit.jobhunter.controller;

import com.turkraft.springfilter.boot.Filter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.WorkShift;
import vn.hoidanit.jobhunter.domain.request.shift.ReqCreateWorkShiftDTO;
import vn.hoidanit.jobhunter.domain.request.shift.ReqTransferWorkShiftDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.shift.ResWorkShiftDTO;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.service.WorkShiftService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class WorkShiftController {

    private final WorkShiftService workShiftService;
    private final UserService userService;

    public WorkShiftController(WorkShiftService workShiftService, UserService userService) {
        this.workShiftService = workShiftService;
        this.userService = userService;
    }

    @PostMapping("/shifts")
    @ApiMessage("Create a new work shift")
    public ResponseEntity<ResWorkShiftDTO> create(@Valid @RequestBody ReqCreateWorkShiftDTO req) throws IdInvalidException {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.workShiftService.create(req));
    }

    @PutMapping("/shifts/{id}")
    @ApiMessage("Update a work shift")
    public ResponseEntity<ResWorkShiftDTO> update(
            @PathVariable("id") Long id,
            @RequestBody WorkShift req
    ) throws IdInvalidException {
        return ResponseEntity.ok(this.workShiftService.update(id, req));
    }

    @PostMapping("/shifts/transfer")
    @ApiMessage("Transfer work shift to another employee")
    public ResponseEntity<ResWorkShiftDTO> transfer(@Valid @RequestBody ReqTransferWorkShiftDTO req) throws IdInvalidException {
        return ResponseEntity.ok(this.workShiftService.transfer(req));
    }

    @DeleteMapping("/shifts/{id}")
    @ApiMessage("Delete a work shift")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) throws IdInvalidException {
        this.workShiftService.delete(id);
        return ResponseEntity.ok(null);
    }

    @GetMapping("/shifts/{id}")
    @ApiMessage("Fetch a work shift by id")
    public ResponseEntity<ResWorkShiftDTO> fetchById(@PathVariable("id") Long id) throws IdInvalidException {
        return ResponseEntity.ok(this.workShiftService.fetchById(id));
    }

    @GetMapping("/shifts")
    @ApiMessage("Fetch all work shifts with pagination")
    public ResponseEntity<ResultPaginationDTO> fetchAll(
            @Filter Specification<WorkShift> spec,
            Pageable pageable
    ) {
        return ResponseEntity.ok(this.workShiftService.fetchAll(spec, pageable));
    }

    @GetMapping("/shifts/my-shifts")
    @ApiMessage("Fetch current user's work shifts")
    public ResponseEntity<ResultPaginationDTO> fetchMyShifts(Pageable pageable) throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        if (email.isEmpty()) {
            throw new IdInvalidException("Bạn chưa đăng nhập");
        }
        User currentUser = this.userService.handleGetUserByUsername(email);
        if (currentUser == null) {
            throw new IdInvalidException("Người dùng không tồn tại");
        }
        return ResponseEntity.ok(this.workShiftService.fetchMyShifts(currentUser, pageable));
    }

    @GetMapping("/shifts/employees")
    @ApiMessage("Fetch employees of a company for shift assignment")
    public ResponseEntity<List<User>> fetchEmployees(@RequestParam(value = "companyId", required = false) Long companyId) {
        return ResponseEntity.ok(this.workShiftService.fetchEmployeesByCompany(companyId));
    }
}
