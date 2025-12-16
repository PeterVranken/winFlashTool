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
 *   SRecord
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

    /** The data bytes filling the address range from..till in the base class. */
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
     * Get the memory contents.
     *   @return
     * Get the memory contents as byte array.
     */
    public byte[] data() {
        assert size() == data_.length;
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
     * a gap between the two SREcords nor an overlap.
     *   @warning
     * We override the base class' mtehod, although the operation is not generally defines
     * for SRecord. Two ranges can be joind if the y overlap but two SRecords can't: what
     * to do with the overlapping data if not by chance identical in both operands?<p>
     *   This implementation fails if the two ranges don't properly connet to one another.
     */
    @Override
    public SRecord join(Range other) {
        assert other instanceof SRecord: "SRecord can only be joined with other SRecord";
        /* Now join the data chunks, too. */
        if (connectsBefore(other)) {
            data_ = ArrayUtils.addAll(data_, ((SRecord)other).data_);
        } else {
            if (!connectsBehind(other)) {
                throw new IndexOutOfBoundsException( "SRecord.join is not fully"
                                                     + " implemented. Address ranges must"
                                                     + " not overlap"
                                                   );
            }
            data_ = ArrayUtils.addAll(((SRecord)other).data_, data_);
        }

        /* Join the address ranges. super.join fails if ranges aren't adjacent. */
        super.join(other);

        return this;
    }

} /* End of class SRecord definition. */




