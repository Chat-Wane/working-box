package fr.sigma.energy;

import fr.sigma.structures.Pair;

import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.Objects;
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
    private int SAMPLESIZE = 1000;
    
    private int maxSize = 10;
    private int maxCosts = 3;
    private TreeMap<String, ArrayList<Double>> inputToCost;
    private TreeMap<String, Double[]> inputToArgs;
    
    /**
     * @param maxSize: the maximum number of arguments that are kept locally.
     * @param maxCosts: the number of costs for each set of arguments
     * that are kept for meaningful statistics purpose.
     */
    public LocalEnergyData (int maxSize, int maxCosts) {
	this.maxSize = maxSize;
        this.maxCosts = maxCosts;
        inputToCost = new TreeMap();
	inputToArgs = new TreeMap();
	logger.info(String.format("Initialized local profiler with max data kept %s x %s.",
				  maxSize, maxCosts));
    }

    public int getMaxSize() { return maxSize; }
    public int size() { return inputToCost.size(); }

    public boolean exists (Double[] args) {
        return inputToCost.containsKey(toKey(args));
    }

    public double getCost(Double[] args) {
        String key = toKey(args);
        return inputToCost.get(key).stream()
            .mapToDouble(d->d).average().orElse(0.);
    }
    
    public double[] getCosts() {
        return inputToCost.values().stream()
            .mapToDouble(costs-> costs.stream()
                         .mapToDouble(d->d).average().orElse(0.))
            .toArray();
    }

    // (TODO) change this: for now, useless and slightly waste
    // resources where we can have O(N) but we have O(NlogN).
    public double[] getSortedCosts() {
        return inputToCost.values().stream()
            .mapToDouble(costs-> costs.stream()
                         .mapToDouble(d->d).average().orElse(0.))
            .sorted().toArray();
    }

    public Pair<Double, Double> getMinMaxCost() {
        var costs = getCosts();
        if (costs.length < 1)
            return new Pair(0., 0.);
        else
            return new Pair(costs[0], costs[costs.length - 1]);
    }

    public Double[] getClosest(double objective) {
	var min = Double.POSITIVE_INFINITY;
	String input = null;
	for (Map.Entry<String, ArrayList<Double>> ic : inputToCost.entrySet()) {
            var avgCost = ic.getValue().stream().mapToDouble(d->d)
                .average().orElse(0.);
            var distance = Math.abs(objective - avgCost);
	    if (distance < min) {
		min = distance;
		input = ic.getKey();
	    }
	}
	
	return Objects.isNull(input) ? null : inputToArgs.get(input);
    }
    

    
    // (TODO) cache results of kernel density
    public TreeRangeSet<Double> getIntervals() {
        TreeRangeSet<Double> resultingIntervals = TreeRangeSet.create();
        
        double[] costs = getCosts();

        if (costs.length == 0) // kernel needs at least 2 values
            return resultingIntervals;
        if (costs.length == 1) {
            resultingIntervals.add(Range.closed(costs[0], costs[0]));
            return resultingIntervals;
        }

        var minmax = getMinMaxCost();
        var minCost = minmax.first;
        var maxCost = minmax.second;
        
        var kernel = new KernelDensity(costs);       
        ArrayList<Double> sample = new ArrayList<Double>();
       
        for (int i=0; i<SAMPLESIZE; ++i)
            sample.add(kernel.p(minCost + (maxCost - minCost)/SAMPLESIZE * i));

        double sd = Math.sqrt(MathEx.var(sample.stream().mapToDouble(e->e).toArray()));
        double avg = MathEx.mean(sample.stream().mapToDouble(e->e).toArray());

        double from = 0;
        boolean building = false;
        TreeRangeSet<Double> results = TreeRangeSet.create();
        for (int i = 0; i < sample.size(); ++i) {
            if ((avg - sd) <= sample.get(i) && sample.get(i) <= (avg + sd)) {
                if (!building) {
                    building = true;
                    from = minCost + (maxCost - minCost)/SAMPLESIZE * i;
                }
            } else {
                if (building) {
                    building = false;
                    var to = minCost + (maxCost - minCost)/SAMPLESIZE * i - 1;
                    results.add(Range.closed(from, to));
                }
            }
        }

        if (building) { // close last bound
            var to = minCost + (maxCost - minCost)/SAMPLESIZE * sample.size() - 1;
            results.add(Range.closed(from, to));
        }
        
        return results;
    }



    /**
     * Tries to add the pair of arguments,cost to the local monitored
     * data if the cost is sufficiently meaningful to be kept.
     * (TODO) cache intermediate results.
     * @param argsAsArray: the arguments of the call.
     * @param cost: the cost of the call with such arguments.
     * @returns true if the data has replaced another value, false otherwise.
     */
    public boolean addEnergyData (Double[] argsAsArray, double cost) {
        String newKey = toKey(argsAsArray);

        // #A already have the args as key, we add the value to add
        // statistical significance to the local sample.
        if (inputToCost.containsKey(newKey)) {
            ArrayList<Double> costsOfInput = inputToCost.get(newKey);
            if (costsOfInput.size() >= maxCosts) 
                costsOfInput.remove(0); // pop oldest
            costsOfInput.add(new Double(cost)); // add newest
            return false; 
        }

        // #B otherwise, if we need data, add it immediately
        if (inputToCost.size() < maxSize) {
            inputToCost.put(newKey, new ArrayList<Double>());
            inputToCost.get(newKey).add(cost);
	    inputToArgs.put(newKey, argsAsArray);
            return true;
        }
        
        // #C otherwise, compete with the other local value to
        // determine which should be kept       
        var costToInput = new TreeMap();                
        double[] costAsDouble = getCosts();        
        var kernel = new KernelDensity(costAsDouble);
        
        ArrayList<Double> sample = new ArrayList<Double>();
        var minmax = getMinMaxCost();
        var minCost = minmax.first;
        var maxCost = minmax.second;
        
        for (int i = 0; i < SAMPLESIZE; ++i)
            sample.add(kernel.p(minCost + (maxCost - minCost)/SAMPLESIZE * i));

        double average = sample.stream().mapToDouble(e->e).average().getAsDouble();
        double[] densityAsDouble = Arrays.stream(costAsDouble)
            .map(e->kernel.p(e)).toArray();

        // get the value that peak in costs to check if it should be replaced
        double max = 0.;
        int maxIndex = -1;
        double maxValue = -1.;
        for (int i = 0; i < costAsDouble.length; ++i) {
            if (max < densityAsDouble[i]) {
                max = densityAsDouble[i];
                maxValue = costAsDouble[i];
                maxIndex = i;
            }
        }

        // replace the highest by the new data and see if it is better
        costAsDouble[maxIndex] = cost;
        var kernelChanged = new KernelDensity(costAsDouble);

        sample = new ArrayList<Double>();
        minCost = cost < minCost ? cost : minCost;
        maxCost = cost > maxCost ? cost : maxCost;
        for (int i = 0; i < SAMPLESIZE; ++i)
            sample.add(kernelChanged.p(minCost + (maxCost - minCost)/SAMPLESIZE * i));

        double averageChanged = sample.stream().mapToDouble(e->e).average().getAsDouble();

        logger.info(String.format("Average of kernel density estimators old: %s vs new: %s.",
				  average, averageChanged));
	boolean isLastInputKept = average > averageChanged;
        if (isLastInputKept) { // Replace peaking value by new flatter value
            String maxKey = toKey(getClosest(maxValue));
            inputToCost.remove(maxKey);
            inputToArgs.remove(maxKey);
            
            inputToCost.put(newKey, new ArrayList<Double>());
            inputToCost.get(newKey).add(cost);
	    inputToArgs.put(newKey, argsAsArray);
        }
	
	return isLastInputKept;
    }

    private String toKey (Double[] args) {
        var asListOfDouble = new ArrayList<Double>(Arrays.asList(args));
        var asListOfString = asListOfDouble.stream()
            .map(arg -> Double.toString(arg))
            .collect(Collectors.toList());
        return String.join(", ", asListOfString);
    }
}
