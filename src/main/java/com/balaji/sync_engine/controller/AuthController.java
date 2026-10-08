package com.balaji.sync_engine.controller;

import com.balaji.sync_engine.dto.ApiResult;
import com.balaji.sync_engine.security.*;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Login, token issuance, and account registration")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository,
                          JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "Authenticate and receive a JWT")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginResponse>> login(@RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        User user = userRepository.findByUsername(request.username()).orElseThrow();
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(ApiResult.ok(new LoginResponse(token, user.getRole().name(), user.getDeviceId())));
    }

    @Operation(summary = "Register a new user account", description = "ADMIN only.")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Username taken, or deviceId missing for a FIELD_WORKER")
    @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResult<RegisterResponse>> register(@RequestBody RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new IllegalArgumentException("Username already taken: " + request.username());
        }
        if (request.role() == Role.FIELD_WORKER
                && (request.deviceId() == null || request.deviceId().isBlank())) {
            throw new IllegalArgumentException("FIELD_WORKER accounts must specify a deviceId");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setDeviceId(request.role() == Role.FIELD_WORKER ? request.deviceId() : null);
        user.setEnabled(true);

        User saved = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.ok(new RegisterResponse(saved.getId(), saved.getUsername(), saved.getRole().name())));
    }
}