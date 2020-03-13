package fr.sigma.box;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.opentracing.Tracer;
import io.jaegertracing.Configuration;
import io.jaegertracing.internal.samplers.ConstSampler;
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
        
        Configuration.SamplerConfiguration samplerConfig =
            Configuration.SamplerConfiguration.fromEnv()
            .withType(ConstSampler.TYPE)
            .withParam(1);
        
        Configuration.ReporterConfiguration reporterConfig =
            Configuration.ReporterConfiguration.fromEnv()
            .withLogSpans(true);

        Configuration config = new Configuration(serviceName)
            .withSampler(samplerConfig)
            .withReporter(reporterConfig);
        
        tracer = config.getTracer();
        return tracer;
    }

}
