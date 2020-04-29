package fr.sigma.box;



/**
 * Local data structure that contains enough energy data to make good
 * decisions concerning arguments and energy awareness.
 */
public class LocalEnergyData {

    private TreeMap<String, Double> xy;

    private int maxSize = 10;

    public LocalEnergyData () {
        xy = new TreeMap();
    }

    public void addEnergyData (ArrayList<Double> args, Double cost) {
        String key = String.join(", ", args);      
        
        if (xy.size() < maxSize) {
            xy.put(key, cost);
            return ;
        }

        // (TODO)
    }
}
