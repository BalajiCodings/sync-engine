package com.balaji.sync_engine.dto;

import com.balaji.sync_engine.conflict.ResolutionAction;
import io.swagger.v3.oas.annotations.media.Schema;

public record ResolveConflictRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "FIELD_CONFLICT: KEEP_CURRENT | ACCEPT_INCOMING | CUSTOM_VALUE. "
                        + "DELETE_UPDATE_CONFLICT: CONFIRM_DELETE | RESTORE_ENTITY.")
        ResolutionAction action,
        @Schema(description = "Required only when action is CUSTOM_VALUE")
        String customValue
) {}