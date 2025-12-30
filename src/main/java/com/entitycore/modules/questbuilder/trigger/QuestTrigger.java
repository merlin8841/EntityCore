package com.entitycore.modules.questbuilder.trigger;

public final class QuestTrigger {

    public final String areaId;
    public final QuestTriggerType type;
    public final String actionId;

    public QuestTrigger(String areaId, QuestTriggerType type, String actionId) {
        this.areaId = areaId;
        this.type = type;
        this.actionId = actionId;
    }
}
