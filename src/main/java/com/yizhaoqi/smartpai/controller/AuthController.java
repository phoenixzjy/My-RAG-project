package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import com.yizhaoqi.smartpai.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/refreshToken")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("REFRESH_TOKEN");
        String username = null;
        try {
            if (request.refreshToken() == null || request.refreshToken().isEmpty()) {
                LogUtils.logUserOperation("anonymous", "REFRESH_TOKEN", "validation", "FAILED_EMPTY_REFRESH_TOKEN");
                monitor.end("refresh token failed: empty refresh token");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Refresh token cannot be empty"));
            }

            if (!jwtUtils.validateRefreshToken(request.refreshToken())) {
                LogUtils.logUserOperation("anonymous", "REFRESH_TOKEN", "validation", "FAILED_INVALID_REFRESH_TOKEN");
                monitor.end("refresh token failed: invalid refresh token");
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Invalid refresh token"));
            }

            username = jwtUtils.extractUsernameFromToken(request.refreshToken());
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "REFRESH_TOKEN", "extraction", "FAILED_NO_USERNAME");
                monitor.end("refresh token failed: cannot extract username");
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Cannot extract username from refresh token"));
            }

            String newToken = jwtUtils.generateToken(username);
            String newRefreshToken = jwtUtils.generateRefreshToken(username);

            LogUtils.logUserOperation(username, "REFRESH_TOKEN", "token_generation", "SUCCESS");
            monitor.end("refresh token success");

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Token refreshed successfully",
                "data", Map.of(
                    "token", newToken,
                    "refreshToken", newRefreshToken
                )
            ));
        } catch (CustomException e) {
            LogUtils.logBusinessError("REFRESH_TOKEN", username, "refresh token failed: %s", e, e.getMessage());
            monitor.end("refresh token failed: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            LogUtils.logBusinessError("REFRESH_TOKEN", username, "refresh token exception: %s", e, e.getMessage());
            monitor.end("refresh token exception: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    @GetMapping("/error")
    public ResponseEntity<?> customBackendError(@RequestParam String code, @RequestParam String msg) {
        return ResponseEntity.status(Integer.parseInt(code)).body(Map.of("code", Integer.parseInt(code), "message", msg));
    }
    private record RefreshTokenRequest(String refreshToken) {}
}
