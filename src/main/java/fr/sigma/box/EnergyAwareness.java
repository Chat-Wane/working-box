package fr.sigma.box;

import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;
import org.json.*;



/**
 * Provides data structures and algorithms to propagate energy
 * awareness as part of the workflow; upgrade or downgrade arguments
 * to adapt to energy consumption objective.
 */
public class EnergyAwareness {

    private TreeMap<String, Pair<Double, Double>> funcToMinMax;

    private TreeMap<String, Double> xy;

    private final String name;
    
    public EnergyAwareness(String name) {
        funcToMinMax = new TreeMap();
        xy = new TreeMap();
        this.name = name;
    }
    
    public void update(String message) {
        // (TODO) not handle manually json parsing
        JSONObject obj = new JSONObject(message);
        var xs = obj.getJSONObject("xy").getJSONArray("x");
        var ys = obj.getJSONObject("xy").getJSONArray("y");
        for (int i = 0; i < xs.length(); ++i){
            String x = xs.getString(i);
            Double y = ys.getDouble(i);
            xy.put(x, y);
        }

        var minmax = obj.getJSONObject("minmax");
        Iterator<String> keys = minmax.keys();
        
        while (keys.hasNext()) {
            var remote = keys.next();
            var min = minmax.getJSONObject(remote).getDouble("min");
            var max = minmax.getJSONObject(remote).getDouble("max");
            funcToMinMax.put(remote, new Pair(min, max));
        }
    }
   
    public TreeMap<String, Double> getObjectives(double objective) {
        var results = new TreeMap<String, Double>();

        // default value
        if (objective < 0) {
            results.put(name, -1.);
            for (String remote : funcToMinMax.keySet()) {
                results.put(remote, -1.);
            }
            return results;
        }
        
        var objectiveToDistribute = objective - min();
        
        var funcToRange = new ArrayList<Pair<String, Double>>();
        funcToRange.add(new Pair(name, max() - min()));
        
        for (Map.Entry<String, Pair<Double, Double>> kv : funcToMinMax.entrySet()) {
            funcToRange.add(new Pair(kv.getKey(),
                                     kv.getValue().second - kv.getValue().first));
            objectiveToDistribute -= kv.getValue().first; // o = o - min
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

            if (kv.first.equals(name)) {
                results.put(kv.first, give + min());
            } else {
                results.put(kv.first, give + funcToMinMax.get(kv.first).first);
            }
        }
        
        return results;
    }

    // (TODO) add read-only variables
    // default value is null, it should keep args as input
    public Double[] solveObjective(double objective) {
        if (objective < 0) return null; // objective unknown
        // (TODO) sort xy to get logarithmic complexity
        var min = Double.POSITIVE_INFINITY;
        String args = null;
        for (Map.Entry<String, Double> kv : xy.entrySet()) {
            if (Math.abs(objective - kv.getValue()) < min) {
                min = Math.abs(objective - kv.getValue());
                args = kv.getKey();
            }
        }

        var toParse = new JSONArray("["+ args+"]"); // (TODO) fix "[" "]" because it was not list
        //        var toParse = new JSONArray(args);
        Double[] xs = new Double[toParse.length()];
        for (int i = 0; i < toParse.length(); ++i) {
            xs[i] = toParse.getDouble(i);
        }
        
        return xs;
    }



    private double min() {
        return xy.size() > 0 ? Collections.min(xy.values()) : 0;
    }

    private double max() {
        return xy.size() > 0 ? Collections.max(xy.values()) : 0;
    }

}
