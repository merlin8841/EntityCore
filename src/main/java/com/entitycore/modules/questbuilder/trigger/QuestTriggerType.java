package com.entitycore.modules.questbuilder.trigger;

public enum QuestTriggerType {
    ENTER_AREA,
    EXIT_AREA,

    // Interaction triggers
    RIGHT_CLICK_BLOCK,
    PHYSICAL_TRIGGER;

    public static QuestTriggerType from(String s) {
        if (s == null) return null;
        try {
            return QuestTriggerType.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
