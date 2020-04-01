package fr.sigma.box;

import org.junit.jupiter.api.Test;



public class PolynomeTest {

    @Test
    public void large() {
        var p = new Polynome(1000, 0 , 100);
        assert(p.get(300) > 0);
    }
    
}
