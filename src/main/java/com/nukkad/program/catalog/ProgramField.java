package com.nukkad.program.catalog;

import java.util.List;

/** One application question. {@code key} is what {@link com.nukkad.program.entity.ProgramApplication}
 *  stores the answer under — stable once a program has live applications, since it's also the join key
 *  between saved answers and this field's current label/type/options for rendering. */
public record ProgramField(
        String key,
        String label,
        ProgramFieldType type,
        boolean required,
        List<String> options
) {
    public ProgramField(String key, String label, ProgramFieldType type, boolean required) {
        this(key, label, type, required, List.of());
    }
}
