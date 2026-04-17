/**
 * @file SRecord.java
 *
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
/* Interface of class SRecord
 *   SRecord (2 variants)
 *   data
 *   intersect
 *   join
 */

package winFlashTool.srecParser;

import java.util.*;
import org.apache.logging.log4j.*;
import org.apache.commons.lang3.ArrayUtils;
import winFlashTool.basics.Range;

/**
 * The description of a solid chunk of memory, with start address, length and contained
 * data bytes.
 */
public class SRecord extends Range {
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(SRecord.class);

    /** The currently allocated byte array can have more capacity as required from the
        current range from..till in the base class. This avoids permanent reallocation of
        the buffer for the typical operation of joining an adjacent other S-record. This
        constant limits the "more" and allows a trade-off between speedup and memory usage. */
    private final static int MAX_HEADROOM = 10000;
    
    /** The data bytes filling the address range from..till in the base class. The buffer
        can be larger than till-from. */
    private byte[] data_;

    /**
     * A new instance of SRecord is created.
     *   @param addrFrom
     * The first memory address.
     *   @param data
     * An array of bytes, which describe the memory contents beginning at address addrFrom.
     */
    public SRecord(long addrFrom, byte[] data) {
        super(addrFrom, addrFrom+data.length);
        data_ = data;

    } /* SRecord.SRecord */

    /**
     * A new, empty instance of SRecord is created. 
     *   @param addressRange
     * The memory address range. The data of the SRecord is initially filled with all zeros.
     */
    public SRecord(Range r) {
        super(r);
        if (r.size() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Memory area is too large (" + r.size()
                                               + " Byte. Up to 2^31-1 Byte are permitted."
                                              );
        }
        data_ = new byte[(int)r.size()];

    } /* SRecord.SRecord */

    /** 
     * Reallocate the byte array if it should have to less capacity for the next operation.
     *   @param newCapa
     * The new capacity of the data buffer - which must be not less than the current contents.
     */
    private void realloc(int newCapa) {
        assert newCapa >= isize();
        byte[] newData = new byte[newCapa];
        
        /* Safe the data by copying it into the new buffer. */
        for(int i=0; i<size(); ++i) {
            newData[i] = data_[i];
        }
        
        /* Replace buffer. */
        data_ = newData;
    }
    
    /**
     * Get the memory contents.
     *   @return
     * Get the memory contents as byte array.
     */
    public byte[] data() {
        if(isize() < data_.length)
        {
            /* Reallocation needed - the caller expects an exactely fitting byte array. */
            data_ = ArrayUtils.subarray(data_, 0, isize());
        }
        assert data_.length == isize();
        return data_;
    }

    @Override
    public SRecord intersect(Range other) {
        assert false: "Intersect is not supported for SRecords with data contents";
        return null;
    }

    /**
     * Combine two SRecords, which connect to one another.
     *   @return
     * Get the concatenated two SRanges.
     *   @param other     
     * The second SRecord, which needs to either begin immediately at the first address
     * behind this SRecord or end at the address, where this begins. There must neither be
     * a gap between the two SRecords nor an overlap.
     *   @warning
     * We override the base class method, although the operation is not generally defines
     * for SRecord. Two ranges can be joined if they overlap but two SRecords can't: what
     * to do with the overlapping data if not by chance identical in both operands?<p>
     *   This implementation fails if the two ranges don't properly connet to one another.
     */
    @Override
    public SRecord join(Range other) {
        assert other instanceof SRecord: "SRecord can only be joined with other SRecord";
        
        /* First join the data chunks. */
        final int newLength = isize() + other.isize();
        if (data_.length < newLength) {
            /* Capacity of current buffer doesn't suffices; buffer needs to be
               reallocated. We allocate more as required to avoid permanet reallocation
               in the important use case of joining many adjacent SRecords. */
            final int headroom = Math.min(newLength, MAX_HEADROOM);
            realloc(newLength + headroom);
        }
        
        /* If we get here, then the data buffer sure has the capacity to hold the joined
           ranges. */
        assert data_.length >= newLength;
        
        if (connectsBefore(other)) {
            //data_ = ArrayUtils.addAll(data_, ((SRecord)other).data_);
            /* Append the new data. */
            int idxWr = isize();
            for(int i=0; i<other.isize(); ++i, ++idxWr) {
                data_[idxWr] = ((SRecord)other).data_[i];
            }
        } else {
            if (!connectsBehind(other)) {
                throw new IndexOutOfBoundsException( "SRecord.join is not fully"
                                                     + " implemented. Address ranges must"
                                                     + " not overlap"
                                                   );
            }
            //data_ = ArrayUtils.addAll(((SRecord)other).data_, data_);
            /* Move already contained data towards the end. */
            int idxWr = newLength - 1;
            for(int i=isize()-1; i>=0; --i, --idxWr) {
                data_[idxWr] = data_[i];
            }
            /* Insert the new data at the beginning. */
            for(int i=0; i<other.isize(); ++i) {
                data_[i] = ((SRecord)other).data_[i];
            }
        }

        /* Now join the address ranges. super.join fails if ranges aren't adjacent. */
        super.join(other);

        return this;
    }

} /* End of class SRecord definition. */