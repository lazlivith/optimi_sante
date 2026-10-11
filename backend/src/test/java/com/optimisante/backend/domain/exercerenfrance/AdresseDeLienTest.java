package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.domain.exercerenfrance.service.AdresseDeLien;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Ce qu'une adresse de lien officiel a le droit d'être.
 *
 * <p>Ces adresses sont saisies dans l'administration et rendues au visiteur dans un
 * {@code <a href>}. Une adresse {@code javascript:} s'exécute au clic, dans la session du
 * visiteur et depuis notre domaine : c'est une injection de script qui passe par un champ de
 * formulaire plutôt que par une faille technique.</p>
 */
class AdresseDeLienTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.cng.sante.fr/session-evc-2026",
            "http://exemple.fr/page",
            "HTTPS://MAJUSCULES.FR",
            "https://exemple.fr/chemin?a=1&b=2#ancre",
    })
    @DisplayName("une adresse http ou https passe, inchangée")
    void adressesAcceptees(String url) {
        assertThat(AdresseDeLien.verifier(url)).isEqualTo(url.trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "  javascript:alert(1)  ",
            // Les espaces et caractères de contrôle sont ignorés par le navigateur à
            // l'analyse de l'adresse : « java\nscript: » s'exécute. Le contrôle porte donc
            // sur la chaîne compactée, et non sur ce qui est écrit.
            "java\nscript:alert(1)",
            "java\tscript:alert(1)",
            "jav\u0000ascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox(1)",
            "file:///etc/passwd",
            "//exemple.fr/sans-protocole",
            "exemple.fr",
    })
    @DisplayName("tout ce qui n'est pas http(s) est refusé")
    void adressesRefusees(String url) {
        assertThatThrownBy(() -> AdresseDeLien.verifier(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }

    @Test
    @DisplayName("une adresse absente devient « à compléter », et la page ne l'affiche pas")
    void adresseAbsente() {
        assertThat(AdresseDeLien.verifier(null)).isEqualTo(AdresseDeLien.A_COMPLETER);
        assertThat(AdresseDeLien.verifier("   ")).isEqualTo(AdresseDeLien.A_COMPLETER);
        assertThat(AdresseDeLien.verifier("A_COMPLETER")).isEqualTo(AdresseDeLien.A_COMPLETER);
    }
}
