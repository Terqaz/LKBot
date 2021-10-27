package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@Accessors (chain = true)
public class Subject implements Serializable {
    private int id;
    @NonNull
    private String lkId;
    @NonNull
    private String name;
    @NonNull
    Set<LkDocument> materialsDocuments;
    @NonNull
    Set<LkDocument> messagesDocuments;

    // Используется только при передаче новых сообщений из ЛК
    @NonNull
    @BsonIgnore
    private List<LkMessage> messagesData;

    @BsonIgnore
    public boolean isNotEmpty () {
        return !(materialsDocuments.isEmpty() && messagesDocuments.isEmpty() && messagesData.isEmpty());
    }

    public Optional<LkDocument> getMaterialsDocumentById(int id) {
        return getDocumentById(materialsDocuments, id);
    }

    public Optional<LkDocument> getMessageDocumentById(int id) {
        return getDocumentById(messagesDocuments, id);
    }

    private Optional<LkDocument> getDocumentById(Set<LkDocument> materialsDocuments, int id) {
        return materialsDocuments.stream().filter(document -> document.getId().equals(id)).findFirst();
    }
}
