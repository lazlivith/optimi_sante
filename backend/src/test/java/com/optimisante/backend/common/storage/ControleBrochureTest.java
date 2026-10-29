package com.optimisante.backend.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ce qu'on accepte comme brochure de formation.
 *
 * <p><b>Pourquoi un contrôle de plus que {@link ControleFichierTest}.</b> La route de dépôt
 * de brochure n'appelait <b>aucun</b> contrôle : ni taille, ni extension, ni type. Et le
 * contrôle qui existait ailleurs ne juge que ce que le poste a déclaré — un exécutable
 * renommé {@code .pdf} et annoncé {@code application/pdf} le franchit. Ce PDF-là est lu par
 * nos serveurs : son en-tête doit être vérifié.</p>
 */
@DisplayName("Contrôle d'une brochure de formation")
class ControleBrochureTest {

    /** La brochure de test du dépôt, qui sert aussi aux lots suivants. */
    private static final Path BROCHURE_DE_TEST =
            Path.of("..", "docs", "brochures_test", "brochure_test_partenaire_CHU_Valbrune.pdf");

    private static MockMultipartFile fichier(String nom, String type, byte[] contenu) {
        return new MockMultipartFile("file", nom, type, contenu);
    }

    private static byte[] pdfMinimal() {
        return "%PDF-1.4\n%%EOF\n".getBytes();
    }

    @Test
    @DisplayName("accepte la brochure de test du dépôt")
    void accepteLaBrochureDeTest() throws IOException {
        // Le fichier est versionné : s'il manque, c'est le dépôt qui est en cause, pas la
        // règle — et le test doit le dire plutôt que de passer en silence.
        assertThat(Files.exists(BROCHURE_DE_TEST))
                .as("brochure de test attendue dans %s", BROCHURE_DE_TEST.toAbsolutePath())
                .isTrue();

        byte[] contenu = Files.readAllBytes(BROCHURE_DE_TEST);
        assertThatCode(() -> ControleFichier.verifierBrochure(
                fichier("brochure_chu_valbrune.pdf", "application/pdf", contenu)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("refuse un exécutable renommé en .pdf, même annoncé comme PDF")
    void refuseUnExecutableRenomme() {
        // « MZ » : l'en-tête de tout exécutable Windows. L'extension et le type annoncé sont
        // tous deux conformes — c'est exactement le fichier qui passait hier.
        byte[] executable = new byte[] {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

        assertThatThrownBy(() -> ControleFichier.verifierBrochure(
                fichier("brochure.pdf", "application/pdf", executable)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("%PDF-");
    }

    @Test
    @DisplayName("refuse un fichier de plus de 10 Mo")
    void refuseUnFichierTropLourd() {
        byte[] trop = new byte[(int) ControleFichier.TAILLE_MAX_OCTETS + 1];
        // Un en-tête valide : c'est bien la taille, et elle seule, qui doit faire refuser.
        System.arraycopy(pdfMinimal(), 0, trop, 0, pdfMinimal().length);

        assertThatThrownBy(() -> ControleFichier.verifierBrochure(
                fichier("brochure.pdf", "application/pdf", trop)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 Mo");
    }

    @Test
    @DisplayName("refuse une image, même valide : il n'y a rien à extraire d'une photo")
    void refuseUneImage() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

        assertThatThrownBy(() -> ControleFichier.verifierBrochure(
                fichier("brochure.png", "image/png", png)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    @DisplayName("refuse un fichier vide et un fichier absent")
    void refuseLeVide() {
        assertThatThrownBy(() -> ControleFichier.verifierBrochure(
                fichier("brochure.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ControleFichier.verifierBrochure(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la signature se lit sur les octets, pas sur le nom")
    void laSignatureSeLitSurLeContenu() {
        assertThat(SignatureFichier.estPdf(fichier("n-importe-quoi.txt", null, pdfMinimal())))
                .as("un vrai PDF reste un PDF quel que soit son nom")
                .isTrue();
        assertThat(SignatureFichier.estPdf(fichier("brochure.pdf", "application/pdf", "%PDF".getBytes())))
                .as("quatre octets ne suffisent pas : l'en-tête est « %PDF- »")
                .isFalse();
    }
}
