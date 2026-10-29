package com.optimisante.backend.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Contrôle d'un document déposé par un utilisateur, avant tout envoi au stockage.
 *
 * <p>Un seul endroit pour la règle — PDF, JPG ou PNG, 10 Mo au plus — et pour ses messages :
 * deux écrans qui refusent le même fichier doivent dire la même chose.</p>
 *
 * <p><b>L'extension et le type annoncé doivent concorder.</b> Les deux viennent du poste qui
 * dépose, donc aucun n'est une preuve ; les exiger cohérents ferme la voie la plus simple, celle
 * du fichier renommé pour passer le filtre. Ce que le fichier contient vraiment n'est vérifié
 * qu'à la lecture, par qui s'en sert — ici rien n'est exécuté ni servi depuis le serveur.</p>
 */
public final class ControleFichier {

    public static final long TAILLE_MAX_OCTETS = 10L * 1024 * 1024;

    /** Extension déposée → types que le poste a le droit d'annoncer pour elle. */
    private static final Map<String, Set<String>> FORMATS_ACCEPTES = Map.of(
            "pdf", Set.of("application/pdf"),
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"));

    private static final String FORMATS_ATTENDUS = "Format non accepté : déposez un PDF, un JPG ou un PNG.";

    private ControleFichier() {
    }

    private static final String BROCHURE_ATTENDUE = "Format non accepté : la brochure doit être un PDF.";

    /**
     * Contrôle d'une brochure de formation : PDF seul, 10 Mo au plus, et l'en-tête vérifié.
     *
     * <p><b>Pourquoi une règle à part et plus stricte.</b> Une brochure n'est pas une pièce
     * justificative : son contenu est extrait, structuré, puis réédité. Une image n'a rien à
     * extraire — l'accepter ouvrirait un chemin sans issue, où le partenaire dépose un fichier
     * que personne ne saura traiter. Et ce PDF-là est lu par nos serveurs, pas seulement
     * conservé : savoir qu'il commence bien par {@code %PDF-} cesse d'être cosmétique.</p>
     *
     * @throws IllegalArgumentException avec un message à montrer tel quel à l'utilisateur
     */
    public static void verifierBrochure(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier reçu.");
        }
        if (fichier.getSize() > TAILLE_MAX_OCTETS) {
            throw new IllegalArgumentException("Le fichier dépasse 10 Mo.");
        }
        if (!"pdf".equals(extension(fichier.getOriginalFilename()))) {
            throw new IllegalArgumentException(BROCHURE_ATTENDUE);
        }
        String type = typeAnnonce(fichier.getContentType());
        if (!type.isEmpty() && !"application/pdf".equals(type)) {
            throw new IllegalArgumentException(
                    "Ce fichier porte l'extension « .pdf » mais se présente comme « " + type
                            + " ». " + BROCHURE_ATTENDUE);
        }
        // Le seul des quatre contrôles qui porte sur le contenu, et donc le seul qui prouve
        // quelque chose : les trois précédents jugent ce que le poste a déclaré.
        SignatureFichier.exigerPdf(fichier);
    }

    /** @throws IllegalArgumentException avec un message à montrer tel quel à l'utilisateur */
    public static void verifierDocument(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier reçu.");
        }
        if (fichier.getSize() > TAILLE_MAX_OCTETS) {
            throw new IllegalArgumentException("Le fichier dépasse 10 Mo.");
        }

        String extension = extension(fichier.getOriginalFilename());
        Set<String> attendus = FORMATS_ACCEPTES.get(extension);
        if (attendus == null) {
            throw new IllegalArgumentException(FORMATS_ATTENDUS);
        }

        String type = typeAnnonce(fichier.getContentType());
        if (!attendus.contains(type)) {
            throw new IllegalArgumentException(
                    "Ce fichier porte l'extension « ." + extension + " » mais se présente comme « "
                            + (type.isEmpty() ? "type inconnu" : type) + " ». " + FORMATS_ATTENDUS);
        }
    }

    /** Extension en minuscules, sans le point ; chaîne vide s'il n'y en a pas. */
    private static String extension(String nomFichier) {
        if (nomFichier == null) {
            return "";
        }
        // Le chemin complet arrive parfois du poste client : seul le dernier segment compte.
        String nom = nomFichier.replace('\\', '/');
        nom = nom.substring(nom.lastIndexOf('/') + 1);
        int point = nom.lastIndexOf('.');
        return point < 0 ? "" : nom.substring(point + 1).toLowerCase(Locale.ROOT).trim();
    }

    /** Type MIME sans ses paramètres : {@code image/jpeg; charset=binary} vaut {@code image/jpeg}. */
    private static String typeAnnonce(String contentType) {
        if (contentType == null) {
            return "";
        }
        int pointVirgule = contentType.indexOf(';');
        String type = pointVirgule < 0 ? contentType : contentType.substring(0, pointVirgule);
        return type.trim().toLowerCase(Locale.ROOT);
    }
}
