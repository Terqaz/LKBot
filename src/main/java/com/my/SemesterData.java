package com.my;

import java.util.List;

public class SemesterData {
    private String name;
    private int number;
    private List<Subject> subjects;

    public String getName () {
        return name;
    }

    public void setName (String name) {
        this.name = name;
    }

    public int getNumber () {
        return number;
    }

    public void setNumber (int number) {
        this.number = number;
    }

    public List<Subject> getSubjects () {
        return subjects;
    }

    public void setSubjects (List<Subject> subjects) {
        this.subjects = subjects;
    }
}
