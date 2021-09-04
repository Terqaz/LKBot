package com.my.services;

import com.my.models.AuthenticationData;
import org.junit.jupiter.api.Test;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CipherServiceTest {

    @Test
    void encrypt_ThenSuccessDecrypt ()
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {

        CipherService cipherService = CipherService.getInstance();
        final AuthenticationData encryptedData = cipherService.encrypt("login", "password");
        assertEquals(
                new AuthenticationData("login", "password"),
                cipherService.decrypt(encryptedData));
    }

}