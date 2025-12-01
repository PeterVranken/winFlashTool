/**
 * @file Sector.java
 * The sector of some flash ROM.
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
/* Interface of class Sector
 *   Sector
 */

package winFlashTool.mcu;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.Range;

/**
 * The sector of some flash ROM.<p>
 *   Basically the address range plus the reference to the partition, the sector belongs to.
 */
class Sector extends Range {

    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(Sector.class);

    /** The reference to the partition, which this sector belongs to. Please not, this
        index is always zero based, regardless of which counting convention the actual MCU
        uses. */
    private final int idxPartition_;
    
    /**
     * A new instance of Sector is created.
     *   @param from
     * The first memory address of the sector.
     *   @param till
     * The last memory address of the sector (exclusive).
     *   @idxPartition
     * The reference to the partition, which this sector belongs to. Please not, this
     * index is always zero based, regardless of which counting convention the actual MCU
     * uses.
     */
    Sector(long from, long till, int idxPartition) {
        super(from, till);
        idxPartition_ = idxPartition;

    } /* Sector.Sector */

    /**
     * A new instance of Sector is created.
     *   @param addrRange
     * The memory address range of the sector.
     *   @idxPartition
     * The reference to the partition, which this sector belongs to. Please not, this
     * index is always zero based, regadless of which counting convention the actual MCU
     * uses.
     */
    Sector(Range addrRange, int idxPartition) {
        super(addrRange);
        idxPartition_ = idxPartition;

    } /* Sector.Sector */

    /**
     * Getter for the reference to the owning partition.
     *   @return
     * Get the zero based index to the owning partition.
     */
    int getIdxPartition() {
        return idxPartition_;
    }
    
} /* End of class Sector definition. */




