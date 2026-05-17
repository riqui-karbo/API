package apigenerica.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class ServicioCifrado {
    private static final String LLAVE = "12345678901234561234567890123456";

    public String encriptar(String dato) throws Exception {
        SecretKeySpec sk = new SecretKeySpec(LLAVE.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, sk);
        return Base64.getEncoder().encodeToString(c.doFinal(dato.getBytes(StandardCharsets.UTF_8)));
    }

    public String desencriptar(String dato) throws Exception {
        SecretKeySpec sk = new SecretKeySpec(LLAVE.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, sk);
        return new String(c.doFinal(Base64.getDecoder().decode(dato)), StandardCharsets.UTF_8);
    }
}