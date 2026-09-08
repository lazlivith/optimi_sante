package com.optimisante.backend.common.email;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Tableau de bord de l'espace « Emails ».
 *
 * Les compteurs `totalSent`/`totalFailed` proviennent du journal local et sont donc
 * toujours disponibles. Le reste vient de l'API Mailtrap et reste nul tant qu'aucun
 * jeton n'est configuré — l'écran demeure alors pleinement fonctionnel.
 *
 * NB : Mailtrap n'expose pas (sur ce plan/jeton) d'endpoint de statistiques de
 * délivrabilité agrégées — les tentatives `/sending/stats`, `/messages/stats` et
 * `/sending_domains/{id}/stats` renvoient toutes 404. Le seul endpoint réellement
 * disponible, `/sending_domains`, donne en revanche l'état de vérification du domaine
 * d'envoi enregistrement par enregistrement : information bien plus actionnable pendant
 * la mise en place, et affichée telle quelle dans l'administration.
 */
@Data
@Builder
public class EmailStatsDto {

    // --- Journal local ---
    private long totalSent;
    private long totalFailed;

    // --- API Mailtrap (facultatif) ---
    private boolean mailtrapConnected;
    private String mailtrapError;

    /** Domaine d'envoi déclaré dans Mailtrap (ex. optimisante.fr). */
    private String sendingDomain;

    /** Vrai lorsque tous les enregistrements DNS requis sont vérifiés : envoi réel possible. */
    private boolean domainVerified;

    private List<DnsRecordDto> dnsRecords;

    @Data
    @Builder
    public static class DnsRecordDto {
        /** Nom court à saisir chez le registrar (ex. `mt02`, `_dmarc`). */
        private String name;
        /** Nom complet résultant (ex. `mt02.optimisante.fr`). */
        private String domain;
        /** CNAME, TXT, MX... */
        private String type;
        private String value;
        /** `passed` lorsque l'enregistrement est en place, `missing` sinon. */
        private String status;
    }
}
