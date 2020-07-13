package fr.sigma.energy;

import fr.sigma.structures.Pair;
import fr.sigma.structures.MCKP;
import fr.sigma.structures.MCKPElement;

import java.util.Objects;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Provides data structures and algorithms to propagate energy
 * awareness as part of the workflow; upgrade or downgrade arguments
 * to adapt to energy consumption objective.
 */
public class EnergyAwareness {

    private Logger logger = LoggerFactory.getLogger(getClass());
    
    private TreeMap<String, TreeRangeSet<Double>> funcToIntervals;
    private LocalEnergyData localEnergyData;
    private ArgsFilter argsFilter;
    private final String name;

    private int maxObjective = 1000; // (TODO) upscale downscale automatically
    // allows other solutions to improve fairness (between 0 and 1)
    private double fairnessFactor = 0.00; 

    public EnergyAwareness(String name, int maxSizeOfLocalData, int thresholdFilter) {
        funcToIntervals = new TreeMap();
        localEnergyData = new LocalEnergyData(maxSizeOfLocalData, thresholdFilter);
        argsFilter = new ArgsFilter(thresholdFilter);
        this.name = name;
    }

    public EnergyAwareness(String name, int maxSizeOfLocalData,
			   int nbDifferentInput, int thresholdFilter) {
        funcToIntervals = new TreeMap();
        localEnergyData = new LocalEnergyData(maxSizeOfLocalData, thresholdFilter);
        argsFilter = new ArgsFilter(nbDifferentInput, thresholdFilter);
        this.name = name;
    }

    public EnergyAwareness(String name, int maxSizeOfLocalData,
                           int nbDifferentInput, int thresholdFilter,
                           double fairnessFactor) {
        funcToIntervals = new TreeMap();
        localEnergyData = new LocalEnergyData(maxSizeOfLocalData, thresholdFilter);
        argsFilter = new ArgsFilter(nbDifferentInput, thresholdFilter);
        this.fairnessFactor = fairnessFactor;
        this.name = name;
    }
    
    public TreeMap<String, TreeRangeSet<Double>> getFuncToIntervals() {
        return funcToIntervals;
    }
    public LocalEnergyData getLocalEnergyData() { return localEnergyData; }
    public String getName() { return name; }



    /**
     * Process the new call to the function.
     * @param objective the objective that has been set by parent service.
     * @param args the args that matter to the local function
     * @return a pair <objectives , self-tuned args>
     */
    public Triple<TreeMap<String, Double>, Double[], Boolean>
	newFunctionCall(double objective, Double[] args) {
		
        if (objective < 0) { // default
            logger.info("This box has no energy objective defined.");
            argsFilter.tryArgs(args);
            return new ImmutableTriple(getObjectives(objective, false), args, false);
        }
        
        logger.info(String.format("This box has an energy consumption objective of %s.",
                                  objective));
        
        TreeMap<String, Double> objectives = null;
        Double[] solution = args;
	boolean isLastInputRewritten = false;
	
	if (!argsFilter.isTriedEnough(args)) {
	    // #1 not enough data to be part of the computation
            if (localEnergyData.exists(args)) {
                // small accuracy improvement when this service
                // already monitored the current args.
                logger.info("Removing known cost from objective.");
                objective = Math.max(0., objective - localEnergyData.getCost(args));
            }
	    objectives = getObjectives(objective, true); // no objective for self
	} else {
	    // #2 divides objective between itself and remotes
	    objectives = getObjectives(objective, false);
            solution = solveObjective(objectives.get(name));
	    isLastInputRewritten = !Objects.isNull(solution);
        }

	logger.info(String.format("Distributes energy objective as: %s.", objectives));	
	if (isLastInputRewritten)
	    logger.info(String.format("Rewrites local arguments: %s -> %s.",
				      Arrays.toString(args),
				      Arrays.toString(solution)));
	else
	    solution = args;
		
        argsFilter.tryArgs(solution);        
        return new ImmutableTriple(objectives, solution, isLastInputRewritten);
    }
    

    
    public boolean addEnergyData(Double[] args, double cost) {
        return localEnergyData.addEnergyData(args, cost);
    }
    
