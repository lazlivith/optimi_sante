package com.optimisante.backend.common.storage.cloudinary;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Nouvelles tentatives vers le stockage : quand, combien de fois, et avec quelle attente. */
class ReessaiReseauTest {

    private final List<Long> attentes = new ArrayList<>();
    private final ReessaiReseau reessai = new ReessaiReseau(attentes::add);

    @Test
    void uneCoupureTlsPassagereEstRetentee() throws Exception {
        AtomicInteger appels = new AtomicInteger();
        String resultat = reessai.executer("test", () -> {
            if (appels.incrementAndGet() == 1) {
                throw new SSLHandshakeException("Remote host terminated the handshake");
            }
            return "ok";
        });
        assertEquals("ok", resultat);
        assertEquals(2, appels.get());
        assertEquals(List.of(500L), attentes);
    }

    @Test
    void troisTentativesAuPlusAvecUneAttenteQuiDouble() {
        AtomicInteger appels = new AtomicInteger();
        IOException erreur = assertThrows(IOException.class, () -> reessai.executer("test", () -> {
            appels.incrementAndGet();
            throw new IOException("enveloppe", new EOFException("SSL peer shut down incorrectly"));
        }));
        assertEquals("enveloppe", erreur.getMessage());
        assertEquals(3, appels.get());
        assertEquals(List.of(500L, 1000L), attentes);
    }

    @Test
    void unRefusQuiNeTientPasAuReseauNEstPasRetente() {
        AtomicInteger appels = new AtomicInteger();
        assertThrows(IOException.class, () -> reessai.executer("test", () -> {
            appels.incrementAndGet();
            throw new IOException("Invalid resource type 'auto'");
        }));
        assertEquals(1, appels.get());
        assertTrue(attentes.isEmpty());
    }

    @Test
    void uneErreurApplicativeTraverseSansAttendre() {
        AtomicInteger appels = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> reessai.executer("test", () -> {
            appels.incrementAndGet();
            throw new IllegalStateException("refus Cloudinary");
        }));
        assertEquals(1, appels.get());
    }
}
