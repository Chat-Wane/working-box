package fr.sigma.energy;

import fr.sigma.structures.Pair;

import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.DoubleStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private int maxCosts = 3;
    private double maxError = 15.;
    private TreeMap<String, ArrayList<Double>> inputToCost;
    private TreeMap<String, Double[]> inputToArgs;


    public LocalEnergyData (int maxSize, int maxCosts) {
	this.maxSize = maxSize;
        this.maxCosts = maxCosts;
        inputToCost = new TreeMap();
	inputToArgs = new TreeMap();
	logger.info(String.format("Initialized local profiler with max data kept %s x %s.",
				  maxSize, maxCosts));
    }
    
    /**
     * @param maxSize: the maximum number of arguments that are kept
     * locally.  Reaching this threshold, costs are kept regularly
     * spaced; otherwise, maxError is the target.
     * @param maxCosts: the number of costs for each set of arguments
     * that are kept for meaningful statistics purpose.
     * @param maxError: the maximal allowed error in costs. Priority
     * to maxSize though.
     */
    public LocalEnergyData (int maxSize, int maxCosts, double maxError) {
	this.maxSize = maxSize;
        this.maxCosts = maxCosts;
        this.maxError = maxError;
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
    
    public ArrayList<Pair<String, Double>> getAvgCosts() {
        var avgCosts = new ArrayList<Pair<String, Double>>();
        for (Map.Entry<String, ArrayList<Double>> ic: inputToCost.entrySet()) 
            avgCosts.add(new Pair(ic.getKey(),
                                  ic.getValue().stream()
                                  .mapToDouble(d->d).average().orElse(0.)));
        return avgCosts;
    }

    public double[] getSortedAvgCosts() {
        var avgCosts = getAvgCosts();
        return avgCosts.stream().mapToDouble(p -> p.second).sorted().toArray();
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
    

    
    public TreeRangeSet<Double> getIntervals() {
        TreeRangeSet<Double> resultingIntervals = TreeRangeSet.create();
        var costs = getAvgCosts();        
        if (costs.size() == 1) 
            resultingIntervals.add(Range.closed(costs.get(0).second, costs.get(0).second));
        else
            for (Pair<String, Double> cost : costs) 
                resultingIntervals.add(Range.closed(Math.max(0., cost.second - maxError),
                                                    Math.max(0.,cost.second + maxError)));        
        return resultingIntervals;
    }
    


public boolean _add(Double[] inputs, Double cost) {
        String newKey = toKey(inputs);
        boolean isNew = !inputToCost.containsKey(newKey);
        if (isNew) {
            var costs = new ArrayList<Double>();
            costs.add(cost);
            inputToCost.put(newKey, costs);
            inputToArgs.put(newKey, inputs);
        } else {
            ArrayList<Double> costsOfInput = inputToCost.get(newKey);
            while (costsOfInput.size() >= maxCosts) 
                costsOfInput.remove(0); // pop oldest
            costsOfInput.add(new Double(cost)); // add newest
        }
        return isNew;
    }

    public void _rem(String key) {
        if (inputToCost.containsKey(key)){
            inputToCost.remove(key);
            inputToArgs.remove(key);
        }
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
        // #A if the key already exists, we only include the new value
        // to the sliding window of monitored values.
        boolean isNew = _add(argsAsArray, cost);        
        if (!isNew)
            return false;

        boolean isLastInputKept = false;
        // #B otherwise, we keep only significant costs.
        ArrayList<Pair<String, Double>> avgCosts = getAvgCosts();
        avgCosts.sort((p1, p2) -> p1.second.compareTo(p2.second));

        if (avgCosts.size() > maxSize) {
            // #1 when there are too many elements, we keep most
            // regularly spaced costs regardless of maxError.
            var errors = new ArrayList<Double>();
            for (int j  = 1; j < avgCosts.size() - 1; ++j) 
                errors.add(Math.pow(avgCosts.get(j).second - avgCosts.get(j-1).second, 2) +
                           Math.pow(avgCosts.get(j+1).second - avgCosts.get(j).second, 2));
            double minError = avgCosts.stream().mapToDouble(p2->p2.second).min().orElse(0.);
            int[] minErrorIndexT = {-1};
            avgCosts.stream().peek(x -> minErrorIndexT[0]++) // ugly and hacky
                .filter(p1 -> p1.second == minError)
                .findFirst().get();
            int minErrorIndex = minErrorIndexT[0];
            String keyToDelete = avgCosts.get(minErrorIndex).first;
            _rem(keyToDelete);
            return keyToDelete.equals(toKey(argsAsArray));
        } else if (avgCosts.size() > 2) {
            // #2 we aim at keeping costs that have a space lower than
            // 2*maxError with their neighbor(s); but only most
            // significant.
            int[] startIndexT = {-1};
            avgCosts.stream().peek(x -> startIndexT[0]++)
                .filter(p -> p.first.equals(toKey(argsAsArray)))
                .findFirst().get();
            int startIndex = Math.max(1, Math.min(startIndexT[0], avgCosts.size() - 2));
            
            // no need to investigate if the new value is only improvement
            boolean investigating =
                avgCosts.get(startIndex).second - avgCosts.get(startIndex - 1).second < 2*maxError ||
                avgCosts.get(startIndex + 1).second - avgCosts.get(startIndex).second < 2*maxError;
            
            if (investigating) {
                int lowerI = startIndex - 1;
                while (lowerI > 0 &&
                       avgCosts.get(lowerI + 1).second - avgCosts.get(lowerI - 1).second < 2*maxError)
                    lowerI -= 1;
                int higherI = startIndex + 1;
                while (higherI < avgCosts.size() - 1 &&
                       avgCosts.get(higherI + 1).second - avgCosts.get(higherI - 1).second < 2*maxError)
                    higherI += 1;

                boolean removing = true;
                while (removing && higherI - lowerI > 0) {
                    var errors = new ArrayList<Double>();
                    for (int j = lowerI + 1; j < higherI; ++j) 
                        errors.add(Math.pow(avgCosts.get(j).second - avgCosts.get(j-1).second, 2) +
                                   Math.pow(avgCosts.get(j+1).second - avgCosts.get(j).second, 2));
                    int[] sortedErrors = IntStream.range(0, errors.size())
                        .boxed().sorted((i, j) -> errors.get(i).compareTo(errors.get(j)) )
                        .mapToInt(ele -> ele).toArray();
                    
                    removing = false;
                    int k = 0, kE = -1;
                    while (!removing && k < sortedErrors.length){
                        kE = sortedErrors[k];
                        removing = avgCosts.get(kE + lowerI + 2).second - avgCosts.get(kE + lowerI).second < 2*maxError;
                        k += 1;
                    }

                    if (removing) {
                        String keyToRemove = avgCosts.get(kE + lowerI + 1).first;
                        isLastInputKept = !isLastInputKept && keyToRemove.equals(toKey(argsAsArray));
                        _rem(keyToRemove);
                        avgCosts.remove(kE + lowerI + 1);
                        higherI -= 1;
                    }
                }
            }            
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
