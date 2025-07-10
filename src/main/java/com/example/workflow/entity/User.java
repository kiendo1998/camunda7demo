package com.example.workflow.entity;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "user_7")
@Data
public class User {

    @Id
    private String username; // director, guard, staff, etc.
    private String password; // director, guard, staff, etc.
    private String role; // director, guard, staff, etc.


}
