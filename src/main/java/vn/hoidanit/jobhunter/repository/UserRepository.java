package vn.hoidanit.jobhunter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;

import java.time.Instant;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User,Long>, JpaSpecificationExecutor<User> {
     @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
     @Query("select u from User u where u.id = :id")
     java.util.Optional<User> lockById(@Param("id") Long id);

     User findByEmail(String email);
     boolean existsByEmail(String email);
     User findByRefreshTokenAndEmail(String refreshToken,String email);
     List<User> findByCompany(Company com);
     List<User> findByRole(Role role);
     List<User> findByRoleIsNull();

     @Modifying
     @Transactional
     @Query("UPDATE User u SET u.otp = null, u.otpExpiredAt = null " +
             "WHERE u.otpExpiredAt IS NOT NULL AND u.otpExpiredAt <= :now")
     void clearExpiredOtp(@Param("now") Instant now);
}
