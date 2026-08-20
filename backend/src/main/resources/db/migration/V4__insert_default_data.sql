INSERT INTO tenants (code, name, domain, currency) 
VALUES ('FR_MAIN', 'Optimi Santé France', 'optimisante.fr', 'EUR')
ON CONFLICT (code) DO NOTHING;
