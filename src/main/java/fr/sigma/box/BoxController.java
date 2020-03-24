package fr.sigma.box;

import io.opentracing.Tracer;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.tag.StringTag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.time.DurationFormatUtils;



/**
 * Spring boot controller that wastes resources on-demand during
 * a time that depends on the parameters of the call.
 */
@RestController
public class BoxController {

    private Logger logger = LoggerFactory.getLogger(getClass());
    public static final StringTag PARAMETERS = new StringTag("parameters");
    
    @Value("#{'${box.polynome.coefficients}'.split(',')}")
    private List<Double> coefficients;
    private Polynome polynome; // could become List<Polynome>

    @Value("#{'${box.remote.calls}'.split(',')}")
    private List<String> remote_calls;
    private ArrayList<Pair<String, Integer>> address_time_list;

    @Autowired
    private Tracer tracer;
    private RestTemplate restTemplate;

    public BoxController() {
    }

    private void init() {
        restTemplate = new RestTemplate();
        
        polynome = new Polynome(coefficients);
        
        address_time_list = new ArrayList<>();
        for (int i = 0; i < remote_calls.size(); ++i) {
            // format <address to call>@<percent before calling>
            String[] address_time = remote_calls.get(i).split("@");
            assert (address_time.length == 2);
            var atProgress = Integer.parseInt(address_time[1]);
            assert (atProgress >= 0) && (atProgress <= 100);
            address_time_list.add(new Pair(address_time[0], atProgress));
        }
        address_time_list.sort((e1, e2) -> e1.second.compareTo(e2.second));
    }
    
    @RequestMapping("/*")
    private ResponseEntity<String> handle(Double x,
                                          @RequestHeader Map<String, String> headers) {
        var start = LocalDateTime.now();
        
        if (Objects.isNull(polynome)) { init(); } // lazy loading
        if (Objects.isNull(x)) { x = 0.; } // default value

        Span currentSpan = tracer.scopeManager().activeSpan();
        currentSpan.setTag(PARAMETERS, String.format("[{\"x\":\"%s\"}]", x));
        
        var duration = Duration.between(start, LocalDateTime.now());
        var limit = polynome.get(x);
        logger.info(String.format("This box must run during %s and call %s other boxes",
                                  DurationFormatUtils.formatDurationHMS(limit.toMillis()),
                                  address_time_list.size()));
        
        int i = 0;
        while (duration.minus(limit).isNegative()) {
            double progress = (double) duration.toMillis() /
                (double) limit.toMillis() * 100.;

            while (i < address_time_list.size() &&
                   progress > address_time_list.get(i).second) {
                var url = String.format("%s", address_time_list.get(i).first);
                Double finalX = x;
                CompletableFuture<String> future =
                    CompletableFuture.supplyAsync(() -> {
                            logger.info(String.format("Calling %s at %s percent.",
                                                      url, progress));

                            HttpHeaders myheader = new HttpHeaders();
                            for (var header : headers.keySet())
                                if (header.contains("x-")) // propagate tracing headers
                                    myheader.set(header, headers.get(header));
                            myheader.set("x-b3-spanid", currentSpan.context().toSpanId());

                            var args = new LinkedMultiValueMap<String, String>();
                            args.add("x", finalX.toString());

                            var request = new HttpEntity<MultiValueMap<String, String>>(args, myheader);

                            return restTemplate.postForEntity(url, request, String.class, args).toString();
                        });
                ++i;
            }
            
            duration = Duration.between(start, LocalDateTime.now());
        }
        
        return new ResponseEntity<String>(":)", HttpStatus.OK);
    }

}
