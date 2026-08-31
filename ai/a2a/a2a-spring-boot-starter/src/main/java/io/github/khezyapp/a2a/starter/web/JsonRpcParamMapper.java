package io.github.khezyapp.a2a.starter.web;

import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.FilePart;
import org.a2aproject.sdk.spec.FileWithUri;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TextPart;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Maps JSON-RPC {@code params} nodes onto the SDK spec param records. The SDK ships no
 * Jackson creator metadata (it serializes via Gson internally), so request bodies are
 * read as a tree and translated here: {@code Part} polymorphism is resolved from the
 * wire {@code kind} discriminator ({@code text}/{@code data}/{@code file}).
 */
final class JsonRpcParamMapper {

    private static final String KIND_TEXT = "text";
    private static final String KIND_DATA = "data";
    private static final String KIND_FILE = "file";

    private final ObjectMapper objectMapper;

    JsonRpcParamMapper(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    MessageSendParams messageSendParams(final JsonNode params) {
        final var messageNode = params.path("message");
        if (!messageNode.isObject()) {
            throw new InvalidParamsError("params.message object is required");
        }
        return new MessageSendParams(
                message(messageNode),
                configuration(params.path("configuration")),
                mapOrNull(params.path("metadata")),
                textOrNull(params.path("tenant"))
        );
    }

    TaskQueryParams taskQueryParams(final JsonNode params) {
        return new TaskQueryParams(
                requiredText(params, "id"),
                integerOrNull(params.path("historyLength")),
                textOrNull(params.path("tenant"))
        );
    }

    /**
     * Wire {@code tasks/cancel} params carry metadata too; the dispatcher port takes {@link TaskIdParams}.
     */
    TaskIdParams cancelTaskParams(final JsonNode params) {
        return new TaskIdParams(
                requiredText(params, "id"),
                textOrNull(params.path("tenant"))
        );
    }

    private Message message(final JsonNode node) {
        final var parts = parts(node.path("parts"));
        try {
            return Message.builder()
                    .role(role(node.path("role")))
                    .parts(parts)
                    .messageId(textOrDefault(node.path("messageId"), UUID.randomUUID().toString()))
                    .contextId(textOrNull(node.path("contextId")))
                    .taskId(textOrNull(node.path("taskId")))
                    .referenceTaskIds(stringList(node.path("referenceTaskIds")))
                    .metadata(mapOrNull(node.path("metadata")))
                    .extensions(stringList(node.path("extensions")))
                    .build();
        } catch (final IllegalArgumentException e) {
            throw new InvalidParamsError(e.getMessage());
        }
    }

    private Message.Role role(final JsonNode roleNode) {
        final var rawRole = roleNode.isString() ? roleNode.asString() : Message.Role.ROLE_USER.name();
        try {
            return Message.Role.valueOf(rawRole);
        } catch (final IllegalArgumentException e) {
            throw new InvalidParamsError("Unknown message role: " + rawRole);
        }
    }

    private List<Part<?>> parts(final JsonNode partsNode) {
        if (!partsNode.isArray() || partsNode.isEmpty()) {
            throw new InvalidParamsError("params.message.parts must be a non-empty array");
        }
        final var parts = new ArrayList<Part<?>>();
        for (final var partNode : partsNode) {
            final var kind = partNode.path("kind").asString("");
            switch (kind) {
                case KIND_TEXT -> parts.add(new TextPart(partNode.path("text").asString(""),
                        mapOrNull(partNode.path("metadata"))));
                case KIND_DATA -> parts.add(new DataPart(objectMapper.convertValue(partNode.path("data"), Object.class),
                        mapOrNull(partNode.path("metadata"))));
                case KIND_FILE -> parts.add(filePart(partNode));
                default -> throw new InvalidParamsError("Unsupported part kind: " + kind);
            }
        }
        return List.copyOf(parts);
    }

    /**
     * v1 supports URI-referenced files only; base64 file parts need the sdk-common runtime.
     */
    private Part<?> filePart(final JsonNode partNode) {
        final var file = partNode.path("file");
        final var uri = textOrNull(file.path("uri"));
        if (Objects.isNull(uri)) {
            throw new InvalidParamsError("Only file parts with a 'uri' are supported");
        }
        return new FilePart(
                new FileWithUri(
                        file.path("mimeType").asString(null),
                        file.path("name").asString(null),
                        uri
                )
        );
    }

    private MessageSendConfiguration configuration(final JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        try {
            return new MessageSendConfiguration(
                    stringList(node.path("acceptedOutputModes")),
                    integerOrNull(node.path("historyLength")),
                    null,
                    Objects.requireNonNullElse(booleanOrNull(node.path("returnImmediately")), false)
            );
        } catch (final IllegalArgumentException e) {
            throw new InvalidParamsError("Invalid params.configuration: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOrNull(final JsonNode node) {
        return node.isObject() ? objectMapper.convertValue(node, Map.class) : null;
    }

    private List<String> stringList(final JsonNode node) {
        if (!node.isArray()) {
            return null;
        }
        final var values = new ArrayList<String>();
        for (final var element : node) {
            values.add(element.asString());
        }
        return List.copyOf(values);
    }

    private String requiredText(final JsonNode params,
                                final String field) {
        final var value = textOrNull(params.path(field));
        if (Objects.isNull(value) || value.isBlank()) {
            throw new InvalidParamsError("params." + field + " is required");
        }
        return value;
    }

    private String textOrNull(final JsonNode node) {
        if (!node.isString() && !node.isMissingNode() && !node.isNull() && node.isValueNode()) {
            return node.asString();
        }
        return node.isString() ? node.asString() : null;
    }

    private String textOrDefault(final JsonNode node,
                                 final String fallback) {
        final var value = textOrNull(node);
        return Objects.isNull(value) || value.isBlank() ? fallback : value;
    }

    private Integer integerOrNull(final JsonNode node) {
        return node.isNumber() ? node.intValue() : null;
    }

    private Boolean booleanOrNull(final JsonNode node) {
        return node.isBoolean() ? node.booleanValue() : null;
    }
}
