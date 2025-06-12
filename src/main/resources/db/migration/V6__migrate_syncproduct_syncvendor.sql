UPDATE sync_product p SET p.vendor_id=(SELECT v.id FROM sync_vendor v WHERE v.name=p.vendor);
COMMIT;
