package io.github.khezyapp.api.audit.mixin;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.Objects;

public class AuditThrowableSerializer extends StdSerializer<Throwable> {

    public AuditThrowableSerializer() {
        super(Throwable.class);
    }

    @Override
    public void serialize(final Throwable value,
                          final JsonGenerator gen,
                          final SerializationContext ctxt) throws JacksonException {
        if (Objects.isNull(value)) {
            gen.writeNull();
            return;
        }

        gen.writeStartObject();

        gen.writeStringProperty("type", value.getClass().getName());
        gen.writeStringProperty("message", value.getMessage());
        if (Objects.nonNull(value.getCause())) {
            gen.writeStringProperty("cause", value.getCause().getMessage());
        }

        gen.writeEndObject();
    }
}
