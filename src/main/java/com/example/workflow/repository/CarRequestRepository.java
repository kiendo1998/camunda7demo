package com.example.workflow.repository;

import com.example.workflow.entity.CarRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarRequestRepository extends JpaRepository<CarRequest, Long> {

    List<CarRequest> findByUsername(String username);

    List<CarRequest> findByStatus(String status);

    List<CarRequest> findByRole(String role);
}

