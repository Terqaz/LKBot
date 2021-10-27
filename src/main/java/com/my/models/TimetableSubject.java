package com.my.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors (chain = true)
public class TimetableSubject {

    @NonNull @NotBlank
    String name;
    @NonNull
    String academicName;
    @NonNull
    String interval; // "8:00-9:30", "9:40-11:10" и тд.
    @NonNull @NotBlank
    String place;
}
