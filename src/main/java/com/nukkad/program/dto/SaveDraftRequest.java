package com.nukkad.program.dto;

import java.util.Map;

/** {@code answers} is merged into the existing draft's answers, not a full replacement — so
 *  autosaving one step never clobbers answers already saved from another step. */
public record SaveDraftRequest(Map<String, String> answers) {
}
