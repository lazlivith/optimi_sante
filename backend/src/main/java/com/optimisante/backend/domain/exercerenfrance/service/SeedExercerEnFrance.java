package com.optimisante.backend.domain.exercerenfrance.service;

import com.optimisante.backend.config.tenant.TenantParDefaut;
import com.optimisante.backend.domain.exercerenfrance.entity.EvcContenu;
import com.optimisante.backend.domain.exercerenfrance.entity.EvcReglage;
import com.optimisante.backend.domain.exercerenfrance.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Contenu initial de « Exercer en France ».
 *
 * <p><b>Il crée, il ne modifie jamais.</b> Chaque élément est posé seulement s'il n'existe pas
 * déjà, reconnu par sa clé. Relancé — et il l'est à chaque démarrage — il ne fait rien.
 * Écraser serait bien pire que ne rien faire : la page est destinée à être corrigée par
 * l'administration, et un redémarrage effacerait son travail sans prévenir, sans trace, et
 * sans que personne ne fasse le lien entre les deux.</p>
 *
 * <p>Cette règle vaut aussi pour la publication : la version 1 n'est posée que si aucune
 * version n'existe. Sinon, chaque redémarrage empilerait une version identique à la
 * précédente, et l'historique deviendrait illisible là où il doit servir de preuve.</p>
 *
 * <p><b>Les textes sont ici, et c'est leur seul séjour.</b> Une fois posés, ils vivent en base
 * et se modifient depuis l'administration. Ce fichier n'est pas la source de vérité — il est
 * le point de départ.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(100)
public class SeedExercerEnFrance implements ApplicationRunner {

    private final DepotSections sections;
    private final DepotPeriodes periodes;
    private final DepotLiens liens;
    private final DepotSpecialites specialites;
    private final DepotOffres offres;
    private final DepotPublications publications;
    private final DepotReglages reglages;
    private final TenantParDefaut tenantParDefaut;
    private final ServicePublicationEvc publication;

    /** Les cinq adresses relevées sur le site du CNG le 11/10/2026. */
    private static final String CNG_EXERCER =
            "https://www.cng.sante.fr/procedures-dautorisation-dexercice/venir-exercer-se-former-en-france/exercer-se-former-en-france";
    private static final String CNG_SESSION_EVC = "https://www.cng.sante.fr/session-evc-2026";
    private static final String CNG_CAE =
            "https://www.cng.sante.fr/procedures-dautorisation-dexercice/obtenir-autorisation-dexercice/commission-dautorisation-dexercice-cae";
    private static final String CNG_FELLOWSHIP =
            "https://www.cng.sante.fr/procedures-dautorisation-dexercice/venir-exercer-se-former-en-france/fellowship-autorisation-temporaire-dexercice";
    private static final String RENOUVELLEMENT_AEP =
            "https://demarche.numerique.gouv.fr/commencer/renouvellement-autorisation-exercice-provisoire-padhue";

    @Override
    public void run(ApplicationArguments args) {
        if (dejaApplique()) {
            log.info("« Exercer en France » : contenu initial déjà posé le {}, rien à faire.",
                    marqueur());
            return;
        }
        // Base DÉJÀ GARNIE, mais sans marqueur : c'est une base remplie avant que le marqueur
        // n'existe. Y repasser reposerait ce que l'administration avait supprimé entre-temps —
        // exactement ce que le marqueur est censé empêcher, et la seule occasion où il ne le
        // pourrait pas. On se contente donc de l'inscrire.
        if (dejaGarnie()) {
            marquerApplique();
            log.info("« Exercer en France » : contenu déjà présent, marqueur posé sans rien "
                     + "ajouter ni republier.");
            return;
        }
        int poses = garnir();
        boolean publiee = publierSiAucuneVersion();
        marquerApplique();
        log.info("« Exercer en France » : {} élément(s) posé(s), publication initiale {}.",
                poses, publiee ? "effectuée" : "déjà faite");
    }

    @Transactional(readOnly = true)
    public boolean dejaApplique() {
        return reglages.findById(EvcReglage.SEED_APPLIQUE_LE).isPresent();
    }

