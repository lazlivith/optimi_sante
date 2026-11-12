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
     * L'adresse du visiteur, telle qu'on peut la connaître derrière le proxy de Render.
     *
     * <p><b>Et une limite qu'il faut énoncer plutôt que masquer.</b> {@code getRemoteAddr()}
     * rend ici l'adresse du proxy, identique pour tout le monde : s'en servir ferait d'un
     * plafond « par adresse » un second plafond global. Il faut donc lire
     * {@code X-Forwarded-For}, dont le premier élément est l'adresse du client telle que le
     * proxy l'a vue.</p>
     *
     * <p>Cet en-tête est <b>falsifiable</b> : un attaquant qui le renseigne lui-même obtient un
     * compteur neuf à chaque requête, et contourne les deux plafonds par adresse. C'est une
     * faiblesse connue et acceptée ici, pour une raison précise : <b>le plafond global, lui,
     * ne se contourne pas</b>. Il ne dépend d'aucune donnée fournie par le client, et c'est lui
     * qui borne la dépense. Les plafonds par adresse protègent le service des maladresses et
     * des scripts naïfs ; le plafond global protège la facture.</p>
     */
    private String adresseDuVisiteur(HttpServletRequest requete) {
        String transmise = requete.getHeader("X-Forwarded-For");
        if (transmise != null && !transmise.isBlank()) {
            String premiere = transmise.split(",")[0].trim();
            if (!premiere.isEmpty()) {
                // Borner la longueur : cette valeur devient une clé de carte, et rien
                // n'empêche un client d'envoyer un en-tête de plusieurs kilo-octets.
                return premiere.length() > 64 ? premiere.substring(0, 64) : premiere;
            }
        }
        String directe = requete.getRemoteAddr();
        return directe == null ? "inconnue" : directe;
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
