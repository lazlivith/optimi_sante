package com.optimisante.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Le contexte applicatif démarre, et les soixante migrations s'appliquent sur une base neuve.
 *
 * <p>Ce test ne demande rien au poste qui l'exécute : ni base lancée, ni variable d'environnement.
 * La base est un conteneur PostgreSQL éphémère ({@link ConfigurationBaseDeTest}), et les réglages
 * viennent de {@code application-test.yml}. C'est à cette condition qu'une chaîne d'intégration
 * peut l'exécuter.</p>
 *
 * <p>Il est ignoré, et non mis en échec, là où Docker n'est pas joignable : sinon on aurait
 * remplacé la dépendance à une variable d'environnement par une dépendance au démon Docker.</p>
 *
 * <p>Il vaut plus qu'un test de démarrage : {@code ddl-auto: validate} confronte chaque entité au
 * schéma produit par Flyway. Une colonne ajoutée à une entité sans migration échoue ici.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL éphémère.")
class BackendApplicationTests {

	@Test
	@DisplayName("le contexte démarre et le schéma correspond aux entités")
	void contextLoads() {
	}

}
