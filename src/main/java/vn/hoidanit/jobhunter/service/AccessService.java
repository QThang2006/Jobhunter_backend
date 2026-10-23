package vn.hoidanit.jobhunter.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;

@Service
public class AccessService {
    private final UserRepository users;

    public AccessService(UserRepository users) {
        this.users = users;
    }

    public User currentUser() {
        User user = SecurityUtil.getCurrentUserLogin().map(users::findByEmail).orElse(null);
        if (user == null || user.getRole() == null || !user.getRole().isActive()) {
            throw new AccessDeniedException("Tai khoan khong co vai tro hoat dong");
        }
        return user;
    }

    public boolean isAdmin(User user) {
        return user != null && user.getRole() != null && user.getRole().isActive()
                && "SUPER_ADMIN".equals(user.getRole().getName());
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(User user, String path, String method) {
        if (isAdmin(user)) return true;
        return user != null && user.getRole() != null && user.getRole().isActive()
                && user.getRole().getPermissions() != null
                && user.getRole().getPermissions().stream().anyMatch(p ->
                    path.equals(p.getApiPath()) && method.equalsIgnoreCase(p.getMethod()));
    }

    public void requirePermission(String path, String method) {
        if (!hasPermission(currentUser(), path, method)) {
            throw new AccessDeniedException("Ban khong co quyen thuc hien thao tac nay");
        }
    }

    public void requireCompany(Company company) {
        User user = currentUser();
        if (!isAdmin(user) && (user.getCompany() == null || company == null
                || !user.getCompany().getId().equals(company.getId()))) {
            throw new AccessDeniedException("Ban khong co quyen truy cap du lieu cong ty nay");
        }
    }
}
