package com.optimisante.backend.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Ce qu'une adresse saisie dans l'administration a le droit d'être.
 *
 * <p>Deux champs la concernent, et tous deux finissent dans un {@code <a href>} :
 * l'adresse de la vidéo d'un produit, rendue sur une <b>fiche publique</b>, et le lien de
 * réunion d'un entretien, saisi par un <b>partenaire extérieur</b> à la maison.</p>
 */
class AdresseWebTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://teams.microsoft.com/l/meetup-join/abc",
            "http://meet.jit.si/salle",
            "HTTPS://WWW.YOUTUBE.COM/watch?v=abc",
            "https://exemple.fr/chemin?a=1&b=2#ancre",
    })
    @DisplayName("une adresse web ordinaire passe")
    void adressesAcceptees(String url) {
        assertThat(AdresseWeb.estSure(url)).isTrue();
        assertThat(AdresseWeb.exiger(url, "Lien")).isEqualTo(url.trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JavaScript:alert(document.cookie)",
            "  javascript:alert(1)  ",
            // Espaces et caractères de contrôle sont ignorés par le navigateur à l'analyse
            // d'une adresse : « java\nscript: » s'exécute. Vérifier ce qui est ÉCRIT, et non
            // ce qui sera INTERPRÉTÉ, ne protège de rien.
            "java\nscript:alert(1)",
            "java\tscript:alert(1)",
            "java\rscript:alert(1)",
            "jav\u0000ascript:alert(1)",
            "java script:alert(1)",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "vbscript:msgbox(1)",
            "file:///etc/passwd",
            "//exemple.fr/sans-protocole",
            "exemple.fr",
    })
    @DisplayName("tout ce qui n'est pas http(s) est refusé")
    void adressesRefusees(String url) {
        assertThat(AdresseWeb.estSure(url)).isFalse();
        assertThatThrownBy(() -> AdresseWeb.exiger(url, "Lien de la vidéo"))
                .isInstanceOf(IllegalArgumentException.class)
                // Le message nomme le champ : un administrateur qui remplit un formulaire
                // entier doit savoir LAQUELLE de ses saisies pose problème.
                .hasMessageContaining("Lien de la vidéo")
                .hasMessageContaining("http://");
    }

    @Test
    @DisplayName("une adresse absente n'est pas sûre : rien à mettre dans un href")
    void adresseAbsente() {
        assertThat(AdresseWeb.estSure(null)).isFalse();
        assertThat(AdresseWeb.estSure("")).isFalse();
        assertThat(AdresseWeb.estSure("   ")).isFalse();
    }
}
