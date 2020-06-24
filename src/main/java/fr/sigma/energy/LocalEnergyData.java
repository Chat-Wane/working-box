package fr.sigma.energy;

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

    private int maxSize = 10;
    private TreeMap<String, Double> inputToCost;
    private TreeMap<Double, String> costToInput;
    private TreeMap<String, Double[]> inputToArgs;

    public LocalEnergyData (int maxSize) {
	this.maxSize = maxSize;
        inputToCost = new TreeMap();
        costToInput = new TreeMap();
	inputToArgs = new TreeMap();
	logger.info(String.format("Initialized local energy data with a maximum size of %s.",
				  maxSize));
    }

    public int getMaxSize() { return maxSize; }
    public int size() { return inputToCost.size(); }

    public double[] getCosts() {
        return inputToCost.values().stream().mapToDouble(e->e).sorted().toArray();
    }


    public Double[] getClosest(double objective) {
	var min = Double.POSITIVE_INFINITY;
	String input = null;
	for (Map.Entry<String, Double> ic : inputToCost.entrySet()) {
	    if (Math.abs(objective - ic.getValue()) < min) {
		min = Math.abs(objective - ic.getValue());
		input = ic.getKey();
	    }
	}
	
	return Objects.isNull(input) ? null : inputToArgs.get(input);
    }
    

    
    // (TODO) cache results of kernel density
    public TreeRangeSet<Double> getIntervals() {
        // kernel needs at least 2 values
        if (inputToCost.size() == 0)
            return TreeRangeSet.create();
        if (inputToCost.size() == 1) {
            TreeRangeSet<Double> oneValueResult = TreeRangeSet.create();
            var cost = inputToCost.values().stream().findFirst().get();
            oneValueResult.add(Range.closed(cost, cost));
            return oneValueResult;
        }
        
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
        TreeRangeSet<Double> results = TreeRangeSet.create();
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



    /**
     * Tries to add the pair of arguments,cost to the local monitored
     * data if the cost is sufficiently meaningful to be kept.
     * @param argsAsArray: the arguments of the call.
     * @param cost: the cost of the call with such arguments.
     * @returns true if the data has been registered, false otherwise.
     */
    public boolean addEnergyData (Double[] argsAsArray, double cost) {
        // (TODO) cache intermediate results
        // (TODO) improve time complexity
        var args = new ArrayList<Double>(Arrays.asList(argsAsArray));

        if (costToInput.containsKey(cost)) {
            // (for now, cannot have multiple values for a cost)
            // (TODO) maybe change this
            return false;
        }
        
        var argsString = args.stream()
            .map(arg -> Double.toString(arg))
            .collect(Collectors.toList());
        
        String key = String.join(", ", argsString);      

        // #1 if we need data, add it immediately
        if (inputToCost.size() < maxSize) {
            inputToCost.put(key, cost);
            costToInput.put(cost, key);
	    inputToArgs.put(key, argsAsArray);
            return true;
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

        // replace the highest by the new data and see if it is better
        costAsDouble[maxIndex] = cost;
        var kernelChanged = new KernelDensity(costAsDouble);

        Supplier<DoubleStream> densityAsStreamChangedSupplier = () ->
            Arrays.stream(costAsDouble).map(e-> kernel.p(e));

        sample = new ArrayList<Double>();
        minCost = cost < minCost ? cost : minCost;
        maxCost = cost > maxCost ? cost : maxCost;
        for (int i=0; i<sampleSize; ++i) {
            sample.add(kernelChanged.p(minCost + (maxCost - minCost)/sampleSize * i));
        }
        
        double averageChanged = sample.stream()
            .mapToDouble(e->e).average().getAsDouble();
        double[] densityAsDoubleChanged = densityAsStreamChangedSupplier.get().toArray();

        logger.info(String.format("Average of kernel density estimators old: %s vs new: %s.",
				  average, averageChanged));
	boolean isLastInputKept = average > averageChanged;
        if (isLastInputKept) { // Replace peaking value by new flatter value
            inputToCost.remove(costToInput.get(maxValue));
	    inputToArgs.remove(costToInput.get(maxValue));
            costToInput.remove(maxValue);
            inputToCost.put(key, cost);
            costToInput.put(cost, key);
	    inputToArgs.put(key, argsAsArray);
        }
	
	return isLastInputKept;
    }
}
