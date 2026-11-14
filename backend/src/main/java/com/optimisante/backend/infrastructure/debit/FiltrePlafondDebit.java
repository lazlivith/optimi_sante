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
 * Applique les plafonds de {@link PlafondDebit} aux routes d'IA ouvertes aux anonymes.
 *
 * <p><b>Pourquoi un filtre et non une annotation.</b> Le plafond doit s'appliquer <i>avant</i>
 * que la requête n'atteigne le service, donc avant tout appel payant, et il doit s'appliquer
 * à une requête qui ne porte aucune authentification — ce qu'une annotation de sécurité ne
 * sait pas faire. Le filtre est placé avant la chaîne d'authentification pour la même raison :
 * une requête refusée ici ne doit coûter ni un décodage de jeton ni un accès à la base.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class FiltrePlafondDebit extends OncePerRequestFilter {

    /**
     * Les routes plafonnées : celles qui sont ouvertes aux anonymes <b>et</b> qui coûtent de
     * l'argent ou du travail de base de données à chaque appel.
     *
     * <p>Les deux routes {@code /ai/tools/**} ne coûtent pas d'appel au modèle — elles lisent le
     * catalogue — mais elles sont ouvertes et interrogeables en boucle : elles sont plafonnées
     * pour la base, pas pour la facture d'IA.</p>
     */
    private static final List<String> ROUTES_PLAFONNEES = List.of(
            "/api/v1/ai/chat",
            "/api/v1/ai/chat/stream",
            "/api/v1/ai/tools/catalog/search",
            "/api/v1/ai/tools/trainings");

    private final PlafondDebit plafondDebit;
    private final ReglagesPlafondDebit reglages;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        if (!reglages.actif()) {
            return true;
        }
        String chemin = requete.getRequestURI();
        return ROUTES_PLAFONNEES.stream().noneMatch(chemin::equals);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        PlafondDebit.VerdictPlafond verdict = plafondDebit.verifier(adresseDuVisiteur(requete));
        if (verdict.permis()) {
            chaine.doFilter(requete, reponse);
            return;
        }
        refuser(requete, reponse, verdict);
    }

    /**
     * L'adresse du visiteur, dans l'ordre de confiance décroissante.
     *
     * <p><b>Pourquoi pas {@code getRemoteAddr()} d'abord.</b> Il rend l'adresse du dernier
     * intermédiaire, identique pour tout le monde : s'en servir ferait du plafond « par
     * adresse » un second plafond global, et un seul visiteur fermerait l'assistant à tous.</p>
     *
     * <p><b>1. {@code CF-Connecting-IP}.</b> Tout {@code *.onrender.com} est servi au travers
     * de Cloudflare — relevé le 14 novembre 2026 sur une réponse de production :
     * {@code Server: cloudflare}, {@code CF-RAY}, {@code cf-cache-status}. C'est l'edge de
     * <b>Render</b>, pas un compte Cloudflare qui nous appartienne, mais l'en-tête est posé
     * par lui et porte l'adresse du client telle que l'edge l'a vue.</p>
     *
     * <p><b>2. La DERNIÈRE entrée de {@code X-Forwarded-For}, et non la première.</b> La
     * documentation de Cloudflare indique que {@code X-Forwarded-For} vaut
     * {@code CF-Connecting-IP} lorsque le client n'en a envoyé aucun — autrement dit l'edge
     * <b>ajoute</b> sa constatation à ce que le client a fourni. Ce que le client contrôle est
     * donc à <b>gauche</b>, et la constatation du proxy à <b>droite</b>. Ma première version
     * lisait la première entrée : elle prenait précisément la partie falsifiable. L'erreur
     * était de moi.</p>
     *
     * <p><b>3. {@code getRemoteAddr()}</b> en dernier recours — en développement local, où il
     * est juste.</p>
     *
     * <p><b>Ce qui reste non vérifié, et comment le vérifier.</b> Que l'edge de Render
     * <b>écrase</b> un {@code CF-Connecting-IP} fourni par le client n'est affirmé par aucune
     * documentation que j'aie pu lire : celle de Cloudflare énonce l'écrasement pour d'autres
     * en-têtes, pas pour celui-ci, et Render ne documente que {@code X-Forwarded-For}. Le test
     * est pourtant simple une fois ce lot déployé : envoyer plus d'appels que le plafond par
     * minute en faisant varier {@code CF-Connecting-IP} à chaque requête. Si le 429 tombe
     * quand même, l'en-tête n'est pas accepté du client et il est digne de confiance ; s'il ne
     * tombe jamais, il l'est, et il faudra se replier sur la dernière entrée de
     * {@code X-Forwarded-For}. <b>Dans les deux cas le plafond global tient</b> : il ne lit
     * aucune donnée fournie par le client. Les plafonds par adresse protègent le service ; le
     * plafond global protège la facture.</p>
     */
    private String adresseDuVisiteur(HttpServletRequest requete) {
        String cloudflare = premiereValeurUtile(requete.getHeader("CF-Connecting-IP"));
        if (cloudflare != null) {
            return cloudflare;
        }

        String transmise = requete.getHeader("X-Forwarded-For");
        if (transmise != null && !transmise.isBlank()) {
            String[] entrees = transmise.split(",");
            // La dernière entrée non vide : celle qu'un proxy a constatée, non celle que le
            // client a pu inventer.
            for (int i = entrees.length - 1; i >= 0; i--) {
                String candidate = premiereValeurUtile(entrees[i]);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        String directe = requete.getRemoteAddr();
        return directe == null ? "inconnue" : directe;
    }

    /**
     * Nettoie et borne une valeur d'en-tête, ou {@code null} si elle ne sert à rien.
     *
     * <p>La borne de 64 caractères n'est pas cosmétique : cette valeur devient une <b>clé de
     * carte</b>, et rien n'empêche un client d'envoyer un en-tête de plusieurs kilo-octets —
     * ce qui ferait du compteur de protection un moyen de consommer la mémoire.</p>
     */
    private String premiereValeurUtile(String brute) {
        if (brute == null) {
            return null;
        }
        String nettoyee = brute.trim();
        if (nettoyee.isEmpty()) {
            return null;
        }
        return nettoyee.length() > 64 ? nettoyee.substring(0, 64) : nettoyee;
    }

    private void refuser(HttpServletRequest requete, HttpServletResponse reponse,
                         PlafondDebit.VerdictPlafond verdict) throws IOException {
        if (verdict.plafondAtteint() == PlafondDebit.TypePlafond.GLOBAL_JOURNALIER) {
            log.warn("Requête refusée sur {} : plafond global journalier atteint.", requete.getRequestURI());
        }

        reponse.setStatus(429);
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        // Un client correct qui lit Retry-After revient au bon moment ; sans cet en-tête, un
        // plafond produit des réessais immédiats, c'est-à-dire exactement ce qu'il veut éviter.
        reponse.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(verdict.secondesAvantReouverture()));
        reponse.getWriter().write(corpsJson(message(verdict.plafondAtteint()),
                verdict.secondesAvantReouverture()));
    }

    /**
     * Le message lu par le visiteur.
     *
     * <p>Aucun des trois ne dit pourquoi le refus arrive réellement. C'est délibéré pour le
     * plafond global : annoncer « le budget du jour est épuisé » apprend à qui cherche
     * exactement combien d'appels suffisent à couper l'assistant pour tout le monde.</p>
     */
    private String message(PlafondDebit.TypePlafond type) {
        return switch (type) {
            case ADRESSE_PAR_MINUTE -> "Trop de messages, réessayez dans quelques minutes.";
            case ADRESSE_PAR_JOUR -> "Vous avez atteint le nombre de messages possibles pour aujourd'hui. "
                    + "L'assistant sera de nouveau disponible demain. Pour une question urgente, "
                    + "écrivez-nous à infos@optimisante.com.";
            case GLOBAL_JOURNALIER -> "L'assistant est momentanément indisponible. "
                    + "Réessayez plus tard, ou écrivez-nous à infos@optimisante.com.";
        };
    }

    /**
     * Le corps de la réponse, écrit à la main plutôt que sérialisé.
     *
     * <p>Un filtre refuse des requêtes parce que quelque chose va mal : il ne doit dépendre de
     * rien. Les messages sont des constantes de ce fichier, sans guillemet ni antislash, donc
     * sans échappement à faire — mais la fonction le fait quand même, pour que l'ajout d'une
     * apostrophe typographique un jour ne produise pas un JSON cassé.</p>
     */
    private String corpsJson(String message, long secondes) {
        return "{\"message\":\"" + echapper(message) + "\",\"reessayerDansSecondes\":" + secondes + "}";
    }

    private String echapper(String texte) {
        return texte.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
