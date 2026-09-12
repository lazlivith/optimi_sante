package com.optimisante.backend.domain.document.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/**
 * Attribue les numéros de document, en série continue et par exercice.
 *
 * <p>Une séquence PostgreSQL ne conviendrait pas : elle avance même quand la transaction est
 * annulée, et laisserait donc des trous. Une ligne verrouillée, incrémentée dans la
 * <b>même transaction</b> que l'émission, n'en laisse aucun — si l'émission échoue, le numéro
 * n'est pas consommé.</p>
 *
 * <p>Le verrou {@code FOR UPDATE} est ce qui compte ici : deux paiements simultanés lisent sinon
 * le même compteur et repartent avec le même numéro.</p>
 */
@Slf4j
@Service
public class DocumentNumberService {

    @PersistenceContext
    private EntityManager em;

    /**
     * Rend le prochain numéro pour ce type de document, sur l'exercice courant.
     *
     * <p>{@code MANDATORY} et non {@code REQUIRED} : cette méthode ne doit jamais ouvrir sa
     * propre transaction. Si elle le faisait, le numéro serait consommé et validé même lorsque
     * l'émission qui l'a demandé échoue ensuite — créant précisément le trou que tout ceci vise
     * à éviter.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String prochainNumero(String kind, String prefixe) {
        int exercice = Year.now().getValue();

        // La ligne de compteur est créée au premier document de l'exercice. ON CONFLICT DO
        // NOTHING : deux émissions simultanées peuvent l'insérer en même temps, et la seconde
        // doit simplement continuer.
        em.createNativeQuery("""
                INSERT INTO document_sequences (kind, exercice, dernier)
                VALUES (:kind, :exercice, 0)
                ON CONFLICT (kind, exercice) DO NOTHING
                """)
                .setParameter("kind", kind)
                .setParameter("exercice", exercice)
                .executeUpdate();

        // Verrou puis incrément dans la foulée : RETURNING évite une seconde requête et, surtout,
        // rend l'opération atomique — la lecture et l'écriture ne peuvent plus être séparées par
        // une transaction concurrente.
        Object valeur = em.createNativeQuery("""
                UPDATE document_sequences
                   SET dernier = dernier + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE kind = :kind AND exercice = :exercice
                RETURNING dernier
                """)
                .setParameter("kind", kind)
                .setParameter("exercice", exercice)
                .getSingleResult();

        int rang = ((Number) valeur).intValue();
        return "%s-%d-%04d".formatted(prefixe, exercice, rang);
    }
}
