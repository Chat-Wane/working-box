package fr.sigma.box;

import io.jaegertracing.internal.JaegerTracer;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.jaegertracing.internal.propagation.B3TextMapCodec;

import java.util.Objects;


@Configuration
public class MyTracingConfig {

    @Value("${spring.application.name:default-service-name}")
    private String serviceName;

    private Tracer tracer;

    @Bean
    public Tracer tracer() {
        if (!Objects.isNull(tracer))
            return tracer;

        var b3Codec = new B3TextMapCodec.Builder().build();

        tracer = new JaegerTracer.Builder(serviceName)
                .registerInjector(Format.Builtin.HTTP_HEADERS, b3Codec)
                .registerExtractor(Format.Builtin.HTTP_HEADERS, b3Codec).build();

        return tracer;
    }

}
