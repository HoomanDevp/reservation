package com.azki.reservation.controller;

import com.azki.reservation.dto.auth.LoginRequestDto;
import com.azki.reservation.dto.auth.LoginResponseDto;
import com.azki.reservation.repository.UserRepository;
import com.azki.reservation.security.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user authentication")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "User login", description = "Authenticates a user and provides a JWT token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful login",
            content = @Content(schema = @Schema(implementation = LoginResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDto loginRequest) {
        logger.debug("Login attempt for email: {}", loginRequest.getEmail());

        return userRepository.findByEmail(loginRequest.getEmail())
                .filter(user -> passwordEncoder.matches(loginRequest.getPassword(), user.getPassword()))
                .map(user -> {
                    // Generate token
                    String token = jwtUtil.generateToken(user.getEmail());

                    // Create response with detailed information
                    LoginResponseDto response = LoginResponseDto.builder()
                            .token(token)
                            .tokenType("Bearer")
                            .email(user.getEmail())
                            .userName(user.getUserName())
                            .expiresAt(jwtUtil.getExpirationDate(token))
                            .build();

                    logger.info("Successful login for user: {}", user.getEmail());
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    logger.warn("Failed login attempt for email: {}", loginRequest.getEmail());
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                });
    }
}
