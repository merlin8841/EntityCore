package com.entitycore.modules.questbuilder;

public enum QuestBuilderMode {
    POINT,
    AREA_POS1,
    AREA_POS2,
    INFO,
    PREVIEW;

    public static QuestBuilderMode from(String s) {
        try {
            return QuestBuilderMode.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return POINT;
        }
    }
}
