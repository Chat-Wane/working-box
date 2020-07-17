package fr.sigma.energy;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;



public class LocalEnergyDataTest {

    @Test 
    void addEnergyDataSimple() {
        var led = new LocalEnergyData(2, 3);
        Double[] args1 = {1.};
        led.addEnergyData(args1, 50.);
        assertEquals(1, led.getAvgCosts().size());
        Double[] args2 = {2.};
        led.addEnergyData(args2, 55.);
        assertEquals(2, led.getAvgCosts().size());
        Double[] args3 = {3.};
        led.addEnergyData(args3, 60.);
        assertEquals(2, led.getAvgCosts().size());
    }

    @Test
    public void getIntervalOfNothin () {
        var led = new LocalEnergyData(10, 3);
        var intervals = led.getIntervals();
        assert(intervals.isEmpty());
    }

    @Test
    public void getIntervalOfOneValue () {
        var led = new LocalEnergyData(10, 3);
        Double[] args = {42.};
        led.addEnergyData(args, 1337.);
        var intervals = led.getIntervals();
        assert(intervals.contains(1337.));
    }
    
    @Test
    void getIntervalsSimple () {
        var led = new LocalEnergyData(10, 3);

        for (int i = 0; i < 500; ++i) {            
            Double[] args = {(double) i};
            led.addEnergyData(args, (double) i);
        }
        assertEquals(10, led.getAvgCosts().size());
        
        var intervals = led.getIntervals();
        assert(!intervals.isEmpty());
        assert(intervals.asRanges().size() <= led.getMaxSize());
    }
}
