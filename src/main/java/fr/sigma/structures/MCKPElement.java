package fr.sigma.structures;



/**
 * An element that can fit in the knapsack.
 */
public class MCKPElement {

    public final int profit;
    public final int weight;
    public final int group;

    public MCKPElement (int profit, int weight, int group) {
        this.profit = profit;
        this.weight = weight;
        this.group = group;
    }

    public static MCKPElement PLACEHOLDER () {
        return new MCKPElement (0, 0, -1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        
        MCKPElement e = (MCKPElement) o;
        return profit == e.profit &&
            weight == e.weight &&
            group == e.group;
    }

    @Override
    public String toString() {
        return String.format("(p=%s, w=%s, g=%s)", profit, weight, group);
    }
}
