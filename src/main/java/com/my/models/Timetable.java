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
    List<List<TimetableSubject>> whiteWeekDaySubjects = new ArrayList<>();
    List<List<TimetableSubject>> greenWeekDaySubjects = new ArrayList<>();

    public Timetable() {
        for (int i = 0; i < 6; i++)
            whiteWeekDaySubjects.add(new ArrayList<>());

        for (int i = 0; i < 6; i++)
            greenWeekDaySubjects.add(new ArrayList<>());
    }

    @BsonIgnore
    public Timetable addWhiteWeekSubject (Integer weekDay, TimetableSubject subject) {
        whiteWeekDaySubjects.get(weekDay).add(subject);
        return this;
    }

    @BsonIgnore
    public Timetable addGreenWeekDaySubject (Integer weekDay, TimetableSubject subject) {
        greenWeekDaySubjects.get(weekDay).add(subject);
        return this;
    }
}
