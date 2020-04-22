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
import java.util.stream.Collectors;
import org.apache.commons.lang3.time.DurationFormatUtils;



/**
 * Spring boot controller that wastes resources on-demand during
 * a time that depends on the parameters of the call.
 */
@RestController
public class BoxController {

    private Logger logger = LoggerFactory.getLogger(getClass());
    public static final StringTag PARAMETERS = new StringTag("parameters");
    
    @Value("#{'${box.polynomes.coefficients}'.split('-')}")
    private List<String> coefficients;
    private Polynomes polynomes; 

    @Value("#{'${box.remote.calls}'.split(',')}")
    private List<String> remote_calls;
    private ArrayList<Pair<String, Integer>> address_time_list;

    @Value("${box.energy.call}")
    private String energy_call_url;

    @Value("${spring.application.name}")
    private String service_name;
    
    @Autowired
    private Tracer tracer;
    private RestTemplate restTemplate;

    public BoxController() {
    }

    private void init() {
        restTemplate = new RestTemplate();
        
        polynomes = new Polynomes();
        for (String coefficient : coefficients) {
            // format <a>,<b>,...,<k>[@index: default 0]
            String[] coefficient_index = coefficient.split("@");
            String[] coefsOfCurrentPoly = coefficient_index[0].split(",");
            
            var coefs = new ArrayList<Double>();
            for (int i = 0; i < coefsOfCurrentPoly.length; ++i)
                coefs.add( Double.parseDouble(coefsOfCurrentPoly[i]) );
            
            var index = coefficient_index.length > 1 ?
                Integer.parseInt(coefficient_index[1]) :
                0;
            polynomes.add(new Polynome(coefs), index);
        }
        
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
    private ResponseEntity<String> handle(Double[] args,
                                          @RequestHeader Map<String, String> headers) {
        var start = LocalDateTime.now();
        var duration = Duration.between(start, LocalDateTime.now());
        
        if (Objects.isNull(polynomes)) { init(); } // lazy loading

        // report important parameters of this box
        Span currentSpan = tracer.scopeManager().activeSpan();
        var parameters = new ArrayList<String>();
        for (int i = 0; i < polynomes.indices.size(); ++i) {
            if (polynomes.polynomes.get(i).coefficients.size() > 1) {
                // > 1 depends on a variable x, otherwise constant
                var index = polynomes.indices.get(i);
                parameters.add(String.format("{\"x%s\": \"%s\"}", index, args[index]));
            }
        }
        var parametersString = String.format("[%s]", String.join(",", parameters));
        currentSpan.setTag(PARAMETERS, parametersString);

        if (headers.keySet().contains("objective")) {
            var objective = headers.get("objective");
            logger.info(String.format("This box has an energy consumption objective of %s",
                                      objective));
            // (TODO) local mode, remote mode, none
            System.out.println("Calling " + String.format("%s?name=handle@%s&objective=%s", energy_call_url, service_name, objective));
            var response = restTemplate.getForEntity(String.format("%s?name=handle@%s&objective=%s", energy_call_url, service_name, objective), String.class);
            System.out.println("RESPONSE +  " + response.toString());
        }


        
        var polyResult = polynomes.get(args);
        var limit = polyResult > 0 ? Duration.ofMillis(polyResult) : Duration.ZERO;  
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
                Double[] finalArgs = args;
                CompletableFuture<String> future =
                    CompletableFuture.supplyAsync(() -> {
                            logger.info(String.format("Calling %s at %s percent.",
                                                      url, (int) progress));

                            var myheader = new HttpHeaders();
                            for (var header : headers.keySet())
                                if (header.contains("x-")) // propagate tracing headers
                                    myheader.set(header, headers.get(header));
                            myheader.set("x-b3-spanid", currentSpan.context().toSpanId());

                            var argsToSend = new LinkedMultiValueMap<String, String>();
                            argsToSend.add("args",
                                           Arrays.stream(finalArgs)
                                           .map(String::valueOf)
                                           .collect(Collectors.joining(",")));

                            var request = new HttpEntity<MultiValueMap<String, String>>(argsToSend, myheader);

                            return restTemplate.postForEntity(url, request, String.class, argsToSend).toString();
                        });
                ++i;
            }
            
            duration = Duration.between(start, LocalDateTime.now());
        }
        
        return new ResponseEntity<String>(":)", HttpStatus.OK);
    }

}
