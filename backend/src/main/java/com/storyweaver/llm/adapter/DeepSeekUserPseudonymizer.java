package com.storyweaver.llm.adapter;

import com.storyweaver.llm.config.DeepSeekProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekUserPseudonymizer {
    private final byte[] secret;

    public DeepSeekUserPseudonymizer(DeepSeekProperties properties) {
        this.secret = properties.userIdSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String pseudonym(UUID userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(userId.toString().getBytes(StandardCharsets.UTF_8));
            return "sw_" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
