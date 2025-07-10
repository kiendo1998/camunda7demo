package com.example.workflow.controller;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
public class CheckInventoryDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        // Lấy tất cả biến từ quá trình ban đầu
        String carName = (String) execution.getVariable("carName");
        String role = (String) execution.getVariable("role");
        String purpose = (String) execution.getVariable("otherInfo");
        String username = (String) execution.getVariable("username");
        String startDate = (String) execution.getVariable("startDate");
        String endDate = (String) execution.getVariable("endDate");
        Long requestId = (Long) execution.getVariable("requestId");

        // Gọi service kiểm tra tồn kho (mock)
        boolean available = checkCarAvailability(carName);

        // ✅ Đặt lại toàn bộ biến nếu cần dùng tiếp sau bước này (có thể bỏ nếu chắc chắn biến không mất)
        execution.setVariable("carName", carName);
        execution.setVariable("role", role);
        execution.setVariable("otherInfo", purpose);
        execution.setVariable("username", username);
        execution.setVariable("startDate", startDate);
        execution.setVariable("endDate", endDate);
        execution.setVariable("requestId", requestId);

        // ✅ Biến để exclusive gateway phân nhánh
        execution.setVariable("inventoryOk", available);

        // Log kết quả cho dễ debug
        System.out.println("[CheckInventoryDelegate] Car: " + carName + ", Available: " + available);
    }


    private boolean checkCarAvailability(String carName) {
        // Giả lập: luôn còn xe
        return carName.toLowerCase().contains("vip");
    }
}

