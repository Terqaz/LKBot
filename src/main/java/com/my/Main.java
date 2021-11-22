package com.my;

import com.my.exceptions.ApplicationStopNeedsException;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class Main {

    public static void main (String[] args)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {

        Bot bot = new Bot();
        try {
            bot.startProcessing();
        } catch (ApplicationStopNeedsException e) {
            e.printStackTrace();
            bot.endProcessing();
        }
    }
}
