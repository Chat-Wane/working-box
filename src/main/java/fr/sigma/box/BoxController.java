package fr.sigma.box;

import fr.sigma.energy.EnergyAwareness;
import fr.sigma.energy.ArgsFilter;
import fr.sigma.structures.Polynomes;
import fr.sigma.structures.Polynome;
import fr.sigma.structures.Pair;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Arrays;
import java.util.Objects;
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
    private EnergyAwareness energyAwareness;

    private ArgsFilter argsFilter;
    
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

        // (TODO) handle errors
        // var jsonEnergyAwareness = restTemplate
        //     .getForEntity(String.format("%s?name=handle@%s",
        //                                 energy_call_url,
        //                                 service_name),
        //                   String.class).getBody();
        energyAwareness = new EnergyAwareness(service_name, 10); // (TODO) configurable
        energyAwareness.updateRemotes(address_time_list);
        // energyAwareness.update(jsonEnergyAwareness);

        argsFilter = new ArgsFilter();
        argsFilter.setThreshold(4); // (TODO) config
    }

    /**
     * A "peer-to-peer" endpoint that provides energy knowledge, i.e.,
     * intervals of energy consumption.
     * @returns A JSON string containing pairs of doubles representing
     * intervals.
     */
    @ConditionalOnExpression("${box.energy.peertopeer.enable:false}")
    @RequestMapping("/getEnergyIntervals")
    private ResponseEntity<String> getEnergyIntervals() {
        // return new ResponseEntity<String>( , HttpStatus.OK);
        // (TODO) to Json
        return null;
    }

    /**
     * A simple loop that lasts depending on inputs and a priori
     * configuration.
     * @param args the input provided to the system.
     * @param headers the header of the http request. Required to
     * transfer execution context to other services.
     * @returns A string ":)" that returns when the execution of this
     * function is over.
     */
    @RequestMapping("/*")
    private ResponseEntity<String> handle(Double[] args,
                                          @RequestHeader Map<String, String> headers) {
        var start = LocalDateTime.now();
        var duration = Duration.between(start, LocalDateTime.now());

        // #A initialize objects and reporting
        if (Objects.isNull(polynomes)) { init(); } // lazy loading

        // report important parameters of this box
        Span currentSpan = tracer.scopeManager().activeSpan();
        var parameters = new ArrayList<String>();
        var doubleParameters = new ArrayList<Double>();
        for (int i = 0; i < polynomes.indices.size(); ++i) {
            if (polynomes.polynomes.get(i).coefficients.size() > 1) {
                // > 1 depends on a variable x, otherwise constant
                var index = polynomes.indices.get(i);
                parameters.add(String.format("{\"x%s\": \"%s\"}", index, args[index]));
                doubleParameters.add(args[index]);
            }
        }
        var parametersString = String.format("[%s]", String.join(",", parameters));
        currentSpan.setTag(PARAMETERS, parametersString);



        // #B Energy awareness handler, distribute objectives, modify parameters
        TreeMap<String, Double> objectives = null;
        if (headers.keySet().contains("objective")) {
            var objective = headers.get("objective");
            logger.info(String.format("This box has an energy consumption objective of %s",
                                      objective));
            objectives = energyAwareness.getObjectives(Double.parseDouble(objective));
            logger.info(String.format("Distributes energy objective as: %s.", objectives));
            
            if (argsFilter.isTriedEnough(doubleParameters)) {                
                var solution = energyAwareness.solveObjective(objectives.get(service_name));
                logger.info(String.format("Rewrites local arguments: %s -> %s",
                                          Arrays.toString(parameters.toArray()),
                                          Arrays.toString(solution)));
            }
        }



        // #C Main loop for different calls to remote services
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
                callRemote(address_time_list.get(i).first, args, headers, objectives,
                           currentSpan, (int) progress);
                ++i;
            }
            
            duration = Duration.between(start, LocalDateTime.now());
        }
        
        return new ResponseEntity<String>(":)", HttpStatus.OK);
    }


    /**
     * Asynchronous call of a remote service. Headers are overloaded depending on
     * service capabilities.
     * @param url The url of the remote service.
     * @param args The args passed to the application globally.
     * @param headers The headers of the http call.
     * @param objectives The energy objectives of calls to remote services.
     */
    private void callRemote(String url, Double[] args,  Map<String, String> headers,
                            TreeMap<String, Double> objectives,
                            Span currentSpan, int progress) {
        
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                logger.info(String.format("Calling %s at %s percent.",
                                          url, progress));
                
                var myheader = new HttpHeaders();
                for (var header : headers.keySet())
                    if (header.contains("x-")) // propagate tracing headers
                        myheader.set(header, headers.get(header));
                myheader.set("x-b3-spanid", currentSpan.context().toSpanId());
                if (!Objects.isNull(objectives)) {
                    var port = url.split(":")[2]; // (TODO) different name <-> url
                    var name = String.format("handle@box-%s", port);
                    // (TODO) handle error when no name
                    myheader.set("objective", objectives.get(name).toString());
                }
                var argsToSend = new LinkedMultiValueMap<String, String>();
                argsToSend.add("args", Arrays.stream(args)
                               .map(String::valueOf)
                               .collect(Collectors.joining(",")));
                var request = new HttpEntity<MultiValueMap<String, String>>(argsToSend,
                                                                            myheader);

                return restTemplate.postForEntity(url, request, String.class,
                                                  argsToSend).toString();
            });
    }
    
}
