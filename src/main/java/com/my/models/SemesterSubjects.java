package com.my.models;

import lombok.Data;

import java.util.List;

@Data
public class SemesterSubjects {
    private String name;
    private int number;
    private List<Subject> subjects;
}
