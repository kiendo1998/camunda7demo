package com.example.workflow.entity;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "car_requests_7")
@Data
public class CarRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username; // director, guard, staff, etc.

    private String role; // GIÁM ĐỐC, TRƯỞNG PHÒNG, etc.

    private String carName; // Ví dụ: Toyota

    private String startDate; // Ngày đi

    private String endDate; // Ngày về

    @Column(length = 500)
    private String purpose; // Mục đích sử dụng xe

    private String status; // pending, approved, rejected (nếu cần workflow)
    private String processInstanceKey;
    private String bpmnProcessId;
    private String processDefinitionKey;
    private int version;
}
