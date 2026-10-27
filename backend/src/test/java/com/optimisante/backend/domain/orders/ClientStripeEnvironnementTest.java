package com.optimisante.backend.domain.orders;

import com.optimisante.backend.domain.finance.Devise;
import com.optimisante.backend.domain.finance.Montant;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.entity.User;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.domain.orders.service.EnvironnementStripe;
import com.optimisante.backend.domain.orders.service.PaiementIndisponibleException;
import com.optimisante.backend.domain.orders.service.ServiceClientStripe;
import com.optimisante.backend.domain.orders.service.StripePaymentService;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un identifiant client Stripe n'a de sens que dans l'environnement qui l'a créé.
 *
 * <p><b>La panne.</b> Le 27 octobre, un changement d'environnement de test a rendu invalides
 * tous les {@code cus_…} déjà en base. La plateforme les renvoyait tels quels, Stripe répondait
 * {@code No such customer}, et plus aucun paiement n'aboutissait pour ces comptes. Le passage
 * en mode réel aurait produit la même panne sur <b>tous</b> les utilisateurs à la fois, le jour
 * de la mise en vente.</p>
 *
 * <p><b>Pourquoi ces tests n'appellent pas Stripe.</b> Reproduire {@code resource_missing}
 * demande un client réellement supprimé chez Stripe : impossible à tenir dans une suite, et
 * dépendant du réseau. Le service de paiement est donc simulé, et c'est lui qui décide de la
 * réponse. Ce qui est éprouvé ici est notre réaction, qui est tout l'objet du correctif.</p>
 */
class ClientStripeEnvironnementTest {

    private StripePaymentService stripe;
    private UserRepository utilisateurs;
    private ServiceClientStripe service;

    /** Le compte et le mode sous lesquels tourne la plateforme dans ces tests. */
    private static final String MODE_COURANT = EnvironnementStripe.MODE_TEST;
    private static final String COMPTE_COURANT = "acct_courant";

