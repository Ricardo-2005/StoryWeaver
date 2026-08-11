package com.storyweaver.importing.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyweaver.importing.book.config.TxtImportProperties;
import com.storyweaver.importing.book.storage.ImportSourceStorage;
import com.storyweaver.shared.error.BadRequestException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class ImportSourceStorageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsToUuidPathAndComputesSha256() {
        ImportSourceStorage storage = new ImportSourceStorage(properties(DataSize.ofBytes(20)));

        var stored = storage.store(new ByteArrayInputStream("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(stored.storageKey()).matches("[0-9a-f-]{36}\\.txt");
        assertThat(stored.sha256()).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(Files.isRegularFile(temporaryDirectory.resolve(stored.storageKey())))
                .isTrue();
    }

    @Test
    void rejectsBytesPastTheConfiguredLimitAndRemovesPartialFile() {
        ImportSourceStorage storage = new ImportSourceStorage(properties(DataSize.ofBytes(4)));

        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(new byte[5])))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("20 MB");
        assertThat(temporaryDirectory.toFile().list()).isEmpty();
    }

    @Test
    void acceptsExactlyTwentyMebibytesAndRejectsTheNextByte() {
        long limit = 20L * 1024 * 1024;
        ImportSourceStorage storage = new ImportSourceStorage(properties(DataSize.ofBytes(limit)));

        assertThat(storage.store(new RepeatedByteInputStream(limit)).sizeBytes())
                .isEqualTo(limit);
        assertThatThrownBy(() -> storage.store(new RepeatedByteInputStream(limit + 1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("20 MB");
    }

    private TxtImportProperties properties(DataSize maxSize) {
        return new TxtImportProperties(maxSize, temporaryDirectory, Duration.ofHours(24), 5_000, 120, 12_000);
    }

    private static final class RepeatedByteInputStream extends InputStream {
        private long remaining;

        private RepeatedByteInputStream(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read() {
            if (remaining == 0) return -1;
            remaining--;
            return 'x';
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (remaining == 0) return -1;
            int count = (int) Math.min(length, remaining);
            java.util.Arrays.fill(buffer, offset, offset + count, (byte) 'x');
            remaining -= count;
            return count;
        }
    }
}
