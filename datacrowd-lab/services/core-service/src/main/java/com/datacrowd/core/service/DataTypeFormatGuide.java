package com.datacrowd.core.service;

import com.datacrowd.core.entity.DataType;

public class DataTypeFormatGuide {

    private DataTypeFormatGuide() {

    }

    public static String getInputFormat(DataType type) {
        return type.getInputFormat();
    }

    public static String getOutputFormat(DataType type) {
        return type.getOutputFormat();
    }

    public static String getDescription(DataType type) {
        return type.getDescription();
    }
}