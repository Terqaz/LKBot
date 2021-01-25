package com.my;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        LstuParser lstuParser = new LstuParser();
        try {
            lstuParser.auth();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
