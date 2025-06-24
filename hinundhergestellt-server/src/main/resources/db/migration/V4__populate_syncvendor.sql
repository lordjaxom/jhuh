INSERT INTO sync_vendor (id, name)
SELECT UUID(), vendor
FROM sync_product
GROUP BY vendor;
COMMIT;
