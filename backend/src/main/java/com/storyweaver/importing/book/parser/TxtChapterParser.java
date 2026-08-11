package com.storyweaver.importing.book.parser;

import com.storyweaver.importing.book.config.TxtImportProperties;
import com.storyweaver.importing.book.domain.TxtImportModels.ParseResult;
import com.storyweaver.importing.book.domain.TxtImportModels.ParsedChapter;
import com.storyweaver.shared.error.BadRequestException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TxtChapterParser {
    public static final String PARSER_VERSION = "txt-lines-v1";
    private static final Pattern CHINESE_NUMBERED = Pattern.compile(
            "^第[0-9零〇○〇一二三四五六七八九十百千万两]{1,16}(?:章|回|卷|部|篇|集)(?:[\\s：:、.．\\-—_]+.{1,80})?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINESE_VOLUME = Pattern.compile(
            "^(?:卷[0-9零〇○〇一二三四五六七八九十百千万两]{1,16}|第[0-9零〇○〇一二三四五六七八九十百千万两]{1,16}卷)(?:[\\s：:、.．\\-—_]+.{1,80})?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPECIAL =
            Pattern.compile("^(?:楔子|序章|序言|前言|引子|尾声|终章|后记|番外)(?:[\\s：:、.．\\-—_]+.{1,80})?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH = Pattern.compile(
            "^(?:chapter\\s+(?:[0-9]{1,8}|[ivxlcdm]{1,12})|prologue|epilogue)(?:[\\s:.\\-—_]+.{1,80})?$",
            Pattern.CASE_INSENSITIVE);

    private final TxtTextReader textReader;
    private final int maxHeadingCharacters;

    public TxtChapterParser(TxtTextReader textReader, TxtImportProperties properties) {
        this.textReader = textReader;
        this.maxHeadingCharacters = properties.parserMaxHeadingCharacters();
    }

    public ParseResult parse(Path path, Charset charset, String fallbackTitle) {
        MessageDigest digest = digest();
        Accumulator state = new Accumulator(safeTitle(fallbackTitle));
        textReader.forEachNormalizedLine(path, charset, (line, segment, offset) -> {
            digest.update(segment.getBytes(StandardCharsets.UTF_8));
            state.totalCharacters = offset + segment.length();
            String trimmed = line.strip();
            if (isHeading(trimmed)) {
                state.headingCount++;
                state.finish(offset);
                state.begin(trimmed, offset + segment.length());
            } else {
                state.consume(line, segment.length());
            }
        });
        state.finish(state.totalCharacters);
        if (state.totalCharacters == 0 || state.chapters.isEmpty()) {
            throw new BadRequestException("NO_TEXT_CONTENT", "TXT contains no importable text");
        }
        return new ParseResult(
                HexFormat.of().formatHex(digest.digest()),
                state.totalCharacters,
                state.headingCount,
                List.copyOf(state.chapters));
    }

    public boolean isHeading(String value) {
        if (value == null || value.isBlank() || value.length() > maxHeadingCharacters) return false;
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return CHINESE_NUMBERED.matcher(normalized).matches()
                || CHINESE_VOLUME.matcher(normalized).matches()
                || SPECIAL.matcher(normalized).matches()
                || ENGLISH.matcher(normalized).matches();
    }

    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String safeTitle(String value) {
        String title = value == null ? "导入原文" : value.strip();
        return title.isBlank() ? "导入原文" : title.substring(0, Math.min(160, title.length()));
    }

    private static final class Accumulator {
        private final List<ParsedChapter> chapters = new ArrayList<>();
        private String title;
        private long startOffset;
        private long characters;
        private int paragraphs;
        private boolean inParagraph;
        private boolean hasText;
        private long totalCharacters;
        private int headingCount;

        private Accumulator(String fallbackTitle) {
            begin(fallbackTitle, 0);
        }

        private void begin(String value, long start) {
            title = value.substring(0, Math.min(160, value.length()));
            startOffset = start;
            characters = 0;
            paragraphs = 0;
            inParagraph = false;
            hasText = false;
        }

        private void consume(String line, int segmentLength) {
            characters += segmentLength;
            if (line.isBlank()) {
                inParagraph = false;
            } else if (!inParagraph) {
                paragraphs++;
                inParagraph = true;
                hasText = true;
            } else {
                hasText = true;
            }
        }

        private void finish(long end) {
            if (!hasText || characters <= 0 || end <= startOffset) return;
            chapters.add(new ParsedChapter(title, startOffset, end, end - startOffset, paragraphs));
        }
    }
}
