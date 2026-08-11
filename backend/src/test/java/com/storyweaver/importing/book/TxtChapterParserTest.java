package com.storyweaver.importing.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.importing.book.config.TxtImportProperties;
import com.storyweaver.importing.book.parser.TxtChapterParser;
import com.storyweaver.importing.book.parser.TxtEncodingDetector;
import com.storyweaver.importing.book.parser.TxtTextReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class TxtChapterParserTest {
    @TempDir
    Path temporaryDirectory;

    private final TxtEncodingDetector encodings = new TxtEncodingDetector();
    private final TxtTextReader textReader = new TxtTextReader(encodings);
    private final TxtChapterParser parser = new TxtChapterParser(textReader, properties(temporaryDirectory));

    @Test
    void recognizesSupportedIndependentHeadingLinesWithoutSplittingBodyMentions() throws Exception {
        Path source = temporaryDirectory.resolve("headings.txt");
        Files.writeString(
                source,
                """
                序章
                他在正文中说第一章只是个代号，这里不应切分。
                第001章 雾港
                正文一。
                第二回：归来
                正文二。
                卷三 远行
                卷内引子。
                Chapter 1 - Arrival
                English body.
                楔子
                楔子正文。
                番外 雨夜
                番外正文。
                后记
                完。
                """);

        var result = parser.parse(source, StandardCharsets.UTF_8, "fallback");

        assertThat(result.headingCount()).isEqualTo(8);
        assertThat(result.chapters())
                .extracting(value -> value.title())
                .containsExactly("序章", "第001章 雾港", "第二回：归来", "卷三 远行", "Chapter 1 - Arrival", "楔子", "番外 雨夜", "后记");
        String first = textReader.readRange(
                source,
                StandardCharsets.UTF_8,
                result.chapters().getFirst().startOffset(),
                result.chapters().getFirst().endOffset(),
                5_000);
        assertThat(first).contains("正文中说第一章").doesNotContain("第001章");
    }

    @Test
    void keepsAHeadinglessBookAsOneCandidate() throws Exception {
        Path source = temporaryDirectory.resolve("plain.txt");
        Files.writeString(source, "第一段。\n\n第二段。", StandardCharsets.UTF_8);

        var result = parser.parse(source, StandardCharsets.UTF_8, "无章节书");

        assertThat(result.headingCount()).isZero();
        assertThat(result.chapters()).singleElement().satisfies(chapter -> {
            assertThat(chapter.title()).isEqualTo("无章节书");
            assertThat(chapter.paragraphCount()).isEqualTo(2);
        });
    }

    @Test
    void detectsUtf8BomAndGb18030WithoutLoadingTheWholeBook() throws Exception {
        Path bom = temporaryDirectory.resolve("bom.txt");
        Files.write(
                bom,
                concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, "序章\n内容".getBytes(StandardCharsets.UTF_8)));
        Path gb = temporaryDirectory.resolve("gb.txt");
        Files.write(gb, "第一章\n中文内容".getBytes(Charset.forName("GB18030")));

        assertThat(encodings.detect(bom))
                .extracting(
                        value -> value.detectedEncoding(),
                        value -> value.selectedEncoding(),
                        value -> value.confident())
                .containsExactly("UTF-8-BOM", "UTF-8", true);
        assertThat(encodings.detect(gb))
                .extracting(
                        value -> value.detectedEncoding(),
                        value -> value.selectedEncoding(),
                        value -> value.confident())
                .containsExactly("GB18030", "GB18030", false);
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private TxtImportProperties properties(Path storage) {
        return new TxtImportProperties(DataSize.ofMegabytes(20), storage, Duration.ofHours(24), 5_000, 120, 12_000);
    }
}