    /**
     * La page a-t-elle déjà un contenu ?
     *
     * <p>Les sections suffisent à trancher : elles sont la matière de la page, et aucun
     * contenu n'a de sens sans elles. Compter toutes les tables rendrait la réponse
     * dépendante d'un détail — une spécialité supprimée ferait conclure à une base vierge.</p>
     */
    @Transactional(readOnly = true)
    public boolean dejaGarnie() {
        return sections.count() > 0;
    }

    private String marqueur() {
        return reglages.findById(EvcReglage.SEED_APPLIQUE_LE)
                .map(EvcReglage::getValeur).orElse("?");
    }

    /**
     * Inscrit que le contenu initial a été posé.
     *
     * <p><b>C'est ce marqueur qui rend une suppression définitive.</b> Sans lui, le seed
     * comblait les trous à chaque démarrage : une section retirée par l'administration
     * revenait au déploiement suivant, et il aurait fallu la supprimer après chaque mise en
     * ligne. Pour retirer un élément de la page sans le détruire, l'administration dispose
     * de l'interrupteur « visible ».</p>
     */
    @Transactional
    public void marquerApplique() {
        reglages.save(EvcReglage.builder()
                .cle(EvcReglage.SEED_APPLIQUE_LE)
                .valeur(LocalDate.now().toString())
                .modifieLe(OffsetDateTime.now())
                .build());
    }

