-- V42 : l'entretien de selection est toujours une visioconference
--
-- CE QUI CHANGE
-- La V41 prevoyait trois formes d'entretien : visio, telephone, sur place. La realite metier
-- n'en connait qu'une — l'entretien se tient en ligne, sur Teams, Meet ou Zoom, et le candidat
-- n'a jamais a se deplacer.
--
-- POURQUOI SUPPRIMER LA COLONNE PLUTOT QUE LA CONTRAINDRE
-- Une colonne qui n'admet qu'une valeur ne porte aucune information : elle occupe de la place
-- dans chaque ecran, chaque DTO et chaque test, pour ne jamais rien distinguer. Pire, tant que
-- « sur place » reste selectionnable, un CHU peut le choisir — et la plateforme editera une
-- convocation sur place pour un entretien qui se tiendra en ligne.
--
-- `location_or_link` devient `meeting_link`, et devient obligatoire : un entretien en ligne
-- sans lien de connexion n'est pas un entretien, c'est un rendez-vous auquel personne ne peut
-- se joindre. La contrainte conditionnelle de la V41 (« obligatoire sauf au telephone ») n'a
-- donc plus lieu d'etre.

-- Les entretiens sans lien ne sont pas convertibles : ils avaient ete proposes par telephone,
-- forme qui n'existe plus, et rien ne permet d'inventer l'adresse de reunion a leur place.
-- Les supprimer plutot que leur fabriquer un lien : une convocation vers une reunion inventee
-- serait pire que pas de convocation du tout. Les creneaux suivent par ON DELETE CASCADE.
DELETE FROM interview_schedules
WHERE location_or_link IS NULL OR length(trim(location_or_link)) = 0;

ALTER TABLE interview_schedules
    DROP CONSTRAINT IF EXISTS interview_schedules_location_check;
ALTER TABLE interview_schedules
    DROP CONSTRAINT IF EXISTS interview_schedules_mode_check;

ALTER TABLE interview_schedules RENAME COLUMN location_or_link TO meeting_link;

ALTER TABLE interview_schedules ALTER COLUMN meeting_link SET NOT NULL;

-- NOT NULL ne suffit pas : une chaine vide passerait, et laisserait le meme rendez-vous
-- injoignable.
ALTER TABLE interview_schedules
    ADD CONSTRAINT interview_schedules_meeting_link_check
    CHECK (length(trim(meeting_link)) > 0);

ALTER TABLE interview_schedules DROP COLUMN IF EXISTS mode;

COMMENT ON COLUMN interview_schedules.meeting_link IS
    'Lien de la reunion en ligne (Teams, Meet, Zoom...). Obligatoire : l''entretien de '
    'selection se tient toujours a distance.';

COMMENT ON TABLE interview_schedules IS
    'Entretien de selection en visioconference : le CHU propose des creneaux et le lien de '
    'reunion, OptimiSante transmet, le medecin choisit. Independant du statut du dossier — '
    'aucun statut d''enrollment n''est ajoute.';
