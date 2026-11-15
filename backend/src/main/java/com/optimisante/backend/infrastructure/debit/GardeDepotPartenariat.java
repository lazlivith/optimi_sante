package com.optimisante.backend.infrastructure.debit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Ce qui protège le dépôt anonyme de partenariat contre l'envoi en boucle.
 *
 * <p>Deux mécanismes, et ils ne valent pas la même chose.</p>
 *
 * <p><b>Le plafond de débit est la vraie borne.</b> Par adresse pour gêner un script naïf,
 * global pour borner ce qu'on stocke et ce qu'on notifie en une journée quoi qu'il arrive.
 * C'est le même {@link PlafondDebit} que l'assistant, avec ses propres compteurs.</p>
 *
 * <p><b>Le jeton de formulaire et le champ piège écartent le robot qui passe.</b> Le formulaire
 * demande un jeton à l'affichage ; l'envoi doit le présenter, signé, vieux d'au moins quelques
 * secondes, et laisser vide un champ qu'aucun humain ne voit. Un robot générique qui poste à
 * l'aveugle échoue sur les trois. <b>Un script écrit pour ce site, non</b> : il demandera le
 * jeton, attendra, et laissera le champ vide. Contre lui il n'y a que le plafond — et c'est
 * pourquoi le plafond ne dépend d'aucun de ces contrôles.</p>
 *
 * <p><b>Pourquoi pas un captcha.</b> Un service tiers recevrait l'adresse et le comportement de
 * chaque visiteur de la page : un sous-traitant de plus à déclarer, pour une page qui reçoit
 * quelques demandes par mois.</p>
 *
 * <p><b>La clé de signature naît au démarrage et ne quitte pas la mémoire.</b> Aucun secret à
 * configurer ni à faire tourner. Le prix : un redémarrage du serveur périme les formulaires
 * ouverts, qui reçoivent alors un message leur demandant de recharger la page. Avec deux
 * instances il faudrait une clé partagée — même échéance que les compteurs de
 * {@link PlafondDebit}.</p>
 */
@Slf4j
@Component
public class GardeDepotPartenariat {

    private static final String ALGORITHME = "HmacSHA256";

    private static final String FORMULAIRE_PERIME =
            "Ce formulaire a expiré. Rechargez la page, puis renvoyez votre demande : "
                    + "ce que vous avez saisi devra être ressaisi.";

    private final ReglagesDepotPartenariat reglages;
    private final Clock horloge;
    private final PlafondDebit plafond;
    private final SecretKeySpec cle;

    public GardeDepotPartenariat(ReglagesDepotPartenariat reglages, Clock horlogePlafondDebit) {
        this.reglages = reglages;
        this.horloge = horlogePlafondDebit;
        this.plafond = new PlafondDebit(
                new ReglagesPlafondDebit(true, reglages.plafondParAdresseParMinute(),
                        reglages.plafondParAdresseParJour(), reglages.plafondGlobalParJour()),
                horlogePlafondDebit, "Le dépôt de partenariat");
        byte[] alea = new byte[32];
        new SecureRandom().nextBytes(alea);
        this.cle = new SecretKeySpec(alea, ALGORITHME);
        if (!reglages.actif()) {
            log.warn("Garde du dépôt de partenariat DÉSACTIVÉE par configuration : ni plafond de "
                    + "débit ni jeton de formulaire. Les contrôles de fichier restent en vigueur.");
        }
    }

    public boolean actif() {
        return reglages.actif();
    }

    public ReglagesDepotPartenariat reglages() {
        return reglages;
    }

    /** Débite un envoi pour cette adresse, ou dit quel plafond le refuse. */
    public PlafondDebit.VerdictPlafond verifierDebit(String adresse) {
        return plafond.verifier(adresse);
    }

    /** Jeton remis à l'affichage du formulaire : l'instant d'émission, et sa signature. */
    public String emettreJeton() {
        long emisLe = horloge.instant().toEpochMilli();
        return emisLe + "." + signer(emisLe);
    }

    /**
     * Refuse l'envoi qui ne vient manifestement pas du formulaire.
     *
     * <p>Tous les refus rendent un message qu'un humain peut suivre. Un champ piège rempli par
     * le remplissage automatique d'un navigateur, ou un formulaire resté ouvert une demi-journée,
     * sont des cas réels : répondre « succès » pour tromper le robot ferait disparaître en
     * silence la demande d'un établissement.</p>
     *
     * @param jeton valeur remise par {@link #emettreJeton()}
     * @param piege contenu du champ qu'aucun humain ne voit ; doit être vide
     * @throws IllegalArgumentException avec un message à montrer tel quel à l'utilisateur
     */
    public void verifierEnvoi(String jeton, String piege) {
        if (!reglages.actif()) {
            return;
        }
        if (piege != null && !piege.isBlank()) {
            log.warn("Dépôt de partenariat refusé : champ piège rempli ({} caractères).", piege.length());
            throw new IllegalArgumentException(
                    "Votre demande n'a pas pu être vérifiée. Rechargez la page et renvoyez-la ; "
                            + "si le problème persiste, écrivez-nous à infos@optimisante.com.");
        }

        long emisLe = instantSigne(jeton);
        Duration age = Duration.between(Instant.ofEpochMilli(emisLe), horloge.instant());
        if (age.compareTo(Duration.ofMinutes(reglages.validiteJetonMinutes())) > 0) {
            throw new IllegalArgumentException(FORMULAIRE_PERIME);
        }
        if (age.compareTo(Duration.ofSeconds(reglages.delaiMinimumSecondes())) < 0) {
            log.warn("Dépôt de partenariat refusé : formulaire envoyé {} ms après son affichage.",
                    age.toMillis());
            throw new IllegalArgumentException(
                    "Votre demande a été envoyée trop vite pour être vérifiée. "
                            + "Patientez quelques secondes, puis renvoyez-la.");
        }
    }

    /** L'instant porté par le jeton, si et seulement si sa signature est la nôtre. */
    private long instantSigne(String jeton) {
        if (jeton == null || jeton.length() > 100) {
            throw new IllegalArgumentException(FORMULAIRE_PERIME);
        }
        int point = jeton.indexOf('.');
        if (point <= 0) {
            throw new IllegalArgumentException(FORMULAIRE_PERIME);
        }
        long emisLe;
        try {
            emisLe = Long.parseLong(jeton.substring(0, point));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(FORMULAIRE_PERIME);
        }
        // Comparaison en temps constant : une comparaison ordinaire s'arrête au premier
        // caractère différent, et sa durée renseigne sur la signature attendue.
        boolean authentique = MessageDigest.isEqual(
                signer(emisLe).getBytes(StandardCharsets.US_ASCII),
                jeton.substring(point + 1).getBytes(StandardCharsets.US_ASCII));
        if (!authentique) {
            throw new IllegalArgumentException(FORMULAIRE_PERIME);
        }
        return emisLe;
    }

    private String signer(long emisLe) {
        try {
            Mac mac = Mac.getInstance(ALGORITHME);
            mac.init(cle);
            byte[] signature = mac.doFinal(Long.toString(emisLe).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            // HmacSHA256 est exigé de toute machine Java : ne pas l'avoir est une panne
            // d'installation, pas un cas à traiter.
            throw new IllegalStateException("Signature du formulaire indisponible.", e);
        }
    }
}
