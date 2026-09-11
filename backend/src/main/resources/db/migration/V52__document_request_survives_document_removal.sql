-- V52 : une demande de piece survit reellement a la disparition du fichier
--
-- LA CONTRADICTION
-- La V37 pose deux regles sur `enrollment_document_requests` qui s'excluent :
--
--   * la cle etrangere vers `enrollment_documents` est `ON DELETE SET NULL`, avec ce commentaire
--     explicite : « si le fichier disparait, la demande subsiste et redevient a satisfaire » ;
--   * la contrainte `enrollment_document_requests_coherent` exige `document_id IS NOT NULL`
--     pour les statuts SUBMITTED et ACCEPTED.
--
-- Supprimer un fichier reclame donc de mettre `document_id` a NULL sur une ligne qui l'interdit.
-- La suppression echoue. La demande ne « redevient » rien du tout : c'est l'operation entiere
-- qui est refusee, avec une violation de contrainte remontee telle quelle a l'utilisateur.
--
-- POURQUOI PERSONNE NE L'AVAIT VU
-- Il faut, pour l'atteindre, supprimer une piece deja deposee. Aucun parcours ne le permettait
-- quand la V37 a ete ecrite. La suppression d'un dossier (`DELETE /admin/enrollments/{id}`)
-- cascade pourtant vers `enrollment_documents` : le chemin existait, simplement inemprunte.
--
-- LA CORRECTION
-- On tient la promesse du commentaire plutot que de l'abandonner : avant qu'un document ne
-- disparaisse, les demandes qui le designent repassent a PENDING — sans piece, sans date de
-- depot, sans revue. Elles redeviennent litteralement « a satisfaire », etat parfaitement
-- coherent au regard de la contrainte.
--
-- Un declencheur plutot qu'un controle applicatif : la suppression peut venir d'une cascade
-- (suppression d'un dossier), d'un script de maintenance ou d'un futur ecran. Une regle posee
-- dans le service ne couvrirait que le chemin qu'on connait aujourd'hui — exactement l'erreur
-- que cette migration repare.

CREATE OR REPLACE FUNCTION reset_document_requests_on_document_removal()
RETURNS TRIGGER AS $$
BEGIN
    -- Seuls SUBMITTED et ACCEPTED exigent une piece. REJECTED et CANCELLED tolerent deja
    -- `document_id IS NULL` : la mise a NULL de la cle etrangere leur convient telle quelle.
    UPDATE enrollment_document_requests
       SET status       = 'PENDING',
           document_id  = NULL,
           submitted_at = NULL,
           reviewed_at  = NULL,
           reviewed_by  = NULL,
           -- Le motif d'un refus anterieur n'a plus d'objet : il portait sur un fichier
           -- qui n'existe plus.
           rejection_reason = NULL
     WHERE document_id = OLD.id
       AND status IN ('SUBMITTED', 'ACCEPTED');
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_reset_document_requests ON enrollment_documents;

CREATE TRIGGER trg_reset_document_requests
    BEFORE DELETE ON enrollment_documents
    FOR EACH ROW
    EXECUTE FUNCTION reset_document_requests_on_document_removal();

COMMENT ON FUNCTION reset_document_requests_on_document_removal() IS
    'Ramene a PENDING les demandes qui designaient une piece supprimee. Sans cela, la cle '
    'etrangere ON DELETE SET NULL de la V37 produit une ligne que la contrainte de coherence '
    'refuse, et la suppression echoue.';
