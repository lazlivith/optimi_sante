package com.optimisante.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une base neuve, migrée comme en production, ne contient aucun compte ni aucune donnée fictive.
 *
 * <p><b>Ce que ce test empêche.</b> Les migrations {@code V5} et {@code V11} insèrent des
 * formations fictives et cinq comptes de test, dont un {@code SUPER_ADMIN} actif portant le mot de
 * passe partagé par les cinq. Tant qu'elles vivaient dans {@code db/migration}, toute base neuve
 * les recevait — la production comprise. Elles sont désormais dans {@code db/demo}, que seul le
 * profil {@code dev} charge.</p>
 *
 * <p>Ce test rejoue exactement ce que fera la production : les seuls emplacements de
 * {@code application-test.yml}, sur une base vide. Remettre l'une de ces migrations dans
 * {@code db/migration} le ferait échouer ici, et non le jour de la mise en ligne.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class BaseNeuveSansDonneesDeDemoTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long compte(String table) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return n == null ? -1 : n;
    }

    @Test
    @DisplayName("aucun compte n'existe, donc aucun administrateur au mot de passe connu")
    void aucunCompte() {
        assertThat(compte("users")).isZero();
    }

    @Test
    @DisplayName("aucune formation ni session fictive n'est publiée")
    void aucuneDonneeFictive() {
        assertThat(compte("trainings")).as("formations").isZero();
        assertThat(compte("training_sessions")).as("sessions").isZero();
        assertThat(compte("partner_profiles")).as("établissements partenaires").isZero();
    }

    @Test
    @DisplayName("le tenant, lui, est bien créé : la plateforme a besoin du sien")
    void leTenantExiste() {
        // V4 n'est pas une donnée de démonstration : sans tenant, aucune écriture n'est possible.
        assertThat(compte("tenants")).isPositive();
    }

    @Test
    @DisplayName("le schéma est complet : le catalogue existe, seulement vide")
    void schemaComplet() {
        assertThat(compte("products")).as("produits").isZero();
        assertThat(compte("categories")).as("catégories").isZero();
        assertThat(compte("enrollments")).as("dossiers").isZero();
        assertThat(compte("orders")).as("commandes").isZero();
    }
}
