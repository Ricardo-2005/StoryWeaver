package com.storyweaver.importing.book.parser;

import com.storyweaver.shared.error.BadRequestException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class TxtEncodingDetector {
    private static final Charset GB18030 = Charset.forName("GB18030");

    public Detection detect(Path path) {
        try {
            byte[] prefix = new byte[3];
            int read;
            try (var input = Files.newInputStream(path)) {
                read = input.read(prefix);
            }
            boolean bom = read == 3 && prefix[0] == (byte) 0xEF && prefix[1] == (byte) 0xBB && prefix[2] == (byte) 0xBF;
            if (bom && decodes(path, StandardCharsets.UTF_8)) {
                return new Detection("UTF-8-BOM", "UTF-8", true);
            }
            if (decodes(path, StandardCharsets.UTF_8)) {
                return new Detection("UTF-8", "UTF-8", true);
            }
            if (decodes(path, GB18030)) {
                return new Detection("GB18030", "GB18030", false);
            }
            throw new BadRequestException("INVALID_TEXT_ENCODING", "TXT is not valid UTF-8, GB18030 or GBK text");
        } catch (IOException exception) {
            throw new BadRequestException("INVALID_TEXT_ENCODING", "TXT encoding could not be inspected");
        }
    }

    public Charset charset(String value) {
        return switch (value) {
            case "UTF-8" -> StandardCharsets.UTF_8;
            case "GB18030" -> GB18030;
            case "GBK" -> Charset.forName("GBK");
            default -> throw new BadRequestException("INVALID_TEXT_ENCODING", "Unsupported TXT encoding");
        };
    }

    public void validate(Path path, Charset charset) {
        if (!decodes(path, charset)) {
            throw new BadRequestException("INVALID_TEXT_ENCODING", "TXT cannot be decoded with the selected encoding");
        }
    }

    public Reader openStrictReader(Path path, Charset charset) throws IOException {
        var decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return new java.io.InputStreamReader(Files.newInputStream(path), decoder);
    }

    private boolean decodes(Path path, Charset charset) {
        try (Reader reader = openStrictReader(path, charset)) {
            char[] buffer = new char[16 * 1024];
            while (reader.read(buffer) >= 0) {
                // Streaming validation deliberately does not retain the decoded book.
            }
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    public record Detection(String detectedEncoding, String selectedEncoding, boolean confident) {}
}
