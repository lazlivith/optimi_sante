package com.optimisante.backend.domain.notification;

import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.notification.entity.AlertThreshold;
import com.optimisante.backend.domain.notification.entity.NotificationSeverity;
import com.optimisante.backend.domain.notification.repository.AlertThresholdRepository;
import com.optimisante.backend.domain.notification.service.NotificationAlertJob;
import com.optimisante.backend.domain.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Une alerte part chez quelqu'un.
 *
 * <p>La veille par seuils visait le seul rôle {@code ADMIN}. Sur une plateforme pilotée par un
 * unique {@code SUPER_ADMIN}, elle ne notifiait donc personne, sans erreur ni trace : un
 * système d'alerte qui n'alerte personne, et dont on se croit couvert.</p>
 */
class AlertesOntUnDestinataireTest {

    private static AlertThreshold seuil(String cle) {
        AlertThreshold seuil = mock(AlertThreshold.class);
        when(seuil.getKey()).thenReturn(cle);
        when(seuil.getLabel()).thenReturn("Libellé de " + cle);
        when(seuil.getSeverity()).thenReturn(NotificationSeverity.values()[0]);
        when(seuil.getThresholdValue()).thenReturn(1);
        when(seuil.getWindowHours()).thenReturn(24);
        return seuil;
    }

    @Test
    @DisplayName("un seuil atteint notifie ADMIN et SUPER_ADMIN, jamais le seul rôle ADMIN")
    void seuilAtteint() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AlertThresholdRepository seuils = mock(AlertThresholdRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        // Construits avant le when : un bouchon ne se prépare pas pendant qu'on en pose un autre.
        List<AlertThreshold> actifs = List.of(
                seuil("REPORT_FAILURE"), seuil("ORDER_UNPAID_STALE"),
                seuil("ENROLLMENT_STALE"), seuil("STOCK_CRITIQUE"));
        when(seuils.findByEnabledTrue()).thenReturn(actifs);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(3);

        new NotificationAlertJob(jdbc, seuils, notifications).runProbes();

        for (String cle : List.of("REPORT_FAILURE", "ORDER_UNPAID_STALE", "ENROLLMENT_STALE", "STOCK_CRITIQUE")) {
            verify(notifications).notifyAdmins(eq(cle), any(), anyString(), anyString(), anyString(), any(), eq(cle));
        }
        verify(notifications, never()).notifyRole(any(Role.class), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("sous le seuil, personne n'est notifié")
    void sousLeSeuil() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AlertThresholdRepository seuils = mock(AlertThresholdRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        List<AlertThreshold> actifs = List.of(seuil("ORDER_UNPAID_STALE"));
        when(seuils.findByEnabledTrue()).thenReturn(actifs);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        new NotificationAlertJob(jdbc, seuils, notifications).runProbes();

        verify(notifications, never()).notifyAdmins(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("aucun code ne vise plus le seul rôle ADMIN pour une notification")
    void aucunAppelAuSeulRoleAdmin() throws IOException {
        // NotificationService est le seul endroit légitime : notifyAdmins y appelle notifyRole
        // pour ADMIN puis pour SUPER_ADMIN. Partout ailleurs, c'est le piège que sa propre
        // documentation décrit, et dans lequel la veille par seuils était tombée.
        Path sources = Path.of("src/main/java");
        assertThat(sources).isDirectory();
        try (Stream<Path> fichiers = Files.walk(sources)) {
            List<String> fautifs = fichiers
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.getFileName().toString().equals("NotificationService.java"))
                    .filter(f -> {
                        try {
                            return Files.readString(f).replaceAll("\\s+", "").contains("notifyRole(Role.ADMIN,");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(f -> f.getFileName().toString())
                    .toList();
            assertThat(fautifs).as("fichiers qui notifient le seul rôle ADMIN").isEmpty();
        }
    }
}
