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
 *   Range (2variants)
 *   from
 *   till
 *   size
 *   isBefore
 *   connectsBefore
 *   connectsBehind
 *   isBehind
 *   connectsTo
 *   overlaps
 *   intersect (2 variants)
 *   isSubtractable
 *   subtract
 *   join (2 variants)
 *   compareTo
 *   toString
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
    private long from_;

    /** Last address (exclusive) of range. */
    private long till_;

    public Range(long from, long till) {
        assert from < till: "Empty ranges aren't supported";
        from_ = from;
        till_ = till;
    }

    public Range(Range r) {
        this(r.from_, r.till_);
    }

    public long from() {
        return from_;
    }

    public long till() {
        return till_;
    }

    public long size() {
        return till_ - from_;
    }

    public int isize() {
        assert size() <= (long)Integer.MAX_VALUE
             : "Size of range exceeds implementation range of int";
        return (int)size();
    }

    public boolean isBefore(Range other) {
        return till_ <= other.from_;
    }

    public boolean connectsBefore(Range other) {
        return till_ == other.from_;
    }

    public boolean connectsBehind(Range other) {
        return from_ == other.till_;
    }

    public boolean isBehind(Range other) {
        return from_ >= other.till_;
    }

    public boolean connectsTo(Range other) {
        return connectsBefore(other) || connectsBehind(other);
    }

    public boolean overlaps(Range other) {
        return from_ < other.till_ && other.from_ < till_;
    }

    public static Range intersect(Range a, Range b) {
        if (a.overlaps(b)) {
            return new Range(Math.max(a.from_, b.from_), Math.min(a.till_, b.till_));
        } else {
            return null;
        }
    }

    public Range intersect(Range other) {
        assert overlaps(other): "Intersect would delete this object";
        from_ = Math.max(from_, other.from_);
        till_ = Math.min(till_, other.till_);
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
        return other.from_ <= from_  ||  other.till_ >= till_;
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
        if (b.from_ <= a.from_) {
            if (b.till_ <= a.from_) {
                /* b is entirely before a. Result is a unmodified. */
                return a;
            } else if (b.till_ < a.till_) {
                /* Result is last part of a, from where b ends. */
                return new Range(b.till_, a.till_);
            } else {
                /* Result is the empty range. This is represented by null. */
                return null;
            }
        } else if(b.from_ < a.till_) {
            assert b.till_ >= a.till_: "Difference of ranges is undefined, not a range";
            /* Result is first part of a, until where b begins. */
            return new Range(a.from_, b.from_);
        } else {
            /* b is entirely behind a. Result is a unmodified. */
            return a;
        }
    } /* Range.subtract */

    /**
     * Join two ranges, which don't have a gap in between them.
     *   @param a
     * The first range.
     *   @param b
     * The second range. It may directly connect to a or overlap with a. Otherwise an
     * assertion fires.
     */
    public static Range join(Range a, Range b) {
        assert a.connectsTo(b) || a.overlaps(b): "Ranges not connected";
        return new Range(Math.min(a.from_, b.from_), Math.max(a.till_, b.till_));
    }

    /**
     * Join this range with another one.<p>
     *   The two ranges don't have a gap in between them.
     *   @param other
     * The range to join with this range. It may directly connect to this or overlap with
     * this. Otherwise an assertion fires.
     */
    public Range join(Range other) {
        assert connectsTo(other) || overlaps(other): "Ranges not connected";
        from_ = Math.min(from_, other.from_);
        till_ = Math.max(till_, other.till_);
        return this;
    }
    
    /** 
     * The Comparable interface is implemented to allow srting of ranges.
     *   @return
     * A range will be "lower/greater than" another if its first address comes
     * before/behind the first address of the other range. The function will return -1/1 in
     * this case. 0 is returned for ranges with same first address. (The last address of
     * the ranges is not considered.)
     *   @param other
     * The second operand of the comparison.
     *   @todo
     * For two ranges with same start address, we could make the result dependent on the
     * end address of the range; the shorter one should be "lower than" the longer one.
     */
    @Override
    public int compareTo(Range other) {
        return Long.compare(from_, other.from_);
    }

    @Override
    public String toString() {
        return "[0x" + Long.toHexString(from_).toUpperCase() + ", 0x"
               + Long.toHexString(till_).toUpperCase() + ")";
    }
} /* End of class Range definition. */





