package com.my.models;

import lombok.*;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AuthenticationData {
    @NotNull
    private String login;
    @NotNull
    private String password;
}
