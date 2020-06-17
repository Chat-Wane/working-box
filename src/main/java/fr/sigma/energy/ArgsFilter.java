package fr.sigma.energy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import orestes.bloomfilter.CountingBloomFilter;
import orestes.bloomfilter.FilterBuilder;

import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;

import java.util.Arrays;



public class ArgsFilter {

    private Logger logger = LoggerFactory.getLogger(getClass());

    // <String> because Double[] does not work well
    private CountingBloomFilter<String> counting; 
    private int threshold;
    
    public ArgsFilter () {
        counting = new FilterBuilder(100, 0.01).buildCountingBloomFilter();
        threshold = 14;
	logger.info(String.format("Initialized filter when |args| > %s", threshold)
                    .concat(String.format("; |distinct args| ~= %s.", 100)));
    }
    
    public ArgsFilter (int threshold) {
        counting = new FilterBuilder(100, 0.01).buildCountingBloomFilter();
        this.threshold = threshold;
        logger.info(String.format("Initialized filter when |args| > %s", threshold)
                    .concat(String.format("; |distinct args| ~= %s.", 100)));       
    }
    
    public ArgsFilter (int numberOfValues, int threshold) {
	// (TODO) maybe configure accuracy ? 
        counting = new FilterBuilder(numberOfValues, 0.01).buildCountingBloomFilter();
        this.threshold = threshold;
        logger.info(String.format("Initialized filter when |args| > %s", threshold)
                    .concat(String.format("; |distinct args| ~= %s.", numberOfValues)));
    }
    
    public int getThreshold () {
        return threshold;
    }
    
    /**
     * Checks if the arguments should be used as is, or self-tuned, depending
     * on the number of times they have been used in the past.
     * @param args the arguments of the endpoint.
     * @returns True if the arguments should be self-tuned, false otherwise.
     */
    public boolean isTriedEnough (Double[] args) {
        long count = counting.getEstimatedCount(Arrays.toString(args));
        logger.info(String.format("Args %s have been seen roughly %s times before.",
                                  Arrays.toString(args),
                                  count));
        return count >= threshold;
    }
    
    public void tryArgs(Double[] args) {
        counting.add(Arrays.toString(args));
    }

}
