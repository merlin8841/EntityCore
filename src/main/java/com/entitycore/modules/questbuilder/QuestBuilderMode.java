package com.entitycore.modules.questbuilder;

public enum QuestBuilderMode {
    POINT,

    // New: WorldEdit-style alternating selection
    AREA_SET,

    // Legacy/manual (kept for compatibility; not used in normal cycling)
    AREA_POS1,
    AREA_POS2,

    INFO,
    PREVIEW,
    IMPORT_WORLD_EDIT,

    // New: popup editor
    EDITOR;

    public static QuestBuilderMode from(String s) {
        if (s == null) return POINT;
        try {
            return QuestBuilderMode.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return POINT;
        }
    }

    /**
     * Intentionally omits AREA_POS1/AREA_POS2 from the normal cycle.
     */
    public QuestBuilderMode next() {
        return switch (this) {
            case POINT -> AREA_SET;
            case AREA_SET -> INFO;
            case INFO -> PREVIEW;
            case PREVIEW -> IMPORT_WORLD_EDIT;
            case IMPORT_WORLD_EDIT -> EDITOR;
            case EDITOR -> POINT;

            case AREA_POS1, AREA_POS2 -> INFO;
        };
    }
}
