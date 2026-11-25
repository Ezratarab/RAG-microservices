package com.example.billing_service.Repository;

import com.example.billing_service.Entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    Payment findByCustomerId(String customerId);
}
