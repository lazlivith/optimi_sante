package com.optimisante.backend.domain.document.controller;

import com.optimisante.backend.common.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import com.optimisante.backend.config.security.AnyAdmin;

@RestController
@RequestMapping("/api/v1/admin/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @PostMapping("/upload")
    @AnyAdmin
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder) {
        
        String publicId = storageService.uploadFile(file, folder);
        return ResponseEntity.ok(Map.of("publicId", publicId, "message", "Fichier uploadé avec succès"));
    }
}
