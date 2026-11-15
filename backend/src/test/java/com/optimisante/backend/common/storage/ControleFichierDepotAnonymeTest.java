package com.optimisante.backend.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les deux contrôles appliqués aux fichiers qu'un anonyme dépose sur le partenariat.
 *
 * <p>Comme pour {@code ValeurDAttenteTest}, la seconde propriété compte autant que la première :
 * les faux sont refusés, <b>et les vrais passent</b>. Un contrôle qui refuserait la convention
 * d'un CHU fermerait la seule porte d'entrée des partenaires.</p>
 */
class ControleFichierDepotAnonymeTest {

    private static final byte[] PDF = "%PDF-1.7\n%âãÏÓ\n1 0 obj".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 6, 0, 8, 0};
    private static final byte[] OLE2 = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0};
    private static final byte[] EXECUTABLE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0};

    private static MockMultipartFile fichier(String nom, String type, byte[] contenu) {
        return new MockMultipartFile("fichier", nom, type, contenu);
    }

    // ---------------------------------------------------------------- convention signée

    @Test
    @DisplayName("convention : un vrai PDF, un vrai DOCX et un vrai DOC passent")
    void conventionsReellesAcceptees() {
        assertThatCode(() -> ControleFichier.verifierConvention(
                fichier("convention.pdf", "application/pdf", PDF))).doesNotThrowAnyException();
        assertThatCode(() -> ControleFichier.verifierConvention(
                fichier("Convention signée.DOCX",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ZIP)))
                .doesNotThrowAnyException();
        assertThatCode(() -> ControleFichier.verifierConvention(
                fichier("convention.doc", "application/msword", OLE2))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("convention : un poste sans Word, qui n'annonce aucun type, n'est pas refusé")
    void typeAnnonceNonExige() {
        assertThatCode(() -> ControleFichier.verifierConvention(
                fichier("convention.docx", "application/octet-stream", ZIP))).doesNotThrowAnyException();
        assertThatCode(() -> ControleFichier.verifierConvention(
                fichier("convention.docx", null, ZIP))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("convention : un exécutable renommé en .pdf, avec le bon type annoncé, est refusé")
    void executableRenommeRefuse() {
        assertThatThrownBy(() -> ControleFichier.verifierConvention(
                fichier("convention.pdf", "application/pdf", EXECUTABLE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ne correspond pas à son extension");
    }

    @Test
    @DisplayName("convention : le contenu doit être celui de l'extension, pas d'une autre extension admise")
    void contenuDUneAutreExtensionRefuse() {
        assertThatThrownBy(() -> ControleFichier.verifierConvention(
                fichier("convention.docx", "application/pdf", PDF)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ControleFichier.verifierConvention(
                fichier("convention.doc", "application/msword", ZIP)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("convention : toute autre extension est refusée, quel que soit le contenu")
    void extensionsHorsListeRefusees() {
        for (String nom : new String[]{"convention.xlsx", "convention.zip", "convention.exe",
                "convention.html", "convention", "convention.pdf.exe"}) {
            assertThatThrownBy(() -> ControleFichier.verifierConvention(fichier(nom, "application/pdf", PDF)))
                    .as(nom)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PDF, un DOC ou un DOCX");
        }
    }

    @Test
    @DisplayName("convention : absente, vide ou au-delà de 10 Mo, elle est refusée")
    void absenteVideOuTropLourde() {
        assertThatThrownBy(() -> ControleFichier.verifierConvention(null))
                .hasMessageContaining("obligatoire");
        assertThatThrownBy(() -> ControleFichier.verifierConvention(
                fichier("convention.pdf", "application/pdf", new byte[0])))
                .hasMessageContaining("obligatoire");

        byte[] lourd = new byte[(int) ControleFichier.TAILLE_MAX_OCTETS + 1];
        System.arraycopy(PDF, 0, lourd, 0, PDF.length);
        assertThatThrownBy(() -> ControleFichier.verifierConvention(
                fichier("convention.pdf", "application/pdf", lourd)))
                .hasMessageContaining("10 Mo");
    }

    // ---------------------------------------------------------------- classeur de dossier

    @Test
    @DisplayName("classeur : un .xlsx qui commence comme une archive passe")
    void classeurReelAccepte() {
        assertThatCode(() -> ControleFichier.verifierClasseurDossier(
                fichier("dossier-partenariat-optimi-sante.xlsx", null, ZIP))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("classeur : autre extension, autre contenu ou plus de 2 Mo, il est refusé avant d'être ouvert")
    void classeurRefuse() {
        assertThatThrownBy(() -> ControleFichier.verifierClasseurDossier(fichier("dossier.pdf", null, PDF)))
                .hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> ControleFichier.verifierClasseurDossier(fichier("dossier.xlsx", null, EXECUTABLE)))
                .hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> ControleFichier.verifierClasseurDossier(fichier("dossier.xlsx", null, new byte[0])))
                .hasMessageContaining("Aucun fichier");

        byte[] lourd = new byte[(int) ControleFichier.TAILLE_MAX_CLASSEUR_OCTETS + 1];
        System.arraycopy(ZIP, 0, lourd, 0, ZIP.length);
        assertThatThrownBy(() -> ControleFichier.verifierClasseurDossier(fichier("dossier.xlsx", null, lourd)))
                .hasMessageContaining("2 Mo");
    }
}
