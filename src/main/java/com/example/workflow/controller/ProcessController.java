package com.example.workflow.controller;

import com.example.workflow.entity.CarRequest;
import com.example.workflow.repository.CarRequestRepository;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/booking")
@RequiredArgsConstructor
public class ProcessController {


    private final RuntimeService runtimeService;
    private final CarRequestRepository carRequestRepository;

    private final TaskService taskService;

    @PostMapping("/request")
    public ResponseEntity<String> createRequest(@RequestBody Map<String, String> payload) {
        // Lưu thông tin yêu cầu (nếu cần)
        CarRequest carRequest = new CarRequest();
        carRequest.setCarName(payload.get("carName"));
        carRequest.setRole(payload.get("role"));
        carRequest.setPurpose(payload.get("otherInfo"));
        carRequest.setUsername(payload.get("username")); // bạn cần extract từ token nếu có
        carRequest.setStartDate(payload.get("startDate"));
        carRequest.setEndDate(payload.get("endDate"));

        CarRequest savedRequest = carRequestRepository.save(carRequest);

        // Add vào variables để truyền vào process
        Map<String, Object> variables = new HashMap<>(payload);
        variables.put("requestId", savedRequest.getId());

        // Khởi tạo process
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("car_booking", variables);

        savedRequest.setProcessInstanceKey(instance.getProcessInstanceId()); // String, không phải long
        savedRequest.setProcessDefinitionKey(instance.getProcessInstanceId()); // String, không phải long
        carRequestRepository.save(savedRequest);

        return ResponseEntity.ok("Started: " + instance.getProcessInstanceId());
    }


    @PostMapping("/complete-task")
    public ResponseEntity<String> completeTask(@RequestBody Map<String, Object> request) {
        String taskId = (String) request.get("taskId");
        Boolean approve = Boolean.valueOf((String) request.get("approve"));
        String completedBy = (String) request.get("completedBy"); // truyền group/user duyệt

        if (taskId == null || approve == null || completedBy == null) {
            return ResponseEntity.badRequest().body("taskId, approve, completedBy required");
        }

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Task not found");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("approve", approve);
        variables.put("completedBy", completedBy);

        taskService.complete(taskId, variables);
        return ResponseEntity.ok("Task completed");
    }

    private final HistoryService historyService;

    @GetMapping("/history-nodes")
    public ResponseEntity<?> getHistoricActivity(@RequestParam(name = "processInstanceId", required = false) String processInstanceId) {
        List<HistoricActivityInstance> history = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();

        List<Map<String, Object>> result = history.stream().map(h -> {
            Map<String, Object> map = new HashMap<>();
            map.put("activityId", h.getActivityId());
            map.put("activityName", h.getActivityName());
            map.put("type", h.getActivityType());
            map.put("assignee", h.getAssignee());
            map.put("startTime", h.getStartTime());
            map.put("endTime", h.getEndTime());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
    @GetMapping("/search-task")
    public ResponseEntity<?> searchTask(
            @RequestParam(name = "assignee", required = false) String assignee,
            @RequestParam(name = "processInstanceId", required = false) String processInstanceId,
            @RequestParam(name = "candidateGroup", required = false) String candidateGroup
    ) {
        var query = taskService.createTaskQuery();

        if (assignee != null) {
            query.taskAssignee(assignee);
        }

        if (candidateGroup != null) {
            query.taskCandidateGroup(candidateGroup);
        }

        if (processInstanceId != null) {
            query.processInstanceId(processInstanceId);
        }

        List<Task> tasks = query.orderByTaskCreateTime().desc().list();

        List<Map<String, Object>> result = tasks.stream().map(task -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", task.getId());
            map.put("name", task.getName());
            map.put("assignee", task.getAssignee());
            map.put("created", task.getCreateTime());
            map.put("processInstanceId", task.getProcessInstanceId());
            map.put("executionId", task.getExecutionId());
            map.put("taskDefinitionKey", task.getTaskDefinitionKey());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }



}
