package com.my;

import org.apache.http.auth.AuthenticationException;

import java.io.IOException;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        LstuParser lstuParser = new LstuParser();
        try {
            lstuParser.login("s11916327", "f7LLDSJibCw8QNGeR6");
            final List<SemesterData> semestersData = lstuParser.getSemestersData();
            lstuParser.logout();
        } catch (AuthenticationException e) {
            e.printStackTrace();
        }
    }
}
