package fr.sigma.box;


import java.util.ArrayList;



public class Polynomes {

    private final ArrayList<Polynome> polynomes = new ArrayList<>();
    public final ArrayList<Integer> indices = new ArrayList<>();
    
    public Polynomes() { }

    public void add(Polynome polynome, int index) {
        polynomes.add(polynome);
        indices.add(index);
    }

    public long get(Double[] args) {
        long sum = 0;
        for (int i = 0; i < polynomes.size(); ++i)
            sum += polynomes.get(i).get(args[indices.get(i)]);
        return sum;
    }
}
