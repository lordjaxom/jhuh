package de.hinundhergestellt.jhuh.sync;

import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProduct;
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductClient;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.krysalis.barcode4j.impl.upcean.UPCEANLogicImpl.calcChecksum;

@SpringBootTest
class BarcodeChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(BarcodeChecker.class);

    @Autowired
    private ArtooProductClient client;
//
//    @Test
//    void testAllBarcodes() {
//        for (var product : (Iterable<ArtooProduct>) client.findAll(null)::iterator) {
//            if (product.getBarcode() == null) {
//                continue;
//            }
//
//            if (product.getBarcode().length() != 13) {
//                LOGGER.info("Not an EAN-13 Barcode: {}", product.getName());
//                continue;
//            }
//            var expected = calcChecksum(product.getBarcode().substring(0, 12));
//            if (expected != product.getBarcode().charAt(12)) {
//                LOGGER.info("Invalid checksum: {}", product.getName());
//            }
//        }
//    }
}
