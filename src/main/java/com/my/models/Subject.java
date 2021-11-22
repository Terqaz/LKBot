package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@Accessors (chain = true)
public class Subject {
    private Integer id;
    @NonNull
    private String lkId;
    @NonNull
    private String name;
    @NonNull
    Set<LkDocument> materialsDocuments;
    @NonNull
    Set<LkDocument> messagesDocuments;
    // Используется только при передаче новых сообщений из ЛК
    @BsonIgnore @NonNull
    private List<LkMessage> messagesData;
    @NonNull
    LocalDateTime lastMessageDate;
    String lastMessageHash;

    public Subject(@NonNull String lkId, @NonNull String name,
                   @NonNull Set<LkDocument> materialsDocuments, @NonNull Set<LkDocument> messagesDocuments,
                   @NonNull List<LkMessage> messagesData, @NonNull LocalDateTime lastMessageDate, String lastMessageHash) {
        this.lkId = lkId;
        this.name = name;
        this.materialsDocuments = materialsDocuments;
        this.messagesDocuments = messagesDocuments;
        this.messagesData = messagesData;
        this.lastMessageDate = lastMessageDate;
        this.lastMessageHash = lastMessageHash;
    }

    @BsonIgnore
    public boolean isNotEmpty () {
        return !(materialsDocuments.isEmpty() && messagesDocuments.isEmpty() && messagesData.isEmpty());
    }

    @BsonIgnore
    public boolean hasDocuments () {
        return !(materialsDocuments.isEmpty() && messagesDocuments.isEmpty());
    }

    @BsonIgnore
    public LkDocument getMaterialsDocumentById(int id) {
        return getDocumentById(materialsDocuments, id);
    }

    @BsonIgnore
    public LkDocument getMessageDocumentById(int id) {
        return getDocumentById(messagesDocuments, id);
    }

    @BsonIgnore
    private LkDocument getDocumentById(Set<LkDocument> materialsDocuments, int id) {
        try {
            return materialsDocuments.stream()
                    .filter(document -> document.getId().equals(id))
                    .findFirst().get();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    @BsonIgnore
    public void deleteDocumentById(Integer documentId, boolean isFromMaterials) {
        if (isFromMaterials)
            materialsDocuments.removeIf(lkDocument -> lkDocument.getId().equals(documentId));
        else
            messagesDocuments.removeIf(lkDocument -> lkDocument.getId().equals(documentId));
    }
}
