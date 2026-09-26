package com.nukkad.program.catalog;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * (De)serializes a program's structured content — its journey, benefits and application steps — to
 * and from the JSON text stored in {@code programs}' {@code journey_json} / {@code benefits_json} /
 * {@code application_steps_json} columns (see {@link com.nukkad.program.entity.Program}'s doc comment
 * for why these are JSON text rather than child tables: they're always authored and read as one whole
 * unit from the admin form, never queried or joined on individually). Reuses the codebase's existing
 * "store a JSON string, decode it in application code" convention (see {@code AuditLog.details}) rather
 * than introducing Hibernate's generic JSON-to-POJO type binding, which this codebase doesn't use
 * anywhere else and can't be verified against a real database in this environment.
 * <p>
 * A blank/null column reads back as an empty list — a program with no journey/benefits/application
 * steps yet is a normal, valid state (e.g. a freshly created draft), not a data error.
 */
@Component
public class ProgramContentCodec {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ProgramJourneyPhase>> JOURNEY = new TypeReference<>() {};
    private static final TypeReference<List<ProgramBenefit>> BENEFITS = new TypeReference<>() {};
    private static final TypeReference<List<ProgramStep>> APPLICATION_STEPS = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ProgramContentCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> readStrings(String json) {
        return read(json, STRING_LIST);
    }

    public String writeStrings(List<String> value) {
        return write(value);
    }

    public List<ProgramJourneyPhase> readJourney(String json) {
        return read(json, JOURNEY);
    }

    public String writeJourney(List<ProgramJourneyPhase> value) {
        return write(value);
    }

    public List<ProgramBenefit> readBenefits(String json) {
        return read(json, BENEFITS);
    }

    public String writeBenefits(List<ProgramBenefit> value) {
        return write(value);
    }

    public List<ProgramStep> readApplicationSteps(String json) {
        return read(json, APPLICATION_STEPS);
    }

    public String writeApplicationSteps(List<ProgramStep> value) {
        return write(value);
    }

    private <T> List<T> read(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Corrupt program content JSON", e);
        }
    }

    private <T> String write(List<T> value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize program content", e);
        }
    }
}
