package com.schoolerp.shared.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.schoolerp.shared.config.AppProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoServiceTest {

    private static AppProperties propsWithKey(String key) {
        return new AppProperties(null, null, null, new AppProperties.Crypto(key), null, null, null, null, null);
    }

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptThenDecryptReturnsOriginal() {
        CryptoService crypto = new CryptoService(propsWithKey(KEY));
        String encrypted = crypto.encrypt("s3cret-password");
        assertNotEquals("s3cret-password", encrypted);
        assertEquals("s3cret-password", crypto.decrypt(encrypted));
    }

    @Test
    void sameInputProducesDifferentCiphertext() {
        CryptoService crypto = new CryptoService(propsWithKey(KEY));
        assertNotEquals(crypto.encrypt("value"), crypto.encrypt("value"));
    }

    @Test
    void rejectsKeyWithWrongLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new CryptoService(propsWithKey(shortKey)));
    }
}
