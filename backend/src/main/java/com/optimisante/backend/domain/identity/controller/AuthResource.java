package com.optimisante.backend.domain.identity.controller;

import com.optimisante.backend.domain.identity.dto.*;
import com.optimisante.backend.domain.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthResource {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register/b2c")
    public ResponseEntity<Void> registerB2C(@Valid @RequestBody RegisterB2CRequestDTO request) {
        authService.registerB2C(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register/b2b")
    public ResponseEntity<Void> registerB2B(@Valid @RequestBody RegisterB2BRequestDTO request) {
        authService.registerB2B(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register/doctor")
    public ResponseEntity<Void> registerDoctor(@Valid @RequestBody RegisterDoctorRequestDTO request) {
        authService.registerDoctor(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDTO> getProfile() {
        return ResponseEntity.ok(authService.getProfile());
    }
}
