package fr.sigma.structures;

import java.util.ArrayList;



public class Polynomes {

    public final ArrayList<Polynome> polynomes = new ArrayList<>();
    public final ArrayList<Integer> indices = new ArrayList<>();
    
    public Polynomes() { }

    public void add(Polynome polynome, Integer index) {
        polynomes.add(polynome);
        indices.add(index);
    }

    public long get(Double[] args) {
        long sum = 0;
        for (int i = 0; i < polynomes.size(); ++i) {
            var index = indices.get(i);
            var arg = args[index];
            sum += polynomes.get(i).get(arg);
        }
        return sum;
    }
}
