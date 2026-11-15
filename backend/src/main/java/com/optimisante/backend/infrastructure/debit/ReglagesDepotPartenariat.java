package com.optimisante.backend.infrastructure.debit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Les réglages du dépôt anonyme de partenariat, modifiables sans redéploiement.
 *
 * <p><b>Des plafonds bien plus bas que ceux de l'assistant, et c'est voulu.</b> Un établissement
 * dépose une demande, la corrige peut-être une fois, fait relire son classeur trois ou quatre
 * fois. Dix envois par minute et quarante par jour laissent passer le plus brouillon des
 * candidats ; un script bute aussitôt. Chaque envoi accepté coûte un fichier au stockage, une
 * ligne en base et un message à l'administration : c'est ce coût-là que le plafond global borne.</p>
 *
 * @param actif                    {@code false} lève les plafonds de débit et le contrôle du
 *                                 jeton de formulaire, pour qu'un mauvais réglage ne ferme
 *                                 jamais la page aux établissements le temps d'un redéploiement.
 *                                 Les contrôles de fichier, eux, ne se désactivent pas
 * @param plafondParAdresseParMinute envois admis par adresse et par minute
 * @param plafondParAdresseParJour   envois admis par adresse et par jour
 * @param plafondGlobalParJour       envois admis toutes adresses confondues et par jour
 * @param tailleMaxRequeteOctets     taille de requête au delà de laquelle le filtre refuse
 *                                   <b>sans lire le corps</b> : 10 Mo de fichier et une marge
 *                                   pour les champs et l'enveloppe multipart
 * @param delaiMinimumSecondes       temps qui doit s'être écoulé entre l'affichage du formulaire
 *                                   et son envoi
 * @param validiteJetonMinutes       au delà, le formulaire est considéré comme périmé
 */
@ConfigurationProperties(prefix = "app.depot-partenariat")
public record ReglagesDepotPartenariat(
        Boolean actif,
        Integer plafondParAdresseParMinute,
        Integer plafondParAdresseParJour,
        Integer plafondGlobalParJour,
        Long tailleMaxRequeteOctets,
        Integer delaiMinimumSecondes,
        Integer validiteJetonMinutes) {

    public ReglagesDepotPartenariat {
        actif = actif == null || actif;
        plafondParAdresseParMinute = plafondParAdresseParMinute == null ? 10 : plafondParAdresseParMinute;
        plafondParAdresseParJour = plafondParAdresseParJour == null ? 40 : plafondParAdresseParJour;
        plafondGlobalParJour = plafondGlobalParJour == null ? 300 : plafondGlobalParJour;
        tailleMaxRequeteOctets = tailleMaxRequeteOctets == null ? 11L * 1024 * 1024 : tailleMaxRequeteOctets;
        delaiMinimumSecondes = delaiMinimumSecondes == null ? 5 : delaiMinimumSecondes;
        validiteJetonMinutes = validiteJetonMinutes == null ? 240 : validiteJetonMinutes;
    }

    public static ReglagesDepotPartenariat defaut() {
        return new ReglagesDepotPartenariat(null, null, null, null, null, null, null);
    }
}
