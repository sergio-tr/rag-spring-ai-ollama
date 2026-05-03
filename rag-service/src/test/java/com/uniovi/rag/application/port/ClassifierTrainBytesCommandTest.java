package com.uniovi.rag.application.port;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassifierTrainBytesCommandTest {

    @Test
    void equalsAndHashCode_considerArrayContents_andToStringIncludesLengths() {
        byte[] file1 = new byte[] {1, 2};
        byte[] file2 = new byte[] {1, 2};
        byte[] labels1 = new byte[] {3};
        byte[] labels2 = new byte[] {3};

        ClassifierTrainBytesCommand a =
                new ClassifierTrainBytesCommand(file1, "ds.csv", "m", "{\"a\":1}", labels1, "labels.json", 2, 8);
        ClassifierTrainBytesCommand b =
                new ClassifierTrainBytesCommand(file2, "ds.csv", "m", "{\"a\":1}", labels2, "labels.json", 2, 8);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("fileContent(len=2").contains("labelsFileContent(len=1").contains("epochs=2");
    }
}

