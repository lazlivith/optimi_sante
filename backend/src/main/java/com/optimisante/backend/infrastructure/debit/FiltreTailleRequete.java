package com.optimisante.backend.infrastructure.debit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Refuse, <b>avant d'en lire le corps</b>, toute requête plus grosse que ce que sa route justifie.
 *
 * <p><b>Le défaut est de refuser.</b> Le serveur accepte 55 Mo par fichier et 120 Mo par requête,
 * parce que quatre routes en ont besoin. Cette limite est unique pour toute l'application : elle
 * s'appliquait donc aussi aux treize autres routes de dépôt, et à toute route qu'on écrira
 * demain. Ici, la règle est inversée : onze mégaoctets pour tout le monde, et une <b>liste
 * fermée</b> de routes nommées qui ont droit à davantage. Une route nouvelle est protégée sans
 * que personne y pense ; lui donner plus de place demande de l'écrire ici, donc de le décider.
 * Il vaut pour <b>toute</b> requête, pas seulement les dépôts de fichier : aucun corps JSON de
 * cette application n'approche onze mégaoctets.</p>
 *
 * <p><b>Onze et non dix.</b> Les contrôles métier refusent un fichier de plus de 10 Mo avec un
 * message qui nomme la limite. Le mégaoctet de marge couvre les champs du formulaire et
 * l'enveloppe multipart : sans lui, un fichier de 9,9 Mo parfaitement valable serait refusé ici,
 * par un message moins précis.</p>
 *
 * <p><b>Ce que ce filtre ne couvre pas.</b> Une requête qui n'annonce pas sa taille (envoi par
 * morceaux) passe, reste bornée par la limite du serveur, et n'est refusée qu'après lecture par
 * le contrôle de la route. Exiger l'annonce refuserait un navigateur légitime si un
 * intermédiaire la retirait, ce qui n'a pas été vérifié sur le chemin Cloudflare → Render.</p>
 */
@Slf4j
public class FiltreTailleRequete extends OncePerRequestFilter {

    private static final long MO = 1024L * 1024;

    /** Ce que toute requête a le droit de peser, sauf exception nommée ci-dessous. */
    static final long TAILLE_PAR_DEFAUT = 11 * MO;

    /**
     * Les seules routes autorisées à dépasser le défaut, chacune avec ce que son usage justifie.
     *
     * <p>La marge au-dessus de la limite métier (50 Mo pour une vidéo, 15 Mo pour un catalogue)
     * a la même raison que plus haut : laisser le contrôle de la route refuser le premier, avec
     * son message à lui.</p>
     */
    private static final List<Derogation> DEROGATIONS = List.of(
            new Derogation("POST", "/api/v1/admin/catalog/products/[^/]+/video", 55 * MO, "50 Mo pour une vidéo"),
            new Derogation("POST", "/api/v1/partner/trainings/[^/]+/video", 55 * MO, "50 Mo pour une vidéo"),
            // Douze visuels de 10 Mo au plus, déposés en une seule requête.
            new Derogation("POST", "/api/v1/admin/catalog/products/[^/]+/gallery", 120 * MO,
                    "12 visuels de 10 Mo"),
            new Derogation("POST", "/api/v1/admin/suppliers/[^/]+/imports", 16 * MO,
                    "15 Mo pour un catalogue"));

    private static final Derogation DEFAUT = new Derogation("", ".*", TAILLE_PAR_DEFAUT, "10 Mo");

    /**
     * @param limiteAnnoncee la limite telle que l'utilisateur la connaît — celle du contrôle
     *                       métier, pas celle, un peu plus large, que ce filtre applique
     */
    private record Derogation(String methode, Pattern chemin, long tailleMax, String limiteAnnoncee) {
        Derogation(String methode, String chemin, long tailleMax, String limiteAnnoncee) {
            this(methode, Pattern.compile(chemin), tailleMax, limiteAnnoncee);
        }
    }

    private static Derogation regle(String methode, String chemin) {
        for (Derogation derogation : DEROGATIONS) {
            if (derogation.methode().equalsIgnoreCase(methode) && derogation.chemin().matcher(chemin).matches()) {
                return derogation;
            }
        }
        return DEFAUT;
    }

    /** Taille admise pour cette requête : celle de sa dérogation, ou le défaut. */
    static long tailleAdmise(String methode, String chemin) {
        return regle(methode, chemin).tailleMax();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        long annoncee = requete.getContentLengthLong();
        Derogation regle = regle(requete.getMethod(), requete.getRequestURI());
        long admise = regle.tailleMax();
        if (annoncee > admise) {
            log.warn("Requête refusée avant lecture : {} octets annoncés sur {} {}, {} admis.",
                    annoncee, requete.getMethod(), requete.getRequestURI(), admise);
            reponse.setStatus(413);
            reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            reponse.setCharacterEncoding("UTF-8");
            reponse.getWriter().write("{\"message\":\"L'envoi est trop volumineux : la limite est de "
                    + regle.limiteAnnoncee() + ".\"}");
            return;
        }
        chaine.doFilter(requete, reponse);
    }
}
