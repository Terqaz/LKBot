package com.my.models;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
public class Timetable {
    List<List<TimetableSubject>> whiteSubjects = new ArrayList<>();
    List<List<TimetableSubject>> greenSubjects = new ArrayList<>();

    public Timetable() {
        for (int i = 0; i < 6; i++)
            whiteSubjects.add(new ArrayList<>());

        for (int i = 0; i < 6; i++)
            greenSubjects.add(new ArrayList<>());
    }

    @BsonIgnore
    public Timetable addWhiteSubject(Integer weekDay, TimetableSubject subject) {
        whiteSubjects.get(weekDay).add(subject);
        return this;
    }

    @BsonIgnore
    public Timetable addGreenSubject(Integer weekDay, TimetableSubject subject) {
        greenSubjects.get(weekDay).add(subject);
        return this;
    }
}