    public void updateRemotes(ArrayList<String> names) {
        for (var func : names)
            funcToIntervals.put(func, TreeRangeSet.create());
    }
    
    public void updateRemote(String func, TreeRangeSet<Double> costs) {
        // (TODO) could be important to handle version of data
        funcToIntervals.put(func, costs);
    }

    public void resetRemote(String func) {
	funcToIntervals.put(func, TreeRangeSet.create());
    }
    
    /**
     * Combine local intervals with ones got from remote services to
     * create a new interval. It should be sent to parent service.
     */
    public TreeRangeSet<Double> combineIntervals() {        
        var result = localEnergyData.getIntervals();
        for (var interval : funcToIntervals.values())
            result = _combination(result, interval);        
        return result;
    }
    
    public TreeRangeSet<Double> getIntervals() { // alias of combine
        return combineIntervals();
    }
    
    public static TreeRangeSet<Double> _combination(RangeSet<Double> i1,
                                                    RangeSet<Double> i2) {
        TreeRangeSet<Double> result = TreeRangeSet.create();
        // #A quick defaults
        if (i1.isEmpty() && i2.isEmpty())
            return result; // empty
        if (i1.isEmpty()) {
            result.addAll(i2);
            return result;
        }
        if (i2.isEmpty()) {
            result.addAll(i1);
            return result;
        }

        // #B otherwise, all combinations       
        // /!\ may be expensive without range factorization, i.e., it
        // has a quadratic complexity
        for (Range<Double> r1 : i1.asRanges()) {
            for (Range<Double> r2 : i2.asRanges()) {
                result.add(Range.closed(r1.lowerEndpoint() + r2.lowerEndpoint(),
                                        r1.upperEndpoint() + r2.upperEndpoint()));
            }
        }   
        return result;
    }

