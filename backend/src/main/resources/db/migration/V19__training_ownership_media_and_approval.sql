-- Permet au partenaire (CHU) de créer/modifier/supprimer ses propres formations, avec
-- validation obligatoire de l'admin avant publication sur le catalogue public, et l'ajout
-- d'une image + une courte vidéo d'illustration.

ALTER TABLE trainings
    ALTER COLUMN brochure_s3_key DROP NOT NULL, -- une formation nouvellement créée n'a pas encore de brochure
    ADD COLUMN approval_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED'
        CHECK (approval_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    ADD COLUMN rejection_reason TEXT,
    ADD COLUMN image_s3_key TEXT,
    ADD COLUMN video_s3_key TEXT,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Les formations déjà en place (créées avant cette fonctionnalité) restent approuvées et
-- publiées telles quelles : seules les NOUVELLES formations créées par un partenaire passeront
-- désormais par le statut PENDING_REVIEW en attendant la validation de l'admin.
