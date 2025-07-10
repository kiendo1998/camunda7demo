package com.example.workflow.controller;

import com.example.workflow.entity.CarRequest;
import com.example.workflow.entity.User;
import com.example.workflow.repository.CarRequestRepository;
import com.example.workflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricIdentityLinkLog;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/booking")
@RequiredArgsConstructor
public class ProcessController {


    private final RuntimeService runtimeService;
    private final CarRequestRepository carRequestRepository;
    private final UserRepository userRepository;
    private final IdentityService identityService;
    private final TaskService taskService;

    @PostMapping("/request")
    public ResponseEntity<Map<String,Object>> createRequest(@RequestBody Map<String, String> payload) {
        // Lấy user từ database
        User user = userRepository.findById(payload.get("username")).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid username"));
        }
        CarRequest carRequest = new CarRequest();
        carRequest.setCarName(payload.get("carName"));
        carRequest.setRole(user.getRole());
        carRequest.setPurpose(payload.get("otherInfo"));
        carRequest.setUsername(payload.get("username"));
        carRequest.setStartDate(payload.get("startDate"));
        carRequest.setEndDate(payload.get("endDate"));

        CarRequest savedRequest = carRequestRepository.save(carRequest);


        // Tạo variables từ entity CarRequest
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", savedRequest.getId());
        variables.put("carName", savedRequest.getCarName());
        variables.put("role", savedRequest.getRole());
        variables.put("otherInfo", savedRequest.getPurpose());
        variables.put("username", savedRequest.getUsername());
        variables.put("startDate", savedRequest.getStartDate());
        variables.put("endDate", savedRequest.getEndDate());

        ProcessInstance instance = runtimeService.startProcessInstanceByKey("car_booking", variables);

        savedRequest.setProcessInstanceKey(instance.getProcessInstanceId());
        savedRequest.setProcessDefinitionKey(instance.getProcessDefinitionId());
        carRequestRepository.save(savedRequest);
        List<Map<String, Object>> history = getHistory(instance.getProcessInstanceId(), "userTask");
        Map<String, Object> result = history.get(history.size()-1);
        result.put("processInstanceId", instance.getProcessInstanceId());
        return ResponseEntity.ok(result);
    }


    @PostMapping("/complete-task")
    public ResponseEntity<String> completeTask(@RequestBody Map<String, Object> request) {
        String taskId = (String) request.get("taskId");
        Boolean approve = Boolean.valueOf((String) request.get("approve"));
        String completedBy = (String) request.get("completedBy"); // truyền group/user duyệt

        if (taskId == null || approve == null || completedBy == null) {
            return ResponseEntity.badRequest().body("taskId, approve, completedBy required");
        }
        User user = userRepository.findById(completedBy).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid username");
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Task not found");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("approve", approve);
        variables.put("completedBy", user.getRole());

        taskService.complete(taskId, variables);
        return ResponseEntity.ok("Task completed");
    }

    private final HistoryService historyService;

    @GetMapping("/history-nodes")
    public ResponseEntity<?> getHistoricActivity(
            @RequestParam(name = "processInstanceId", required = false) String processInstanceId,
            @RequestParam(name = "type", required = false) String type
    ) {
        List<Map<String, Object>> result = getHistory(processInstanceId, type);
        return ResponseEntity.ok(result);
    }

    private List<Map<String, Object>> getHistory(String processInstanceId, String filterType) {
        List<HistoricActivityInstance> history = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();

        return history.stream()
                .filter(h -> filterType == null || h.getActivityType().equals(filterType)) // lọc theo type nếu có
                .map(h -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("activityId", h.getActivityId());
                    map.put("activityName", h.getActivityName());
                    map.put("type", h.getActivityType());
                    map.put("assignee", h.getAssignee());
                    map.put("startTime", h.getStartTime());
                    map.put("endTime", h.getEndTime());

                    // ✅ Thêm status
                    String status;
                    if (h.getStartTime() == null) {
                        status = "not_started";
                    } else if (h.getEndTime() == null) {
                        status = "active";
                    } else {
                        status = "completed";
                    }
                    map.put("status", status);

                    // ✅ Nếu là userTask, thêm candidateGroups
                    if ("userTask".equals(h.getActivityType()) && h.getTaskId() != null) {
                        List<String> candidateGroups = historyService
                                .createHistoricIdentityLinkLogQuery()
                                .taskId(h.getTaskId())
                                .type("candidate")
                                .list()
                                .stream()
                                .map(HistoricIdentityLinkLog::getGroupId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .collect(Collectors.toList());
                        map.put("candidateGroups", candidateGroups);
                    }

                    return map;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/search-task")
    public ResponseEntity<?> searchTask(
            @RequestParam(name = "assignee", required = false) String assignee,
            @RequestParam(name = "processInstanceId", required = false) String processInstanceId,
            @RequestParam(name = "candidateUser", required = false) String candidateUser
    ) {
        var query = taskService.createTaskQuery();

        if (assignee != null) {
            query.taskAssignee(assignee);
        }

        if (candidateUser != null) {
            User user = userRepository.findById(candidateUser).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid username"));
            }
            query.taskCandidateGroup(user.getRole());
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
            map.put("status", "active");
            // Lấy toàn bộ process variables
            Map<String, Object> variables = runtimeService.getVariables(task.getExecutionId());
            map.put("variables", variables);

            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/search-process")
    public ResponseEntity<?> searchProcess(
            @RequestParam(name="username", required = false) String username,
            @RequestParam(name="processDefinitionKey",required = false) String processDefinitionKey,
            @RequestParam(name="status",defaultValue = "all") String status // running, completed, all
    ) {
        List<Map<String, Object>> results = new java.util.ArrayList<>();

        // Lọc theo trạng thái
        if (!status.equals("completed")) {
            var runningQuery = runtimeService.createProcessInstanceQuery();

            if (processDefinitionKey != null) {
                runningQuery.processDefinitionKey(processDefinitionKey);
            }
            if (username != null) {
                runningQuery.variableValueEquals("username", username);
            }

            List<ProcessInstance> runningInstances = runningQuery.list();

            for (ProcessInstance pi : runningInstances) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", pi.getId());
                map.put("definitionId", pi.getProcessDefinitionId());
                map.put("definitionKey", pi.getProcessDefinitionKey());
                map.put("status", "completed");
                map.put("isEnded", pi.isEnded());
                map.put("businessKey", pi.getBusinessKey());
                map.put("variables", runtimeService.getVariables(pi.getId()));
                results.add(map);
            }
        }

        if (!status.equals("running")) {
            var historicQuery = historyService.createHistoricProcessInstanceQuery().finished();

            if (processDefinitionKey != null) {
                historicQuery.processDefinitionKey(processDefinitionKey);
            }
            if (username != null) {
                historicQuery.variableValueEquals("username", username);
            }

            List<org.camunda.bpm.engine.history.HistoricProcessInstance> completedInstances = historicQuery.list();

            for (var pi : completedInstances) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", pi.getId());
                map.put("definitionId", pi.getProcessDefinitionId());
                map.put("definitionKey", pi.getProcessDefinitionKey());
                map.put("isEnded", true);
                map.put("status", "running");
                map.put("startTime", pi.getStartTime());
                map.put("endTime", pi.getEndTime());
                map.put("variables", historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(pi.getId())
                        .list()
                        .stream()
                        .collect(Collectors.toMap(
                                v -> v.getName(),
                                v -> v.getValue()
                        )));
                results.add(map);
            }
        }

        return ResponseEntity.ok(results);
    }


}
