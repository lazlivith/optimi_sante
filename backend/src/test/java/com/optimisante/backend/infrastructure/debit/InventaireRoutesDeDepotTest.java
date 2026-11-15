package com.optimisante.backend.infrastructure.debit;

import com.optimisante.backend.ConfigurationBaseDeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toutes les routes qui reçoivent un fichier, et la taille de requête que chacune admet.
 *
 * <p>L'inventaire est lu <b>dans l'application démarrée</b>, pas dans une liste tenue à la main :
 * une route de dépôt ajoutée demain apparaît ici d'elle-même. Elle sera protégée par le défaut
 * de {@link FiltreTailleRequete} — mais ce test échouera tant que quelqu'un n'aura pas écrit,
 * dans le tableau ci-dessous, la taille qu'il a décidé de lui accorder.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class InventaireRoutesDeDepotTest {

    private static final long MO = 1024L * 1024;

    /** Route de dépôt → taille de requête admise, en Mo. Arbitré le 15 novembre 2026. */
    private static final Map<String, Long> ATTENDU = new TreeMap<>(Map.ofEntries(
            Map.entry("POST /api/v1/partnership/requests", 11L),
            Map.entry("POST /api/v1/partnership/dossier/verification", 11L),
            Map.entry("POST /api/v1/storage/upload", 11L),
            Map.entry("POST /api/v1/admin/storage/upload", 11L),
            Map.entry("POST /api/v1/enrollments/{id}/documents", 11L),
            Map.entry("POST /api/v1/enrollments/document-requests/{requestId}/fulfil", 11L),
            Map.entry("POST /api/v1/partner/enrollments/{enrollmentId}/official-documents", 11L),
            Map.entry("POST /api/v1/admin/enrollments/{enrollmentId}/official-documents", 11L),
            Map.entry("POST /api/v1/partner/trainings/{id}/brochure", 11L),
            Map.entry("POST /api/v1/partner/trainings/{id}/image", 11L),
            Map.entry("POST /api/v1/partner/trainings/{id}/video", 55L),
            Map.entry("POST /api/v1/admin/blog/cover", 11L),
            Map.entry("PUT /api/v1/admin/catalog/products/{id}/media", 11L),
            Map.entry("POST /api/v1/admin/catalog/products/{id}/gallery", 120L),
            Map.entry("POST /api/v1/admin/catalog/products/{id}/video", 55L),
            Map.entry("POST /api/v1/admin/suppliers/{id}/imports", 16L),
            Map.entry("POST /api/v1/ai/extract", 11L)));

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping routes;

    private static boolean recoitUnFichier(HandlerMethod methode) {
        for (Parameter parametre : methode.getMethod().getParameters()) {
            if (parametre.getParameterizedType().getTypeName().contains(MultipartFile.class.getName())) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("chaque route de dépôt de l'application a une taille décidée, et le filtre l'applique")
    void inventaire() {
        Map<String, Long> trouve = new TreeMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entree : routes.getHandlerMethods().entrySet()) {
            if (!recoitUnFichier(entree.getValue())) {
                continue;
            }
            RequestMappingInfo info = entree.getKey();
            for (var methode : info.getMethodsCondition().getMethods()) {
                for (String chemin : info.getPatternValues()) {
                    // Le filtre lit une adresse réelle : on remplace chaque variable par une valeur.
                    String adresse = chemin.replaceAll("\\{[^}]+}", "7f3c");
                    trouve.put(methode + " " + chemin,
                            FiltreTailleRequete.tailleAdmise(methode.name(), adresse) / MO);
                }
            }
        }

        assertThat(trouve)
                .as("routes de dépôt de l'application, avec la taille de requête admise en Mo")
                .containsExactlyInAnyOrderEntriesOf(ATTENDU);
    }
}
