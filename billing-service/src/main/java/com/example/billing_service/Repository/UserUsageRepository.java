package com.example.billing_service.Repository;

import com.example.billing_service.Entity.UserUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserUsageRepository extends JpaRepository<UserUsage, Long> {
    UserUsage findByCustomerId(Long userId);
}
