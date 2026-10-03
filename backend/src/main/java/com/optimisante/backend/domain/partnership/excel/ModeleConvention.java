package com.optimisante.backend.domain.partnership.excel;

import java.util.List;

/**
 * Le contenu de la convention de partenariat : ses articles, et ce que le centre doit remplir.
 *
 * <p><b>Pourquoi le texte vit ici et non dans le code qui met en forme.</b> Même partage que
 * {@link ModeleDossier} : une clause corrigée doit l'être à un seul endroit. Le jour où la
 * convention sera aussi relue par le serveur — pour pré-remplir un dossier à partir du fichier
 * déposé —, la lecture s'appuiera sur cette même description plutôt que sur des numéros de
 * ligne recopiés.</p>
 *
 * <p><b>Rien de l'identité d'Optimi Santé n'est écrit ici.</b> Dénomination, capital, SIRET,
 * adresse et IBAN viennent de la configuration ({@code app.legal}), comme sur toutes les pièces
 * émises. Une convention qui porterait une immatriculation recopiée finirait par contredire les
 * reçus et les devis — c'est précisément le défaut qui faisait imprimer un IBAN inventé au pied
 * des documents.</p>
 */
public final class ModeleConvention {

    private ModeleConvention() {}

    /**
     * Version du modèle, inscrite dans la feuille masquée.
     *
     * <p>À incrémenter dès qu'une clause ou un champ change : un exemplaire signé sur une
     * version antérieure n'engage pas sur le même texte, et il faut pouvoir le constater.</p>
     */
    public static final String VERSION = "1";

    public static final String FEUILLE_CONVENTION = "Convention";
    public static final String FEUILLE_SAISIE = "À remplir";
    public static final String FEUILLE_TECHNIQUE = "_modele";

    /** Un champ que le centre renseigne : libellé, aide de saisie, et s'il est exigé. */
    public record Champ(String cle, String libelle, boolean obligatoire, String aide) {}

    /** Un bloc de champs, avec son intitulé. */
    public record Bloc(String titre, List<Champ> champs) {}

    /**
     * Ce que le centre complète.
     *
     * <p>Groupé par nature plutôt qu'en une liste unique : un responsable administratif remplit
     * l'identité, un service comptable les coordonnées bancaires. Les deux n'ont pas à se lire
     * l'un l'autre pour trouver leur partie.</p>
     */
    public static final List<Bloc> BLOCS = List.of(
            new Bloc("Identité de l'établissement", List.of(
                    new Champ("raisonSociale", "Raison sociale / Nom de l'institution", true,
                            "Dénomination officielle, telle qu'elle figurera sur la convention."),
                    new Champ("formeJuridique", "Forme juridique", true,
                            "Ex. : établissement public de santé, SAS, association."),
                    new Champ("capitalSocial", "Capital social", false,
                            "Le cas échéant. Laissez vide si sans objet."),
                    new Champ("finess", "N° FINESS", false,
                            "9 chiffres. Laissez vide si votre établissement n'en dispose pas."),
                    new Champ("siret", "N° SIRET ou immatriculation", true,
                            "Pour un établissement hors de France, l'immatriculation équivalente."),
                    new Champ("agrement", "N° d'agrément / déclaration d'activité", false,
                            "Déclaration d'activité de formation, si vous en détenez une."),
                    new Champ("adresse", "Adresse du siège social", true,
                            "Voie, code postal, ville, pays."))),

            new Bloc("Représentation et contact", List.of(
                    new Champ("representantNom", "Représentant légal — nom et prénom", true,
                            "La personne habilitée à engager l'établissement."),
                    new Champ("representantFonction", "Représentant légal — fonction", true,
                            "Ex. : directeur général, président."),
                    new Champ("referentNom", "Contact référent — nom et prénom", true,
                            "La personne que nous joindrons au quotidien."),
                    new Champ("referentFonction", "Contact référent — fonction", false,
                            "Ex. : responsable des affaires médicales."),
                    new Champ("telephone", "Téléphone direct", true,
                            "Avec l'indicatif international si hors France."),
                    new Champ("email", "Adresse e-mail de contact", true,
                            "Adresse à laquelle nous répondrons."))),

            new Bloc("Coordonnées bancaires du reversement", List.of(
                    new Champ("titulaire", "Titulaire du compte", true,
                            "Tel qu'il figure sur le relevé d'identité bancaire."),
                    new Champ("iban", "IBAN", true,
                            "Sans espaces. C'est le compte que nous créditerons."),
                    new Champ("bic", "BIC", false,
                            "Facultatif en zone SEPA, requis hors SEPA."))),

            new Bloc("Signature", List.of(
                    new Champ("faitA", "Fait à", true, "Ville de signature."),
                    new Champ("faitLe", "Le", true, "Date de signature, au format JJ/MM/AAAA."),
                    new Champ("signataireNom", "Nom du signataire", true,
                            "Doit correspondre au représentant légal ci-dessus."),
                    new Champ("signataireQualite", "Qualité du signataire", true,
                            "La fonction au titre de laquelle il signe."))));

