package com.optimisante.backend.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Contrôle d'un document déposé")
class ControleFichierTest {

    private static MockMultipartFile fichier(String nom, String type) {
        return new MockMultipartFile("file", nom, type, "contenu".getBytes());
    }

    @Test
    @DisplayName("accepte un PDF, un JPG et un PNG cohérents")
    void accepteLesFormatsAttendus() {
        assertThatCode(() -> ControleFichier.verifierDocument(fichier("diplome.pdf", "application/pdf")))
                .doesNotThrowAnyException();
        assertThatCode(() -> ControleFichier.verifierDocument(fichier("photo.JPG", "image/jpeg")))
                .doesNotThrowAnyException();
        assertThatCode(() -> ControleFichier.verifierDocument(fichier("scan.jpeg", "image/jpeg")))
                .doesNotThrowAnyException();
        assertThatCode(() -> ControleFichier.verifierDocument(fichier("visa.png", "image/png")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("accepte un type annoncé avec ses paramètres")
    void accepteUnTypeParametre() {
        assertThatCode(() -> ControleFichier.verifierDocument(fichier("scan.pdf", "application/pdf; charset=binary")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("refuse un PDF renommé en .png")
    void refuseUnFichierRenomme() {
        assertThatThrownBy(() -> ControleFichier.verifierDocument(fichier("piege.png", "application/pdf")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".png")
                .hasMessageContaining("application/pdf");
    }

    @Test
    @DisplayName("refuse une extension hors liste, quel que soit le type annoncé")
    void refuseUneExtensionHorsListe() {
        assertThatThrownBy(() -> ControleFichier.verifierDocument(fichier("script.svg", "image/svg+xml")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ControleFichier.verifierDocument(fichier("charge.html", "image/png")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ControleFichier.verifierDocument(fichier("sans-extension", "application/pdf")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refuse un fichier vide ou trop lourd")
    void refuseLesTaillesImpossibles() {
        assertThatThrownBy(() -> ControleFichier.verifierDocument(
                new MockMultipartFile("file", "vide.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Aucun fichier");
        assertThatThrownBy(() -> ControleFichier.verifierDocument(new MockMultipartFile(
                "file", "gros.pdf", "application/pdf", new byte[(int) ControleFichier.TAILLE_MAX_OCTETS + 1])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 Mo");
    }

    @Test
    @DisplayName("ne se laisse pas tromper par un chemin complet venu du poste client")
    void litLaVraieExtension() {
        assertThatCode(() -> ControleFichier.verifierDocument(
                fichier("C:\\Users\\moi\\Mes documents\\diplome.pdf", "application/pdf")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ControleFichier.verifierDocument(
                fichier("C:\\dossier.pdf\\charge.exe", "application/pdf")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
