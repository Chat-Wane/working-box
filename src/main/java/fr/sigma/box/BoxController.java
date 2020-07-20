package fr.sigma.box;

import fr.sigma.energy.EnergyAwareness;
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

import javax.annotation.PostConstruct;
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
import com.google.common.primitives.Doubles;
import com.google.common.collect.ImmutableMap;



/**
 * Spring boot controller that wastes resources on-demand during
 * a time that depends on the parameters of the call.
 */
@RestController
public class BoxController {

    private Logger logger = LoggerFactory.getLogger(getClass());
    
    @Value("#{'${box.polynomes.coefficients}'.split('-')}")
    private List<String> coefficients;
    private Polynomes polynomes; 

    @Value("#{'${box.remote.calls}'.split(',')}")
    private List<String> remote_calls;
    private ArrayList<Pair<String, Integer>> address_time_list;

    @Value("${box.energy.call.url:''}")
    private String energy_call_url; // (TODO) use this, i.e., with smartwatts
    @Value("${box.energy.threshold.before.self.tuning.args:14}")
    private Integer energy_threshold_before_self_tuning_args;
    @Value("${box.energy.max.local.data:20}")
    private Integer energy_max_local_data;
    @Value("${box.energy.fairness.factor:0}")
    private Double energy_fairness_factor;
    @Value("${box.energy.factor.localdatakept.differentdatamonitored:10.0}")
    private Double energy_factor_localdatakept_differentdatamonitored;
    @Value("${box.energy.max.error:15}")
    private Double energy_max_error;
    private EnergyAwareness energyAwareness;

    @Value("${spring.application.name}")
    private String service_name;
    
    @Autowired
    private Tracer tracer;
    private RestTemplate restTemplate;


    
    public BoxController() { }

    @PostConstruct
    private void init() {
	// Span currentSpan = tracer.scopeManager().activeSpan();
	// currentSpan.log(ImmutableMap.of("event", "startInit"));
	
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
	    if (address_time.length == 2) {
		var atProgress = Integer.parseInt(address_time[1]);
		address_time_list.add(new Pair(address_time[0], atProgress));
		names.add(address_time[0]); // (TODO) include name of function
	    }
	}
	address_time_list.sort((e1, e2) -> e1.second.compareTo(e2.second));

	var nbDifferentInputMonitored = (int) (energy_max_local_data *
					       energy_factor_localdatakept_differentdatamonitored);
        energyAwareness = new EnergyAwareness(service_name,
					      energy_max_local_data,
					      nbDifferentInputMonitored,
                                              energy_threshold_before_self_tuning_args,
                                              energy_fairness_factor,
                                              energy_max_error);
        energyAwareness.updateRemotes(names);

	// currentSpan.log(ImmutableMap.of("event", "stopInit"));
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
        if (Objects.isNull(polynomes)) { init(); }
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
	Span currentSpan = tracer.scopeManager().activeSpan();
	
        // #A initialize objects and reporting 
        if (Objects.isNull(polynomes)) { init(); }

	var startEnergyAwareness = LocalDateTime.now();
	currentSpan.log(ImmutableMap.of("event", "startEnergyAwareness"));
                
        // keep important parameters of this box
        Double[] copyArgs = new Double[args.length];
        for (int i = 0; i < copyArgs.length; ++i)
            copyArgs[i] = 0.;
        for (int i = 0; i < polynomes.indices.size(); ++i)
            if (polynomes.polynomes.get(i).coefficients.size() > 1) // not constant
                copyArgs[polynomes.indices.get(i)] = args[polynomes.indices.get(i)];
        

        
        // #B Energy awareness handler, distribute objectives, modify parameters
        TreeMap<String, Double> objectives = null;
        Double[] solution = copyArgs;
        if (headers.keySet().contains("objective")) {
            var objective = Double.parseDouble(headers.get("objective"));
            var os = energyAwareness.newFunctionCall(objective, copyArgs);
            objectives = os.getLeft();
            solution = os.getMiddle();
	    currentSpan.setTag("isLastInputRewritten", os.getRight());
	    currentSpan.setTag("objective", objective);
	    currentSpan.setTag("objectives", String.format("%s", objectives));
        }

	var endEnergyAwareness = LocalDateTime.now();
	logger.info(String.format("Energy awareness took %s ms to process.",
				  Duration.between(startEnergyAwareness,
						   endEnergyAwareness).toMillis()));
	currentSpan.log(ImmutableMap.of("event", "endEnergyAwareness"));
	


	var start = LocalDateTime.now();
        var duration = Duration.between(start, LocalDateTime.now());

        // #C Main loop for different calls to remote services
        logger.info(String.format("This box executes with args: %s", Arrays.toString(solution)));
	currentSpan.setTag("parameters", Arrays.toString(args));
	currentSpan.setTag("solution", Arrays.toString(solution));	
	
        var polyResult = polynomes.get(solution);
        currentSpan.setTag("polyResult", polyResult);
        
        var limit = polyResult > 0 ? Duration.ofMillis(polyResult) : Duration.ZERO;  
        logger.info(String.format("This box must run during %s and call %s other boxes.",
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



        // #D monitor and update local energy        
	var lastLocalInputKept = updateEnergy(solution, start, LocalDateTime.now());
	currentSpan.setTag("isLastInputKept", lastLocalInputKept);
        currentSpan.setTag("localCosts",
                           Arrays.toString(energyAwareness
                                           .getLocalEnergyData()
                                           .getSortedAvgCosts()));
        
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
    private boolean updateEnergy (Double[] args, LocalDateTime from, LocalDateTime to) {
        // (TODO) call energy stuff, for now, cost is only about duration
        var kept = energyAwareness
	    .addEnergyData(args, (double) Duration.between(from, to).toMillis());
	
	// (TODO) how often? maybe inverse direction
        for (var address_time : address_time_list) {
            try {
		// #A update remote service energy data
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
		// #B can't reach remote service, considered dead until further news
                logger.warn(String.format("Error while calling %s to get energy costs. Resetting.",
					  address_time.first));
		energyAwareness.resetRemote(address_time.first);
                // (TODO) can fall down to remote dedicated service if there is.
            }
        }

	return kept;
    }

}
