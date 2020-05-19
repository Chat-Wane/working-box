package fr.sigma.energy;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;



public class EnergyAwarenessTest {

    @Test
    public void initialize () {
        var ea = new EnergyAwareness("meow", 10);
        assertEquals(0, ea.getFuncToIntervals().size());
        assertEquals(0, ea.getLocalEnergyData().size());
        assertEquals("meow", ea.getName());
    }

    @Test
    public void testSimple_combination () {
        var ea = new EnergyAwareness("meow", 10);
        RangeSet<Double> r1 = TreeRangeSet.create();
        RangeSet<Double> r2 = TreeRangeSet.create();
        assert(ea._combination(r1,r2).isEmpty());
        r1.add(Range.closed(1.,2.));
        assert(ea._combination(r1,r2).enclosesAll(r1));
        assert(r1.enclosesAll(ea._combination(r1,r2)));
        assert(ea._combination(r2,r1).enclosesAll(r1));
        assert(r1.enclosesAll(ea._combination(r2,r1)));
    }

    public void test_combination () {
        var ea = new EnergyAwareness("meow", 10);
        RangeSet<Double> r1 = TreeRangeSet.create();
        RangeSet<Double> r2 = TreeRangeSet.create();
        r1.add(Range.closed(1.,2.));
        r2.add(Range.closed(3.,10.));
        RangeSet<Double> expected = TreeRangeSet.create();
        expected.add(Range.closed(4., 12.));
        var result = ea._combination(r1, r2);
        assert(result.enclosesAll(expected));
        assert(expected.enclosesAll(result));
    }

    public void test_combination_with_factorize () {
        var ea = new EnergyAwareness("meow", 10);
        RangeSet<Double> r1 = TreeRangeSet.create();
        RangeSet<Double> r2 = TreeRangeSet.create();
        r1.add(Range.closed(1.,2.));
        r1.add(Range.closed(5.,6.));
        r2.add(Range.closed(3.,10.));
        RangeSet<Double> expected = TreeRangeSet.create();
        expected.add(Range.closed(4., 16.));
        var result = ea._combination(r1, r2);
        assert(result.enclosesAll(expected));
        assert(expected.enclosesAll(result));
    }

    public void test_combination_without_factorize () {
        var ea = new EnergyAwareness("meow", 10);
        RangeSet<Double> r1 = TreeRangeSet.create();
        RangeSet<Double> r2 = TreeRangeSet.create();
        r1.add(Range.closed(1.,2.));
        r1.add(Range.closed(12.,13.));
        r2.add(Range.closed(3.,10.));
        RangeSet<Double> expected = TreeRangeSet.create();
        expected.add(Range.closed(4., 12.));
        expected.add(Range.closed(15., 23.));
        var result = ea._combination(r1, r2);
        assert(result.enclosesAll(expected));
        assert(expected.enclosesAll(result));
    }

    @Test
    public void testCombineIntervalsOfNothing () {
        var ea = new EnergyAwareness("meow", 10);
        assert(ea.getIntervals().isEmpty());
    }

    @Test
    public void testCombineIntervalsWithLocalAndRemoteIntervals () {
        var ea = new EnergyAwareness("meow", 10);
        RangeSet<Double> remoteCosts = TreeRangeSet.create();
        remoteCosts.add(Range.closed(13., 16.));
        ea.updateRemote("woof", remoteCosts);
        var toSend = ea.getIntervals();
        assert(remoteCosts.enclosesAll(toSend));
        
        var ea2 = new EnergyAwareness("anotherService", 10);
        ea2.updateRemote("waf", remoteCosts);
        ea2.updateRemote("meow", toSend);
        RangeSet<Double> expected = TreeRangeSet.create();
        expected.add(Range.closed(26., 32.));
        assert(expected.enclosesAll(ea2.getIntervals()));
        assert(ea2.getIntervals().enclosesAll(expected));        
    }

    
}
