-- Le champ "Téléphone" du formulaire Mon Profil est affiché pour TOUS les rôles côté frontend,
-- mais seul DoctorProfile.phone_whatsapp existait en base : la sauvegarde pour B2C/B2B/Admin/CHU
-- retournait un succès sans jamais rien persister.
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
