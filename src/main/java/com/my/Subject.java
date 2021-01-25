package com.my;

public class Subject {
    private int hours;
    private int semesterWorkPoints;
    private int creditPoints;
    private int examPoints;
    private int courseWorkPoints;

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
}
