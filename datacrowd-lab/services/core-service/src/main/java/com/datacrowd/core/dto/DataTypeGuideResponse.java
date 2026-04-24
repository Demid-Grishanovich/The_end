package com.datacrowd.core.dto;

public class DataTypeGuideResponse {
    public String description;
    public String inputFormat;
    public String outputFormat;

    public DataTypeGuideResponse(String description, String inputFormat, String outputFormat) {
        this.description = description;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
    }
}