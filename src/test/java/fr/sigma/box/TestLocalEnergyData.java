package fr.sigma.box;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;



public class TestLocalEnergyData {

    @Test 
    void addEnergyDataSimple() {
        var led = new LocalEnergyData();
        led.setMaxSize(2);
        var args = new ArrayList<Double>();
        
        args.add(1.);
        led.addEnergyData(args, 50.);        
        args.add(3.);
        led.addEnergyData(args, 55.);
        args.add(37.);        
        led.addEnergyData(args, 60.);
    }

    @Test
    void addEnergyDataEvenList() {
        var led = new LocalEnergyData();
        led.setMaxSize(10);

        for (int i = 0; i < 100; ++i) {            
            var args = new ArrayList<Double>();
            args.add((double) i);
            led.addEnergyData(args, (double) i);
        }

        System.out.println(String.format("even list -> %s",
                                         Arrays.toString(led.getCosts())));
    }

    @Test
    void addEnergyDataShuffledList() {
        int NBINPUTS = 1000;
        var led = new LocalEnergyData();
        led.setMaxSize(10);

        var costs = new ArrayList<Double>();
        for (int i = 0; i < NBINPUTS; ++i) {
            costs.add(new Double(i));
        }

        Collections.shuffle(costs);
        
        for (int i = 0; i < NBINPUTS; ++i) {            
            var args = new ArrayList<Double>();
            args.add((double) i);
            led.addEnergyData(args, costs.get(i));
        }   

        System.out.println(String.format("shuffled list -> %s",
                                         Arrays.toString(led.getCosts())));        
    }
}
