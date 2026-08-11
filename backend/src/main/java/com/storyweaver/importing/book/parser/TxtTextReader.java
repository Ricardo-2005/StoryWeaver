package com.storyweaver.importing.book.parser;

import com.storyweaver.shared.error.BadRequestException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TxtTextReader {
    private final TxtEncodingDetector encodings;

    public TxtTextReader(TxtEncodingDetector encodings) {
        this.encodings = encodings;
    }

    public void forEachNormalizedLine(Path path, Charset charset, LineConsumer consumer) {
        try (BufferedReader reader = new BufferedReader(encodings.openStrictReader(path, charset), 64 * 1024)) {
            String line;
            boolean first = true;
            int blankRun = 0;
            long offset = 0;
            while ((line = reader.readLine()) != null) {
                if (first && !line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
                first = false;
                line = line.replace("\u0000", "");
                boolean blank = line.isBlank();
                if (blank && ++blankRun > 2) continue;
                if (!blank) blankRun = 0;
                String segment = line + '\n';
                consumer.accept(line, segment, offset);
                offset += segment.length();
            }
        } catch (IOException exception) {
            throw new BadRequestException(
                    "INVALID_TEXT_ENCODING", "TXT could not be decoded with the selected encoding");
        }
    }

    public String readRange(Path path, Charset charset, long start, long end, int maxCharacters) {
        if (start < 0 || end <= start || maxCharacters <= 0) return "";
        StringBuilder result = new StringBuilder(Math.min(maxCharacters, 16 * 1024));
        forEachNormalizedLine(path, charset, (line, segment, offset) -> {
            long segmentEnd = offset + segment.length();
            if (segmentEnd <= start || offset >= end || result.length() >= maxCharacters) return;
            int from = (int) Math.max(0, start - offset);
            int to = (int) Math.min(segment.length(), end - offset);
            int remaining = maxCharacters - result.length();
            if (to > from) result.append(segment, from, Math.min(to, from + remaining));
        });
        return result.toString();
    }

    public void forEachRange(Path path, Charset charset, List<TextRange> requestedRanges, RangeConsumer consumer) {
        List<TextRange> ranges = requestedRanges.stream()
                .sorted(Comparator.comparingLong(TextRange::startOffset))
                .toList();
        if (ranges.isEmpty()) return;
        int[] index = {0};
        StringBuilder[] content = {new StringBuilder()};
        forEachNormalizedLine(path, charset, (line, segment, offset) -> {
            while (index[0] < ranges.size()) {
                TextRange range = ranges.get(index[0]);
                long segmentEnd = offset + segment.length();
                if (segmentEnd <= range.startOffset()) return;
                if (offset >= range.endOffset()) {
                    consumer.accept(range.id(), content[0].toString());
                    content[0] = new StringBuilder();
                    index[0]++;
                    continue;
                }
                int from = (int) Math.max(0, range.startOffset() - offset);
                int to = (int) Math.min(segment.length(), range.endOffset() - offset);
                if (to > from) content[0].append(segment, from, to);
                if (segmentEnd >= range.endOffset()) {
                    consumer.accept(range.id(), content[0].toString());
                    content[0] = new StringBuilder();
                    index[0]++;
                    continue;
                }
                return;
            }
        });
        while (index[0] < ranges.size()) {
            TextRange range = ranges.get(index[0]++);
            consumer.accept(range.id(), content[0].toString());
            content[0] = new StringBuilder();
        }
    }

    @FunctionalInterface
    public interface LineConsumer {
        void accept(String line, String normalizedSegment, long startOffset);
    }

    public record TextRange(java.util.UUID id, long startOffset, long endOffset) {}

    @FunctionalInterface
    public interface RangeConsumer {
        void accept(java.util.UUID id, String content);
    }
}
