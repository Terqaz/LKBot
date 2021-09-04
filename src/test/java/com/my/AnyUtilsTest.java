package com.my;

import com.my.models.AuthenticationData;
import com.my.services.CipherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith (MockitoExtension.class)
class AnyUtilsTest {

    @Test
    void translateFromEnglishKeyboardLayout_isCorrect () {
        final List<String> expected =
                Arrays.asList("rjvfyls", "rjvFYls", "кjvFYls", "кjvF Yls", "rjvF Yls", "rjvF Yls 2", "кjvF Yls 2")
                .stream().map(Utils::translateFromEnglishKeyboardLayoutIfNeeds)
                .collect(Collectors.toList());

        final List<String> actual =
                Arrays.asList("команды", "комАНды", "кjvFYls", "кjvF Yls", "комА Нды", "комА Нды 2", "кjvF Yls 2");

        assertIterableEquals(expected, actual);
    }

    @Test
    void encrypt_ThenSuccessDecrypt ()
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {

        CipherService cipherService = CipherService.getInstance();
        final AuthenticationData encryptedData = cipherService.encrypt("login", "password");
        assertEquals(
                new AuthenticationData("login", "password"),
                cipherService.decrypt(encryptedData));
    }

    @Test
    void ParserUtils_capitalize_IsCorrect () {
        assertEquals("Тест", ParserUtils.capitalize("Тест"));
        assertEquals("Тест", ParserUtils.capitalize("ТЕСТ"));
        assertEquals("Тест", ParserUtils.capitalize("тест"));
        assertThrows(StringIndexOutOfBoundsException.class,
                () -> ParserUtils.capitalize(""));
    }
}