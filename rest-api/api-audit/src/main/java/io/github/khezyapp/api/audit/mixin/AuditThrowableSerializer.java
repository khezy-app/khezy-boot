package io.github.khezyapp.api.audit.mixin;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Objects;

public class AuditThrowableSerializer extends StdSerializer<Throwable> {

    public AuditThrowableSerializer() {
        super(Throwable.class);
    }

    @Override
    public void serialize(final Throwable value,
                          final JsonGenerator gen,
                          final SerializerProvider serializerProvider) throws IOException {
        if (Objects.isNull(value)) {
            gen.writeNull();
            return;
        }

        gen.writeStartObject();

        gen.writeStringField("type", value.getClass().getName());
        gen.writeStringField("message", value.getMessage());
        if (Objects.nonNull(value.getCause())) {
            gen.writeStringField("cause", value.getCause().getMessage());
        }

        gen.writeEndObject();
    }
}
