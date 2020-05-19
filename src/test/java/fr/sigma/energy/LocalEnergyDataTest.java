package fr.sigma.energy;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;



public class LocalEnergyDataTest {

    @Test 
    void addEnergyDataSimple() {
        var led = new LocalEnergyData(2);
        var args1 = new ArrayList<Double>(Arrays.asList(1.));
        led.addEnergyData(args1, 50.);
        assertEquals(1, led.getCosts().length);
        var args2 = new ArrayList<Double>(Arrays.asList(2.));
        led.addEnergyData(args2, 55.);
        assertEquals(2, led.getCosts().length);
        var args3 = new ArrayList<Double>(Arrays.asList(3.));
        led.addEnergyData(args3, 60.);
        assertEquals(2, led.getCosts().length);
    }

    @Test
    public void getIntervalOfNothin () {
        var led = new LocalEnergyData(10);
        var intervals = led.getIntervals();
        assert(intervals.isEmpty());
    }

    @Test
    public void getIntervalOfOneValue () {
        var led = new LocalEnergyData(10);
        var args = new ArrayList<Double>(Arrays.asList(42.));
        led.addEnergyData(args, 1337.);
        var intervals = led.getIntervals();
        assert(intervals.contains(1337.));
    }
    
    @Test
    void getIntervalsSimple () {
        var led = new LocalEnergyData(10);

        for (int i = 0; i < 100; ++i) {            
            var args = new ArrayList<Double>(Arrays.asList((double) i));
            led.addEnergyData(args, (double) i);
        }
        assertEquals(10, led.getCosts().length);
        
        var intervals = led.getIntervals();
        assert(!intervals.isEmpty());
        assert(intervals.asRanges().size() <= led.getMaxSize());
    }
}