    @Transactional
    public int garnir() {
        return section("bandeau_non_officiel", null, "Optimi Santé n'est pas un organisme officiel", 0, """
                Les demandes se déposent auprès du Centre National de Gestion (CNG) ou des \
                Agences Régionales de Santé (ARS). Nous vous aidons à comprendre les règles et \
                à préparer un dossier complet.""")

             + section("evc", "ÉTAPE 1", "Les épreuves de vérification des connaissances (EVC)", 1, """
                Les EVC sont la première étape de la procédure d'autorisation d'exercice. Elles \
                concernent les praticiens de toute nationalité dont le diplôme a été obtenu hors \
                Union européenne et hors Espace économique européen, et qui leur permet d'exercer \
                dans leur pays de délivrance. À l'issue de la procédure, les praticiens qui \
                obtiennent l'autorisation de plein exercice peuvent exercer leur profession en \
                France.""")

             + section("aep", "EXERCER PENDANT LA PROCÉDURE", "L'attestation d'exercice provisoire (AEP)", 2, """
                Cadre fixé par le décret n° 2024-1191 du 19 décembre 2024 (articles L. 4111-2-1 \
                et L. 4221-12-1 du code de la santé publique). Seules les demandes déposées \
                pendant les périodes officielles sont traitées.

                ## Renouvellement après les EVC 2025
                Titulaires d'une AEP ayant échoué à la session 2025 des EVC, ou n'ayant pas pu s'y \
                présenter : formulaire spécifique, sans restriction de calendrier.

                ## Où exercer comme PACT ?
                Établissements publics de santé, établissements de santé privés à but non \
                lucratif, établissements et services sociaux ou médico-sociaux.

                ## Spécialités relevant des ARS
                Les autres spécialités relèvent des ARS : consultez les dates de l'ARS concernée.""")

             + section("dossier", "À LIRE AVANT DE DÉPOSER", "Bien constituer son dossier", 3, """
                Un dossier mal renseigné peut entraîner le rejet de la demande. La recevabilité \
                d'un dossier ne garantit pas que les conditions d'encadrement sont suffisamment \
                détaillées.

                ## 01 — Profession et spécialité exactes
                Pas de demande en médecine pour un pharmacien biologiste ou un chirurgien-dentiste. \
                Un chirurgien-dentiste sans spécialité demande en omnipratique, et non sous \
                l'intitulé du service d'accueil.

                ## 02 — Conditions d'encadrement précises
                Décrites dans la profession et la spécialité du praticien accueilli, de préférence \
                rédigées par le chef de service.

                ## 03 — Pièces complémentaires
                CV, activité en France le cas échéant, lettre de recommandation, DU ou FMC suivis. \
                Chirurgiens-dentistes : bilan d'activité, AFGSU, formation à la radioprotection.

                > Composition réglementaire : articles R. 4111-13-8-3 (médecins, sages-femmes, \
                chirurgiens-dentistes) et R. 4221-13-4-3 (pharmaciens) du code de la santé publique.""")

             + section("cae", "ÉTAPE 3", "Commission d'autorisation d'exercice", 4, """
                Pour exercer en France avec un diplôme étranger, ou pour exercer les fonctions de \
                biologiste médical, une demande est déposée auprès de la commission d'autorisation \
                d'exercice. Une fois l'autorisation obtenue, le praticien s'inscrit au tableau de \
                l'Ordre compétent et peut exercer à l'hôpital comme en libéral.

                ## Calendrier des commissions
                Publié et mis à jour par le CNG. Les dates ne sont communiquées que sur le site \
                officiel.

                Odontologie : des commissions sont souvent programmées vers septembre-octobre. \
                Le candidat est contacté lors de l'instruction de son dossier.""")

             + section("autres_voies", null, "Autres voies", 5, """
                ## Fellowship / autorisation temporaire
                Pour parfaire sa formation médicale en France, dans le cadre du dispositif proposé \
                par le ministère de la Santé.

                ## Praticiens réfugiés
                Les praticiens réfugiés politiques, apatrides, bénéficiaires de l'asile territorial \
                ou de la protection subsidiaire, ainsi que les Français ayant rejoint le territoire \
                national à la demande des autorités françaises, relèvent de la liste B des épreuves \
                de vérification des connaissances.

                Les épreuves sont les mêmes que celles de la liste A, notées sur 20 avec les mêmes \
                coefficients. La différence tient au mode d'admission : la liste A est un concours \
                à nombre de places limité, tandis que la liste B est un examen où seule la moyenne \
                aux épreuves est nécessaire, sans classement. Les lauréats de la liste B ne sont \
                pas tenus de choisir une affectation dans la liste de postes.

                Une note inférieure ou égale à 6 sur 20 à l'une des épreuves reste éliminatoire, \
                sur les deux listes.

                Le statut doit être justifié par un document officiel, délivré par l'OFPRA. Les \
                Français rapatriés doivent établir leur retour dans les trois mois suivant la \
                consigne des autorités.

                Les conditions exactes et les justificatifs demandés sont précisés sur la page \
                officielle de la session EVC.""")

             + section("accompagnements", null, "Nos accompagnements", 6, """
                Optimi Santé ne garantit pas l'issue des épreuves ni des décisions \
                administratives, qui relèvent des autorités compétentes.""")

             + periode("1re période 2026", LocalDate.of(2026, 1, 15), LocalDate.of(2026, 4, 30), null, 0)
             + periode("2nde période 2026", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 1), """
                Chirurgiens-dentistes ; chirurgie pédiatrique ; chirurgie esthétique, plastique \
                et reconstructrice ; néphrologie ; chirurgie viscérale et digestive""", 1)
             // Dates vides : la page affichera « À venir ». Le CNG ne les a pas publiées.
             + periode("Périodes 2027", null, null, null, 2)

             + lien("site_cng", "Site officiel du CNG", CNG_EXERCER)
             + lien("session_evc", "Prochaine session EVC", CNG_SESSION_EVC)
             + lien("formulaire_renouvellement_aep", "Formulaire de renouvellement (AEP)", RENOUVELLEMENT_AEP)
             + lien("calendrier_commissions", "Voir le calendrier officiel", CNG_CAE)
             + lien("fellowship", "En savoir plus (site officiel)", CNG_FELLOWSHIP)
             // Le CNG n'a aucune page dédiée aux réfugiés : le dispositif n'est décrit qu'au
             // sein de la page EVC. Même adresse que session_evc, donc, plutôt qu'un lien mort.
             + lien("refugies", "Dispositions spécifiques (site officiel)", CNG_SESSION_EVC)

             + professions() + medecine()

