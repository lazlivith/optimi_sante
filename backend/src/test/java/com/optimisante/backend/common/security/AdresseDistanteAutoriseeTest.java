package com.optimisante.backend.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Adresses que le serveur accepte d'appeler")
class AdresseDistanteAutoriseeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8080/api/v1/admin/orders",
            "http://localhost:5432/",
            "http://169.254.169.254/latest/meta-data/",  // métadonnées de l'hébergeur
            "http://10.0.0.5/image.jpg",
            "http://192.168.1.10/image.jpg",
            "http://172.16.4.2/image.jpg",
            "http://100.64.0.1/image.jpg",
            "http://0.0.0.0/image.jpg",
            "http://[::1]/image.jpg",
            "http://[fd00::1]/image.jpg",
    })
    @DisplayName("refuse ce qui désigne la machine ou le réseau interne")
    void refuseLeReseauInterne(String url) {
        assertThat(AdresseDistanteAutorisee.motifDeRefus(URI.create(url)))
                .as(url)
                .isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "ftp://exemple.fr/image.jpg",
            "gopher://exemple.fr/",
            "jar:http://exemple.fr/a.jar!/b.png",
    })
    @DisplayName("refuse tout protocole autre que http et https")
    void refuseLesAutresProtocoles(String url) {
        assertThat(AdresseDistanteAutorisee.motifDeRefus(URI.create(url)))
                .as(url)
                .isPresent();
    }

    @Test
    @DisplayName("refuse une adresse relative ou sans hôte")
    void refuseLesAdressesIncompletes() {
        assertThat(AdresseDistanteAutorisee.motifDeRefus(URI.create("/images/produit.jpg"))).isPresent();
        assertThat(AdresseDistanteAutorisee.motifDeRefus(null)).isPresent();
    }

    @Test
    @DisplayName("refuse un nom d'hôte qui ne se résout pas")
    void refuseUnHoteInconnu() {
        assertThat(AdresseDistanteAutorisee.motifDeRefus(
                URI.create("https://hote-inexistant.invalid/image.jpg")))
                .isPresent();
    }

    @Test
    @DisplayName("laisse passer une adresse publique littérale")
    void accepteUneAdressePublique() {
        // Une adresse IP littérale : le test ne dépend d'aucune résolution de nom.
        assertThat(AdresseDistanteAutorisee.motifDeRefus(URI.create("https://93.184.216.34/produit.jpg")))
                .isEmpty();
        assertThat(AdresseDistanteAutorisee.motifDeRefus(URI.create("http://8.8.8.8:8443/visuel.png")))
                .isEmpty();
    }
}
