package fr.sigma.energy;

import java.util.ArrayList;
import java.util.Arrays;
    
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;



public class ArgsFilterTest {

    @Test
    public void initialize () {
        var filter = new ArgsFilter();
        // /!\ everything is implementation dependent here...
        assertEquals(14, filter.getThreshold());
    }

    @Test
    public void insertWithoutArgs () {
        var filter = new ArgsFilter();
        var args = new ArrayList<Double>();
        var stop = filter.isTriedEnough(args);
        assert(!stop);
    }
    
    @Test
    public void firstInsert () {
        var filter = new ArgsFilter();
        var args = new ArrayList<Double>(Arrays.asList(42.));
        var stop = filter.isTriedEnough(args);
        assert(!stop);
    }

    @Test
    public void aboveThresh () {
        var filter = new ArgsFilter();
        filter.setThreshold(4);
        var args = new ArrayList<Double>(Arrays.asList(42.));
        var stop = filter.isTriedEnough(args);
        assert(!stop);
        stop = filter.isTriedEnough(args);
        assert(!stop);
        stop = filter.isTriedEnough(args);
        assert(!stop);
        stop = filter.isTriedEnough(args);
        assert(!stop);
        stop = filter.isTriedEnough(args);
        assert(stop);
    }
    
}
