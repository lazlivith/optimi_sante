-- Delete CEMAC_PORTAL tenant and gracefully handle cascade if any (though expected to be empty)
DELETE FROM tenants WHERE code = 'CEMAC_PORTAL';
