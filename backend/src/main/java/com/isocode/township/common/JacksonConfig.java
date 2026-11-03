package com.isocode.township.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer decimalsAsPlainStrings() {
        return builder -> builder.serializerByType(BigDecimal.class, new PlainDecimalStringSerializer());
    }

    static final class PlainDecimalStringSerializer extends StdSerializer<BigDecimal> {

        PlainDecimalStringSerializer() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeString(value.toPlainString());
        }
    }
}
