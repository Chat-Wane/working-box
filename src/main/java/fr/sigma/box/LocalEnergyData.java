package fr.sigma.box;

import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import smile.stat.distribution.KernelDensity;
import smile.math.MathEx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Local data structure that contains enough energy data to make good
 * decisions concerning arguments and energy awareness.
 */
public class LocalEnergyData {

    private Logger logger = LoggerFactory.getLogger(getClass());    

    private int maxSize = 10;
    private TreeMap<String, Double> inputToCost;
    private TreeMap<Double, String> costToInput;

    
    public LocalEnergyData () {
        inputToCost = new TreeMap();
        costToInput = new TreeMap();
    }

    public void setMaxSize(int maxSize) {
        // (TODO) reduce the size if to many data compared to new
        // maxSize
        this.maxSize = maxSize;
    }

    public double[] getCosts() {
        return inputToCost.values().stream().mapToDouble(e->e).sorted().toArray();
    }

    public RangeSet<Double> getIntervals() {
        DoubleStream costAsStream = inputToCost.values().stream()
            .mapToDouble(e->e).sorted();
        double[] costAsDouble = costAsStream.toArray();
        
        var kernel = new KernelDensity(costAsDouble);
        
        int sampleSize = 100;
        ArrayList<Double> sample = new ArrayList<Double>();
        double minCost = inputToCost.values().stream()
            .mapToDouble(e->e).min().getAsDouble();
        double maxCost = inputToCost.values().stream()
            .mapToDouble(e->e).max().getAsDouble();
        for (int i=0; i<sampleSize; ++i) {
            sample.add(kernel.p(minCost + (maxCost - minCost)/sampleSize * i));
        }

        double sd = Math.sqrt(MathEx.var(sample.stream().mapToDouble(e->e).toArray()));
        double avg = MathEx.mean(sample.stream().mapToDouble(e->e).toArray());

        double from = 0;
        boolean building = false;
        RangeSet<Double> results = TreeRangeSet.create();
        for (int i = 0; i < sample.size(); ++i) {
            if ((avg - sd) <= sample.get(i) && sample.get(i) <= (avg + sd)) {
                if (!building) {
                    building = true;
                    from = minCost + (maxCost - minCost)/sampleSize * i;
                }
            } else {
                if (building) {
                    building = false;
                    var to = minCost + (maxCost - minCost)/sampleSize * i - 1;
                    results.add(Range.closed(from, to));
                }
            }
        }

        if (building) { // last bound
            var to = minCost + (maxCost - minCost)/sampleSize * sample.size() - 1;
            results.add(Range.closed(from, to));
        }
        
        return results;
    }


    
    public void addEnergyData (ArrayList<Double> args, Double cost) {
        // (TODO) cache intermediate results
        // (TODO) improve time complexity

        if (costToInput.containsKey(cost)) {
            // (for now, cannot have multiple values for a cost)
            // (TODO) maybe change this
            return ;
        }
        
        var argsString = args.stream()
            .map(arg -> Double.toString(arg))
            .collect(Collectors.toList());
        
        String key = String.join(", ", argsString);      

        // #1 if we need data, add it immediately
        if (inputToCost.size() < maxSize) {
            inputToCost.put(key, cost);
            costToInput.put(cost, key);
            return ;
        }

        // #2 otherwise, try and see if it adds knowledge
        DoubleStream costAsStream = inputToCost.values().stream()
            .mapToDouble(e->e);
        double[] costAsDouble = costAsStream.toArray();
        
        var kernel = new KernelDensity(costAsDouble);

        Supplier<DoubleStream> densityAsStreamSupplier = () ->
            inputToCost.values().stream().mapToDouble(e-> kernel.p(e));

        int sampleSize = 100;
        ArrayList<Double> sample = new ArrayList<Double>();
        double minCost = inputToCost.values().stream()
            .mapToDouble(e->e).min().getAsDouble();
        double maxCost = inputToCost.values().stream()
            .mapToDouble(e->e).max().getAsDouble();
        for (int i=0; i<sampleSize; ++i) {
            sample.add(kernel.p(minCost + (maxCost - minCost)/sampleSize * i));
        }

        double average = sample.stream().mapToDouble(e->e).average().getAsDouble();
        double[] densityAsDouble = densityAsStreamSupplier.get().toArray();

        double max = 0.;
        int maxIndex = -1;
        double maxValue = -1.;
        for (int i=0; i<costAsDouble.length; ++i) {
            if (max < densityAsDouble[i]) {
                max = densityAsDouble[i];
                maxValue = costAsDouble[i];
                maxIndex = i;
            }
        }

        // System.out.println(String.format("cost = %s ; density = %s",
        //                                  costAsDouble[maxIndex],
        //                                  densityAsDouble[maxIndex]));

        // replace the highest by the new data and see if it is better
        costAsDouble[maxIndex] = cost;
        var kernelChanged = new KernelDensity(costAsDouble);

        Supplier<DoubleStream> densityAsStreamChangedSupplier = () ->
            Arrays.stream(costAsDouble).map(e-> kernel.p(e));

        sample = new ArrayList<Double>();
        minCost = cost < minCost ? cost : minCost;
        maxCost = cost > maxCost ? cost: maxCost;
        for (int i=0; i<sampleSize; ++i) {
            sample.add(kernelChanged.p(minCost + (maxCost - minCost)/sampleSize * i));
        }
        
        double averageChanged = sample.stream()
            .mapToDouble(e->e).average().getAsDouble();
        double[] densityAsDoubleChanged = densityAsStreamChangedSupplier.get().toArray();

        // System.out.println(String.format("%s -> %s", average, averageChanged));
        if (average > averageChanged) {
            inputToCost.remove(costToInput.get(maxValue));
            costToInput.remove(maxValue);
            inputToCost.put(key, cost);
            costToInput.put(cost, key);
            
            // System.out.println(String.format("COSTS = %s",
            //                                  Arrays.toString(inputToCost.values().stream().mapToDouble(e->e).sorted().toArray())));
        }
    }
}
