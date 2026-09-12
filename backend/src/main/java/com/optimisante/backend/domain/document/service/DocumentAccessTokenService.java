package com.optimisante.backend.domain.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Fabrique et vérifie les jetons qui donnent accès à un document.
 *
 * <p><b>Pourquoi un jeton dans l'URL plutôt qu'un contrôle d'accès classique ?</b> Tous les écrans
 * ouvrent les documents par {@code window.open(url)}. Un onglet ouvert ainsi n'emporte
 * <em>aucun</em> en-tête {@code Authorization} : le jeton de session vit dans le
 * {@code localStorage}, que le navigateur ne joint pas à une navigation. Une route simplement
 * « authentifiée » renverrait donc 401 à tous les coups. Le droit d'accès doit voyager dans
 * l'adresse elle-même.</p>
 *
 * <p>Ce que le jeton remplace : jusqu'ici le serveur livrait l'URL Cloudinary du fichier, déposé
 * en mode public. Cette adresse ne périmait jamais, ne vérifiait rien, et son nom était
 * reconstituable ({@code RECEIPT-OPT-<date>-<4 hex>}). Le jeton, lui, est signé, daté, et ne
 * désigne qu'un seul fichier.</p>
 *
 * <p><b>Ce qu'il ne fait pas</b>, et il faut le savoir : pendant sa durée de vie, quiconque
 * détient l'adresse peut ouvrir le document. C'est inhérent à une URL ouverte dans un onglet.
 * Le gain est ailleurs — l'adresse expire, elle ne se devine pas, et l'autorisation a été
 * vérifiée au moment de la délivrance par le service métier qui, lui, sait qui a le droit de
 * voir quoi.</p>
 */
@Slf4j
@Service
public class DocumentAccessTokenService {

    /**
     * Préfixe de séparation de domaine : un jeton de document et un jeton de session sont signés
     * avec le même secret, mais sur des messages préfixés différemment. Sans cela, un jeton d'un
     * domaine pourrait en théorie être présenté à l'autre.
     */
    private static final String DOMAINE = "doc-access:v1:";

    private static final Base64.Encoder ENCODEUR = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODEUR = Base64.getUrlDecoder();

    private final byte[] cle;

    public DocumentAccessTokenService(@Value("${app.security.jwt.secret}") String secret) {
        this.cle = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Délivre un jeton pour un fichier donné.
     *
     * @param publicId   la clé de stockage — elle ne quitte jamais le serveur en clair
     * @param minutes    durée de validité
     * @param libelle    nom lisible proposé au téléchargement ; peut être nul
     */
    public String delivrer(String publicId, int minutes, String libelle) {
        long expiration = Instant.now().plusSeconds(minutes * 60L).getEpochSecond();
        // Le « | » sépare les champs : on le retire du libellé plutôt que d'inventer un
        // échappement, un nom de fichier n'en contient jamais légitimement.
        String charge = publicId + "|" + expiration + "|" + nettoyer(libelle);
        String chargeEncodee = ENCODEUR.encodeToString(charge.getBytes(StandardCharsets.UTF_8));
        return chargeEncodee + "." + ENCODEUR.encodeToString(signer(chargeEncodee));
    }

    /**
     * Vérifie un jeton et rend le fichier qu'il désigne.
     *
     * @return vide si la signature est invalide, le jeton malformé, ou la validité écoulée —
     *         l'appelant ne doit pas distinguer ces cas pour le visiteur.
     */
    public Optional<DocumentDemande> verifier(String jeton) {
        if (jeton == null || jeton.isBlank()) return Optional.empty();

        int point = jeton.lastIndexOf('.');
        if (point <= 0 || point == jeton.length() - 1) return Optional.empty();

        String chargeEncodee = jeton.substring(0, point);
        byte[] signatureRecue;
        String charge;
        try {
            signatureRecue = DECODEUR.decode(jeton.substring(point + 1));
            charge = new String(DECODEUR.decode(chargeEncodee), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malforme) {
            return Optional.empty();
        }

        // Comparaison à temps constant : une comparaison ordinaire s'arrête au premier octet
        // différent, et la durée de l'échec renseigne alors sur la signature attendue.
        if (!MessageDigest.isEqual(signatureRecue, signer(chargeEncodee))) {
            log.warn("Jeton de document rejeté : signature invalide.");
            return Optional.empty();
        }

        String[] champs = charge.split("\\|", 3);
        if (champs.length < 2) return Optional.empty();

        long expiration;
        try {
            expiration = Long.parseLong(champs[1]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (Instant.now().getEpochSecond() > expiration) {
            return Optional.empty();
        }

        String libelle = champs.length == 3 && !champs[2].isBlank() ? champs[2] : null;
        return Optional.of(new DocumentDemande(champs[0], libelle));
    }

    private byte[] signer(String chargeEncodee) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cle, "HmacSHA256"));
            return mac.doFinal((DOMAINE + chargeEncodee).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Une signature impossible à produire est un défaut de configuration, pas un cas
            // d'usage : mieux vaut échouer bruyamment que délivrer un jeton non signé.
            throw new IllegalStateException("Signature du jeton de document impossible", e);
        }
    }

    private static String nettoyer(String libelle) {
        return libelle == null ? "" : libelle.replace('|', '-').replace('\n', ' ').trim();
    }

    /** Ce qu'un jeton valide désigne : un fichier, et le nom sous lequel le proposer. */
    public record DocumentDemande(String publicId, String libelle) {}
}
