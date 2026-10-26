package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.domain.catalog.entity.MotifMouvementStock;
import com.optimisante.backend.domain.catalog.entity.MouvementStock;
import com.optimisante.backend.domain.catalog.entity.Product;
import com.optimisante.backend.domain.catalog.repository.MouvementStockRepository;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.domain.notification.entity.NotificationSeverity;
import com.optimisante.backend.domain.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Le service par lequel toute variation de stock doit passer.
 *
 * <p>Ce qu'on vérifie ici n'est pas l'arithmétique, mais les trois promesses qui rendent le
 * chiffre exploitable : la somme des mouvements égale le stock, un inventaire se saisit en
 * quantité comptée, et l'alerte part au franchissement du seuil — pas à chaque vente qui
 * suit.</p>
 */
class StockServiceTest {

    private ProductRepository produits;
    private MouvementStockRepository mouvements;
    private NotificationService notifications;
    private StockService service;
    private List<MouvementStock> journal;

    private static final UUID PRODUIT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        produits = mock(ProductRepository.class);
        mouvements = mock(MouvementStockRepository.class);
        notifications = mock(NotificationService.class);
        UserRepository utilisateurs = mock(UserRepository.class);
        service = new StockService(produits, mouvements, utilisateurs, notifications);

        journal = new ArrayList<>();
        when(mouvements.save(any())).thenAnswer(i -> {
            journal.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(produits.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Product produitAvec(int stock, int seuil) {
        Product p = Product.builder().name("Tensiomètre").stockQuantity(stock).stockThreshold(seuil).build();
        p.setId(PRODUIT);
        when(produits.findByIdWithPessimisticLock(PRODUIT)).thenReturn(Optional.of(p));
        when(produits.findById(PRODUIT)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    @DisplayName("une vente retire la quantité et journalise le stock obtenu")
    void venteRetireEtJournalise() {
        Product p = produitAvec(20, 5);
        UUID commande = UUID.randomUUID();

        assertThat(service.sortieVente(PRODUIT, 3, commande)).isEqualTo(17);
        assertThat(p.getStockQuantity()).isEqualTo(17);

        assertThat(journal).singleElement().satisfies(m -> {
            assertThat(m.getDelta()).isEqualTo(-3);
            assertThat(m.getStockApres()).isEqualTo(17);
            assertThat(m.getMotif()).isEqualTo(MotifMouvementStock.VENTE);
            assertThat(m.getOrderId()).isEqualTo(commande);
        });
    }

    @Test
    @DisplayName("le stock ne descend jamais sous zéro, et le journal dit ce qui a vraiment été retiré")
    void planchezAZeroEtJournalFidele() {
        Product p = produitAvec(2, 0);

        assertThat(service.sortieVente(PRODUIT, 5, null)).isZero();
        assertThat(p.getStockQuantity()).isZero();

        // Journaliser -5 ferait mentir la somme des mouvements, qui doit toujours égaler le
        // stock courant : c'est cette égalité qui permet de vérifier un historique.
        assertThat(journal).singleElement()
                .extracting(MouvementStock::getDelta, MouvementStock::getStockApres)
                .containsExactly(-2, 0);
    }

    @Test
    @DisplayName("un inventaire pose la quantité comptée, quel que soit l'écart")
    void inventairePoseLaQuantiteComptee() {
        Product p = produitAvec(50, 5);

        assertThat(service.inventorier(PRODUIT, 12, null, "comptage annuel")).isEqualTo(12);
        assertThat(p.getStockQuantity()).isEqualTo(12);
        assertThat(p.getStockInventorieLe()).isNotNull();

        assertThat(journal).singleElement()
                .extracting(MouvementStock::getDelta, MouvementStock::getMotif)
                .containsExactly(-38, MotifMouvementStock.INVENTAIRE);
    }

    @Test
    @DisplayName("compter et retrouver le même chiffre date quand même l'inventaire")
    void comptageConformeDateLInventaire() {
        Product p = produitAvec(12, 5);

        assertThat(service.inventorier(PRODUIT, 12, null, null)).isEqualTo(12);

        // Sans ce cas, confirmer un stock juste n'aurait aucun effet : le produit resterait
        // marqué « jamais inventorié » alors que quelqu'un vient de le compter.
        assertThat(p.getStockInventorieLe()).isNotNull();
        assertThat(journal).singleElement()
                .extracting(MouvementStock::getDelta, MouvementStock::getStockApres)
                .containsExactly(0, 12);
    }

    @Test
    @DisplayName("une quantité comptée négative est refusée")
    void refuseUneQuantiteNegative() {
        produitAvec(10, 5);
        assertThatThrownBy(() -> service.inventorier(PRODUIT, -1, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("l'alerte part au franchissement du seuil")
    void alerteAuFranchissement() {
        produitAvec(6, 5);

        service.sortieVente(PRODUIT, 2, null);

        ArgumentCaptor<String> titre = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyAdmins(eq("STOCK_CRITIQUE"), eq(NotificationSeverity.WARNING),
                titre.capture(), anyString(), eq("/admin/stock"), isNull(),
                eq("STOCK_CRITIQUE:" + PRODUIT));
        assertThat(titre.getValue()).isEqualTo("Stock critique");
    }

    @Test
    @DisplayName("une vente sur un produit déjà sous le seuil ne réalerte pas")
    void pasDAlerteRepeteeSousLeSeuil() {
        produitAvec(3, 5);

        service.sortieVente(PRODUIT, 1, null);

        // Alerter à chaque mouvement d'un produit déjà critique produirait une notification
        // par vente : le signal se noierait en une journée.
        verify(notifications, never()).notifyAdmins(anyString(), any(), anyString(), anyString(),
                anyString(), any(), anyString());
    }

    @Test
    @DisplayName("tomber à zéro est signalé comme une rupture, pas comme un simple seuil")
    void ruptureEstCritique() {
        produitAvec(2, 5);

        service.sortieVente(PRODUIT, 2, null);

        verify(notifications).notifyAdmins(eq("STOCK_CRITIQUE"), eq(NotificationSeverity.CRITICAL),
                eq("Rupture de stock"), anyString(), anyString(), isNull(), anyString());
    }

    @Test
    @DisplayName("un seuil à zéro fait taire l'avertissement, jamais la rupture")
    void seuilNulNeFaitTaireQueLAvertissement() {
        produitAvec(4, 0);

        service.sortieVente(PRODUIT, 1, null);
        // Pas de seuil, donc rien à franchir : aucun avertissement.
        verify(notifications, never()).notifyAdmins(anyString(), any(), anyString(), anyString(),
                anyString(), any(), anyString());

        service.sortieVente(PRODUIT, 3, null);
        // Mais atteindre zéro rend le produit invendable, et c'est exactement le defaut qu'on
        // cherche a ne plus subir en silence : dix fauteuils l'etaient depuis l'import sans
        // que personne le sache. Un seuil a zero dit « ne me previens pas d'un creux », pas
        // « laisse-moi decouvrir la rupture par un client qui ne peut pas commander ».
        verify(notifications).notifyAdmins(eq("STOCK_CRITIQUE"), eq(NotificationSeverity.CRITICAL),
                eq("Rupture de stock"), anyString(), anyString(), isNull(), anyString());
    }

    @Test
    @DisplayName("un retour d'annulation remet la quantité en stock")
    void annulationRemetEnStock() {
        Product p = produitAvec(10, 5);
        UUID commande = UUID.randomUUID();

        assertThat(service.retourAnnulation(PRODUIT, 4, commande, null)).isEqualTo(14);
        assertThat(p.getStockQuantity()).isEqualTo(14);
        assertThat(journal).singleElement()
                .extracting(MouvementStock::getDelta, MouvementStock::getMotif)
                .containsExactly(4, MotifMouvementStock.ANNULATION);
    }

    @Test
    @DisplayName("une variation nulle n'écrit rien")
    void variationNulleNEcritRien() {
        produitAvec(10, 5);

        assertThat(service.appliquer(PRODUIT, 0, MotifMouvementStock.CORRECTION, null, null, null))
                .isEqualTo(10);
        assertThat(journal).isEmpty();
        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("une alerte qui échoue ne fait pas échouer la vente")
    void laVeilleNeCassePasLaVente() {
        Product p = produitAvec(6, 5);
        doThrow(new RuntimeException("serveur de notifications indisponible"))
                .when(notifications).notifyAdmins(anyString(), any(), anyString(), anyString(),
                        anyString(), any(), anyString());

        assertThat(service.sortieVente(PRODUIT, 2, null)).isEqualTo(4);
        assertThat(p.getStockQuantity()).isEqualTo(4);
    }
}
