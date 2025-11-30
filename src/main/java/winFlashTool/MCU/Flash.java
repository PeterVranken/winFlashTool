/**
 * @file Flash.java
 * The abstract descripting of the flash ROM memory in an MCU.
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
/* Interface of class Flash
 *   Flash
 */

package winFlashTool.MCU;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.Range;

/**
 * The abstract descripting of the flash ROM memory in an MCU.
 */
public class Flash {

    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(Flash.class);

    /** A flash ROM consists of a list of sectors. A sector (aka block) is an address
        range, which can be deleted independently from all other sectors. The list is
        ordered by rising addresses. Sectors don't overlap and mostly they follow up
        without address gap in between. */
    private final List<Sector> sectorList_;

    /** Sub-sets of the sectors form partitions. A partition can be normally used, while
        another one is currently subject to sector erasure or programming. The sectors of a
        partition don't nesessarily form a contiguous address space. The list of sectors
        belonging to a partition is order by rsing addresses. The relationship between
        partitions in respect to addresses is undefined. */
    private final ArrayList<List<Sector>> partitionList_;

    /** Partitions are normally addressed to by index (not by address). The index may be
        one or zero based. */
    private final boolean isIdxPartitionZeroBased_;

    /**
     * An instance of this class is the description of the hardware buildup of a real MCU.
     * It is therefore considered immutable. This is supported by filling the data tables
     * only once at object creation time. This constructor is the only way to ever modify
     * an object of this class. All sections and their relationship to partitions needs to
     * be provided once to this constructor.
     *   @param isIdxPartitionZeroBased
     * Partitions are normally addressed to by index. Pass true if the index is zeror based
     * and false if it is one based. (Other indexes aren't supported.)
     *   @param argAry
     * A list of tripples of arguments. Each first argument is an (address) Range, each second
     * argument is the index of the partition it belongs to as an Integer object and each
     * third one is a flag, whether this sector may be erased and (re-)programmed as a
     * Boolean.<p>
     *   It is expected that the sectors are listed in order of rising addreses and that
     * they don't overlap. A check of gapless sector addresses is however not performed.<p>
     *   Note, all kinds of errors found will be handled by throwing an
     * IllegalArgumentException.
     */
    Flash(boolean isIdxPartitionZeroBased, Object... argAry) {
        isIdxPartitionZeroBased_ = isIdxPartitionZeroBased;

        final int noSectors = argAry.length / 3;
        if (3*noSectors != argAry.length) {
            throw new IllegalArgumentException("Invalid number of constructor arguments");
        }

        sectorList_ = new ArrayList<Sector>(noSectors);

        Range guard = new Range(-1, 0);
        int maxIdxPartition = -1;
        for (int idxSec=0; idxSec<noSectors; ++idxSec) {
            if (!(argAry[3*idxSec+0] instanceof Range)) {
                throw new IllegalArgumentException("First element in argument tripple is not a"
                                                   + " Range"
                                                  );
            }
            final Range addrRange = (Range)argAry[3*idxSec+0];
            
            if (!(argAry[3*idxSec+1] instanceof Integer)) {
                throw new IllegalArgumentException("Second element in argument tripple is not"
                                                   + " an Integer"
                                                  );
            }
            final Integer idxPartitionAsInteger = (Integer)argAry[3*idxSec+1];
            final int idxPartition = idxPartitionAsInteger.intValue()
                                     + (isIdxPartitionZeroBased_? 0: -1);
                                     
            if (!(argAry[3*idxSec+2] instanceof Boolean)) {
                throw new IllegalArgumentException("Third element in argument tripple is not a"
                                                   + " Boolean"
                                                  );
            }
            final boolean isSectorProgrammable = ((Boolean)argAry[3*idxSec+2]).booleanValue();
            
            /* Here, we have the type checked tripple of address range, reference to
               partitioning and enable flag at hand. Now, we run the logical checks for
               valid input. */
            
            /* Sectors need to be ordered in rising address and they must not overlap. */
            if (!guard.isBefore(addrRange)) {
                throw new IllegalArgumentException("Sectors aren't provided in order of"
                                                   + " rising address or they overlap"
                                                  );
            } else {
                guard = addrRange;
            }

            /* We expect no partition index below 0 or 1, depending on the kind of index. */
            if (isIdxPartitionZeroBased_ && idxPartition < 0) {
                throw new IllegalArgumentException("Invalid index of partition");
            }
            
            /* How many partitions are known for the chip? */
            maxIdxPartition = Math.max(maxIdxPartition, idxPartition);
            
            /* A sector object is appended to the list of all flash ROM sectors if the
               sector is enabled for programming. */
            if (isSectorProgrammable) {
                sectorList_.add(new Sector(addrRange, idxPartition));
            }
        }
        
        /* Check use of partition index space. */
        final int noPartitions = maxIdxPartition + 1;
        if (maxIdxPartition < 0) {
            throw new IllegalArgumentException("Expect at least one partition with one"
                                               + " sector"
                                              );
        } else if (noPartitions > 1000) {
            /* Here, we check for an arbitrary set limit, just to avoid an unintended slip
               of the pen, resulting in endless memory cleanup and reordering of the JVM
               before we evetually get the out-of-memory error. If your particular device
               should indeed have mor than this number of parttions, then you may change
               the limit to any reasonable greater value. */
            throw new IllegalArgumentException("Suspicious use of a very large number of"
                                               + " partitions. Does your device indeed have "
                                               + noPartitions + " partitions?"
                                              );
        }
        
        /* Create the list of partitions with their still empty sector lists. */
        partitionList_ = new ArrayList<List<Sector>>(noPartitions);
        int approxNoSectorsInPartition = (noSectors + maxIdxPartition) / noPartitions;
        for (int idxPar=0; idxPar<noPartitions; ++idxPar) {
            partitionList_.add(new ArrayList<Sector>(approxNoSectorsInPartition));
        }
        
        /* Now we can iterate the list of sectors and create the full relationship with the
           partitions; the sectors are added to the referenced partition. */
        for (Sector sector: sectorList_) {
            assert sector.getIdxPartition() < noPartitions;
            final List<Sector> listOfSecsOfPar = partitionList_.get(sector.getIdxPartition());
            listOfSecsOfPar.add(sector);
        }        
    } /* Flash.Flash */

} /* End of class Flash definition. */






