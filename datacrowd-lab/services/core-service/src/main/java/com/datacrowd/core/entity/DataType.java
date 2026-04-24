package com.datacrowd.core.entity;

public enum DataType {

    TEXT(
            "Text classification / sentiment analysis",
            "CSV file with columns: id, text. Example row: 1,\"Label this sentence\"",
            "{\"taskId\":\"...\",\"input\":{\"text\":\"...\"},\"label\":\"positive\",\"confidence\":0.95,\"reviewedBy\":[\"uuid1\"]}"
    ),
    IMAGE(
            "Image labeling / object detection",
            "ZIP archive containing: manifest.jsonl + images/ folder. Each manifest line: {\"id\":\"1\",\"file\":\"images/cat.jpg\",\"type\":\"image\"}",
            "{\"taskId\":\"...\",\"input\":{\"file\":\"images/cat.jpg\"},\"label\":\"cat\",\"bbox\":{\"x\":10,\"y\":20,\"w\":100,\"h\":80},\"reviewedBy\":[\"uuid1\"]}"
    ),
    AUDIO(
            "Audio transcription / classification",
            "ZIP archive containing: manifest.jsonl + audio/ folder. Each manifest line: {\"id\":\"1\",\"file\":\"audio/clip.mp3\",\"type\":\"audio\"}",
            "{\"taskId\":\"...\",\"input\":{\"file\":\"audio/clip.mp3\"},\"transcript\":\"Hello world\",\"language\":\"en\",\"reviewedBy\":[\"uuid1\"]}"
    ),
    CODE(
            "Code review / bug detection",
            "JSONL file. Each line: {\"id\":\"1\",\"code\":\"def foo(): pass\",\"language\":\"python\"}",
            "{\"taskId\":\"...\",\"input\":{\"code\":\"...\",\"language\":\"python\"},\"hasBug\":true,\"severity\":\"high\",\"reviewedBy\":[\"uuid1\"]}"
    ),
    MATH(
            "Math solution verification",
            "JSONL file. Each line: {\"id\":\"1\",\"problem\":\"2+2=?\",\"solution\":\"4\"}",
            "{\"taskId\":\"...\",\"input\":{\"problem\":\"2+2=?\",\"solution\":\"4\"},\"isCorrect\":true,\"reviewedBy\":[\"uuid1\"]}"
    );

    private final String description;
    private final String inputFormat;
    private final String outputFormat;

    DataType(String description, String inputFormat, String outputFormat) {
        this.description = description;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
    }

    public String getDescription()  { return description; }
    public String getInputFormat()  { return inputFormat; }
    public String getOutputFormat() { return outputFormat; }
}