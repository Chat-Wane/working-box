package fr.sigma.energy;

import java.util.TreeMap;

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

    @Test
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

    @Test
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

    @Test
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



    @Test
    public void objectivesAllAlone () {
        var ea = new EnergyAwareness("meow", 10);
        // doing with remote only because easier to manipulate
        // compared to kernel density calls        
        var remoteRange = new TreeMap<String, Range>();
        remoteRange.put("woof", Range.closed(13., 16.));
        var result = ea.getObjectivesFromInterval(14.,remoteRange);
        assertEquals(14., (double) result.get("woof"));
    }

    @Test
    public void objectivesMultipleServices () {
        var ea = new EnergyAwareness("meow", 10);
        var remoteRange = new TreeMap<String, Range>();
        remoteRange.put("woof", Range.closed(13., 16.));
        remoteRange.put("waf", Range.closed(14., 16.));
        var result = ea.getObjectivesFromInterval(28., remoteRange);
        assertEquals(13.5, (double) result.get("woof"));
        assertEquals(14.5, (double) result.get("waf"));
    }

    @Test
    public void objectivesMultipleServicesWithSurplus () {
        var ea = new EnergyAwareness("meow", 10);
        var remoteRange = new TreeMap<String, Range>();
        remoteRange.put("woof", Range.closed(12., 12.5));
        remoteRange.put("waf", Range.closed(14., 16.));
        var result = ea.getObjectivesFromInterval(28.,remoteRange);
        assertEquals(12.5, (double) result.get("woof"));
        assertEquals(15.5, (double) result.get("waf"));
    }



    
}
