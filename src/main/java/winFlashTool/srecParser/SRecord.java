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
 * In brief.
 *   This class ... Use {@link #method} to ...
 * <pre>{@code
 * Code example: x=y*class.method(3.34);}
 * </pre>
 *   @author Peter Vranken <a href="mailto:vranken@fev.io">(Contact)</a>
 *   @see SRecord#method
 *   @see #method
 */
//TODO: Fill in the method header.
class SRecord extends Range {
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(SRecord.class);

    /** The data bytes filling the address range from..till in the base class. */
    private byte[] data_;

    /**
     * A new instance of SRecord is created.
     *   @throws {@inheritDoc}
     * The exception is thrown if
     *   @param i
     * The ... or: {@inheritDoc}
     *   @see SRecord#
     */
    //TODO: Fill in the method header.
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

    @Override
    public SRecord join(Range other) {
        assert other instanceof SRecord: "SRecord can only be joined with other SRecord";

        /* Join the address ranges. */
        super.join(other);

        /* Now join the data chunks, too. */
        if (connectsBefore(other)) {
            data_ = ArrayUtils.addAll(data_, ((SRecord)other).data_);
        } else {
            assert connectsBehind(other): "Internal implementation error";
            data_ = ArrayUtils.addAll(((SRecord)other).data_, data_);
        }

        return this;
    }

} /* End of class SRecord definition. */




