package io.github.mainalisandeep.cvgen.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mainalisandeep.cvgen.common.exception.BadRequestException;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.config.CvProperties;
import io.github.mainalisandeep.cvgen.enums.CvSectionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The only gate a content document passes through before it is stored, and the only place the
 * starter document is written.
 * <p>
 * Checks shape, not meaning: the tree must be an object, must carry a schema version this
 * server understands, and must fit the size cap. Field-level rules - required names, date
 * sanity, per-section item caps - belong to the editing phase and are deliberately absent, so
 * a half-filled draft can still be saved.
 * <p>
 * Nothing here rejects an unknown section type. An older server must be able to hand a newer
 * client's document back untouched rather than quietly destroying the parts it cannot draw.
 */
@Component
@RequiredArgsConstructor
public class CvContentValidator {

    /** The only document version this server writes. Present from day one; free now, expensive later. */
    public static final int SCHEMA_VERSION = 1;

    private static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    private static final String FIELD_BASICS = "basics";
    private static final String FIELD_SECTIONS = "sections";

    private final ObjectMapper objectMapper;
    private final CvProperties cvProperties;

    /**
     * Returns the document to persist: the caller's tree with a schema version filled in when it
     * omitted one.
     *
     * @throws BadRequestException when the tree is not an object, carries a version this server
     *                             cannot write, or exceeds the configured size cap
     */
    public JsonNode validate(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw new BadRequestException(ErrorConstantValue.CV_CONTENT_INVALID);
        }

        ObjectNode document = content.deepCopy();
        JsonNode schemaVersion = document.get(FIELD_SCHEMA_VERSION);

        if (schemaVersion == null || schemaVersion.isNull()) {
            document.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION);
        } else if (!schemaVersion.isInt() || schemaVersion.intValue() != SCHEMA_VERSION) {
            throw new BadRequestException(ErrorConstantValue.CV_SCHEMA_VERSION_UNSUPPORTED,
                    schemaVersion.asText());
        }

        long size = serialisedSize(document);
        if (size > cvProperties.getMaxContentBytes()) {
            throw new BadRequestException(ErrorConstantValue.CV_CONTENT_TOO_LARGE);
        }

        return document;
    }

    /**
     * The v1 starter document, used when a create request omits {@code content}.
     * <p>
     * Section order is array order - there is no position field to renumber - and the two seeded
     * sections are empty rather than absent so the editor has something to render into.
     */
    public JsonNode starterDocument() {
        ObjectNode document = objectMapper.createObjectNode();
        document.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION);

        ObjectNode basics = document.putObject(FIELD_BASICS);
        basics.put("fullName", "");
        basics.put("headline", "");
        basics.put("email", "");
        basics.put("phone", "");
        basics.put("location", "");
        basics.putArray("links");

        ArrayNode sections = document.putArray(FIELD_SECTIONS);
        sections.add(section(CvSectionType.EXPERIENCE, "Experience"));
        sections.add(section(CvSectionType.EDUCATION, "Education"));

        return document;
    }

    private ObjectNode section(CvSectionType type, String title) {
        ObjectNode section = objectMapper.createObjectNode();
        section.put("id", UUID.randomUUID().toString());
        section.put("type", type.name());
        section.put("title", title);
        section.put("visible", true);
        section.putArray("items");
        return section;
    }

    /**
     * Size of the document as it will be written, not as it arrived: whitespace in the request
     * body is not what the column stores, so measuring the raw payload would reject documents
     * that fit and accept ones that do not.
     */
    private long serialisedSize(JsonNode document) {
        try {
            return objectMapper.writeValueAsString(document).getBytes(StandardCharsets.UTF_8).length;
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorConstantValue.CV_CONTENT_INVALID);
        }
    }
}
