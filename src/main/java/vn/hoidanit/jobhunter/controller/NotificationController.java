package vn.hoidanit.jobhunter.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.NotificationService;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService,
                                  UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    private User getCurrentUser() throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new IdInvalidException("Không xác định được người dùng"));
        User user = userService.handleGetUserByUsername(email);
        if (user == null) throw new IdInvalidException("User không tồn tại");
        return user;
    }

    @GetMapping("/notifications")
    @ApiMessage("Fetch notifications by current user")
    public ResponseEntity<ResultPaginationDTO> getMyNotifications(Pageable pageable)
            throws IdInvalidException {
        User user = getCurrentUser();
        return ResponseEntity.ok(notificationService.getNotificationsByUser(user, pageable));
    }

    @GetMapping("/notifications/unread-count")
    @ApiMessage("Count unread notifications")
    public ResponseEntity<Map<String, Long>> countUnread() throws IdInvalidException {
        User user = getCurrentUser();
        long count = notificationService.countUnread(user);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }


    @PutMapping("/notifications/{id}/read")
    @ApiMessage("Mark notification as read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) throws IdInvalidException {
        User user = getCurrentUser();
        boolean success = notificationService.markAsRead(id, user);
        if (!success) throw new IdInvalidException("Thông báo không tồn tại hoặc không có quyền truy cập");
        return ResponseEntity.ok().build();
    }


    @PutMapping("/notifications/read-all")
    @ApiMessage("Mark all notifications as read")
    public ResponseEntity<Void> markAllAsRead() throws IdInvalidException {
        User user = getCurrentUser();
        notificationService.markAllAsRead(user);
        return ResponseEntity.ok().build();
    }
}
