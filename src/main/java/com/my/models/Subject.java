package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.io.Serializable;
import java.util.List;
import java.util.Set;


@Data
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
@Accessors (chain = true)
@EqualsAndHashCode
public class Subject implements Serializable {
    private int id;
    @NonNull
    private String lkId;
    @NonNull
    private String name;
    @NonNull
    private Set<String> documentNames;
    @NonNull
    private List<MessageData> messagesData;

    @BsonIgnore
    public boolean isNotEmpty () {
        return !(documentNames.isEmpty() && messagesData.isEmpty());
    }
}
