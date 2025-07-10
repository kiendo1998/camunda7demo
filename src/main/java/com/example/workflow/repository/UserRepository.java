package com.example.workflow.repository;

import com.example.workflow.entity.CarRequest;
import com.example.workflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    User findByUsernameAndPassword(String username, String password);

}

