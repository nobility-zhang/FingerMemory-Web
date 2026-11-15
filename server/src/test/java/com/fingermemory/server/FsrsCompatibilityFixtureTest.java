package com.fingermemory.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FsrsCompatibilityFixtureTest {
    @Test
    void recordsTheLockedFsrs7ContractWithoutUsingAnIncompatibleJvmScheduler() throws Exception {
        String fixture = Files.readString(Path.of("src/test/resources/fsrs/fsrs7-golden.json"));

        assertThat((String) JsonPath.read(fixture, "$.source.packageVersion")).isEqualTo("6.0.0");
        assertThat((String) JsonPath.read(fixture, "$.source.modelVersion")).isEqualTo("fsrs-7");
        var document = JsonPath.parse(fixture);
        assertThat(document.read("$.source.weights", List.class)).hasSize(34);
        assertThat(document.read("$.sameCardRatingPreviews", List.class)).hasSize(4);
        assertThat(document.read("$.sequence", List.class)).hasSize(5);
    }
}
