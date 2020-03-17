package fr.sigma.box;

import io.opentracing.Tracer;
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



/**
 * Spring boot controller that wastes resources on-demand during
 * a time that depends on the parameters of the call.
 */
@RestController
public class BoxController {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Value("#{'${box.polynome.coefficients:0.}'.split(',')}")
    private List<Double> coefficients;
    // (TODO) maybe think of multiple polynomes. and x, y, z etc. args
    private Polynome polynome;

    @Value("#{'${box.remote.calls:}'.split(',')}")
    private List<String> remote_calls;
    private ArrayList<Pair<String, Integer>> address_time_list;

    private Tracer tracer;
    private RestTemplate restTemplate;

    public BoxController() {
    }

    @RequestMapping("/*")
    private ResponseEntity<String> handle(Double x, @RequestHeader Map<String, String> headers) {
        restTemplate = new RestTemplate();
        
        polynome = new Polynome(coefficients);

        address_time_list = new ArrayList<>();
        for (int i = 0; i < remote_calls.size(); ++i) {
            // format <address to call>@<percent before calling>
            String[] address_time = remote_calls.get(i).split("@");
            assert address_time.length == 2;
            address_time_list.add(new Pair(address_time[0],
                                           Integer.parseInt(address_time[1])));
        }
        address_time_list.sort((e1, e2) -> e1.second.compareTo(e2.second));

        
        var start = LocalDateTime.now();

        if (Objects.isNull(x)) // default value
            x = 0.;

        var duration = Duration.between(start, LocalDateTime.now());
        var limit = polynome.get(x);

        int i = 0;
        logger.info(String.format("This box must run during %s ms.",
                                  limit.toMillis()));
        logger.info(String.format("This box must call %s other boxes.",
                                  address_time_list.size()));
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

                            MultiValueMap<String, String> args = new LinkedMultiValueMap<>();
                            args.add("x", finalX.toString());

                            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(args, myheader);

                            return restTemplate.postForEntity(url, request, String.class, args).toString();
                        });
                ++i;
            }
            
            duration = Duration.between(start, LocalDateTime.now());
        }
        
        return new ResponseEntity<String>(":)", HttpStatus.OK);
    }

}
