package fr.sigma.box;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.opentracing.Tracer;
import io.jaegertracing.Configuration;
import io.opentracing.propagation.Format;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.jaegertracing.internal.propagation.B3TextMapCodec;
import java.util.Objects;



@Component
public class MyTracingConfig {

    @Value("${spring.application.name:default-service-name}")
    private String serviceName;

    private Tracer tracer;

    @Bean
    public Tracer tracer () {
        if (!Objects.isNull(tracer))
            return tracer;

        var b3Codec = new B3TextMapCodec();
        
        Configuration.CodecConfiguration codecConfig =
            new Configuration.CodecConfiguration()
            .withCodec(Format.Builtin.HTTP_HEADERS, b3Codec)
            .withCodec(Format.Builtin.HTTP_HEADERS, b3Codec);
        
        Configuration.SamplerConfiguration samplerConfig =
            Configuration.SamplerConfiguration.fromEnv()
            .withType(ConstSampler.TYPE)
            .withParam(1);
        
        Configuration.ReporterConfiguration reporterConfig =
            Configuration.ReporterConfiguration.fromEnv()
            .withLogSpans(true);

        Configuration config = new Configuration(serviceName)
            .withSampler(samplerConfig)
            .withReporter(reporterConfig)
            .withCodec(codecConfig);
        
        tracer = config.getTracer();
        return tracer;
    }

}