             // CE QUE NOUS FAISONS, ET CE QUE NOUS NE FAISONS PAS. Optimi Santé informe,
             // oriente, vérifie et suit. Elle n'assure NI préparation pédagogique (QCM,
             // examens blancs, classes virtuelles), NI traduction, NI assurance, NI logement.
             // Les premiers textes promettaient les quatre : un praticien qui paie pour une
             // banque de QCM et reçoit une orientation a été trompé, et il l'a payé.
             + offre("preparation_evc", "AVANT LES EVC", "Orientation EVC", "Être orienté", 0,
                     prix("89"), null, null, """
                Vérification de votre éligibilité
                Explication de la procédure et du calendrier
                Aide à l'inscription à la session
                Orientation vers des ressources de préparation""")
             + offre("dossier_demarches", "APRÈS LA RÉUSSITE", "Dossier & démarches", "Faire relire mon dossier", 1,
                     prix("450"), null, null, """
                Vérification de la complétude du dossier
                Relecture des conditions d'encadrement
                Liste personnalisée des pièces à fournir
                Suivi jusqu'à la commission""")
             + offre("mobilite_installation", "ARRIVÉE EN FRANCE", "Mobilité & installation", "Préparer mon arrivée", 2,
                     prix("650"), null, null, """
                Accompagnement des démarches de visa et de titre de séjour
                Conseils pour le logement et l'assurance
                Mise en relation avec des établissements d'accueil
                Démarches auprès de l'Ordre""")

             // Le pack reprend les points des deux offres qu'il réunit, sans en ajouter : ce
             // qu'il vend est le prix, pas une prestation de plus.
             + offre("pack_dossier_mobilite", "LE PARCOURS COMPLET", "Pack Dossier + Mobilité",
                     "Choisir le pack", 3, prix("990"), prix("1100"), "Le plus complet", """
                Vérification de la complétude du dossier
                Relecture des conditions d'encadrement
                Liste personnalisée des pièces à fournir
                Suivi jusqu'à la commission
                Accompagnement des démarches de visa et de titre de séjour
                Conseils pour le logement et l'assurance
                Mise en relation avec des établissements d'accueil
                Démarches auprès de l'Ordre""")

