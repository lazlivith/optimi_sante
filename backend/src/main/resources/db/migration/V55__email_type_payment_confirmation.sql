-- ===========================================================================================
-- Confirmation de paiement : nouveau type d'e-mail.
--
-- La contrainte énumère les types autorisés. Sans cette extension, l'écriture de la trace
-- échouerait — et elle échouerait APRÈS l'envoi du message, laissant un courrier parti sans
-- trace et un journal d'administration incomplet. C'est ce qu'avait failli provoquer la V44
-- de la branche de travail, qui omettait deux types déjà émis.
-- ===========================================================================================

-- Le nom réel est `email_logs_type_check`, et non `..._email_type_check` : les deux se
-- ressemblent assez pour qu'un DROP IF EXISTS sur le mauvais nom passe en silence, laissant
-- l'ancienne contrainte en place. L'essai à blanc l'a montré ; une lecture du script ne
-- l'aurait pas montré.
ALTER TABLE email_logs
    DROP CONSTRAINT IF EXISTS email_logs_type_check;

ALTER TABLE email_logs
    ADD CONSTRAINT email_logs_type_check
    CHECK (email_type IN (
        'CREDENTIALS',
        'TEST',
        'INTERVIEW_SLOTS',
        'INTERVIEW_CONFIRMED',
        'NOTIFICATION',
        'PAYMENT_CONFIRMATION'));
