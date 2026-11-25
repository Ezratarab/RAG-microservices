package com.example.billing_service.Repository;

import com.example.billing_service.Entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    /** A customer can have many payments. */
    List<Payment> findByCustomerId(String customerId);
}
