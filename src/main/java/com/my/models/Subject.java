package com.my.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.List;
import java.util.Objects;
import java.util.Set;


@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Accessors (chain = true)
public class Subject {
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

    @Override
    @BsonIgnore
    public boolean equals (Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subject that = (Subject) o;
        return name.equals(that.name);
    }

    @Override
    @BsonIgnore
    public int hashCode () {
        return Objects.hash(name);
    }
}
