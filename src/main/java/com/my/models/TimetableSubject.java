package com.my.models;

import lombok.*;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors (chain = true)
@EqualsAndHashCode
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
