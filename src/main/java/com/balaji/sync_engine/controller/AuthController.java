package com.balaji.sync_engine.controller;

import com.balaji.sync_engine.dto.ApiResult;
import com.balaji.sync_engine.security.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Login, token issuance, refresh, and account registration")
@RestController
@RequestMapping({"/api/v1/auth", "/api/auth"})
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "Authenticate and receive access token + refresh token")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        User user = userRepository.findByUsername(request.username()).orElseThrow();
        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return ResponseEntity.ok(ApiResult.ok(new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                user.getRole().name(),
                user.getDeviceId()
        )));
    }

    @Operation(summary = "Rotate refresh token and get a new access token")
    @ApiResponse(responseCode = "400", description = "Invalid or revoked refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResult<RefreshTokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(request.refreshToken());
        String newAccessToken = jwtService.generateToken(newRefreshToken.getUser());

        return ResponseEntity.ok(ApiResult.ok(new RefreshTokenResponse(
                newAccessToken,
                newRefreshToken.getToken()
        )));
    }

    @Operation(summary = "Revoke a refresh token on logout")
    @PostMapping("/logout")
    public ResponseEntity<ApiResult<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revokeToken(request.refreshToken());
        return ResponseEntity.ok(ApiResult.empty());
    }

    @Operation(summary = "Register a new user account (ADMIN only)")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Username taken, or deviceId missing for FIELD_WORKER")
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResult<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
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