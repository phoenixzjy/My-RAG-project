package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.service.UserService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import com.yizhaoqi.smartpai.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRequest request) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("USER_REGISTER");
        try {
            if (request.username() == null || request.username().isEmpty()
                    || request.password() == null || request.password().isEmpty()) {
                LogUtils.logUserOperation("anonymous", "REGISTER", "validation", "FAILED_EMPTY_PARAMS");
                monitor.end("register failed: empty params");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Username and password cannot be empty"));
            }

            userService.registerUser(request.username(), request.password());
            LogUtils.logUserOperation(request.username(), "REGISTER", "user_creation", "SUCCESS");
            monitor.end("register success");

            return ResponseEntity.ok(Map.of("code", 200, "message", "User registered successfully"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("USER_REGISTER", request.username(), "register failed: %s", e, e.getMessage());
            monitor.end("register failed: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("USER_REGISTER", request.username(), "register exception: %s", e, e.getMessage());
            monitor.end("register exception: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserRequest request) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("USER_LOGIN");
        try {
            if (request.username() == null || request.username().isEmpty()
                    || request.password() == null || request.password().isEmpty()) {
                LogUtils.logUserOperation("anonymous", "LOGIN", "validation", "FAILED_EMPTY_PARAMS");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Username and password cannot be empty"));
            }

            String username = userService.authenticateUser(request.username(), request.password());
            if (username == null) {
                LogUtils.logUserOperation(request.username(), "LOGIN", "authentication", "FAILED_INVALID_CREDENTIALS");
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Invalid credentials"));
            }

            String token = jwtUtils.generateToken(username);
            String refreshToken = jwtUtils.generateRefreshToken(username);
            LogUtils.logUserOperation(username, "LOGIN", "token_generation", "SUCCESS");
            monitor.end("login success");

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Login successful",
                "data", Map.of(
                    "token", token,
                    "refreshToken", refreshToken
                )
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("USER_LOGIN", request.username(), "login failed: %s", e, e.getMessage());
            monitor.end("login failed: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("USER_LOGIN", request.username(), "login exception: %s", e, e.getMessage());
            monitor.end("login exception: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_USER_INFO");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "GET_USER_INFO", "token_validation", "FAILED_INVALID_TOKEN");
                monitor.end("get user info failed: invalid token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }

            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

            Map<String, Object> displayUserData = new LinkedHashMap<>();
            displayUserData.put("id", user.getId());
            displayUserData.put("username", user.getUsername());
            displayUserData.put("role", user.getRole());

            if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
                displayUserData.put("orgTags", Arrays.asList(user.getOrgTags().split(",")));
            } else {
                displayUserData.put("orgTags", List.of());
            }

            displayUserData.put("primaryOrg", user.getPrimaryOrg());
            displayUserData.put("createdAt", user.getCreatedAt());
            displayUserData.put("updatedAt", user.getUpdatedAt());

            LogUtils.logUserOperation(username, "GET_USER_INFO", "user_profile", "SUCCESS");
            monitor.end("get user info success");

            return ResponseEntity.ok(Map.of("code", 200, "message", "Get user detail successful", "data", displayUserData));
        } catch (CustomException e) {
            LogUtils.logBusinessError("GET_USER_INFO", username, "get user info failed: %s", e, e.getMessage());
            monitor.end("get user info failed: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_USER_INFO", username, "get user info exception: %s", e, e.getMessage());
            monitor.end("get user info exception: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    @GetMapping("/org-tags")
    public ResponseEntity<?> getUserOrgTags(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_USER_ORG_TAGS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "GET_ORG_TAGS", "token_validation", "FAILED_INVALID_TOKEN");
                monitor.end("get org tags failed: invalid token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }

            Map<String, Object> orgTagsInfo = userService.getUserOrgTags(username);

            LogUtils.logUserOperation(username, "GET_ORG_TAGS", "organization_tags", "SUCCESS");
            monitor.end("get org tags success");

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Get user organization tags successful",
                "data", orgTagsInfo
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("GET_USER_ORG_TAGS", username, "get user org tags failed: %s", e, e.getMessage());
            monitor.end("get org tags failed: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_USER_ORG_TAGS", username, "get user org tags exception: %s", e, e.getMessage());
            monitor.end("get org tags exception: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    @PutMapping("/primary-org")
    public ResponseEntity<?> setPrimaryOrg(
            @RequestHeader("Authorization") String token,
            @RequestBody PrimaryOrgRequest request) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("SET_PRIMARY_ORG");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "SET_PRIMARY_ORG", "token_validation", "FAILED_INVALID_TOKEN");
                monitor.end("set primary org failed: invalid token");
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }

            if (request.primaryOrg() == null || request.primaryOrg().isEmpty()) {
                LogUtils.logUserOperation(username, "SET_PRIMARY_ORG", "validation", "FAILED_EMPTY_ORG");
                monitor.end("set primary org failed: empty org");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Primary organization tag cannot be empty"));
            }

            userService.setUserPrimaryOrg(username, request.primaryOrg());

            LogUtils.logUserOperation(username, "SET_PRIMARY_ORG", request.primaryOrg(), "SUCCESS");
            monitor.end("set primary org success");

            return ResponseEntity.ok(Map.of("code", 200, "message", "Primary organization set successfully"));
        } catch (CustomException e) {
            LogUtils.logBusinessError("SET_PRIMARY_ORG", username, "set primary org failed: %s", e, e.getMessage());
            monitor.end("set primary org failed: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("SET_PRIMARY_ORG", username, "set primary org exception: %s", e, e.getMessage());
            monitor.end("set primary org exception: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    @GetMapping("/upload-orgs")
    public ResponseEntity<?> getUploadOrgTags(@RequestAttribute("userId") String userId) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_UPLOAD_ORG_TAGS");
        try {
            LogUtils.logBusiness("GET_UPLOAD_ORG_TAGS", userId, "fetching upload org tags");

            Map<String, Object> userOrgTags = userService.getUserOrgTags(userId);
            Object rawOrgTags = userOrgTags.get("orgTags");
            List<String> orgTags;
            if (rawOrgTags instanceof List<?>) {
                orgTags = ((List<?>) rawOrgTags).stream().map(String::valueOf).toList();
            } else if (rawOrgTags == null) {
                orgTags = List.of();
            } else {
                orgTags = Arrays.asList(String.valueOf(rawOrgTags).split(","));
            }

            String primaryOrg = userService.getUserPrimaryOrg(userId);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("orgTags", orgTags);
            responseData.put("primaryOrg", primaryOrg);

            LogUtils.logUserOperation(userId, "GET_UPLOAD_ORG_TAGS", "upload_organizations", "SUCCESS");
            monitor.end("get upload org tags success");

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Get user upload organization tags successful",
                "data", responseData
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_UPLOAD_ORG_TAGS", userId, "get upload org tags failed: %s", e, e.getMessage());
            monitor.end("get upload org tags failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", 500,
                "message", "Get user upload organization tags failed: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("USER_LOGOUT");
        String username = null;
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                LogUtils.logUserOperation("anonymous", "LOGOUT", "validation", "FAILED_INVALID_TOKEN");
                monitor.end("logout failed: invalid token format");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Invalid token format"));
            }

            String jwtToken = token.replace("Bearer ", "");
            username = jwtUtils.extractUsernameFromToken(jwtToken);

            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "LOGOUT", "token_extraction", "FAILED_NO_USERNAME");
                monitor.end("logout failed: cannot extract username");
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Invalid token"));
            }

            jwtUtils.invalidateToken(jwtToken);

            LogUtils.logUserOperation(username, "LOGOUT", "token_invalidation", "SUCCESS");
            monitor.end("logout success");

            return ResponseEntity.ok(Map.of("code", 200, "message", "Logout successful"));
        } catch (Exception e) {
            LogUtils.logBusinessError("USER_LOGOUT", username, "logout exception: %s", e, e.getMessage());
            monitor.end("logout exception: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(@RequestHeader("Authorization") String token) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("USER_LOGOUT_ALL");
        String username = null;
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                LogUtils.logUserOperation("anonymous", "LOGOUT_ALL", "validation", "FAILED_INVALID_TOKEN");
                monitor.end("logout all failed: invalid token format");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Invalid token format"));
            }

            String jwtToken = token.replace("Bearer ", "");
            username = jwtUtils.extractUsernameFromToken(jwtToken);
            String userId = jwtUtils.extractUserIdFromToken(jwtToken);

            if (username == null || username.isEmpty() || userId == null) {
                LogUtils.logUserOperation("anonymous", "LOGOUT_ALL", "token_extraction", "FAILED_NO_USER_INFO");
                monitor.end("logout all failed: cannot extract user info");
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Invalid token"));
            }

            jwtUtils.invalidateAllUserTokens(userId);

            LogUtils.logUserOperation(username, "LOGOUT_ALL", "all_tokens_invalidation", "SUCCESS");
            monitor.end("logout all success");

            return ResponseEntity.ok(Map.of("code", 200, "message", "Logout from all devices successful"));
        } catch (Exception e) {
            LogUtils.logBusinessError("USER_LOGOUT_ALL", username, "logout all exception: %s", e, e.getMessage());
            monitor.end("logout all exception: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    private record UserRequest(String username, String password) {}

    private record PrimaryOrgRequest(String primaryOrg) {}
}
