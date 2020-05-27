package fr.sigma.box;

import java.util.ArrayList;

import com.google.common.base.Converter;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.BoundType;



/**
 * RangeSet are not easily serializable/deserialisable to json using
 * gson. Instead, we use this that comes from
 * "https://stackoverflow.com/questions/23179024/create-a-gson-typeadapter-for-a-guava-range".
 * (TODO) change this to gson when it is ready.
 */

public class RangeSetConverter {

    /**
     * Converter between Range instances and Strings, essentially a custom serializer.
     * Ideally we'd let Gson or Guava do this for us, but presently this is cleaner.
     */
    public static <T extends Comparable<? super T>> Converter<Range<T>, String> rangeConverter(final Converter<T, String> elementConverter) {
	final String NEG_INFINITY = "-\u221e";
	final String POS_INFINITY = "+\u221e";
	final String DOTDOT = "\u2025";
	return new Converter<Range<T>, String>() {
	    @Override
	    protected String doForward(Range<T> range) {
		return (range.hasLowerBound() && range.lowerBoundType() == BoundType.CLOSED ? "[" : "(") +
		    (range.hasLowerBound() ? elementConverter.convert(range.lowerEndpoint()) : NEG_INFINITY) +
		    DOTDOT +
		    (range.hasUpperBound() ? elementConverter.convert(range.upperEndpoint()) : POS_INFINITY) +
		    (range.hasUpperBound() && range.upperBoundType() == BoundType.CLOSED ? "]" : ")");
	    }

	    @Override
	    protected Range<T> doBackward(String range) {
		String[] endpoints = range.split(DOTDOT);

		Range<T> ret = Range.all();
		if(!endpoints[0].substring(1).equals(NEG_INFINITY)) {
		    T lower = elementConverter.reverse().convert(endpoints[0].substring(1));
		    ret = ret.intersection(Range.downTo(lower, endpoints[0].charAt(0) == '[' ? BoundType.CLOSED : BoundType.OPEN));
		}
		if(!endpoints[1].substring(0,endpoints[1].length()-1).equals(POS_INFINITY)) {
		    T upper = elementConverter.reverse().convert(endpoints[1].substring(0,endpoints[1].length()-1));
		    ret = ret.intersection(Range.upTo(upper, endpoints[1].charAt(endpoints[1].length()-1) == ']' ? BoundType.CLOSED : BoundType.OPEN));
		}
		return ret;
	    }
	};
    }

    /**
     * Converter between RangeSet instances and Strings, essentially a custom serializer.
     * Ideally we'd let Gson or Guava do this for us, but presently this is cleaner.
     */
    public static <T extends Comparable<? super T>> Converter<TreeRangeSet<T>, String> rangeSetConverter(final Converter<T, String> elementConverter) {
	return new Converter<TreeRangeSet<T>, String>() {
	    private final Converter<Range<T>, String> rangeConverter = rangeConverter(elementConverter);
	    @Override
	    protected String doForward(TreeRangeSet<T> rs) {
		if (rs.asRanges().isEmpty())
		    return "EMPTY";
		
		ArrayList<String> ls = new ArrayList<>();
		for(Range<T> range : rs.asRanges()) {
		    ls.add(rangeConverter.convert(range));
		}
		return Joiner.on(", ").join(ls);
	    }

	    @Override
	    protected TreeRangeSet<T> doBackward(String rs) {
		if (rs.equals("EMPTY"))
		    return TreeRangeSet.create();
		
		Iterable<String> parts = Splitter.on(",").trimResults().split(rs);
		TreeRangeSet<T> trs = TreeRangeSet.create();
		for(String range : parts) {
		    trs.add(rangeConverter.reverse().convert(range));
		}
		return trs;
	    }
	};
    }

}
