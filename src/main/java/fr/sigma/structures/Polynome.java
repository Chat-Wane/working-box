package fr.sigma.structures;

import java.util.ArrayList;
import java.util.List;



public class Polynome {

    // c0 + c1*x + c2*x² ...
    public final ArrayList<Double> coefficients = new ArrayList<>(); 

    public Polynome() {
        coefficients.add(0.);
    }

    public Polynome(double... coefs) {
        for (int i = 0; i < coefs.length; ++i)
            coefficients.add(coefs[i]);
    }

    public Polynome(List<Double> coefs) {
        for (int i = 0; i < coefs.size(); ++i)
            coefficients.add(coefs.get(i));
    }

    public long get(double x) {
        double result = 0.;
        for (int i = 0; i < coefficients.size(); ++i)
            result += coefficients.get(i) * Math.pow(x, i);
        return (long) result;
    }
    
}
