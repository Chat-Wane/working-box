package fr.sigma.box;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.util.bloom.CountingBloomFilter;
import org.apache.hadoop.util.bloom.Key;
import org.apache.hadoop.util.hash.Hash;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;



public class ArgsFilter {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private CountingBloomFilter counting;

    private int threshold;
    
    public ArgsFilter () {
        // Default (TODO) smarter allocation depending on parameters
        counting = new CountingBloomFilter(100, 3, Hash.JENKINS_HASH);
        threshold = 14;
    }
    
    public ArgsFilter (ArrayList<Double> args, int numberOfValuesToDiscover) {
        // (TODO)
    }

    public void setThreshold (int threshold) {
        this.threshold = threshold;
    }
    
    /**
     * Checks if the arguments should be used as is, or self-tuned, depending
     * on the number of times they have been used in the past.
     * @param args: the arguments of the endpoint.
     * @returns True if the arguments should be self-tuned, false otherwise.
     */    
    public boolean isTriedEnough (ArrayList<Double> args) {
        var key = new Key(toBytes(args));
        int count = counting.approximateCount(key);
        
        logger.info(String.format("Args have been seen roughly %s times before.",
                                  count));

        if (count <= threshold)
            counting.add(key);

        return count > threshold;
    }


    
    private byte[] toBytes (ArrayList<Double> args) {
        byte[] keyBytes = new byte[0];
        for (Double arg : args) {
            byte[] bytes = ByteBuffer.allocate(8).putDouble(arg).array();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                outputStream.write(keyBytes);
                outputStream.write(bytes);
                keyBytes = outputStream.toByteArray();
            } catch (Exception e){
                logger.warn("Could not write args as byte. Filter may not work.");
            }
        }
        return keyBytes;
    }
}
