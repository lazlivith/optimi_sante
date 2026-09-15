package com.optimisante.backend.common.storage;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLException;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Set;

/**
 * Nouvelle tentative sur les coupures réseau passagères vers le stockage.
 *
 * <p><b>Le symptôme.</b> « Remote host terminated the handshake » : Cloudinary ferme parfois une
 * connexion réutilisée, surtout quand deux appels se suivent de près (une suppression puis un
 * téléversement). L'appel échouait, et le médecin ou le CHU voyait « le document n'a pas pu être
 * déposé » pour un incident qui se serait réglé une seconde plus tard.</p>
 *
 * <p><b>Ce qui est retenté, et seulement cela.</b> Les pannes de transport : poignée de main TLS,
 * connexion coupée, délai dépassé. Un refus de Cloudinary (type de ressource, droits, fichier
 * rejeté) échouerait à l'identique : on le laisse passer tout de suite plutôt que de faire
 * attendre l'utilisateur pour rien.</p>
 *
 * <p><b>Sans doublon.</b> Les appels retentés portent une clé de stockage fixée avant la première
 * tentative : si la réponse s'est perdue alors que le fichier était arrivé, la nouvelle tentative
 * écrit au même endroit au lieu d'en créer un second.</p>
 */
@Slf4j
final class ReessaiReseau {

    static final int TENTATIVES = 3;
    static final long DELAI_INITIAL_MS = 500;

    /** Pannes d'Apache HttpClient, reconnues par leur nom pour ne pas dépendre de sa version. */
    private static final Set<String> PANNES_HTTPCLIENT = Set.of(
            "NoHttpResponseException", "ConnectionClosedException", "ConnectTimeoutException");

    @FunctionalInterface
    interface Appel<T> {
        T executer() throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface Attente {
        void attendre(long millisecondes) throws InterruptedException;
    }

    private final Attente attente;

    ReessaiReseau() {
        this(Thread::sleep);
    }

    ReessaiReseau(Attente attente) {
        this.attente = attente;
    }

    <T> T executer(String operation, Appel<T> appel) throws IOException, InterruptedException {
        long delai = DELAI_INITIAL_MS;
        for (int tentative = 1; ; tentative++) {
            try {
                return appel.executer();
            } catch (IOException e) {
                if (tentative >= TENTATIVES || !estPassagere(e)) {
                    throw e;
                }
                log.warn("{} : coupure réseau ({}), nouvelle tentative {}/{} dans {} ms",
                        operation, e.getMessage(), tentative + 1, TENTATIVES, delai);
                attente.attendre(delai);
                delai *= 2;
            }
        }
    }

    /** Parcourt la chaîne des causes : la panne TLS arrive souvent enveloppée. */
    static boolean estPassagere(Throwable erreur) {
        for (Throwable t = erreur; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SSLException || t instanceof SocketException || t instanceof SocketTimeoutException
                    || t instanceof EOFException || t instanceof HttpTimeoutException
                    || PANNES_HTTPCLIENT.contains(t.getClass().getSimpleName())) {
                return true;
            }
        }
        return false;
    }
}
