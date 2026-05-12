package vn.hoidanit.jobhunter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.hoidanit.jobhunter.domain.Subscriber;

public interface SubscriberRepository extends JpaRepository<Subscriber,Long>, JpaSpecificationExecutor<Subscriber> {
    boolean existsByEmail(String email);
    Subscriber findByEmail(String email);
}
