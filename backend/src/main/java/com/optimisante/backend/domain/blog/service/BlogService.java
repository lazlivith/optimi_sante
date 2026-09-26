package com.optimisante.backend.domain.blog.service;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.blog.dto.BlogPostRequestDto;
import com.optimisante.backend.domain.blog.dto.BlogPostResponseDto;
import com.optimisante.backend.domain.blog.entity.BlogPost;
import com.optimisante.backend.domain.blog.repository.BlogPostRepository;
import com.optimisante.backend.domain.identity.entity.Tenant;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Le blog : rédaction côté administration, lecture côté site.
 *
 * <p>Les deux publics ne voient pas la même chose. L'administration travaille sur tout, y
 * compris les brouillons ; le site ne reçoit que ce qui a été mis en ligne. Cette frontière
 * est tenue ici, et pas seulement par les annotations de sécurité des contrôleurs : une
 * lecture publique qui passerait par un autre chemin ne doit pas pouvoir exposer un
 * brouillon.</p>
 */
@Service
@RequiredArgsConstructor
public class BlogService {

    private final BlogPostRepository blogPostRepository;
    private final TenantRepository tenantRepository;

    // ─────────────────────────────── Lecture publique ───────────────────────────────

    @Transactional(readOnly = true)
    public List<BlogPostResponseDto> publications() {
        return blogPostRepository
                .findByTenantIdAndIsPublishedTrueOrderByPublishedAtDesc(tenantRequis())
                .stream().map(BlogService::versDto).toList();
    }

    @Transactional(readOnly = true)
    public BlogPostResponseDto publicationParSlug(String slug) {
        BlogPost publication = blogPostRepository.findByTenantIdAndSlug(tenantRequis(), slug)
                .filter(BlogPost::getIsPublished)
                // Un brouillon répond comme une publication inexistante : distinguer les deux
                // révélerait au visiteur qu'une annonce se prépare à cette adresse.
                .orElseThrow(() -> new IllegalArgumentException("Publication introuvable."));
        return versDto(publication);
    }

    /**
     * Le prochain événement, pour la bannière d'accueil. Vide tant que rien n'est annoncé :
     * la page d'accueil retombe alors sur sa présentation d'origine.
     */
    @Transactional(readOnly = true)
    public Optional<BlogPostResponseDto> prochainEvenement() {
        return blogPostRepository
                .evenementsAVenir(tenantRequis(), LocalDate.now(), Limit.of(1))
                .stream().findFirst().map(BlogService::versDto);
    }

    // ──────────────────────────────── Administration ────────────────────────────────

    @Transactional(readOnly = true)
    public List<BlogPostResponseDto> toutesLesPublications() {
        return blogPostRepository.findByTenantIdOrderByCreatedAtDesc(tenantRequis())
                .stream().map(BlogService::versDto).toList();
    }

    @Transactional
    public BlogPostResponseDto creer(BlogPostRequestDto demande) {
        UUID tenantId = tenantRequis();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant introuvable."));
        verifierDates(demande);

        boolean enLigne = Boolean.TRUE.equals(demande.getPublished());
        BlogPost publication = BlogPost.builder()
                .tenant(tenant)
                .title(demande.getTitle().trim())
                .slug(slugDisponible(tenantId, demande.getTitle()))
                .excerpt(vide(demande.getExcerpt()))
                .content(demande.getContent())
                .coverImageUrl(vide(demande.getCoverImageUrl()))
                .eventLocation(vide(demande.getEventLocation()))
                .eventStartsOn(demande.getEventStartsOn())
                .eventEndsOn(demande.getEventEndsOn())
                .isPublished(enLigne)
                .publishedAt(enLigne ? OffsetDateTime.now() : null)
                .build();
        return versDto(blogPostRepository.save(publication));
    }

    @Transactional
    public BlogPostResponseDto modifier(UUID id, BlogPostRequestDto demande) {
        BlogPost publication = publicationDuTenant(id);
        verifierDates(demande);

        publication.setTitle(demande.getTitle().trim());
        publication.setExcerpt(vide(demande.getExcerpt()));
        publication.setContent(demande.getContent());
        publication.setCoverImageUrl(vide(demande.getCoverImageUrl()));
        publication.setEventLocation(vide(demande.getEventLocation()));
        publication.setEventStartsOn(demande.getEventStartsOn());
        publication.setEventEndsOn(demande.getEventEndsOn());
        // Le slug n'est délibérément pas recalculé : il est déjà partagé, indexé, peut-être
        // envoyé par email. Corriger une coquille dans un titre ne doit pas casser ces liens.

        if (demande.getPublished() != null) {
            appliquerMiseEnLigne(publication, demande.getPublished());
        }
        return versDto(publication);
    }

    @Transactional
    public BlogPostResponseDto changerMiseEnLigne(UUID id, boolean enLigne) {
        BlogPost publication = publicationDuTenant(id);
        appliquerMiseEnLigne(publication, enLigne);
        return versDto(publication);
    }

    @Transactional
    public void supprimer(UUID id) {
        blogPostRepository.delete(publicationDuTenant(id));
    }

    // ──────────────────────────────────── Interne ────────────────────────────────────

    private void appliquerMiseEnLigne(BlogPost publication, boolean enLigne) {
        publication.setIsPublished(enLigne);
        // La date de première mise en ligne fait foi : la retirer d'un site puis l'y remettre
        // ne doit pas la faire remonter en tête de la liste comme une nouveauté.
        if (enLigne && publication.getPublishedAt() == null) {
            publication.setPublishedAt(OffsetDateTime.now());
        }
        if (!enLigne) {
            publication.setPublishedAt(null);
        }
    }

    private void verifierDates(BlogPostRequestDto demande) {
        if (demande.getEventEndsOn() != null && demande.getEventStartsOn() == null) {
            throw new IllegalArgumentException(
                    "Une date de fin suppose une date de début : renseignez le début de l'événement.");
        }
        if (demande.getEventEndsOn() != null
                && demande.getEventEndsOn().isBefore(demande.getEventStartsOn())) {
            throw new IllegalArgumentException(
                    "L'événement ne peut pas se terminer avant d'avoir commencé.");
        }
    }

    private BlogPost publicationDuTenant(UUID id) {
        BlogPost publication = blogPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Publication introuvable."));
        if (!publication.getTenant().getId().equals(tenantRequis())) {
            throw new IllegalArgumentException("Publication introuvable.");
        }
        return publication;
    }

    private String slugDisponible(UUID tenantId, String titre) {
        String base = slugifier(titre);
        String slug = base;
        int suffixe = 2;
        while (blogPostRepository.existsByTenantIdAndSlug(tenantId, slug)) {
            slug = base + "-" + suffixe;
            suffixe++;
        }
        return slug;
    }

    private String slugifier(String texte) {
        String sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = sansAccents.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > 200) {
            slug = slug.substring(0, 200).replaceAll("-+$", "");
        }
        return slug.isBlank() ? "publication" : slug;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private UUID tenantRequis() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        return tenantId;
    }

    private static BlogPostResponseDto versDto(BlogPost p) {
        return new BlogPostResponseDto(
                p.getId(), p.getTitle(), p.getSlug(), p.getExcerpt(), p.getContent(),
                p.getCoverImageUrl(), p.getEventLocation(), p.getEventStartsOn(),
                p.getEventEndsOn(), p.estUnEvenement(), Boolean.TRUE.equals(p.getIsPublished()),
                p.getPublishedAt(), p.getCreatedAt());
    }
}
