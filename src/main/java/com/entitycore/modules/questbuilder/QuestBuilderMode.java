package com.entitycore.modules.questbuilder;

public enum QuestBuilderMode {
    POINT,

    // WorldEdit-style alternating selection + resize
    AREA_SET,

    INFO,
    PREVIEW,
    IMPORT_WORLD_EDIT,

    // Chat/Popup editor
    EDITOR,

    // Bedrock-friendly delete mode (no left-click needed)
    DELETE;

    public static QuestBuilderMode from(String s) {
        if (s == null) return POINT;
        try {
            return QuestBuilderMode.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return POINT;
        }
    }

    public QuestBuilderMode next() {
        return switch (this) {
            case POINT -> AREA_SET;
            case AREA_SET -> INFO;
            case INFO -> PREVIEW;
            case PREVIEW -> IMPORT_WORLD_EDIT;
            case IMPORT_WORLD_EDIT -> EDITOR;
            case EDITOR -> DELETE;
            case DELETE -> POINT;
        };
    }
}