    /**
     * Get the objectives of (i) ourself and (ii) other called remote services.
     * @param objective: the objective to divide.
     * @param withoutMe: do we have enough data to get an objective for ourself.
     * @return a map of service_name to its assignated objective.
     **/
    public TreeMap<String, Double> getObjectives(double objective, boolean withoutMe) {
	var localIntervals = localEnergyData.getIntervals();

	// #A objective is not set or,
	// we don't even have our own energy data, how could we have others ?
	if (localIntervals.isEmpty() || objective < 0) {
	    var defaultResult = new TreeMap<String, Double>();
	    defaultResult.put(name, -1.);
	    for (var func : funcToIntervals.keySet())
		defaultResult.put(func, -1.);	    
            return defaultResult;
	}
	
	// #B Otherwiiiiiiiiise, process objectives of children and self.
        double ratio = (double) maxObjective / objective; // (TODO) configurable scaling
        var groupToFunc = new TreeMap<Integer, String>();
        var mckpElements = new ArrayList<MCKPElement>();

	// format the data to go through mckp solver
	var funcToIntervalsCopy = new TreeMap<String, TreeRangeSet<Double>>(funcToIntervals);
	if (withoutMe)
	    funcToIntervalsCopy.put(name, TreeRangeSet.create());
	else
	    funcToIntervalsCopy.put(name, localIntervals);

	int groupIndex = 0;
        for (Map.Entry<String, TreeRangeSet<Double>> kv : funcToIntervalsCopy.entrySet()) {
            for (var intervals : kv.getValue().asRanges())
                mckpElements.add(new MCKPElement((int)(intervals.lowerEndpoint()*ratio),
                                                 (int)(intervals.lowerEndpoint()*ratio),
                                                 groupIndex));
            groupToFunc.put(groupIndex, kv.getKey());
            ++groupIndex;
        }

        var mckp = new MCKP(maxObjective, mckpElements); // (TODO) cache mckp

        // improve fairness by looking at other solutions
        // (TODO) improve complexity by examining different solutions only
        var solveWithObjective = maxObjective + (int) (maxObjective * fairnessFactor);
        var untilObjective = maxObjective - (int) (maxObjective * fairnessFactor);
        ArrayList<MCKPElement> examineSolution = null, solution = null;
        var minStdDev = Double.POSITIVE_INFINITY;
        while (solveWithObjective >= untilObjective) {
            examineSolution = mckp.solve(solveWithObjective);
            solveWithObjective -= 1;

            double meanSolution = 0.;
            double stdDev = 0.;
            for (var element : examineSolution) 
                meanSolution += element.weight;
            meanSolution = (double) meanSolution / examineSolution.size();

            for (var element : examineSolution) 
                stdDev += Math.pow(element.weight - meanSolution, 2);
            stdDev = Math.sqrt(stdDev / examineSolution.size());

            if (Objects.isNull(solution))
                solution = examineSolution;
            if (stdDev < minStdDev) { // fairer solution, keep it
                minStdDev = stdDev;
                solution = examineSolution;
            }
        }                     

        var funcToInterval = new TreeMap<String, Range>();
        for (int i = 0; i < solution.size(); ++i) {
            double value = solution.get(i).weight / ratio;
            String func = groupToFunc.get(solution.get(i).group);
            TreeRangeSet<Double> interval = funcToIntervalsCopy.get(func);
	    
            double distance = Double.MAX_VALUE;
            Range<Double> closestRange = null;
            for (Range<Double> range : interval.asRanges()) {
                if (distance > Math.abs(range.lowerEndpoint() - value)) {
                    distance = Math.abs(range.lowerEndpoint() - value);
                    closestRange = range;
                }
            }
	    
            funcToInterval.put(func, closestRange);
        }
	
	var objectives = getObjectivesFromInterval(objective, funcToInterval);

	for (var func : funcToIntervalsCopy.keySet()) // fill gaps of missing data
	    if (!objectives.containsKey(func))
		objectives.put(func, -1.);
	
	return objectives;
    }

    /**
     * Gives minimal energy to everyone then distributes equally among 
     * services.
     */ 
    public static TreeMap<String, Double> getObjectivesFromInterval
        (double objective,
         TreeMap<String, Range> funcToInterval) {        
        var results = new TreeMap<String, Double>();
        
        // default value -1 for everyone.
        if (objective < 0) {
            for (var service  : funcToInterval.keySet())
                results.put(service, -1.);
            return results;
        }
        
        var objectiveToDistribute = objective;
        var funcToRange = new ArrayList<Pair<String, Double>>();
        for (Map.Entry<String, Range> kv : funcToInterval.entrySet()) {
            Range<Double> span = kv.getValue();            
            funcToRange.add(new Pair(kv.getKey(),
                                     span.upperEndpoint() - span.lowerEndpoint()));
            objectiveToDistribute -= span.lowerEndpoint(); // o = o - min
        }

        // v (UGLY) Double -> double -> Double -> int
        funcToRange.sort((a, b)-> (new Double(a.second - b.second)).intValue());
        var nbShares = funcToRange.size();

        for (Pair<String, Double> kv : funcToRange) {
            var share = objectiveToDistribute/nbShares;
            var surplus = share - kv.second;
            var give = share;
            if (surplus > 0) {
                give = kv.second;
                objectiveToDistribute += surplus;
            }
            objectiveToDistribute -= share;
            nbShares -= 1;

            Range<Double> span = funcToInterval.get(kv.first);
            results.put(kv.first, give + span.lowerEndpoint());
        }
        
        return results;
    }


    public Double[] solveObjective(double objective) {
	if (objective < 0) return null; // default when objective unknown	
        return localEnergyData.getClosest(objective);
    }
    
}