    /** Un article de la convention : son intitulé et ses alinéas. */
    public record Article(String titre, List<String> alineas) {}

    /**
     * Le corps de la convention.
     *
     * <p>Les montants n'y sont pas écrits : la commission d'agence et les tarifs relèvent des
     * conditions particulières, qui se négocient. Une convention cadre qui fige un taux oblige à
     * la resigner pour le changer.</p>
     */
    public static final List<Article> ARTICLES = List.of(
            new Article("Préambule", List.of(
                    "Optimi Santé édite une plateforme numérique destinée à faciliter la mise en "
                    + "relation, la gestion administrative et l'accueil de professionnels de santé "
                    + "souhaitant effectuer des stages cliniques et des formations au sein "
                    + "d'établissements de santé partenaires.",
                    "Le Partenaire dispose des moyens humains et matériels nécessaires à l'accueil "
                    + "et à l'encadrement de ces professionnels, et souhaite ouvrir ses sessions de "
                    + "formation aux candidats présentés par Optimi Santé.")),

            new Article("Article 1 — Objet de la convention", List.of(
                    "La présente convention définit les conditions juridiques, financières et "
                    + "organisationnelles selon lesquelles le Partenaire accueille des "
                    + "professionnels de santé candidats via la plateforme Optimi Santé, publie ses "
                    + "sessions de formation et assure leur suivi administratif et pédagogique.",
                    "Elle constitue une convention cadre. Chaque session donne lieu à une "
                    + "convention tripartite distincte, signée par Optimi Santé, le Partenaire et "
                    + "le stagiaire.")),

            new Article("Article 2 — Engagements du Partenaire", List.of(
                    "2.1 Accueil et encadrement — Accueillir les professionnels de santé "
                    + "sélectionnés au sein de ses structures, pour les durées prévues aux "
                    + "programmes de formation, et en assurer l'encadrement.",
                    "2.2 Publication des offres — Tenir à jour, sur son Espace Partenaire, la liste "
                    + "de ses sessions, le nombre de places ouvertes et les conditions d'admission.",
                    "2.3 Gestion administrative — Examiner les candidatures transmises et fournir, "
                    + "dans un délai de cinq (5) jours ouvrés, les pièces requises : conventions "
                    + "tripartites, attestations d'accueil, justificatifs nécessaires à l'obtention "
                    + "des visas.",
                    "2.4 Conformité réglementaire — Garantir, au sein de son établissement, le "
                    + "respect des règles d'hygiène, de sécurité et du secret médical.")),

            new Article("Article 3 — Engagements d'Optimi Santé", List.of(
                    "3.1 Promotion — Assurer la promotion des sessions du Partenaire auprès de son "
                    + "réseau de médecins et de professionnels de santé.",
                    "3.2 Présélection — Vérifier les dossiers de candidature — identité, diplômes, "
                    + "prérequis — avant toute transmission au Partenaire.",
                    "3.3 Accès à l'outil — Mettre à disposition un accès sécurisé à l'Espace "
                    + "Partenaire pour la gestion des candidatures et le suivi des sessions.")),

            new Article("Article 4 — Dispositions financières", List.of(
                    "4.1 Tarifs — Les frais de chaque formation ou stage sont fixés par le "
                    + "Partenaire et affichés sur la plateforme.",
                    "4.2 Rémunération d'Optimi Santé — Optimi Santé retient une commission "
                    + "d'agence sur les sommes encaissées au titre des sessions. Son taux est "
                    + "porté aux conditions particulières et rappelé dans l'Espace Partenaire. Le "
                    + "taux appliqué à une session est celui en vigueur à son encaissement ; sa "
                    + "renégociation ultérieure ne modifie pas les sessions déjà réglées.",
                    "4.3 Reversement — Les sommes dues au Partenaire sont virées sur le compte "
                    + "bancaire renseigné à la présente convention, déduction faite de la "
                    + "commission mentionnée ci-dessus.",
                    "4.4 Échéance — Le paiement intervient sous trente (30) jours à compter de la "
                    + "réception de la facture correspondante et de la confirmation d'assiduité du "
                    + "candidat.")),

            new Article("Article 5 — Durée et résiliation", List.of(
                    "5.1 Durée — La présente convention est conclue pour une durée indéterminée à "
                    + "compter de sa signature par les deux Parties.",
                    "5.2 Résiliation — Chaque Partie peut y mettre fin à tout moment, par lettre "
                    + "recommandée avec accusé de réception ou notification électronique "
                    + "confirmée, sous réserve d'un préavis écrit de trois (3) mois.",
                    "5.3 Sessions en cours — En cas de résiliation, le Partenaire s'engage à mener "
                    + "à leur terme les formations déjà validées et engagées pour les candidats en "
                    + "cours de cursus.")),

            new Article("Article 6 — Protection des données personnelles", List.of(
                    "Les Parties s'engagent à respecter le règlement (UE) 2016/679 relatif à la "
                    + "protection des données à caractère personnel.",
                    "Les données collectées sur les candidats ne sont transmises que pour les "
                    + "besoins stricts de l'exécution de l'accueil et des démarches "
                    + "administratives qui s'y rattachent. Chaque Partie en répond pour les "
                    + "traitements qu'elle met en œuvre, et met fin à leur conservation dès que la "
                    + "finalité qui les justifiait a cessé.",
                    "Les Parties se tiennent informées sans délai de tout incident affectant ces "
                    + "données.")),

            new Article("Article 7 — Confidentialité", List.of(
                    "Chaque Partie s'interdit de divulguer les informations de toute nature dont "
                    + "elle aurait connaissance à l'occasion de l'exécution de la présente "
                    + "convention, et ce pendant toute sa durée et les deux (2) années qui suivent "
                    + "son terme.")),

            new Article("Article 8 — Droit applicable et juridiction", List.of(
                    "La présente convention est régie par le droit français.",
                    "Tout litige relatif à son exécution ou à son interprétation sera soumis, à "
                    + "défaut d'accord amiable, aux tribunaux compétents du ressort du siège social "
                    + "d'Optimi Santé.")));

    /** Le mode d'emploi, en tête du classeur : trois gestes, dans l'ordre. */
    public static final List<String> MODE_EMPLOI = List.of(
            "1. Lisez la convention ci-dessous. Elle n'est pas modifiable : seules les cases "
            + "jaunes de l'onglet « À remplir » le sont.",
            "2. Complétez l'onglet « À remplir ». Les champs marqués d'une étoile sont exigés.",
            "3. Imprimez, signez, apposez le cachet de l'établissement, puis déposez le document "
            + "signé sur la page « Devenir centre partenaire ».");
}
