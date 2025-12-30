package com.entitycore.modules.questbuilder.trigger;

public enum QbTriggerType {
    ENTER_AREA,
    EXIT_AREA,

    // Interaction triggers
    RIGHT_CLICK_BLOCK,
    PHYSICAL_TRIGGER;

    public static QbTriggerType from(String s) {
        if (s == null) return null;
        try {
            return QbTriggerType.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
