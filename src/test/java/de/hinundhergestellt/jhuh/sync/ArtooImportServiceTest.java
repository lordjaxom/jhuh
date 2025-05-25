package de.hinundhergestellt.jhuh.sync;

import de.hinundhergestellt.jhuh.HuhApplication;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = HuhApplication.class)
class ArtooImportServiceTest {

    @Autowired
    private ArtooImportService artooImportService;

    @Test
    void whyDoesPoliFlexTurboNotHaveVariations() {
        var group = artooImportService.findProductGroupByName("POLI-FLEX® TURBO Flexfolie");
        assertThat(group).isPresent();
        assertThat(group).get()
                .extracting(ArtooProductGroup::getTypeId)
                .isEqualTo(3);
        assertThat(artooImportService.getItemVariations(group.get()))
                .isNotEmpty()
                .get()
                .isNotEqualTo(0L);
    }
}