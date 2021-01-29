package com.my;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Subject {
    private String name;
    private int hours = -1;
    private int semesterWorkPoints = -1;
    private int creditPoints = -1;
    private int examPoints = -1;
    private int courseWorkPoints = -1;

    public String getName () {
        return name;
    }

    public void setName (String name) {
        this.name = name;
    }

    public int getHours () {
        return hours;
    }

    public void setHours (int hours) {
        this.hours = hours;
    }

    public int getSemesterWorkPoints () {
        return semesterWorkPoints;
    }

    public void setSemesterWorkPoints (int semesterWorkPoints) {
        this.semesterWorkPoints = semesterWorkPoints;
    }

    public int getCreditPoints () {
        return creditPoints;
    }

    public void setCreditPoints (int creditPoints) {
        this.creditPoints = creditPoints;
    }

    public int getExamPoints () {
        return examPoints;
    }

    public void setExamPoints (int examPoints) {
        this.examPoints = examPoints;
    }

    public int getCourseWorkPoints () {
        return courseWorkPoints;
    }

    public void setCourseWorkPoints (int courseWorkPoints) {
        this.courseWorkPoints = courseWorkPoints;
    }

    @JsonIgnore
    public boolean isPractice () {
        return getName().toLowerCase().contains("практика");
    }
}
