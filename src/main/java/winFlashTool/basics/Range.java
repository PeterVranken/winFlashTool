/**
 * @file Range.java
 * Base class for joinable ranges [a, b). For example, [a, b) can be joind with [b, c),
 * resulting in [a, c).
 *
 * Copyright (C) 2025 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
/* Interface of class Range
 *   Range
 */

package winFlashTool.basics;

import java.util.*;

/**
 * A range is some interval [from, till). Operations like comparisons, intersection, union
 * are implemented. For example, [a, b) can be joind with [b, c), resulting in [a, c).
 */
public class Range implements Comparable<Range>
{
    /** First address (inclusive) of range. */
    private long from;

    /** Last address (exclusive) of range. */
    private long till;

    public Range(long from, long till) {
        assert from <= till: "Empty ranges aren't supported";
        this.from = from;
        this.till = till;
    }

    public Range(Range r) {
        this(r.from, r.till);
    }

    public long from() {
        return from;
    }

    public long till() {
        return till;
    }

    public long size() {
        return till - from;
    }

    public boolean isBefore(Range other) {
        return this.till <= other.from;
    }

    public boolean connectsBefore(Range other) {
        return this.till == other.from;
    }

    public boolean connectsBehind(Range other) {
        return this.from == other.till;
    }

    public boolean isBehind(Range other) {
        return this.from >= other.till;
    }

    public boolean connectsTo(Range other) {
        return connectsBefore(other) || connectsBehind(other);
    }

    public boolean overlaps(Range other) {
        return this.from < other.till && other.from < this.till;
    }

    public static Range intersect(Range a, Range b) {
        if (a.overlaps(b)) {
            return new Range(Math.max(a.from, b.from), Math.min(a.till, b.till));
        } else {
            return null;
        }
    }

    public Range intersect(Range other) {
        assert overlaps(other): "Intersect would delete this object";
        from = Math.max(this.from, other.from);
        till = Math.min(this.till, other.till);
        return this;
    }

    /**
     * Check is another range can be subtracted from this range: If the other range is in the
     * middle of this range then the subtraction would yield two ranges as result, which is
     * not represented by the chosen implementation of ranges. Therefore, method subtract()
     * must be used only if this method returns true.
     *   @return
     * Get true if the other range is not in the middle of this range.
     *   @param other
     * The other range.
     */
    public boolean isSubtractable(Range other) {
        return other.from <= this.from  ||  other.till >= this.till;
    }

    /**
     * Calculate the range which is in a but not in b. (Everything in b, which overlaps
     * with a is removed from a.)\n
     *   Note, the operation is impossible if b is somehwere in the middle of a; the result
     * wouldn't be a new range but a pair of those. This method must not be used in this
     * case; please, consider using isSubtractable() prior to this method.
     *   @return
     * Get the delta range a-b or null if a is entirely inside b.
     *   @param a
     * The first range.
     *   @param b
     * The second, subtracted range.
     */
    public static Range subtract(Range a, Range b) {
        assert a.isSubtractable(b);
        if (a.till > b.from  &&  b.till >= a.till) {
            /* Result is first part of a, until where b begins. */
            if (b.from > a.from) {
                return new Range(a.from, b.from);
            } else {
                /* Result is the empty range. This is represented by null. */
                return null;
            }
        } else if (a.from >= b.from) {
            if (b.till >= a.till) {
                /* a is entirely inside b. This is not representable as Range object but -
                   at least for a static method - as null. */
                return null;
            } else if(b.till > a.from) {
                /* Result is last part of a, from where b ends. */
                if (a.till > b.till) {
                    return new Range(b.till, a.till);
                } else {
                   /* Result is the empty range. This is represented by null. */
                    return null;
                }
            } else {
                assert a.isBehind(b);
                return a;
            }
        } else {
            assert a.isBefore(b);
            return a;
        }
    } /* Range.subtract */

    public static Range join(Range a, Range b) {
        assert a.connectsTo(b) || a.overlaps(b): "Ranges not connected";
        return new Range( Math.min(a.from, b.from)
                               , Math.max(a.till, b.till)
                               );
    }

    public Range join(Range other) {
        assert connectsTo(other) || overlaps(other): "Ranges not connected";
        from = Math.min(this.from, other.from);
        till = Math.max(this.till, other.till);
        return this;
    }

    @Override
    public int compareTo(Range other) {
        return Long.compare(this.from, other.from);
    }

    @Override
    public String toString() {
        return "[0x" + Long.toHexString(from) + ", 0x" + Long.toHexString(till) + ")";
    }
} /* End of class Range definition. */





