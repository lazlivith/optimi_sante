package com.optimisante.backend.infrastructure.debit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Arrête les envois de fichier anonymes vers le partenariat <b>avant que le corps ne soit lu</b>.
 *
 * <p><b>Pourquoi un filtre.</b> Le serveur accepte 55 Mo par fichier, parce qu'une vidéo de
 * formation déposée par un administrateur les vaut. Cette limite est globale : elle s'applique
 * aussi aux deux seules routes où un inconnu peut envoyer un fichier. Un contrôle dans le
 * contrôleur arrive trop tard — quand il s'exécute, les 55 Mo sont déjà écrits sur le disque du
 * serveur. Ici, la requête trop grosse est refusée sur sa seule annonce de taille.</p>
 *
 * <p><b>Ce que la taille annoncée ne couvre pas.</b> Un client peut ne pas annoncer de taille
 * (envoi par morceaux). Sa requête passe alors ce filtre, reste bornée par la limite globale, et
 * le fichier est refusé après lecture par {@code ControleFichier}. Exiger l'annonce fermerait ce
 * cas, mais refuserait aussi un navigateur légitime si un intermédiaire la retirait — ce que je
 * n'ai pas pu vérifier sur le chemin Cloudflare → Render. Le plafond de débit borne de toute
 * façon le nombre de ces requêtes.</p>
 *
 * <p>L'ordre compte : la taille d'abord, le débit ensuite, pour qu'une requête refusée pour sa
 * taille ne consomme pas le quota d'un établissement qui s'est simplement trompé de fichier.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class FiltreDepotPartenariat extends OncePerRequestFilter {

    /** Les deux routes du partenariat qui reçoivent un fichier d'un anonyme. */
    private static final List<String> ROUTES_DE_DEPOT = List.of(
            "/api/v1/partnership/requests",
            "/api/v1/partnership/dossier/verification");

    private final GardeDepotPartenariat garde;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        if (!"POST".equalsIgnoreCase(requete.getMethod())) {
            return true;
        }
        String chemin = requete.getRequestURI();
        return ROUTES_DE_DEPOT.stream().noneMatch(chemin::equals);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        long annoncee = requete.getContentLengthLong();
        if (annoncee > garde.reglages().tailleMaxRequeteOctets()) {
            log.warn("Dépôt de partenariat refusé avant lecture : {} octets annoncés sur {}.",
                    annoncee, requete.getRequestURI());
            repondre(reponse, 413, "Le fichier envoyé est trop volumineux : la limite est de 10 Mo.", null);
            return;
        }

        if (garde.actif()) {
            PlafondDebit.VerdictPlafond verdict =
                    garde.verifierDebit(FiltrePlafondDebit.adresseDuVisiteur(requete));
            if (!verdict.permis()) {
                repondre(reponse, 429, message(verdict.plafondAtteint()), verdict.secondesAvantReouverture());
                return;
            }
        }
        chaine.doFilter(requete, reponse);
    }

    /** Comme pour l'assistant, le refus global ne dit pas qu'un quota commun est épuisé. */
    private String message(PlafondDebit.TypePlafond type) {
        return switch (type) {
            case ADRESSE_PAR_MINUTE -> "Trop d'envois en peu de temps. Réessayez dans une minute.";
            case ADRESSE_PAR_JOUR -> "Vous avez atteint le nombre d'envois possibles pour aujourd'hui. "
                    + "Réessayez demain, ou écrivez-nous à infos@optimisante.com.";
            case GLOBAL_JOURNALIER -> "Le dépôt de dossier est momentanément indisponible. "
                    + "Réessayez plus tard, ou écrivez-nous à infos@optimisante.com.";
        };
    }

    private void repondre(HttpServletResponse reponse, int statut, String message, Long secondes)
            throws IOException {
        reponse.setStatus(statut);
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        String corps = "{\"message\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        if (secondes != null) {
            reponse.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(secondes));
            corps += ",\"reessayerDansSecondes\":" + secondes;
        }
        reponse.getWriter().write(corps + "}");
    }
}
