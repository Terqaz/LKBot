package com.my.threads;

import com.my.TestUtils;
import com.my.models.LkDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlannedSubjectsUpdateTest {

    @Test
    void moveVkAttachments_isCorrect() {
        final List<LkDocument> oldDocuments = TestUtils.createDocumentsList("d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7");
        oldDocuments.get(1).setVkAttachment("a1");
        oldDocuments.get(4).setVkAttachment("a4");
        oldDocuments.get(5).setVkAttachment("a5");
        oldDocuments.get(7).setVkAttachment("a7");

        final List<LkDocument> newDocuments = TestUtils.createDocumentsList("nd0", "nd1", "d2", "d3", "d5", "d6", "d7");

        assertDoesNotThrow(() -> PlannedSubjectsUpdate.copyVkAttachments(List.of(), oldDocuments));
        assertDoesNotThrow(() -> PlannedSubjectsUpdate.copyVkAttachments(newDocuments, List.of()));

        PlannedSubjectsUpdate.copyVkAttachments(newDocuments, oldDocuments);

        assertNull(newDocuments.get(0).getVkAttachment());
        assertNull(newDocuments.get(1).getVkAttachment());
        assertNull(newDocuments.get(2).getVkAttachment());
        assertNull(newDocuments.get(3).getVkAttachment());
        assertEquals("a5", newDocuments.get(4).getVkAttachment());
        assertNull(newDocuments.get(5).getVkAttachment());
        assertEquals("a7", newDocuments.get(6).getVkAttachment());
    }
}