package com.my.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


@Data
@NoArgsConstructor
@RequiredArgsConstructor
@JsonIgnoreProperties({"notEmpty"})
public class SubjectData {
    private int id;
    @NonNull
    private String subjectName;
    @NonNull
    private String localUrl;
    @NonNull
    private Set<String> newDocumentNames;
    private Set<String> oldDocumentNames = new HashSet<>();
    @NonNull
    private List<MessageData> messagesData;

    public boolean isNotEmpty () {
        return !newDocumentNames.isEmpty() || !messagesData.isEmpty();
    }

    public void removeOldDocuments (SubjectData oldData) {
        newDocumentNames.removeAll(oldData.oldDocumentNames);
    }

    @Override
    public boolean equals (Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubjectData that = (SubjectData) o;
        return subjectName.equals(that.subjectName);
    }

    @Override
    public int hashCode () {
        return Objects.hash(subjectName);
    }
}
