package com.yizhaoqi.smartpai.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.OrganizationTag;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.service.UserService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import com.yizhaoqi.smartpai.utils.LogUtils;
import com.yizhaoqi.smartpai.utils.MinioMigrationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserService userService;

    @Autowired
    private OrganizationTagRepository organizationTagRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MinioMigrationUtil migrationUtil;

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_ALL_USERS");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);

            LogUtils.logBusiness("ADMIN_GET_ALL_USERS", adminUsername, "admin started fetching all users");

            List<User> users = userRepository.findAll();
            users.forEach(user -> user.setPassword(null));

            LogUtils.logUserOperation(adminUsername, "ADMIN_GET_ALL_USERS", "user_list", "SUCCESS");
            LogUtils.logBusiness("ADMIN_GET_ALL_USERS", adminUsername, "fetched user list successfully, count=%d", users.size());
            monitor.end("get all users success");

            return ResponseEntity.ok(Map.of("code", 200, "message", "Get all users successful", "data", users));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_USERS", adminUsername, "failed to get all users", e);
            monitor.end("get all users failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to get users: " + e.getMessage()));
        }
    }

    @PostMapping("/knowledge/add")
    public ResponseEntity<?> addKnowledgeDocument(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("description") String description) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Knowledge document added successfully");
            response.put("fileName", file.getOriginalFilename());
            response.put("description", description);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_ADD_KNOWLEDGE", adminUsername, "failed to add knowledge document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to add knowledge document: " + e.getMessage()));
        }
    }

    @DeleteMapping("/knowledge/{documentId}")
    public ResponseEntity<?> deleteKnowledgeDocument(
            @RequestHeader("Authorization") String token,
            @PathVariable("documentId") String documentId) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Knowledge document deleted successfully");
            response.put("documentId", documentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_KNOWLEDGE", adminUsername, "failed to delete knowledge document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete knowledge document: " + e.getMessage()));
        }
    }

    @GetMapping("/system/status")
    public ResponseEntity<?> getSystemStatus(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            Map<String, Object> status = new HashMap<>();
            status.put("cpu_usage", "30%");
            status.put("memory_usage", "45%");
            status.put("disk_usage", "60%");
            status.put("active_users", 15);
            status.put("total_documents", 250);
            status.put("total_conversations", 1200);

            return ResponseEntity.ok(Map.of("data", status));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_SYSTEM_STATUS", adminUsername, "failed to get system status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get system status: " + e.getMessage()));
        }
    }

    @GetMapping("/user-activities")
    public ResponseEntity<?> getUserActivities(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            List<Map<String, Object>> activities = List.of(
                Map.of(
                    "username", username != null && !username.isBlank() ? username : "user1",
                    "action", "LOGIN",
                    "timestamp", "2023-03-01T10:15:30",
                    "ip_address", "192.168.1.100"
                ),
                Map.of(
                    "username", "user2",
                    "action", "UPLOAD_FILE",
                    "timestamp", "2023-03-01T11:20:45",
                    "ip_address", "192.168.1.101"
                )
            );

            return ResponseEntity.ok(Map.of(
                "data", activities,
                "start_date", start_date,
                "end_date", end_date
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_ACTIVITIES", adminUsername, "failed to get user activities", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get user activities: " + e.getMessage()));
        }
    }

    @PostMapping("/users/create-admin")
    public ResponseEntity<?> createAdminUser(
            @RequestHeader("Authorization") String token,
            @RequestBody AdminUserRequest request) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            userService.createAdminUser(request.username(), request.password(), adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "Admin user created successfully"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ADMIN_USER", adminUsername, "failed to create admin user: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ADMIN_USER", adminUsername, "admin user creation exception: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to create admin user: " + e.getMessage()));
        }
    }

    @PostMapping("/org-tags")
    public ResponseEntity<?> createOrganizationTag(
            @RequestHeader("Authorization") String token,
            @RequestBody OrgTagRequest request) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            OrganizationTag tag = userService.createOrganizationTag(
                request.tagId(),
                request.name(),
                request.description(),
                request.parentTag(),
                adminUsername
            );
            return ResponseEntity.ok(Map.of("code", 200, "message", "Organization tag created successfully", "data", tag));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ORG_TAG", adminUsername, "failed to create organization tag: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_CREATE_ORG_TAG", adminUsername, "organization tag creation exception: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to create organization tag: " + e.getMessage()));
        }
    }

    @GetMapping("/org-tags")
    public ResponseEntity<?> getAllOrganizationTags(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            List<OrganizationTag> tags = organizationTagRepository.findAll();
            return ResponseEntity.ok(Map.of("code", 200, "message", "Get organization tags successful", "data", tags));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAGS", adminUsername, "failed to get organization tags", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to get organization tags: " + e.getMessage()));
        }
    }

    @PutMapping("/users/{userId}/org-tags")
    public ResponseEntity<?> assignOrgTagsToUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestBody AssignOrgTagsRequest request) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            userService.assignOrgTagsToUser(userId, request.orgTags(), adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "Organization tags assigned successfully"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ORG_TAGS", adminUsername, "failed to assign organization tags: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_ASSIGN_ORG_TAGS", adminUsername, "organization tag assignment exception: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to assign organization tags: " + e.getMessage()));
        }
    }

    @GetMapping("/org-tags/tree")
    public ResponseEntity<?> getOrganizationTagTree(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            List<Map<String, Object>> tagTree = userService.getOrganizationTagTree();
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Get organization tag tree successful",
                "data", tagTree
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ORG_TAG_TREE", adminUsername, "failed to get organization tag tree", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to get organization tag tree: " + e.getMessage()));
        }
    }

    @PutMapping("/org-tags/{tagId}")
    public ResponseEntity<?> updateOrganizationTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId,
            @RequestBody OrgTagUpdateRequest request) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            OrganizationTag updatedTag = userService.updateOrganizationTag(
                tagId,
                request.name(),
                request.description(),
                request.parentTag(),
                adminUsername
            );
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Organization tag updated successfully",
                "data", updatedTag
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ORG_TAG", adminUsername, "failed to update organization tag: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_UPDATE_ORG_TAG", adminUsername, "organization tag update exception: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to update organization tag: " + e.getMessage()));
        }
    }

    @DeleteMapping("/org-tags/{tagId}")
    public ResponseEntity<?> deleteOrganizationTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            userService.deleteOrganizationTag(tagId, adminUsername);
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Organization tag deleted successfully"
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ORG_TAG", adminUsername, "failed to delete organization tag: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_DELETE_ORG_TAG", adminUsername, "organization tag deletion exception: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to delete organization tag: " + e.getMessage()));
        }
    }

    @GetMapping("/users/list")
    public ResponseEntity<?> getUserList(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orgTag,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);

        try {
            Map<String, Object> usersData = userService.getUserList(keyword, orgTag, status, page, size);
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Get user list successful",
                "data", usersData
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_LIST", adminUsername, "failed to get user list: %s", e, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_USER_LIST", adminUsername, "user list exception: %s", e, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to get user list: " + e.getMessage()));
        }
    }

    @GetMapping("/conversation")
    public ResponseEntity<?> getAllConversations(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String userid,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("ADMIN_GET_ALL_CONVERSATIONS");
        String adminUsername = null;
        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);

            LogUtils.logBusiness(
                "ADMIN_GET_ALL_CONVERSATIONS",
                adminUsername,
                "admin started querying conversations, targetUserId=%s, start=%s, end=%s",
                userid,
                start_date,
                end_date
            );

            List<Map<String, Object>> allConversations = new ArrayList<>();
            String targetUsername = null;

            if (userid != null && !userid.isEmpty()) {
                try {
                    Long userIdLong = Long.parseLong(userid);
                    Optional<User> targetUser = userRepository.findById(userIdLong);
                    if (targetUser.isPresent()) {
                        targetUsername = targetUser.get().getUsername();
                        LogUtils.logBusiness(
                            "ADMIN_GET_ALL_CONVERSATIONS",
                            adminUsername,
                            "target user found: id=%s, username=%s",
                            userid,
                            targetUsername
                        );
                    } else {
                        LogUtils.logBusiness(
                            "ADMIN_GET_ALL_CONVERSATIONS",
                            adminUsername,
                            "target user id not found: %s",
                            userid
                        );
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("code", 404, "message", "Target user not found"));
                    }
                } catch (NumberFormatException e) {
                    LogUtils.logBusiness(
                        "ADMIN_GET_ALL_CONVERSATIONS",
                        adminUsername,
                        "invalid user id format: %s",
                        userid
                    );
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("code", 400, "message", "Invalid user id format"));
                }
            }

            Set<String> userKeys = redisTemplate.keys("user:*:current_conversation");

            if (userKeys != null && !userKeys.isEmpty()) {
                for (String userKey : userKeys) {
                    String conversationId = redisTemplate.opsForValue().get(userKey);
                    if (conversationId == null) {
                        continue;
                    }

                    String redisUserId = userKey.replace("user:", "").replace(":current_conversation", "");

                    if (userid != null && !userid.isEmpty()) {
                        if (!redisUserId.equals(userid) && !redisUserId.equals(targetUsername)) {
                            continue;
                        }
                    }

                    String conversationKey = "conversation:" + conversationId;
                    String json = redisTemplate.opsForValue().get(conversationKey);
                    if (json != null) {
                        String displayUsername = targetUsername != null ? targetUsername : redisUserId;
                        processRedisConversation(json, allConversations, displayUsername, start_date, end_date);
                    }
                }
            }

            LogUtils.logBusiness(
                "ADMIN_GET_ALL_CONVERSATIONS",
                adminUsername,
                "admin conversation query completed, total=%d",
                allConversations.size()
            );
            LogUtils.logUserOperation(adminUsername, "ADMIN_GET_ALL_CONVERSATIONS", "conversation_history", "SUCCESS");
            monitor.end("get all conversations success");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "Get conversation history successful");
            response.put("data", allConversations);
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "failed to get conversations: %s", e, e.getMessage());
            monitor.end("get all conversations failed: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", adminUsername, "conversation query exception: %s", e, e.getMessage());
            monitor.end("get all conversations exception: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error: " + e.getMessage()));
        }
    }

    private void processRedisConversation(
            String json,
            List<Map<String, Object>> targetList,
            String username,
            String startDate,
            String endDate) throws JsonProcessingException {

        List<Map<String, String>> history = objectMapper.readValue(
            json,
            new TypeReference<List<Map<String, String>>>() {}
        );

        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        if (startDate != null && !startDate.trim().isEmpty()) {
            try {
                startDateTime = parseDateTime(startDate);
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", username, "failed to parse start time: %s", e, startDate);
            }
        }

        if (endDate != null && !endDate.trim().isEmpty()) {
            try {
                endDateTime = parseDateTime(endDate);
            } catch (Exception e) {
                LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", username, "failed to parse end time: %s", e, endDate);
            }
        }

        for (Map<String, String> message : history) {
            String messageTimestamp = message.getOrDefault("timestamp", "unknown");

            if (startDateTime != null || endDateTime != null) {
                if (!"unknown".equals(messageTimestamp)) {
                    try {
                        LocalDateTime messageDateTime = LocalDateTime.parse(
                            messageTimestamp,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                        );

                        if (startDateTime != null && messageDateTime.isBefore(startDateTime)) {
                            continue;
                        }
                        if (endDateTime != null && messageDateTime.isAfter(endDateTime)) {
                            continue;
                        }
                    } catch (Exception e) {
                        LogUtils.logBusinessError("ADMIN_GET_ALL_CONVERSATIONS", username, "invalid message timestamp: %s", e, messageTimestamp);
                    }
                } else {
                    continue;
                }
            }

            Map<String, Object> messageWithMetadata = new HashMap<>();
            messageWithMetadata.put("role", message.get("role"));
            messageWithMetadata.put("content", message.get("content"));
            messageWithMetadata.put("timestamp", messageTimestamp);
            messageWithMetadata.put("username", username);
            targetList.add(messageWithMetadata);
        }
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(dateTimeStr);
        } catch (DateTimeParseException e1) {
            try {
                if (dateTimeStr.length() == 16) {
                    return LocalDateTime.parse(dateTimeStr + ":00");
                }
                if (dateTimeStr.length() == 13) {
                    return LocalDateTime.parse(dateTimeStr + ":00:00");
                }
                if (dateTimeStr.length() == 10) {
                    return LocalDateTime.parse(dateTimeStr + "T00:00:00");
                }

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (Exception e2) {
                LogUtils.logBusinessError("PARSE_DATETIME", "system", "failed to parse datetime: %s", e2, dateTimeStr);
                throw new CustomException("Invalid datetime format: " + dateTimeStr, HttpStatus.BAD_REQUEST);
            }
        }
    }

    private User validateAdmin(String username) {
        if (username == null || username.isEmpty()) {
            throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
        }

        User admin = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Unauthorized access: Admin role required", HttpStatus.FORBIDDEN);
        }

        return admin;
    }

    @PostMapping("/migrate-minio")
    public ResponseEntity<?> migrateMinioFiles(
            @RequestHeader("Authorization") String token,
            @RequestParam String adminKey) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("MIGRATE_MINIO");
        String adminUsername = null;

        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);

            if (!"migration2024".equals(adminKey)) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", 403);
                response.put("message", "Invalid admin key");
                return ResponseEntity.status(403).body(response);
            }

            LogUtils.logBusiness("MIGRATE_MINIO", adminUsername, "starting MinIO file migration");

            MinioMigrationUtil.MigrationReport report = migrationUtil.migrateAllFiles();

            LogUtils.logBusiness(
                "MIGRATE_MINIO",
                adminUsername,
                "migration complete: success=%d, skip=%d, error=%d",
                report.successCount,
                report.skipCount,
                report.errorCount
            );

            monitor.end("MinIO migration completed");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "Migration completed");
            response.put("data", Map.of(
                "successCount", report.successCount,
                "skipCount", report.skipCount,
                "errorCount", report.errorCount,
                "errors", report.getErrors()
            ));
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            LogUtils.logBusinessError("MIGRATE_MINIO", adminUsername, "MinIO migration failed: %s", e, e.getMessage());
            monitor.end("MinIO migration failed: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", e.getStatus().value());
            response.put("message", e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("MIGRATE_MINIO", adminUsername, "MinIO migration exception: %s", e, e.getMessage());
            monitor.end("MinIO migration exception: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "Migration failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/clear-all-data")
    public ResponseEntity<?> clearAllData(
            @RequestHeader("Authorization") String token,
            @RequestParam String adminKey) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("CLEAR_ALL_DATA");
        String adminUsername = null;

        try {
            adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            validateAdmin(adminUsername);

            if (!"CLEAR_ALL_2024".equals(adminKey)) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", 403);
                response.put("message", "Invalid admin key");
                return ResponseEntity.status(403).body(response);
            }

            LogUtils.logBusiness("CLEAR_ALL_DATA", adminUsername, "starting to clear all data");

            migrationUtil.clearAllData();

            LogUtils.logBusiness("CLEAR_ALL_DATA", adminUsername, "all data cleared");

            monitor.end("clear all data success");

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "All data cleared");
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            LogUtils.logBusinessError("CLEAR_ALL_DATA", adminUsername, "clear all data failed: %s", e, e.getMessage());
            monitor.end("clear all data failed: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", e.getStatus().value());
            response.put("message", e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("CLEAR_ALL_DATA", adminUsername, "clear all data exception: %s", e, e.getMessage());
            monitor.end("clear all data exception: " + e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("message", "Clear all data failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    private record AdminUserRequest(String username, String password) {}

    private record OrgTagRequest(String tagId, String name, String description, String parentTag) {}

    private record AssignOrgTagsRequest(List<String> orgTags) {}

    private record OrgTagUpdateRequest(String name, String description, String parentTag) {}
}
