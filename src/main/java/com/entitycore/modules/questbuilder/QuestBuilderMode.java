package com.entitycore.modules.questbuilder;

public enum QuestBuilderMode {
    POINT,
    AREA_POS1,
    AREA_POS2,
    INFO,
    PREVIEW,
    IMPORT_WORLD_EDIT;

    public static QuestBuilderMode from(String s) {
        if (s == null) return POINT;
        try {
            return QuestBuilderMode.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return POINT;
        }
    }

    public QuestBuilderMode next() {
        QuestBuilderMode[] vals = values();
        return vals[(this.ordinal() + 1) % vals.length];
    }
}
