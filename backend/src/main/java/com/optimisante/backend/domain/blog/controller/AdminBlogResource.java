package com.optimisante.backend.domain.blog.controller;

import com.optimisante.backend.config.security.PlatformAdmin;
import com.optimisante.backend.domain.blog.dto.BlogPostRequestDto;
import com.optimisante.backend.domain.blog.dto.BlogPostResponseDto;
import com.optimisante.backend.domain.blog.service.BlogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * La rédaction du blog.
 *
 * <p>Réservée au super administrateur : ce qui est publié ici parle au nom d'Optimi Santé,
 * sur sa page d'accueil. Ce n'est ni du négoce ni de la mobilité, d'où {@code @PlatformAdmin}
 * plutôt qu'une des deux annotations métier.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/blog")
@RequiredArgsConstructor
@PlatformAdmin
public class AdminBlogResource {

    private final BlogService blogService;

    /** Brouillons compris — c'est l'écran où on les reprend. */
    @GetMapping
    public ResponseEntity<List<BlogPostResponseDto>> lister() {
        return ResponseEntity.ok(blogService.toutesLesPublications());
    }

    @PostMapping
    public ResponseEntity<BlogPostResponseDto> creer(@Valid @RequestBody BlogPostRequestDto demande) {
        return ResponseEntity.ok(blogService.creer(demande));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BlogPostResponseDto> modifier(@PathVariable UUID id,
                                                        @Valid @RequestBody BlogPostRequestDto demande) {
        return ResponseEntity.ok(blogService.modifier(id, demande));
    }

    @PatchMapping("/{id}/publication")
    public ResponseEntity<BlogPostResponseDto> changerMiseEnLigne(@PathVariable UUID id,
                                                                  @RequestParam boolean enLigne) {
        return ResponseEntity.ok(blogService.changerMiseEnLigne(id, enLigne));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        blogService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
