package fr.sigma.box;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
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
    private ArrayList<Pair<String,Integer>> address_time_list;
    
    public BoxController () {
    }

    @RequestMapping("/*")
    private ResponseEntity<String> handle (Double x) {
        var start = LocalDateTime.now();
        
        if (Objects.isNull(polynome)) // lazy loading
            polynome = new Polynome(coefficients);

        if (Objects.isNull(address_time_list)) { // lazy loading
            address_time_list = new ArrayList<>();
            for (int i = 0; i < remote_calls.size(); ++i) {
                String[] address_time = remote_calls.get(i).split("@");
                assert address_time.length == 2;
                address_time_list.add(new Pair(address_time[0],
                                               Integer.parseInt(address_time[1])));
            }
            address_time_list.sort((e1, e2) -> e1.second.compareTo(e2.second));
        }
        
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
                var uri = String.format("%s?x=%s", address_time_list.get(i).first, x);
                CompletableFuture<String> future =
                    CompletableFuture.supplyAsync(() -> {
                            // (TODO) rest template bean
                            logger.info(String.format("Calling %s at %s percent.",
                                                      uri, progress));
                            var restTemplate = new RestTemplate();
                            return restTemplate.getForObject(uri, String.class);
                        });
                ++i;
            }
            
            duration = Duration.between(start, LocalDateTime.now());
        }
        return new ResponseEntity<String>(":)", HttpStatus.OK);
    }
    
}
