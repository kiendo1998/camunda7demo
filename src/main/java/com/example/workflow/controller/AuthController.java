package com.example.workflow.controller;

import com.example.workflow.LoginRequest;
import com.example.workflow.entity.User;
import com.example.workflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        User user = userRepository.findByUsernameAndPassword(
                loginRequest.getUsername(),
                loginRequest.getPassword()
        );
        if (user != null) {
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
    }
}