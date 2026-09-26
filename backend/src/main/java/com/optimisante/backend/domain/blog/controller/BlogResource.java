package com.optimisante.backend.domain.blog.controller;

import com.optimisante.backend.domain.blog.dto.BlogPostResponseDto;
import com.optimisante.backend.domain.blog.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Le blog tel que le site le lit : publications en ligne uniquement, sans authentification. */
@RestController
@RequestMapping("/api/v1/blog")
@RequiredArgsConstructor
public class BlogResource {

    private final BlogService blogService;

    @GetMapping
    public ResponseEntity<List<BlogPostResponseDto>> publications() {
        return ResponseEntity.ok(blogService.publications());
    }

    /**
     * Le prochain événement annoncé, pour la bannière d'accueil. Répond 204 quand il n'y en a
     * aucun, plutôt qu'un 404 qui signalerait une erreur là où il n'y a qu'un agenda vide.
     */
    @GetMapping("/prochain-evenement")
    public ResponseEntity<BlogPostResponseDto> prochainEvenement() {
        return blogService.prochainEvenement()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<BlogPostResponseDto> publication(@PathVariable String slug) {
        return ResponseEntity.ok(blogService.publicationParSlug(slug));
    }
}
