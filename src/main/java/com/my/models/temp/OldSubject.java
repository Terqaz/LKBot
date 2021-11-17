package com.my.models.temp;

import com.my.models.LkDocument;
import com.my.models.LkMessage;
import lombok.*;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class OldSubject {
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
    @BsonIgnore
    @NonNull
    private List<LkMessage> messagesData;
}
