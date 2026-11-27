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

    /**
     * Borne la taille d'un fichier, <b>et rien d'autre</b>.
     *
     * <p>Pour les routes qui n'avaient aucune limite à elles. Y brancher {@link #verifierDocument}
     * aurait aussi imposé un format : une règle plus stricte posée d'un coup refuserait des
     * fichiers qui passaient hier, sans que personne ait mesuré lesquels. La taille, elle, ne
     * refuse rien de légitime.</p>
     *
     * @param objet ce que le fichier est pour l'utilisateur, avec son article : « La vidéo »
     * @throws IllegalArgumentException avec un message à montrer tel quel à l'utilisateur
     */
    public static void verifierTaille(MultipartFile fichier, long maximumOctets, String objet) {
        if (fichier != null && fichier.getSize() > maximumOctets) {
            throw new IllegalArgumentException(
                    objet + " dépasse " + (maximumOctets / (1024 * 1024)) + " Mo.");
        }
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

    /** Le modèle de dossier pèse quelques dizaines de kilo-octets : deux mégaoctets sont larges. */
    public static final long TAILLE_MAX_CLASSEUR_OCTETS = 2L * 1024 * 1024;

    private static final String CONVENTION_ATTENDUE =
            "Format non accepté : la convention doit être un PDF, un DOC ou un DOCX.";

    private static final String CLASSEUR_ATTENDU =
            "Format non accepté : déposez le modèle de dossier au format Excel (.xlsx).";

    /**
     * Contrôle de la convention signée, déposée <b>par un anonyme</b> depuis la page publique.
     *
     * <p>Les formats sont ceux que le formulaire proposait déjà — PDF, DOC, DOCX. Ce qui change
     * est que le serveur les vérifie : jusqu'ici la liste n'existait que dans l'attribut
     * {@code accept} du navigateur, qui n'est qu'une suggestion, et n'importe quel fichier de
     * 55 Mo partait au stockage.</p>
     *
     * <p>Le type annoncé n'est pas exigé pour les formats Office : un poste sans Word annonce
     * {@code application/octet-stream}, ou rien. C'est le contenu qui tranche.</p>
     *
     * @throws IllegalArgumentException avec un message à montrer tel quel à l'utilisateur
     */
    public static void verifierConvention(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("La convention de partenariat signée est obligatoire.");
        }
        if (fichier.getSize() > TAILLE_MAX_OCTETS) {
            throw new IllegalArgumentException("La convention dépasse 10 Mo.");
        }
        boolean reconnu = switch (extension(fichier.getOriginalFilename())) {
            case "pdf" -> SignatureFichier.estPdf(fichier);
            case "docx" -> SignatureFichier.estZip(fichier);
            case "doc" -> SignatureFichier.estOle2(fichier);
            default -> throw new IllegalArgumentException(CONVENTION_ATTENDUE);
        };
        if (!reconnu) {
            throw new IllegalArgumentException(
                    "Le contenu de ce fichier ne correspond pas à son extension. "
                            + "Renommer un fichier ne change pas ce qu'il contient. " + CONVENTION_ATTENDUE);
        }
    }

    /**
     * Contrôle du classeur de dossier soumis à la relecture, lui aussi par un anonyme.
     *
     * <p>Ce fichier-là est <b>ouvert et décompressé</b> par le serveur. Le plafond de taille
     * n'est donc pas une politesse : c'est ce qui borne la mémoire qu'un inconnu peut nous
     * faire consommer par requête.</p>
     *
     * @throws IllegalArgumentException avec un message à montrer tel quel à l'utilisateur
     */
    public static void verifierClasseurDossier(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier reçu.");
        }
        if (fichier.getSize() > TAILLE_MAX_CLASSEUR_OCTETS) {
            throw new IllegalArgumentException(
                    "Le fichier dépasse 2 Mo : ce n'est pas le modèle de dossier téléchargé ici.");
        }
        if (!"xlsx".equals(extension(fichier.getOriginalFilename())) || !SignatureFichier.estZip(fichier)) {
            throw new IllegalArgumentException(CLASSEUR_ATTENDU);
        }
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
