package fr.sigma.structures;


import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;



public class MCKPTest {

    @Test
    public void initializeProperly () {
        int max = 1000;
        var mckp = new MCKP(max, new ArrayList<>());
        assertEquals(1, mckp.getElements().size());
        assertEquals(0, mckp.getMatrix().size());
        assertEquals(max, mckp.getMaxObjective());
    }



    @Test
    public void getMatrixOfOneGroup () {
        var mckp = new MCKP(4,
                            new ArrayList<>(Arrays.asList(new MCKPElement(1,1,1),
                                                          new MCKPElement(2,2,1),
                                                          new MCKPElement(3,3,1))));
        mckp.process();
        assertEquals(new ArrayList<Integer>(Arrays.asList(0, 0, 0, 0, 0)),
                     mckp.getMatrix().get(0));
        assertEquals(new ArrayList<Integer>(Arrays.asList(-1, 1, 1, 1, 1)),
                     mckp.getMatrix().get(1));
        assertEquals(new ArrayList<Integer>(Arrays.asList(-1, 1, 2, 2, 2)),
                     mckp.getMatrix().get(2));
        assertEquals(new ArrayList<Integer>(Arrays.asList(-1, 1, 2, 3, 3)),
                     mckp.getMatrix().get(3));
    }

    @Test
    public void getSolutionsOfOneGroup () {
        var mckp = new MCKP(4,
                            new ArrayList<>(Arrays.asList(new MCKPElement(1,1,0),
                                                          new MCKPElement(2,2,0),
                                                          new MCKPElement(3,3,0))));
        mckp.process();
        var s1 = mckp.backtrack(1);
        assertEquals(1, s1.size());
        assertEquals(1, (int) s1.get(0));
        var s2 = mckp.backtrack(2);
        assertEquals(1, s2.size());
        assertEquals(2, (int) s2.get(0));
        var s3 = mckp.backtrack(3);
        assertEquals(1, s3.size());
        assertEquals(3, (int) s3.get(0));
        var s4 = mckp.backtrack(4);
        assertEquals(1, s4.size());
        assertEquals(3, (int) s4.get(0));
    }
    

    
    @Test
    public void solveNothing () {
        var mckp = new MCKP(1000, new ArrayList<>());
        var solution = mckp.solve(120);
        assert(mckp.getMatrix().size()>0);
        assert(solution.isEmpty());
    }


    @Test
    public void solveWithMultipleElements () {
        var mckp = new MCKP(10,
                            new ArrayList<>(Arrays.asList(new MCKPElement(1,1,1),
                                                          new MCKPElement(1,1,2),
                                                          new MCKPElement(2,2,2),
                                                          new MCKPElement(3,3,2),
                                                          new MCKPElement(2,2,3))));
        var s = mckp.solve(7);
        assertEquals(3, s.size());
        assertEquals(new MCKPElement(2,2,3), s.get(0));
        assertEquals(new MCKPElement(3,3,2), s.get(1));
        assertEquals(new MCKPElement(1,1,1), s.get(2));

        var s2 = mckp.solve(5);
        assertEquals(3, s2.size());
        assertEquals(new MCKPElement(2,2,3), s2.get(0));
        assertEquals(new MCKPElement(2,2,2), s2.get(1));
        assertEquals(new MCKPElement(1,1,1), s2.get(2));

        var s3 = mckp.solve(4);
        assertEquals(3, s3.size());
        assertEquals(new MCKPElement(2,2,3), s3.get(0));
        assertEquals(new MCKPElement(1,1,2), s3.get(1));
        assertEquals(new MCKPElement(1,1,1), s3.get(2));
    }

    @Test
    public void solutionOfThingThatDoesNotWorkAsExpected () {
        var mckp = new MCKP(100,
                            new ArrayList<>(Arrays.asList(new MCKPElement(0,0,0),
                                                          new MCKPElement(47,47,1),
                                                          new MCKPElement(94,94,1),
                                                          new MCKPElement(11,11,2),
                                                          new MCKPElement(29,29,2))));
        var s = mckp.solve(100);
        assertEquals(3, s.size());
        assertEquals(new MCKPElement(29,29,2), s.get(0));
        assertEquals(new MCKPElement(47,47,1), s.get(1));
        assertEquals(new MCKPElement(0,0,0), s.get(2));        
    }
}
