package com.my.models;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class Timetable {
    List<List<TimetableSubject>> whiteWeekDaySubjects = new ArrayList<>();
    List<List<TimetableSubject>> greenWeekDaySubjects = new ArrayList<>();

    public Timetable() {
        for (int i = 0; i < 6; i++)
            whiteWeekDaySubjects.add(new ArrayList<>());

        for (int i = 0; i < 6; i++)
            greenWeekDaySubjects.add(new ArrayList<>());
    }

    public void addWhiteWeekSubject (Integer weekDay, TimetableSubject subject) {
        whiteWeekDaySubjects.get(weekDay).add(subject);
    }

    public void addGreenWeekDaySubject (Integer weekDay, TimetableSubject subject) {
        greenWeekDaySubjects.get(weekDay).add(subject);
    }
}
