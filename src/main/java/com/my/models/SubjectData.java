package com.my.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;


@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class SubjectData {
    @NonNull
    private String subjectName;
    @NonNull
    private Set<String> documentNames;
    @NonNull
    private List<MessageData> messagesData;
    private String primaryAcademic;
    private Set<String> secondaryAcademics;
    private Date lastCheckDate;

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
