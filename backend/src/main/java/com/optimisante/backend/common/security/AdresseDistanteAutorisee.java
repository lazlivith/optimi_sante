package com.optimisante.backend.common.security;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;

/**
 * Adresses que le serveur accepte d'aller chercher lui-même.
 *
 * <p><b>Pourquoi cette classe existe.</b> Le catalogue d'un fournisseur apporte des adresses
 * d'images, et c'est le serveur — pas le navigateur de l'administrateur — qui va les chercher. Une
 * adresse pointant vers le réseau interne ferait du serveur un relais vers ce que personne ne doit
 * atteindre depuis l'extérieur : la base de données, l'API voisine, le service de métadonnées de
 * l'hébergeur sur {@code 169.254.169.254}. C'est la faille décrite par l'OWASP sous le nom
 * <i>Server-Side Request Forgery</i>.</p>
 *
 * <p><b>Ce qui est refusé.</b> Tout ce qui n'est pas {@code http} ou {@code https}, et toute adresse
 * qui se résout vers la machine elle-même, un réseau privé, un lien local ou une diffusion. Une
 * adresse publique ordinaire passe sans changement.</p>
 *
 * <p><b>Ce que cela ne couvre pas.</b> Un nom de domaine peut se résoudre une seconde fois, au
 * moment de la connexion, vers une autre adresse (<i>DNS rebinding</i>). S'en prémunir imposerait
 * d'ouvrir la connexion soi-même sur l'adresse vérifiée ; le contrôle ci-dessous arrête l'attaque
 * directe, qui est celle qu'un fichier catalogue permet.</p>
 */
public final class AdresseDistanteAutorisee {

    private AdresseDistanteAutorisee() {
    }

    /**
     * @return le motif du refus, à journaliser, ou vide si l'adresse peut être appelée
     */
    public static Optional<String> motifDeRefus(URI uri) {
        if (uri == null || !uri.isAbsolute()) {
            return Optional.of("adresse incomplète");
        }
        String protocole = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!protocole.equals("http") && !protocole.equals("https")) {
            return Optional.of("protocole « " + protocole + " » refusé");
        }
        String hote = uri.getHost();
        if (hote == null || hote.isBlank()) {
            return Optional.of("hôte absent");
        }

        InetAddress[] adresses;
        try {
            adresses = InetAddress.getAllByName(hote);
        } catch (UnknownHostException e) {
            return Optional.of("hôte « " + hote + " » introuvable");
        }
        for (InetAddress adresse : adresses) {
            if (interne(adresse)) {
                return Optional.of("« " + hote + " » désigne une adresse interne (" + adresse.getHostAddress() + ")");
            }
        }
        return Optional.empty();
    }

    /** Vrai si l'adresse appartient au réseau de la machine ou à un réseau non routable sur Internet. */
    private static boolean interne(InetAddress adresse) {
        if (adresse.isLoopbackAddress() || adresse.isAnyLocalAddress() || adresse.isLinkLocalAddress()
                || adresse.isSiteLocalAddress() || adresse.isMulticastAddress()) {
            return true;
        }
        byte[] octets = adresse.getAddress();
        if (adresse instanceof Inet4Address) {
            int premier = octets[0] & 0xFF;
            int second = octets[1] & 0xFF;
            // 100.64.0.0/10 : plage des opérateurs, hors d'Internet public.
            // 0.0.0.0/8 : « cette machine ». 192.0.0.0/24 : affectations protocolaires.
            return premier == 0 || (premier == 100 && second >= 64 && second <= 127)
                    || (premier == 192 && second == 0 && (octets[2] & 0xFF) == 0);
        }
        // fc00::/7 : adresses locales uniques, équivalent IPv6 des réseaux privés.
        return (octets[0] & 0xFE) == 0xFC;
    }
}
