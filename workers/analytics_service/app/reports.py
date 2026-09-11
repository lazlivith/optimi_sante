"""Catalogue des rapports analytiques. Chacun = une requête SQL en lecture seule sur la base
Optimi Santé, exécutée telle quelle (batch nocturne) ou avec des paramètres (à la demande).

Déploiement mono-tenant (FR_MAIN) : les rapports agrègent l'ensemble des données. Un filtre
par tenant pourra être ajouté ici sans toucher au runner ni à l'API.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class Report:
    key: str
    title: str
    description: str
    sql: str
    params: dict[str, Any] = field(default_factory=dict)

    def resolved_params(self, overrides: dict[str, Any] | None) -> dict[str, Any]:
        merged = dict(self.params)
        for name, value in (overrides or {}).items():
            if name in merged and value is not None:
                merged[name] = value
        return merged


_REVENUE_DAILY = Report(
    key="revenue_daily",
    title="Revenu quotidien",
    description="Chiffre d'affaires payé, commandes et devis par jour sur une fenêtre glissante.",
    params={"days": 90},
    sql="""
        SELECT d::date AS jour,
               COALESCE(SUM(o.total_amount) FILTER (WHERE o.payment_status = 'PAID'), 0) AS revenu_eur,
               COUNT(o.id) FILTER (WHERE o.payment_status = 'PAID') AS commandes_payees,
               COUNT(o.id) FILTER (WHERE o.is_quote) AS devis
        FROM generate_series(
                 date_trunc('day', now()) - make_interval(days => (:days)::int - 1),
                 date_trunc('day', now()),
                 interval '1 day') d
        LEFT JOIN orders o ON date_trunc('day', o.created_at) = d
        GROUP BY d
        ORDER BY d
    """,
)

_SALES_BY_CATEGORY = Report(
    key="sales_by_category",
    title="Ventes par catégorie",
    description="Revenu, quantités et nombre de commandes payées par catégorie de produit.",
    sql="""
        SELECT COALESCE(c.name, '(sans catégorie)') AS categorie,
               COALESCE(SUM(oi.subtotal), 0) AS revenu_eur,
               COALESCE(SUM(oi.quantity), 0) AS quantite,
               COUNT(DISTINCT o.id) AS commandes
        FROM order_items oi
        JOIN orders o ON o.id = oi.order_id AND o.payment_status = 'PAID'
        LEFT JOIN products p ON p.id = oi.product_id
        LEFT JOIN categories c ON c.id = p.category_id
        GROUP BY c.name
        ORDER BY revenu_eur DESC
    """,
)

_ACQUISITION_FUNNEL = Report(
    key="acquisition_funnel",
    title="Entonnoir d'acquisition mobilité",
    description="De la brochure téléchargée au visa obtenu : volumes à chaque étape.",
    sql="""
        SELECT
          (SELECT COUNT(*) FROM prospect_leads) AS brochures_telechargees,
          (SELECT COUNT(*) FROM doctor_applications) AS candidatures,
          (SELECT COUNT(*) FROM doctor_applications WHERE status = 'PAID') AS candidatures_payees,
          (SELECT COUNT(*) FROM enrollments) AS inscriptions,
          (SELECT COUNT(*) FROM enrollments
             WHERE status IN ('CONVENTION_ISSUED','VISA_SUBMITTED','VISA_GRANTED','READY_TO_START')) AS conventions_emises,
          (SELECT COUNT(*) FROM enrollments
             WHERE status IN ('VISA_GRANTED','READY_TO_START')) AS visas_obtenus
    """,
)

_ENROLLMENT_PIPELINE = Report(
    key="enrollment_pipeline",
    title="Pipeline des inscriptions",
    description="Détail de chaque dossier de mobilité : médecin, formation, statut, ancienneté.",
    sql="""
        SELECT e.id AS inscription_id,
               u.email AS medecin_email,
               dp.first_name AS prenom,
               dp.last_name AS nom,
               tr.title AS formation,
               s.location AS lieu,
               s.start_date AS debut,
               s.end_date AS fin,
               e.status AS statut,
               e.submitted_at AS depose_le,
               EXTRACT(DAY FROM now() - e.submitted_at)::int AS jours_dans_le_pipeline
        FROM enrollments e
        JOIN training_sessions s ON s.id = e.session_id
        JOIN trainings tr ON tr.id = s.training_id
        JOIN users u ON u.id = e.doctor_id
        LEFT JOIN doctor_profiles dp ON dp.user_id = u.id
        ORDER BY e.submitted_at DESC
    """,
)

_RGPD_COMPLIANCE = Report(
    key="rgpd_compliance",
    title="Conformité RGPD",
    description="Soft delete, anonymisations, consentements manquants, comptes et leads anciens.",
    sql="""
        SELECT
          (SELECT COUNT(*) FROM users) AS comptes_total,
          (SELECT COUNT(*) FROM users WHERE deleted_at IS NULL) AS comptes_actifs,
          (SELECT COUNT(*) FROM users WHERE deleted_at IS NOT NULL) AS comptes_effaces_soft,
          (SELECT COUNT(*) FROM users WHERE anonymized_at IS NOT NULL) AS comptes_anonymises,
          (SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND gdpr_consent_at IS NULL) AS sans_consentement,
          (SELECT COUNT(*) FROM users u
             WHERE u.deleted_at IS NULL AND u.anonymized_at IS NULL
               AND u.role IN ('CLIENT_B2C','CLIENT_B2B','MEDECIN')
               AND u.created_at < now() - interval '24 months'
               AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.user_id = u.id)
               AND NOT EXISTS (SELECT 1 FROM enrollments en WHERE en.doctor_id = u.id)) AS comptes_inactifs_24m,
          (SELECT COUNT(*) FROM prospect_leads WHERE downloaded_at < now() - interval '24 months') AS leads_anciens_24m,
          (SELECT COUNT(*) FROM audit_logs WHERE created_at >= now() - interval '30 days') AS entrees_audit_30j
    """,
)

_CATALOG_STOCK_ALERT = Report(
    key="catalog_stock_alert",
    title="Alertes de stock catalogue",
    description="Produits dont le stock est au niveau ou sous le seuil d'alerte.",
    sql="""
        SELECT p.sku,
               p.name AS produit,
               p.stock_quantity AS stock,
               p.stock_threshold AS seuil,
               (p.stock_quantity - p.stock_threshold) AS marge,
               p.is_active AS actif
        FROM products p
        WHERE p.deleted_at IS NULL AND p.stock_quantity <= p.stock_threshold
        ORDER BY marge ASC, p.name
    """,
)

_B2B_ACTIVITY = Report(
    key="b2b_activity",
    title="Activité B2B",
    description="Commandes, devis et chiffre d'affaires par entreprise cliente B2B.",
    sql="""
        SELECT cp.company_name AS entreprise,
               u.email AS contact_email,
               COUNT(o.id) FILTER (WHERE o.payment_status = 'PAID') AS commandes_payees,
               COUNT(o.id) FILTER (WHERE o.is_quote) AS devis,
               COALESCE(SUM(o.total_amount) FILTER (WHERE o.payment_status = 'PAID'), 0) AS ca_eur,
               MAX(o.created_at) AS derniere_activite
        FROM users u
        JOIN company_profiles cp ON cp.user_id = u.id
        LEFT JOIN orders o ON o.user_id = u.id
        WHERE u.role = 'CLIENT_B2B' AND u.deleted_at IS NULL
        GROUP BY cp.company_name, u.email
        ORDER BY ca_eur DESC
    """,
)

REPORTS: dict[str, Report] = {
    r.key: r
    for r in (
        _REVENUE_DAILY,
        _SALES_BY_CATEGORY,
        _ACQUISITION_FUNNEL,
        _ENROLLMENT_PIPELINE,
        _RGPD_COMPLIANCE,
        _CATALOG_STOCK_ALERT,
        _B2B_ACTIVITY,
    )
}


def get_report(key: str) -> Report:
    report = REPORTS.get(key)
    if report is None:
        raise KeyError(key)
    return report
