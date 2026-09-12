package com.optimisante.backend.domain.document.service;

import com.optimisante.backend.domain.document.DocumentKind;
import com.optimisante.backend.infrastructure.legal.CompanyIdentity;
import com.optimisante.backend.infrastructure.legal.MarqueAssets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Point d'entrée unique de la production de documents.
 *
 * <p>Tout document passe par ici, et c'est ce qui permet de garantir une chose sans la répéter
 * huit fois : <b>l'identité légale est injectée dans chaque rendu</b>. Avant, le pied de page
 * portait ses valeurs en dur, faute d'un endroit où les faire transiter.</p>
 *
 * <p>{@code PdfGeneratorService} subsiste et délègue à cette classe : ses appelants continuent de
 * fonctionner à l'identique. Le moteur de rendu redevient un détail d'implémentation que l'on
 * pourra remplacer — ce qu'il faudra faire le jour où un PDF balisé (PDF/UA) sera exigé.</p>
 */
@Slf4j
@Service
public class DocumentRenderer {

    /** Nom sous lequel les gabarits accèdent à l'identité : {@code ${societe.ligneIdentite()}}. */
    public static final String VARIABLE_SOCIETE = "societe";

    /** Nom sous lequel les gabarits accèdent au logo : {@code ${marque.logoDataUri()}}. */
    public static final String VARIABLE_MARQUE = "marque";

    private final TemplateEngine templateEngine;
    private final CompanyIdentity societe;
    private final MarqueAssets marque;

    public DocumentRenderer(@Qualifier("pdfTemplateEngine") TemplateEngine templateEngine,
                            CompanyIdentity societe, MarqueAssets marque) {
        this.templateEngine = templateEngine;
        this.societe = societe;
        this.marque = marque;
    }

    /** Rend un document déclaré au registre. */
    public byte[] rendre(DocumentKind kind, Map<String, Object> variables) {
        return rendreGabarit(kind.gabarit(), variables);
    }

    /**
     * Rend un gabarit désigné par son nom.
     *
     * <p>Réservé à la compatibilité avec le code existant : le gabarit est vérifié contre le
     * registre, de sorte qu'un document non déclaré échoue tout de suite, avec un message qui
     * dit quoi faire — plutôt que de produire un PDF que personne n'a recensé.</p>
     */
    public byte[] rendreGabarit(String nomGabarit, Map<String, Object> variables) {
        DocumentKind.parGabarit(nomGabarit);   // lève si le document n'est pas déclaré

        Map<String, Object> contexte = new HashMap<>(variables == null ? Map.of() : variables);
        // putIfAbsent et non put : un appelant qui fournirait sa propre société (un cas de test,
        // par exemple) garde la main.
        contexte.putIfAbsent(VARIABLE_SOCIETE, societe);
        contexte.putIfAbsent(VARIABLE_MARQUE, marque);

        Context ctx = new Context();
        ctx.setVariables(contexte);
        String html = templateEngine.process(nomGabarit, ctx);

        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(sortie);
            return sortie.toByteArray();
        } catch (Exception e) {
            log.error("Rendu impossible pour le gabarit {}", nomGabarit, e);
            throw new RuntimeException("Rendu du document impossible : " + nomGabarit, e);
        }
    }
}
