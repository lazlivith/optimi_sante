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
 * <p><b>Pourquoi un jeton dans l'URL plutôt qu'un contrôle d'accès classique ?</b> Le document est
 * récupéré par une requête qui n'emporte <em>aucun</em> en-tête {@code Authorization} — l'aperçu
 * intégré du front, un lien d'enregistrement, et à l'origine un {@code window.open(url)}. Le jeton
 * de session vit dans le {@code localStorage}, que le navigateur ne joint à rien de tout cela. Une
 * route simplement « authentifiée » renverrait donc 401 à tous les coups. Le droit d'accès doit voyager dans
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

    /**
     * Séparation de domaine de la clé de <b>chiffrement</b> : dérivée du même secret que la
     * signature, mais jamais égale à elle. Deux usages, deux clés.
     */
    private static final String DOMAINE_CHIFFREMENT = "doc-access:chiffrement:v1:";

    /** Taille du vecteur d'initialisation d'AES-GCM, en octets. 12 est la valeur recommandée. */
    private static final int TAILLE_NONCE = 12;

    /** Longueur de l'étiquette d'authentification d'AES-GCM, en bits. */
    private static final int TAILLE_ETIQUETTE = 128;

    private static final Base64.Encoder ENCODEUR = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODEUR = Base64.getUrlDecoder();

    private static final java.security.SecureRandom ALEA = new java.security.SecureRandom();

    private final byte[] cle;
    private final javax.crypto.SecretKey cleChiffrement;

    public DocumentAccessTokenService(@Value("${app.security.jwt.secret}") String secret) {
        this.cle = secret.getBytes(StandardCharsets.UTF_8);
        this.cleChiffrement = deriverCleChiffrement(secret);
    }

    private static javax.crypto.SecretKey deriverCleChiffrement(String secret) {
        try {
            byte[] brut = MessageDigest.getInstance("SHA-256")
                    .digest((DOMAINE_CHIFFREMENT + secret).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(brut, "AES");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }

    /**
     * Chiffre la charge du jeton, au lieu de la coder en base64.
     *
     * <p><b>Pourquoi, et c'est un correctif, pas une précaution.</b> La charge était simplement
     * <em>encodée</em> : {@code base64url(publicId|expiration|libelle)}. Un base64 n'est pas un
     * secret — un {@code atob(jeton.split('.')[0])} dans la console du navigateur rendait la clé
     * de stockage en clair. Toute la raison d'être de DP-4 — « la clé ne quitte pas le serveur » —
     * était donc fausse tant que la charge restait lisible : le jeton <b>était</b> la fuite, sous
     * une autre forme. La signature empêchait de <em>fabriquer</em> un jeton ; elle n'empêchait
     * pas de <em>lire</em> celui qu'on avait reçu.</p>
     *
     * <p>AES-GCM avec un vecteur d'initialisation tiré au hasard à chaque appel : deux jetons
     * délivrés pour le même fichier ne se ressemblent pas, ce qui interdit aussi de reconnaître
     * deux liens comme désignant la même pièce. L'étiquette d'authentification de GCM s'ajoute à
     * la signature HMAC existante, qui est conservée : une charge modifiée est rejetée deux
     * fois.</p>
     */
    private String chiffrer(String charge) {
        try {
            byte[] nonce = new byte[TAILLE_NONCE];
            ALEA.nextBytes(nonce);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, cleChiffrement,
                    new javax.crypto.spec.GCMParameterSpec(TAILLE_ETIQUETTE, nonce));
            byte[] chiffre = cipher.doFinal(charge.getBytes(StandardCharsets.UTF_8));

            byte[] assemble = new byte[nonce.length + chiffre.length];
            System.arraycopy(nonce, 0, assemble, 0, nonce.length);
            System.arraycopy(chiffre, 0, assemble, nonce.length, chiffre.length);
            return ENCODEUR.encodeToString(assemble);
        } catch (Exception e) {
            // Même raisonnement que pour la signature : un chiffrement impossible est un défaut
            // de configuration. Mieux vaut échouer que délivrer un jeton lisible.
            throw new IllegalStateException("Chiffrement du jeton de document impossible", e);
        }
    }

    /** @return la charge en clair, ou {@code null} si le jeton ne se déchiffre pas. */
    private String dechiffrer(byte[] assemble) {
        if (assemble.length <= TAILLE_NONCE) return null;
        try {
            byte[] nonce = java.util.Arrays.copyOfRange(assemble, 0, TAILLE_NONCE);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, cleChiffrement,
                    new javax.crypto.spec.GCMParameterSpec(TAILLE_ETIQUETTE, nonce));
            byte[] clair = cipher.doFinal(assemble, TAILLE_NONCE, assemble.length - TAILLE_NONCE);
            return new String(clair, StandardCharsets.UTF_8);
        } catch (Exception illisible) {
            // Jeton d'un ancien format, ou charge altérée : indistinguable pour le visiteur, et
            // c'est voulu.
            return null;
        }
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
        // Chiffrée, et non seulement encodée : voir chiffrer(). Un base64 n'est pas un secret.
        String chargeEncodee = chiffrer(charge);
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
        byte[] chargeChiffree;
        try {
            signatureRecue = DECODEUR.decode(jeton.substring(point + 1));
            chargeChiffree = DECODEUR.decode(chargeEncodee);
        } catch (IllegalArgumentException malforme) {
            return Optional.empty();
        }

        // Comparaison à temps constant : une comparaison ordinaire s'arrête au premier octet
        // différent, et la durée de l'échec renseigne alors sur la signature attendue.
        if (!MessageDigest.isEqual(signatureRecue, signer(chargeEncodee))) {
            log.warn("Jeton de document rejeté : signature invalide.");
            return Optional.empty();
        }

        // Le déchiffrement vient APRÈS la signature : on ne fait travailler le chiffre que sur une
        // charge dont on a déjà établi qu'elle vient de nous.
        String charge = dechiffrer(chargeChiffree);
        if (charge == null) {
            log.warn("Jeton de document rejeté : charge illisible.");
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
