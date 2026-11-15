package com.optimisante.backend.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Ce qu'un fichier est vraiment, lu dans ses premiers octets.
 *
 * <p><b>Pourquoi un contrôle de plus.</b> {@link ControleFichier} exige que l'extension et le
 * type annoncé concordent. Les deux viennent du poste qui dépose : un fichier renommé
 * {@code .pdf} et envoyé avec {@code Content-Type: application/pdf} passe ce filtre sans être
 * un PDF. L'en-tête, lui, est dans le contenu — il ne s'annonce pas, il se lit.</p>
 *
 * <p><b>Utilitaire réutilisable, branché nulle part d'autre pour l'instant.</b> Quatorze routes
 * de dépôt du projet ne vérifient aucune signature. Les y brancher est un lot à part : une
 * règle plus stricte posée d'un coup sur quatorze écrans refuserait des fichiers qui passaient
 * hier, sans que personne ait mesuré lesquels. Ici, une seule route en dépend.</p>
 *
 * <p><b>On lit le flux, pas le fichier entier.</b> {@code getBytes()} charge tout en mémoire —
 * dix mégaoctets par dépôt simultané. Cinq octets suffisent.</p>
 */
public final class SignatureFichier {

    /** Les cinq premiers octets de tout PDF, quelle que soit sa version (ISO 32000, § 7.5.2). */
    private static final byte[] EN_TETE_PDF = {'%', 'P', 'D', 'F', '-'};

    /** En-tête d'entrée locale d'une archive ZIP : {@code PK\3\4}. */
    private static final byte[] EN_TETE_ZIP = {'P', 'K', 3, 4};

    /** En-tête d'un fichier composé OLE2 (Word, Excel d'avant 2007). */
    private static final byte[] EN_TETE_OLE2 = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private SignatureFichier() {
    }

    /**
     * @throws IllegalArgumentException avec un message à montrer tel quel à l'utilisateur
     */
    public static void exigerPdf(MultipartFile fichier) {
        if (!estPdf(fichier)) {
            throw new IllegalArgumentException(
                    "Ce fichier n'est pas un PDF : son contenu ne commence pas par « %PDF- ». "
                            + "Renommer un fichier ne change pas ce qu'il contient.");
        }
    }

    /** Faux si le fichier est absent, vide, illisible, ou ne commence pas par {@code %PDF-}. */
    public static boolean estPdf(MultipartFile fichier) {
        return commencePar(fichier, EN_TETE_PDF);
    }

    /**
     * Vrai si le fichier commence comme une archive ZIP — ce que sont un {@code .docx} et un
     * {@code .xlsx}.
     *
     * <p><b>Ce que ce contrôle prouve, et ce qu'il ne prouve pas.</b> Il écarte l'exécutable,
     * le script ou l'image renommés. Il ne distingue pas un document Word d'une archive
     * quelconque renommée {@code .docx} : les deux commencent par les mêmes quatre octets. Le
     * savoir demanderait d'ouvrir l'archive, donc de la décompresser — précisément ce qu'on ne
     * veut pas faire d'un fichier déposé par un anonyme.</p>
     */
    public static boolean estZip(MultipartFile fichier) {
        return commencePar(fichier, EN_TETE_ZIP);
    }

    /** Vrai si le fichier commence comme un document Office d'avant 2007 ({@code .doc}). */
    public static boolean estOle2(MultipartFile fichier) {
        return commencePar(fichier, EN_TETE_OLE2);
    }

    private static boolean commencePar(MultipartFile fichier, byte[] debut) {
        if (fichier == null || fichier.isEmpty()) {
            return false;
        }
        try (InputStream flux = fichier.getInputStream()) {
            return commencePar(flux, debut);
        } catch (IOException e) {
            // Un fichier qu'on ne sait pas lire n'est pas un fichier qu'on accepte.
            return false;
        }
    }

    /** Vrai si {@code debut} se trouve en tête de {@code flux}, lu en une seule fois. */
    private static boolean commencePar(InputStream flux, byte[] debut) throws IOException {
        byte[] lus = new byte[debut.length];
        // readNBytes et non read : un flux a le droit de rendre moins d'octets que demandé,
        // et un read() partiel ferait échouer un PDF parfaitement valide.
        int nombre = flux.readNBytes(lus, 0, debut.length);
        if (nombre < debut.length) {
            return false;
        }
        for (int i = 0; i < debut.length; i++) {
            if (lus[i] != debut[i]) {
                return false;
            }
        }
        return true;
    }
}
