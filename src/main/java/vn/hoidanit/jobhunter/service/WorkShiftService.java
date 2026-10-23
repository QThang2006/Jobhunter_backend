package vn.hoidanit.jobhunter.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import vn.hoidanit.jobhunter.domain.*;
import vn.hoidanit.jobhunter.domain.request.shift.ReqCreateWorkShiftDTO;
import vn.hoidanit.jobhunter.domain.request.shift.ReqTransferWorkShiftDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.shift.ResWorkShiftDTO;
import vn.hoidanit.jobhunter.repository.*;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.constant.WorkShiftStatusEnum;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WorkShiftService {
    @org.springframework.beans.factory.annotation.Autowired
    private vn.hoidanit.jobhunter.service.AccessService access;


    private final WorkShiftRepository workShiftRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public WorkShiftService(WorkShiftRepository workShiftRepository,
                            UserRepository userRepository,
                            CompanyRepository companyRepository,
                            JobRepository jobRepository,
                            NotificationRepository notificationRepository,
                            SimpMessagingTemplate simpMessagingTemplate) {
        this.workShiftRepository = workShiftRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.jobRepository = jobRepository;
        this.notificationRepository = notificationRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ResWorkShiftDTO create(ReqCreateWorkShiftDTO req) throws IdInvalidException {
        Optional<String> currentUserLogin = SecurityUtil.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            User currentUser = this.userRepository.findByEmail(currentUserLogin.get());
            if (currentUser != null && currentUser.getCompany() != null) {
                req.setCompanyId(currentUser.getCompany().getId());
            }
        }

        if (req.getCompanyId() == null) throw new IdInvalidException("Company is required");
        Optional<User> userOpt = this.userRepository.findById(req.getUserId());
        if (userOpt.isEmpty()) {
            throw new IdInvalidException("Nhân viên với id = " + req.getUserId() + " không tồn tại");
        }

        Optional<Company> companyOpt = this.companyRepository.findById(req.getCompanyId());
        if (companyOpt.isEmpty()) {
            throw new IdInvalidException("Tiệm bánh với id = " + req.getCompanyId() + " không tồn tại");
        }

        WorkShift shift = new WorkShift();
        shift.setUser(userOpt.get());
        shift.setCompany(companyOpt.get());
        shift.setShiftDate(req.getShiftDate());
        shift.setShiftName(req.getShiftName() != null ? req.getShiftName() : "Ca làm việc");
        shift.setStartTime(req.getStartTime());
        shift.setEndTime(req.getEndTime());
        shift.setNote(req.getNote());
        shift.setStatus(WorkShiftStatusEnum.ASSIGNED);

        if (req.getJobId() != null) {
            Optional<Job> jobOpt = this.jobRepository.findById(req.getJobId());
            jobOpt.ifPresent(shift::setJob);
        }

        validateShift(shift);
        WorkShift saved = this.workShiftRepository.save(shift);

        // Real-time broadcast
        this.simpMessagingTemplate.convertAndSend("/topic/shifts", "REFRESH");
        this.simpMessagingTemplate.convertAndSend("/topic/shifts/" + saved.getUser().getId(), "REFRESH");

        // Send in-app notification to employee
        createNotification(
                saved.getUser(),
                "Phân ca làm việc mới",
                "Bạn được xếp ca " + saved.getShiftName() + " (" + (saved.getStartTime() != null ? saved.getStartTime() : "")
                        + " - " + (saved.getEndTime() != null ? saved.getEndTime() : "") + ") ngày " + saved.getShiftDate()
                        + " tại " + saved.getCompany().getName() + "."
        );

        return convertToDTO(saved);
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ResWorkShiftDTO update(Long id, WorkShift req) throws IdInvalidException {
        Optional<WorkShift> shiftOpt = this.workShiftRepository.findById(id);
        if (shiftOpt.isEmpty()) {
            throw new IdInvalidException("Ca làm việc với id = " + id + " không tồn tại");
        }

        WorkShift current = shiftOpt.get();
        access.requireCompany(current.getCompany());

        Optional<String> currentUserLogin = SecurityUtil.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            User currentUser = this.userRepository.findByEmail(currentUserLogin.get());
            if (currentUser != null && currentUser.getCompany() != null) {
                if (current.getCompany() == null || !current.getCompany().getId().equals(currentUser.getCompany().getId())) {
                    throw new IdInvalidException("Bạn không có quyền sửa đổi ca làm việc của công ty khác");
                }
            }
        }

        if (req.getShiftName() != null) current.setShiftName(req.getShiftName());
        if (req.getShiftDate() != null) current.setShiftDate(req.getShiftDate());
        if (req.getStartTime() != null) current.setStartTime(req.getStartTime());
        if (req.getEndTime() != null) current.setEndTime(req.getEndTime());
        if (req.getNote() != null) current.setNote(req.getNote());
        if (req.getStatus() != null) current.setStatus(req.getStatus());

        if (req.getUser() != null && req.getUser().getId() != null) {
            Optional<User> uOpt = this.userRepository.findById(req.getUser().getId());
            uOpt.ifPresent(current::setUser);
        }

        validateShift(current);
        WorkShift saved = this.workShiftRepository.save(current);

        this.simpMessagingTemplate.convertAndSend("/topic/shifts", "REFRESH");
        if (saved.getUser() != null) {
            this.simpMessagingTemplate.convertAndSend("/topic/shifts/" + saved.getUser().getId(), "REFRESH");
        }

        return convertToDTO(saved);
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ResWorkShiftDTO transfer(ReqTransferWorkShiftDTO req) throws IdInvalidException {
        Optional<WorkShift> shiftOpt = this.workShiftRepository.findById(req.getShiftId());
        if (shiftOpt.isEmpty()) {
            throw new IdInvalidException("Ca làm việc với id = " + req.getShiftId() + " không tồn tại");
        }

        Optional<User> newUserOpt = this.userRepository.findById(req.getNewUserId());
        if (newUserOpt.isEmpty()) {
            throw new IdInvalidException("Nhân viên mới nhận ca với id = " + req.getNewUserId() + " không tồn tại");
        }

        WorkShift shift = shiftOpt.get();
        access.requireCompany(shift.getCompany());
        User oldUser = shift.getUser();
        User newUser = newUserOpt.get();

        Optional<String> currentUserLogin = SecurityUtil.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            User currentUser = this.userRepository.findByEmail(currentUserLogin.get());
            if (currentUser != null && currentUser.getCompany() != null) {
                if (shift.getCompany() == null || !shift.getCompany().getId().equals(currentUser.getCompany().getId())) {
                    throw new IdInvalidException("Bạn không có quyền chuyển ca làm việc của công ty khác");
                }
                if (newUser.getCompany() == null || !newUser.getCompany().getId().equals(currentUser.getCompany().getId())) {
                    throw new IdInvalidException("Không thể chuyển ca cho nhân viên của công ty khác");
                }
            }
        }

        String oldUserName = oldUser != null ? oldUser.getName() : "Nhân viên cũ";
        String newUserName = newUser.getName();

        // Ghi chú lịch sử chuyển ca
        String transferNote = "[ĐÃ CHUYỂN CA] Từ: " + oldUserName + " -> Sang: " + newUserName;
        if (req.getReason() != null && !req.getReason().trim().isEmpty()) {
            transferNote += " (Lý do: " + req.getReason() + ")";
        }
        if (shift.getNote() != null && !shift.getNote().isEmpty()) {
            shift.setNote(shift.getNote() + " | " + transferNote);
        } else {
            shift.setNote(transferNote);
        }

        shift.setUser(newUser);
        if (req.getNewShiftDate() != null) {
            shift.setShiftDate(req.getNewShiftDate());
        }
        shift.setStatus(WorkShiftStatusEnum.TRANSFERRED);

        validateShift(shift);
        WorkShift saved = this.workShiftRepository.save(shift);

        // Broadcast websocket
        this.simpMessagingTemplate.convertAndSend("/topic/shifts", "REFRESH");
        if (oldUser != null) {
            this.simpMessagingTemplate.convertAndSend("/topic/shifts/" + oldUser.getId(), "REFRESH");
            createNotification(
                    oldUser,
                    "Ca làm việc của bạn đã được chuyển giao",
                    "Ca " + saved.getShiftName() + " ngày " + saved.getShiftDate() + " đã được chuyển cho " + newUserName + "."
            );
        }
        this.simpMessagingTemplate.convertAndSend("/topic/shifts/" + newUser.getId(), "REFRESH");
        createNotification(
                newUser,
                "Bạn vừa nhận ca làm việc được chuyển",
                "Bạn được phân công nhận ca " + saved.getShiftName() + " ngày " + saved.getShiftDate()
                        + " từ " + oldUserName + "." + (req.getReason() != null ? " Lý do: " + req.getReason() : "")
        );

        return convertToDTO(saved);
    }

    public void delete(Long id) throws IdInvalidException {
        Optional<WorkShift> shiftOpt = this.workShiftRepository.findById(id);
        if (shiftOpt.isEmpty()) {
            throw new IdInvalidException("Ca làm việc với id = " + id + " không tồn tại");
        }
        WorkShift shift = shiftOpt.get();
        access.requireCompany(shift.getCompany());

        Optional<String> currentUserLogin = SecurityUtil.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            User currentUser = this.userRepository.findByEmail(currentUserLogin.get());
            if (currentUser != null && currentUser.getCompany() != null) {
                if (shift.getCompany() == null || !shift.getCompany().getId().equals(currentUser.getCompany().getId())) {
                    throw new IdInvalidException("Bạn không có quyền xóa ca làm việc của công ty khác");
                }
            }
        }

        Long userId = shift.getUser() != null ? shift.getUser().getId() : null;

        this.workShiftRepository.deleteById(id);

        this.simpMessagingTemplate.convertAndSend("/topic/shifts", "REFRESH");
        if (userId != null) {
            this.simpMessagingTemplate.convertAndSend("/topic/shifts/" + userId, "REFRESH");
        }
    }

    public ResWorkShiftDTO fetchById(Long id) throws IdInvalidException {
        Optional<WorkShift> shiftOpt = this.workShiftRepository.findById(id);
        if (shiftOpt.isEmpty()) {
            throw new IdInvalidException("Ca làm việc với id = " + id + " không tồn tại");
        }
        WorkShift shift = shiftOpt.get();
        access.requireCompany(shift.getCompany());

        Optional<String> currentUserLogin = SecurityUtil.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            User currentUser = this.userRepository.findByEmail(currentUserLogin.get());
            if (currentUser != null && currentUser.getCompany() != null) {
                if (shift.getCompany() == null || !shift.getCompany().getId().equals(currentUser.getCompany().getId())) {
                    throw new IdInvalidException("Bạn không có quyền xem ca làm việc của công ty khác");
                }
            }
        }

        return convertToDTO(shift);
    }

    public ResultPaginationDTO fetchAll(Specification<WorkShift> spec, Pageable pageable) {
        access.requireCompany(access.currentUser().getCompany());
        Optional<String> currentUserLogin = SecurityUtil.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            User currentUser = this.userRepository.findByEmail(currentUserLogin.get());
            if (currentUser != null && currentUser.getCompany() != null) {
                Long companyId = currentUser.getCompany().getId();
                Specification<WorkShift> companySpec = (root, query, cb) ->
                        cb.equal(root.get("company").get("id"), companyId);
                spec = (spec == null) ? companySpec : spec.and(companySpec);
            }
        }

        Page<WorkShift> pageShift = this.workShiftRepository.findAll(spec, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageShift.getNumber() + 1);
        mt.setPageSize(pageShift.getSize());
        mt.setPages(pageShift.getTotalPages());
        mt.setTotal(pageShift.getTotalElements());
        rs.setMeta(mt);

        List<ResWorkShiftDTO> list = pageShift.getContent()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        rs.setResult(list);
        return rs;
    }

    public ResultPaginationDTO fetchMyShifts(User user, Pageable pageable) {
        Page<WorkShift> pageShift = this.workShiftRepository.findByUserOrderByShiftDateDescStartTimeAsc(user, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageShift.getNumber() + 1);
        mt.setPageSize(pageShift.getSize());
        mt.setPages(pageShift.getTotalPages());
        mt.setTotal(pageShift.getTotalElements());
        rs.setMeta(mt);

        List<ResWorkShiftDTO> list = pageShift.getContent()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        rs.setResult(list);
        return rs;
    }

    public record Employee(Long id, String name, String email) { }

    public List<Employee> fetchEmployeesByCompany(Long companyId) {
        User caller = access.currentUser();
        if (!access.isAdmin(caller)) {
            access.requireCompany(caller.getCompany());
            companyId = caller.getCompany().getId();
        }
        List<User> employees;
        if (companyId == null) {
            employees = userRepository.findAll().stream().filter(u -> u.getCompany() != null).toList();
        } else {
            Company company = companyRepository.findById(companyId).orElse(null);
            if (company == null) return List.of();
            employees = userRepository.findByCompany(company);
        }
        return employees.stream().map(u -> new Employee(u.getId(), u.getName(), u.getEmail())).toList();
    }

    void validateShift(WorkShift shift) throws IdInvalidException {
        access.requireCompany(shift.getCompany());
        if (shift.getUser() == null || shift.getCompany() == null || shift.getShiftDate() == null) {
            throw new IdInvalidException("Employee, company and date are required");
        }
        // Lock the employee so concurrent assignments cannot both pass the overlap check.
        User employee = userRepository.lockById(shift.getUser().getId()).orElseThrow(
                () -> new IdInvalidException("Employee not found"));
        if (employee.getCompany() == null || !employee.getCompany().getId().equals(shift.getCompany().getId())) {
            throw new IdInvalidException("Employee must belong to the shift company");
        }
        if (shift.getJob() != null && (shift.getJob().getCompany() == null
                || !shift.getCompany().getId().equals(shift.getJob().getCompany().getId()))) {
            throw new IdInvalidException("Job must belong to the shift company");
        }
        java.time.LocalDateTime[] proposed = shiftInterval(shift);
        if (shift.getStatus() == WorkShiftStatusEnum.CANCELLED) return;
        List<WorkShift> nearby = workShiftRepository.findByUserAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
                employee, shift.getShiftDate().minusDays(1), shift.getShiftDate().plusDays(1));
        for (WorkShift other : nearby) {
            if (java.util.Objects.equals(other.getId(), shift.getId()) || other.getStatus() == WorkShiftStatusEnum.CANCELLED) continue;
            java.time.LocalDateTime[] existing = shiftInterval(other);
            if (proposed[0].isBefore(existing[1]) && existing[0].isBefore(proposed[1])) {
                throw new IdInvalidException("Employee already has an overlapping shift");
            }
        }
    }

    private java.time.LocalDateTime[] shiftInterval(WorkShift shift) throws IdInvalidException {
        try {
            java.time.LocalTime start = java.time.LocalTime.parse(shift.getStartTime());
            java.time.LocalTime end = java.time.LocalTime.parse(shift.getEndTime());
            if (start.equals(end)) throw new IllegalArgumentException();
            java.time.LocalDateTime from = shift.getShiftDate().atTime(start);
            java.time.LocalDateTime to = shift.getShiftDate().atTime(end);
            if (end.isBefore(start)) to = to.plusDays(1);
            return new java.time.LocalDateTime[] { from, to };
        } catch (RuntimeException ex) {
            throw new IdInvalidException("Shift times must be valid HH:mm values with different start and end");
        }
    }

    private void createNotification(User user, String title, String body) {
        if (user == null) return;
        Notification n = new Notification();
        n.setTitle(title);
        n.setBody(body);
        n.setUser(user);
        n.setRead(false);
        this.notificationRepository.save(n);

        this.simpMessagingTemplate.convertAndSend(
                "/topic/notifications/" + user.getId(),
                "NEW_NOTIFICATION"
        );
    }

    public ResWorkShiftDTO convertToDTO(WorkShift s) {
        ResWorkShiftDTO dto = new ResWorkShiftDTO();
        dto.setId(s.getId());
        dto.setShiftDate(s.getShiftDate());
        dto.setShiftName(s.getShiftName());
        dto.setStartTime(s.getStartTime());
        dto.setEndTime(s.getEndTime());
        dto.setNote(s.getNote());
        dto.setStatus(s.getStatus());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        dto.setCreatedBy(s.getCreatedBy());
        dto.setUpdatedBy(s.getUpdatedBy());

        if (s.getUser() != null) {
            ResWorkShiftDTO.UserShift u = new ResWorkShiftDTO.UserShift();
            u.setId(s.getUser().getId());
            u.setName(s.getUser().getName());
            u.setEmail(s.getUser().getEmail());
            dto.setUser(u);
        }

        if (s.getCompany() != null) {
            ResWorkShiftDTO.CompanyShift c = new ResWorkShiftDTO.CompanyShift();
            c.setId(s.getCompany().getId());
            c.setName(s.getCompany().getName());
            c.setLogo(s.getCompany().getLogo());
            dto.setCompany(c);
        }

        if (s.getJob() != null) {
            ResWorkShiftDTO.JobShift j = new ResWorkShiftDTO.JobShift();
            j.setId(s.getJob().getId());
            j.setName(s.getJob().getName());
            dto.setJob(j);
        }

        return dto;
    }
}
