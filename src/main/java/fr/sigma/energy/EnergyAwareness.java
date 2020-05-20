package fr.sigma.energy;

import fr.sigma.structures.Pair;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;

import org.json.*;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import knapsack.Knapsack;
import knapsack.model.OneOrNoneFromGroupProblem;
    


/**
 * Provides data structures and algorithms to propagate energy
 * awareness as part of the workflow; upgrade or downgrade arguments
 * to adapt to energy consumption objective.
 */
public class EnergyAwareness {

    private TreeMap<String, RangeSet<Double>> funcToIntervals;
    private LocalEnergyData localEnergyData;

    private final String name;
    
    public EnergyAwareness(String name, int maxSizeOfLocalData) {
        funcToIntervals = new TreeMap();
        localEnergyData = new LocalEnergyData(maxSizeOfLocalData);
        this.name = name;
    }

    public TreeMap<String, RangeSet<Double>> getFuncToIntervals() {
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

    public void updateRemote(String func, RangeSet<Double> costs) {
        // (TODO) could be important to handle version of data
        funcToIntervals.put(func, costs);
    }
    
    /**
     * Combine local intervals with ones got from remote services to
     * create a new interval. It should be sent to parent service.
     */
    public RangeSet<Double> combineIntervals() {
        var result = localEnergyData.getIntervals();
        for (var interval : funcToIntervals.values())
            result = _combination(result, interval);        
        return result;
    }

    public RangeSet<Double> getIntervals() { // alias of combine
        return combineIntervals();
    }

    public static RangeSet<Double> _combination(RangeSet<Double> i1,
                                                RangeSet<Double> i2) {
        RangeSet<Double> result = TreeRangeSet.create();
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
        
        if (goDefault) {
            var results = new TreeMap<String, Double>();
            results.put(name, -1.);
            for (var func : funcToIntervals.keySet())
                results.put(func, -1.);
            return results;
        }
        
        double ratio = 1000. / objective; // (TODO) configurable scaling
        var groupToFunc = new TreeMap<Integer, String>();

        var weightAsList = new ArrayList<Integer>();
        var profitAsList = new ArrayList<Integer>();
        var groupAsList = new ArrayList<Integer>();

        weightAsList.add(0); // placeholders
        profitAsList.add(1);
        groupAsList.add(-1);

        var localIntervals = localEnergyData.getIntervals();
        groupToFunc.put(0, name);
        for (Range<Double> interval : localIntervals.asRanges()) {
            weightAsList.add((int)(interval.lowerEndpoint() * ratio));
            profitAsList.add(1);
            groupAsList.add(0);
        }

        int groupIndex = 1;
        for (Map.Entry<String, RangeSet<Double>> kv : funcToIntervals.entrySet()) {
            for (var intervals : kv.getValue().asRanges()) {
                weightAsList.add((int)(intervals.lowerEndpoint() * ratio));
                profitAsList.add(1);
                groupAsList.add(groupIndex);
            }
            groupToFunc.put(groupIndex, kv.getKey());
            groupIndex += 1;
        }

        System.out.println(Arrays.toString(profitAsList.toArray()));
        System.out.println(Arrays.toString(weightAsList.toArray()));
        System.out.println(Arrays.toString(groupAsList.toArray()));

        
        var problem = new OneOrNoneFromGroupProblem
            (1000,
             profitAsList.stream().mapToInt(i->i).toArray(),
             weightAsList.stream().mapToInt(i->i).toArray(),
             groupAsList.stream().mapToInt(i->i).toArray());
        
        var knapsack = new Knapsack(problem);
        var solution = knapsack.solve().solution;

        var funcToInterval = new TreeMap<String, Range>();
        System.out.println("=======");
        System.out.println(Arrays.toString(solution));
        for (int i = 0; i < solution.length; ++i) {
            if (solution[i]) {
                double value = weightAsList.get(i) / ratio;
                String func = groupToFunc.get(groupAsList.get(i));
                RangeSet<Double> interval = (func.equals(name)) ?
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