    @BeforeEach
    void preparer() {
        stripe = Mockito.mock(StripePaymentService.class);
        utilisateurs = Mockito.mock(UserRepository.class);
        Mockito.when(utilisateurs.save(Mockito.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new ServiceClientStripe(stripe, environnement(MODE_COURANT, COMPTE_COURANT), utilisateurs);
    }

    /**
     * Un {@link EnvironnementStripe} déjà résolu.
     *
     * <p>Le vrai lit la clé et interroge Stripe au démarrage ; on court-circuite les deux, sans
     * quoi chaque test exigerait un réseau et une clé valide.</p>
     */
    private EnvironnementStripe environnement(String mode, String compte) {
        return new EnvironnementStripe() {
            @Override
            public String mode() {
                return mode;
            }

            @Override
            public String compte() {
                return compte;
            }
        };
    }

    private User utilisateur(String identifiant, String mode, String compte) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("client@essai.test")
                .passwordHash("x")
                .role(Role.CLIENT_B2C)
                .stripeCustomerId(identifiant)
                .stripeCustomerMode(mode)
                .stripeCustomerCompte(compte)
                .build();
    }

    private Montant montant() {
        return Montant.de(new BigDecimal("120.00"), Devise.de("eur"));
    }

    private Session sessionFactice() {
        return new Session();
    }

    /** L'erreur exacte lue dans les journaux Render le 27 octobre. */
    private InvalidRequestException clientIntrouvable(String identifiant) {
        return new InvalidRequestException(
                "No such customer: '" + identifiant + "'",
                "customer", null, "resource_missing", 400, null);
    }

    private Session ouvrir(User u) {
        return service.ouvrirSession(u, UUID.randomUUID(), montant(),
                "https://exemple.test/retour", "Commande d'essai", Map.of());
    }

    // ── Ce qui se répare tout seul ──────────────────────────────────────────────────────

    @Test
    @DisplayName("un client inconnu chez Stripe est recréé, et la session est créée")
    void clientRecreePuisSessionCreee() throws Exception {
        User u = utilisateur("cus_mort", MODE_COURANT, COMPTE_COURANT);
        AtomicInteger appels = new AtomicInteger();

        Mockito.when(stripe.createElementsCheckoutSessionForCustomer(
                        Mockito.any(), Mockito.any(Montant.class), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    String client = invocation.getArgument(2);
                    appels.incrementAndGet();
                    if ("cus_mort".equals(client)) {
                        throw clientIntrouvable("cus_mort");
                    }
                    return sessionFactice();
                });
        Mockito.when(stripe.createCustomer(Mockito.anyString())).thenReturn("cus_neuf");

        assertThat(ouvrir(u)).as("le paiement aboutit malgré l'identifiant mort").isNotNull();
        assertThat(appels.get()).as("une tentative, puis une reprise").isEqualTo(2);
        assertThat(u.getStripeCustomerId()).isEqualTo("cus_neuf");
        // Les trois champs ensemble : un identifiant sans provenance serait de nouveau
        // invérifiable, et on retomberait dans le défaut qu'on vient de corriger.
        assertThat(u.getStripeCustomerMode()).isEqualTo(MODE_COURANT);
        assertThat(u.getStripeCustomerCompte()).isEqualTo(COMPTE_COURANT);
        Mockito.verify(utilisateurs).save(u);
    }

    @Test
    @DisplayName("on ne boucle jamais : une seule nouvelle tentative, puis on renonce")
    void uneSeuleReprise() throws Exception {
        User u = utilisateur("cus_mort", MODE_COURANT, COMPTE_COURANT);
        AtomicInteger sessions = new AtomicInteger();
        AtomicInteger clientsCrees = new AtomicInteger();

        // Stripe répond « introuvable » quoi qu'on lui envoie : le pire cas, celui où une
        // reprise naïve créerait un client de plus à chaque tour.
        Mockito.when(stripe.createElementsCheckoutSessionForCustomer(
                        Mockito.any(), Mockito.any(Montant.class), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    sessions.incrementAndGet();
                    throw clientIntrouvable(invocation.getArgument(2));
                });
        Mockito.when(stripe.createCustomer(Mockito.anyString()))
                .thenAnswer(invocation -> "cus_" + clientsCrees.incrementAndGet());

        assertThatThrownBy(() -> ouvrir(u))
                .as("la panne est annoncée comme telle, pas déguisée en requête invalide")
                .isInstanceOf(PaiementIndisponibleException.class);

        assertThat(sessions.get()).as("deux appels au plus, jamais trois").isEqualTo(2);
        assertThat(clientsCrees.get())
                .as("un seul client créé chez Stripe : sans cela, chaque clic en créerait un")
                .isEqualTo(1);
    }

    // ── Ce qui n'arrive jamais jusqu'à Stripe ───────────────────────────────────────────

    @Test
    @DisplayName("un identifiant d'un autre mode est ignoré, sans même appeler Stripe avec lui")
    void autreModeIgnore() throws Exception {
        // L'identifiant vient du mode réel, la plateforme tourne en test.
        User u = utilisateur("cus_live", EnvironnementStripe.MODE_LIVE, COMPTE_COURANT);
        Map<String, Integer> vus = new HashMap<>();

        Mockito.when(stripe.createElementsCheckoutSessionForCustomer(
                        Mockito.any(), Mockito.any(Montant.class), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    vus.merge(invocation.getArgument(2), 1, Integer::sum);
                    return sessionFactice();
                });
        Mockito.when(stripe.createCustomer(Mockito.anyString())).thenReturn("cus_neuf");

        assertThat(ouvrir(u)).isNotNull();
        assertThat(vus)
                .as("l'identifiant étranger ne doit pas même être tenté : c'est ce qui évite la panne")
                .doesNotContainKey("cus_live")
                .containsKey("cus_neuf");
        assertThat(u.getStripeCustomerMode()).isEqualTo(MODE_COURANT);
    }

    @Test
    @DisplayName("un identifiant d'un autre compte, dans le même mode, est ignoré aussi")
    void autreCompteIgnore() throws Exception {
        User u = utilisateur("cus_ailleurs", MODE_COURANT, "acct_un_autre");
        Map<String, Integer> vus = new HashMap<>();

        Mockito.when(stripe.createElementsCheckoutSessionForCustomer(
                        Mockito.any(), Mockito.any(Montant.class), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    vus.merge(invocation.getArgument(2), 1, Integer::sum);
                    return sessionFactice();
                });
        Mockito.when(stripe.createCustomer(Mockito.anyString())).thenReturn("cus_neuf");

        ouvrir(u);
        assertThat(vus).doesNotContainKey("cus_ailleurs").containsKey("cus_neuf");
    }

    @Test
    @DisplayName("un identifiant d'avant la migration, sans provenance, est remplacé")
    void provenanceInconnueRemplacee() throws Exception {
        // Tous les identifiants déjà en base sont dans ce cas : nul ne sait d'où ils viennent.
        User u = utilisateur("cus_ancien", null, null);
        Map<String, Integer> vus = new HashMap<>();

        Mockito.when(stripe.createElementsCheckoutSessionForCustomer(
                        Mockito.any(), Mockito.any(Montant.class), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    vus.merge(invocation.getArgument(2), 1, Integer::sum);
                    return sessionFactice();
                });
        Mockito.when(stripe.createCustomer(Mockito.anyString())).thenReturn("cus_neuf");

        ouvrir(u);
        assertThat(vus)
                .as("une provenance inconnue n'est pas une provenance valable")
                .doesNotContainKey("cus_ancien");
    }

    @Test
    @DisplayName("un identifiant de l'environnement courant est réutilisé tel quel")
    void identifiantValableReutilise() throws Exception {
        // Le cas normal, et le plus important : réutiliser le client, c'est ce qui permet au
        // visiteur de retrouver la carte qu'il a enregistrée la fois précédente.
        User u = utilisateur("cus_bon", MODE_COURANT, COMPTE_COURANT);
        Map<String, Integer> vus = new HashMap<>();

        Mockito.when(stripe.createElementsCheckoutSessionForCustomer(
                        Mockito.any(), Mockito.any(Montant.class), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    vus.merge(invocation.getArgument(2), 1, Integer::sum);
                    return sessionFactice();
                });

        ouvrir(u);
        assertThat(vus).containsOnlyKeys("cus_bon");
        Mockito.verify(stripe, Mockito.never()).createCustomer(Mockito.anyString());
        Mockito.verify(utilisateurs, Mockito.never()).save(Mockito.any(User.class));
    }

    // ── La lecture du mode sur la clé ───────────────────────────────────────────────────

    @Test
    @DisplayName("le mode se lit sur la clé, et une clé inconnue ne se devine pas")
    void modeDeLaCle() {
        assertThat(EnvironnementStripe.modeDeLaCle("sk_test_abc")).isEqualTo(EnvironnementStripe.MODE_TEST);
        assertThat(EnvironnementStripe.modeDeLaCle("sk_live_abc")).isEqualTo(EnvironnementStripe.MODE_LIVE);
        assertThat(EnvironnementStripe.modeDeLaCle("rk_live_abc")).isEqualTo(EnvironnementStripe.MODE_LIVE);
        // Ni l'un ni l'autre : on ne devine pas, et l'identifiant sera donc remplacé. Perdre une
        // carte enregistrée coûte moins cher qu'un paiement impossible.
        assertThat(EnvironnementStripe.modeDeLaCle("bouchon")).isNull();
        assertThat(EnvironnementStripe.modeDeLaCle(null)).isNull();
    }

    @Test
    @DisplayName("quand notre compte est illisible, le mode seul tranche")
    void compteIllisible() throws Exception {
        // Account.retrieve() a échoué au démarrage : exiger que le compte corresponde
        // rejetterait alors TOUS les identifiants, y compris les bons.
        ServiceClientStripe sansCompte =
                new ServiceClientStripe(stripe, environnement(MODE_COURANT, null), utilisateurs);
        User u = utilisateur("cus_bon", MODE_COURANT, "acct_quelconque");
        Map<String, Integer> vus = new HashMap<>();

        Mockito.when(stripe.createElementsCheckoutSessionForCustomer(
                        Mockito.any(), Mockito.any(Montant.class), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    vus.merge(invocation.getArgument(2), 1, Integer::sum);
                    return sessionFactice();
                });

        sansCompte.ouvrirSession(u, UUID.randomUUID(), montant(),
                "https://exemple.test/retour", "Commande d'essai", Map.of());
        assertThat(vus).containsOnlyKeys("cus_bon");
    }

    // ── Ce qui n'est pas un identifiant mort ────────────────────────────────────────────

    @Test
    @DisplayName("une panne qui n'est pas un client introuvable n'entraîne aucune reprise")
    void panneGeneraleSansReprise() throws Exception {
        User u = utilisateur("cus_bon", MODE_COURANT, COMPTE_COURANT);
        AtomicInteger sessions = new AtomicInteger();

        Mockito.when(stripe.createElementsCheckoutSessionForCustomer(
                        Mockito.any(), Mockito.any(Montant.class), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    sessions.incrementAndGet();
                    throw new InvalidRequestException("Montant invalide", "amount", null,
                            "parameter_invalid_integer", 400, null);
                });

        assertThatThrownBy(() -> ouvrir(u)).isInstanceOf(PaiementIndisponibleException.class);
        assertThat(sessions.get())
                .as("recréer le client ne réparerait rien ici : une seule tentative")
                .isEqualTo(1);
        Mockito.verify(stripe, Mockito.never()).createCustomer(Mockito.anyString());
    }

    @Test
    @DisplayName("le repère d'un client introuvable ne dépend pas du seul message")
    void repereDuClientIntrouvable() {
        assertThat(ServiceClientStripe.ressourceIntrouvable(clientIntrouvable("cus_x"))).isTrue();
        assertThat(ServiceClientStripe.ressourceIntrouvable(
                new InvalidRequestException("Montant invalide", "amount", null,
                        "parameter_invalid_integer", 400, null)))
                .isFalse();
        // Une session absente porte le même code : c'est voulu, la page de retour s'en sert.
        assertThat(ServiceClientStripe.ressourceIntrouvable(
                new InvalidRequestException("No such checkout.session: 'cs_x'", "session", null,
                        "resource_missing", 400, null)))
                .isTrue();
    }
}
