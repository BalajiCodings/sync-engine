package com.balaji.sync_engine.conflict;

public enum ResolutionAction {
    // FIELD_CONFLICT
    KEEP_CURRENT,
    ACCEPT_INCOMING,
    CUSTOM_VALUE,
    // DELETE_UPDATE_CONFLICT
    CONFIRM_DELETE,
    RESTORE_ENTITY
}
