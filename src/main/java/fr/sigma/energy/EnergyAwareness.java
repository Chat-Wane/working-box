package fr.sigma.energy;

import fr.sigma.structures.Pair;
import fr.sigma.structures.MCKP;
import fr.sigma.structures.MCKPElement;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
    


/**
 * Provides data structures and algorithms to propagate energy
 * awareness as part of the workflow; upgrade or downgrade arguments
 * to adapt to energy consumption objective.
 */
public class EnergyAwareness {

    private TreeMap<String, TreeRangeSet<Double>> funcToIntervals;
    private LocalEnergyData localEnergyData;

    private final String name;
    
    public EnergyAwareness(String name, int maxSizeOfLocalData) {
        funcToIntervals = new TreeMap();
        localEnergyData = new LocalEnergyData(maxSizeOfLocalData);
        this.name = name;
    }

    public TreeMap<String, TreeRangeSet<Double>> getFuncToIntervals() {
        return funcToIntervals;
    }

    public LocalEnergyData getLocalEnergyData() {
        return localEnergyData;
    }

    public String getName() {
        return name;
    }


    
    public void update(String message) {
        // (TODO)
    }
    //     // (TODO) not handle manually json parsing
    //     JSONObject obj = new JSONObject(message);
    //     var xs = obj.getJSONObject("xy").getJSONArray("x");
    //     var ys = obj.getJSONObject("xy").getJSONArray("y");
    //     for (int i = 0; i < xs.length(); ++i){
    //         String x = xs.getString(i);
    //         Double y = ys.getDouble(i);
    //         xy.put(x, y);
    //     }

    //     var minmax = obj.getJSONObject("minmax");
    //     Iterator<String> keys = minmax.keys();
        
    //     while (keys.hasNext()) {
    //         var remote = keys.next();
    //         var min = minmax.getJSONObject(remote).getDouble("min");
    //         var max = minmax.getJSONObject(remote).getDouble("max");
    //         funcToMinMax.put(remote, new Pair(min, max));
    //     }
    // }

    public void addEnergyData(ArrayList<Double> args, double cost) {
        localEnergyData.addEnergyData(args, cost);
    }

    public void updateRemotes(ArrayList<String> names) {
        for (var func : names)
            funcToIntervals.put(name, TreeRangeSet.create());
    }

    public void updateRemote(String func, TreeRangeSet<Double> costs) {
        // (TODO) could be important to handle version of data
        funcToIntervals.put(func, costs);
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

    public TreeMap<String, Double> getObjectives(double objective) {
        // Check if has enough data to create objectives, otherwise, default
        // values are returned.
        boolean goDefault = localEnergyData.getIntervals().isEmpty();
        for (var interval : funcToIntervals.values())
            if (!goDefault && interval.isEmpty())
                goDefault = true;

	var defaultResult = new TreeMap<String, Double>();
	defaultResult.put(name, -1.);
	for (var func : funcToIntervals.keySet())
	    defaultResult.put(func, -1.);
	
        if (goDefault) 
            return defaultResult;

	
        double ratio = 1000. / objective; // (TODO) configurable scaling
        var groupToFunc = new TreeMap<Integer, String>();

        var mckpElements = new ArrayList<MCKPElement>();

        var localIntervals = localEnergyData.getIntervals();
        groupToFunc.put(0, name);
        int groupIndex = 0;
        for (Range<Double> interval : localIntervals.asRanges())
            mckpElements.add(new MCKPElement((int)(interval.lowerEndpoint()*ratio),
                                             (int)(interval.lowerEndpoint()*ratio),
                                             groupIndex));
        
        ++groupIndex;
        
        for (Map.Entry<String, TreeRangeSet<Double>> kv : funcToIntervals.entrySet()) {
            for (var intervals : kv.getValue().asRanges())
                mckpElements.add(new MCKPElement((int)(intervals.lowerEndpoint()*ratio),
                                                 (int)(intervals.lowerEndpoint()*ratio),
                                                 groupIndex));
            groupToFunc.put(groupIndex, kv.getKey());
            ++groupIndex;
        }

        var mckp = new MCKP(1000, mckpElements); // (TODO) cache mckp
        var solution = mckp.solve(1000);

	if (solution.isEmpty())
	    return defaultResult;
	
        var funcToInterval = new TreeMap<String, Range>();
        for (int i = 0; i < solution.size(); ++i) {            
            double value = solution.get(i).weight / ratio;
            String func = groupToFunc.get(solution.get(i).group);
            TreeRangeSet<Double> interval = (func.equals(name)) ?
                localIntervals : funcToIntervals.get(func);
                
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
        
        return getObjectivesFromInterval(objective, funcToInterval);
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
        return null; // (TODO)
    }
    
    // (TODO) add read-only variables
    // default value is null, it should keep args as input
    // public Double[] solveObjective(double objective) {
    //     if (objective < 0) return null; // objective unknown
    //     // (TODO) sort xy to get logarithmic complexity
    //     var min = Double.POSITIVE_INFINITY;
    //     String args = null;
    //     for (Map.Entry<String, Double> kv : xy.entrySet()) {
    //         if (Math.abs(objective - kv.getValue()) < min) {
    //             min = Math.abs(objective - kv.getValue());
    //             args = kv.getKey();
    //         }
    //     }

    //     var toParse = new JSONArray("["+ args+"]"); // (TODO) fix "[" "]" because it was not list
    //     //        var toParse = new JSONArray(args);
    //     Double[] xs = new Double[toParse.length()];
    //     for (int i = 0; i < toParse.length(); ++i) {
    //         xs[i] = toParse.getDouble(i);
    //     }
        
    //     return xs;
    // }
    
}

