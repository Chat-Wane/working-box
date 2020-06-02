package fr.sigma.box;

import fr.sigma.energy.EnergyAwareness;
import fr.sigma.energy.ArgsFilter;
import fr.sigma.structures.Polynomes;
import fr.sigma.structures.Polynome;
import fr.sigma.structures.Pair;

import io.opentracing.Tracer;
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

import com.google.common.collect.TreeRangeSet;
import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;



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

    @Value("${box.energy.call.url}")
    private String energy_call_url; // (TODO) use this
    @Value("${box.energy.threshold.before.self.tuning.args:14}")
    private Integer energy_threshold_before_self_tuning_args;
    @Value("${box.energy.max.local.data:20}")
    private Integer energy_max_local_data;
    private EnergyAwareness energyAwareness;

    @Value("${spring.application.name}")
    private String service_name;
    
    @Autowired
    private Tracer tracer;
    private RestTemplate restTemplate;

    public BoxController() {}

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
        var names = new ArrayList<String>();
        for (int i = 0; i < remote_calls.size(); ++i) {
            // format <address to call>@<percent before calling>
            String[] address_time = remote_calls.get(i).split("@");
            assert (address_time.length == 2);
            var atProgress = Integer.parseInt(address_time[1]);
            assert (atProgress >= 0) && (atProgress <= 100);
            address_time_list.add(new Pair(address_time[0], atProgress));
            names.add(address_time[0]); // (TODO) include name of function
        }
        address_time_list.sort((e1, e2) -> e1.second.compareTo(e2.second));

        energyAwareness = new EnergyAwareness(service_name, energy_max_local_data,
                                              energy_threshold_before_self_tuning_args);
        energyAwareness.updateRemotes(names);
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
        if (Objects.isNull(polynomes)) { init(); } // lazy loading (TODO) unuglyfy
	var converter = RangeSetConverter.rangeSetConverter(Doubles.stringConverter().reverse());
	var stringOfRanges = converter.convert(energyAwareness.combineIntervals()); // (TODO) as json
        return new ResponseEntity<String>(stringOfRanges, HttpStatus.OK);
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
        if (Objects.isNull(polynomes)) { init(); } // lazy loading (TODO) unuglyfy
        
        // keep important parameters of this box
        Double[] copyArgs = new Double[args.length];
        for (int i = 0; i < polynomes.indices.size(); ++i)
            copyArgs[polynomes.indices.get(i)] = args[polynomes.indices.get(i)];
        
        // (TODO) refactor jaeger tracing outside
        Span currentSpan = tracer.scopeManager().activeSpan();
        //var parametersString = String.format("[%s]", String.join(",", parameters));
        //parameters.add(String.format("{\"x%s\": \"%s\"}", index, args[index]));//jaeger wasinloop
        // currentSpan.setTag(PARAMETERS, parametersString);



        // #B Energy awareness handler, distribute objectives, modify parameters
        TreeMap<String, Double> objectives = null;
        Double[] solution = copyArgs;
        if (headers.keySet().contains("objective")) {
            var objective = (int) Double.parseDouble(headers.get("objective"));
            var os = energyAwareness.newFunctionCall(objective, copyArgs);
            objectives = os.first;
            solution = os.second;
        }
        


        // #C Main loop for different calls to remote services
        var polyResult = polynomes.get(solution);
        var limit = polyResult > 0 ? Duration.ofMillis(polyResult) : Duration.ZERO;  
        logger.info(String.format("This box must run during %s and call %s other boxes",
                                  DurationFormatUtils.formatDurationHMS(limit.toMillis()),
                                  address_time_list.size()));
        
        int i = 0;
        while (duration.minus(limit).isNegative()) {
            var progress = (double) duration.toMillis() / (double) limit.toMillis() * 100.;
            
            while (i < address_time_list.size() &&
                   progress > address_time_list.get(i).second) {
                callRemote(address_time_list.get(i).first, args, headers, objectives,
                           currentSpan, (int) progress);
                ++i;
            }
            
            duration = Duration.between(start, LocalDateTime.now());
        }

	// while (i < address_time_list.size()) { // call the rest that would have been skipped
        for (int j = i; j < address_time_list.size(); ++j)
	    callRemote(address_time_list.get(j).first, args, headers, objectives,
		       currentSpan, 100);
        
	updateEnergy(solution, start, LocalDateTime.now());
        return new ResponseEntity<String>(":)\n", HttpStatus.OK);
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
                if (!Objects.isNull(objectives) && objectives.containsKey(url))
		    myheader.set("objective", objectives.get(url).toString());
		else
		    myheader.set("objective", "-1.0"); // default

                var argsToSend = new LinkedMultiValueMap<String, String>();
                argsToSend.add("args", Arrays.stream(args)
                               .map(String::valueOf)
                               .collect(Collectors.joining(",")));
                var request = new HttpEntity<MultiValueMap<String, String>>(argsToSend,
									    myheader);
		var result = ":(";
		try {
		    result = restTemplate.postForEntity(url, request, String.class,
							argsToSend).toString();
		    // logger.info(String.format("Got the result %s from %s",
		    // result, url));
		} catch (Exception e) {
		    logger.warn(String.format("Error while calling %s.", url));
		    // logger.warn(e.toString());
		}
                return result;
	    });
    }




    // (TODO) from span get from, get to, get args, get remote calls
    private void updateEnergy (Double[] args, LocalDateTime from, LocalDateTime to) {
        // (TODO) call energy stuff, for now, cost is only about duration
        energyAwareness.addEnergyData(args, (double) Duration.between(from, to).toMillis());
        
	// (TODO) how often? maybe inverse direction
        for (var address_time : address_time_list) {
            try {
                var stringRangeSet = restTemplate // (TODO) as json
                    .getForEntity(String.format("%s/getEnergyIntervals",
                                                address_time.first),
                                  String.class).getBody();
		var converter = RangeSetConverter.rangeSetConverter(Doubles.stringConverter()
								    .reverse());
		var costs = converter.reverse().convert(stringRangeSet);
		logger.info(String.format("Just received remote energy data: %s sets from %s.",
					  costs.asRanges().size(), address_time.first));
                energyAwareness.updateRemote(address_time.first, costs);
            } catch (Exception e) {
                logger.warn(String.format("Error while calling %s to get energy costs.",
					  address_time.first));
                // (TODO) can fall down to remote dedicated service.
            }
        }
    }
}
