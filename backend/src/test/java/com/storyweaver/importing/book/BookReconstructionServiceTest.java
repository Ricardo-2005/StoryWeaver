package com.storyweaver.importing.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.importing.book.application.BookReconstructionService;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class BookReconstructionServiceTest {
    @Test
    void splitsAtLineBoundariesAndEnforcesChunkLimit() throws Exception {
        String source = "第一段正文\n".repeat(8) + "第二段正文\n".repeat(8);

        var chunks = BookReconstructionService.chunks(new StringReader(source), 30);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(30));
        assertThat(String.join("", chunks)).isEqualTo(source);
    }
}