             // Porte d'entrée, et non prestation : jamais affichée dans « Nos
             // accompagnements ». Elle existe pour que les demandes d'alerte portent une clé,
             // et qu'on retrouve qui prévenir le jour où le CNG publie ses dates.
             + offreTechnique("alerte_aep", "Alerte d'ouverture AEP", "Être alerté à l'ouverture");
    }

    /**
     * Publie la version 1, et seulement si aucune n'existe.
     *
     * <p>Sans cette condition, chaque redémarrage ajouterait une version identique à la
     * précédente : l'historique, qui sert à établir qui a mis quoi en ligne et quand,
     * deviendrait un empilement sans information.</p>
     */
    @Transactional
    public boolean publierSiAucuneVersion() {
        if (publications.findFirstByOrderByVersionDesc().isPresent()) {
            return false;
        }
        publication.publier(null);
        return true;
    }

    // ── Poseurs : chacun rend 1 s'il a créé, 0 s'il a trouvé ────────────────────────────

    private int section(String cle, String surTitre, String titre, int ordre, String contenu) {
        if (sections.findByCle(cle).isPresent()) {
            return 0;
        }
        sections.save(EvcContenu.Section.builder()
                .cle(cle).surTitre(surTitre).titre(titre).contenu(contenu.strip())
                .ordre(ordre).visible(true).modifieLe(OffsetDateTime.now()).build());
        return 1;
    }

    /**
     * Les périodes n'ont pas de clé fonctionnelle : le libellé en tient lieu.
     *
     * <p>Il est stable — « 1re période 2026 » ne se renomme pas — et c'est ce que
     * l'administration lit. Introduire une clé technique invisible pour elle aurait créé un
     * second identifiant à tenir à jour, sans rien résoudre.</p>
     */
    private int periode(String libelle, LocalDate debut, LocalDate fin, String nonOuvertes, int ordre) {
        boolean existe = periodes.findAllByOrderByOrdreAsc().stream()
                .anyMatch(p -> p.getLibelle().equalsIgnoreCase(libelle));
        if (existe) {
            return 0;
        }
        periodes.save(EvcContenu.Periode.builder()
                .libelle(libelle).dateDebut(debut).dateFin(fin)
                .specialitesNonOuvertes(nonOuvertes == null ? null : nonOuvertes.strip())
                .sourceUrl(CNG_EXERCER).visible(true).ordre(ordre).build());
        return 1;
    }

    private int lien(String cle, String libelle, String url) {
        if (liens.findByCle(cle).isPresent()) {
            return 0;
        }
        // verifieLe à maintenant : ces cinq adresses viennent d'être ouvertes et relevées.
        // Les poser sans date les ferait aussitôt apparaître dans les rappels, et le premier
        // réflexe serait d'ignorer un rappel qui crie dès le premier jour.
        liens.save(EvcContenu.Lien.builder()
                .cle(cle).libelle(libelle).url(url).verifieLe(OffsetDateTime.now()).build());
        return 1;
    }

    private int professions() {
        return specialite("profession", List.of(
                "Chirurgiens-dentistes", "Sages-femmes", "Pharmaciens"));
    }

    private int medecine() {
        return specialite("medecine", List.of(
                "Allergologie", "Anatomie et cytologie pathologiques", "Biologie médicale",
                "Chirurgie maxillo-faciale", "Chirurgie orale", "Chirurgie pédiatrique",
                "Chirurgie plastique, reconstructrice et esthétique",
                "Chirurgie thoracique et cardio-vasculaire", "Chirurgie vasculaire",
                "Dermatologie et vénéréologie", "Endocrinologie-diabétologie-nutrition",
                "Génétique médicale", "Gynécologie médicale", "Hématologie",
                "Maladies infectieuses et tropicales", "Médecine et santé au travail",
                "Médecine intensive et réanimation", "Médecine interne et immunologie clinique",
                "Médecine légale et expertise médicale", "Médecine nucléaire",
                "Médecine physique et réadaptation", "Médecine vasculaire", "Néphrologie",
                "Neurochirurgie", "Oncologie", "Ophtalmologie",
                "ORL-chirurgie cervico-faciale", "Rhumatologie", "Santé publique", "Urologie"));
    }

    private int specialite(String groupe, List<String> libelles) {
        var dejaLa = specialites.findAllByOrderByGroupeAscOrdreAsc();
        int poses = 0;
        for (int i = 0; i < libelles.size(); i++) {
            String libelle = libelles.get(i);
            boolean existe = dejaLa.stream().anyMatch(s -> s.getGroupe().equals(groupe)
                    && s.getLibelle().equalsIgnoreCase(libelle));
            if (existe) {
                continue;
            }
            specialites.save(EvcContenu.Specialite.builder()
                    .groupe(groupe).libelle(libelle).ordre(i).build());
            poses++;
        }
        return poses;
    }

    /** Clé de l'offre rattachée aux demandes d'alerte d'ouverture d'une période AEP. */
    public static final String ALERTE_AEP = "alerte_aep";

    private int offreTechnique(String cle, String titre, String bouton) {
        var tenant = tenantParDefaut.identifiant();
        if (offres.findByTenantIdAndCle(tenant, cle).isPresent()) {
            return 0;
        }
        offres.save(EvcContenu.Offre.builder()
                .tenantId(tenant).cle(cle).titre(titre).libelleBouton(bouton)
                .prix((BigDecimal) null).devise("EUR")
                .actif(true).technique(true).ordre(99).build());
        return 1;
    }

    /** Un montant en euros, écrit tel qu'il sera affiché. */
    private static BigDecimal prix(String euros) {
        return new BigDecimal(euros);
    }

    /**
     * @param prix      montant TTC en euros ; nul = la page affiche « Sur devis »
     * @param prixBarre prix de référence affiché barré ; nul = aucune comparaison
     * @param badge     mention mise en avant sur la carte ; nulle = aucun badge
     */
    private int offre(String cle, String surTitre, String titre, String bouton, int ordre,
                      BigDecimal prix, BigDecimal prixBarre, String badge, String points) {
        var tenant = tenantParDefaut.identifiant();
        if (offres.findByTenantIdAndCle(tenant, cle).isPresent()) {
            return 0;
        }
        offres.save(EvcContenu.Offre.builder()
                .tenantId(tenant).cle(cle).surTitre(surTitre).titre(titre)
                .points(points.strip())
                .prix(prix).prixBarre(prixBarre).badge(badge)
                .devise("EUR").libelleBouton(bouton)
                .actif(true).ordre(ordre).build());
        return 1;
    }
}
