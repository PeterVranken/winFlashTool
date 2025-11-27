/**
 * @file SRecordSequence.java
 * Assemble a binary program from all S-Records read from an srec file.
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
/* Interface of class SRecordSequence
 *   SRecordSequence
 */

package winFlashTool.srecParser;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;

/**
 * An object of this class has a list of SRecords. Small s-records read from an srec file
 * can be added and added will join them with already contained s-records so that
 * (normally) a few records in the list grow rather than that the list will contain many of
 * them. When all small s-records from the file have been added then the (probably large)
 * s-records in the list form the memory sections to program.
 */
public class SRecordSequence {

    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(SRecordSequence.class);

    /** A list of SRecords. */
    final LinkedList<SRecord> recordList_ = new LinkedList<SRecord>();

    /** This module uses the one and only global error counter. */
    private static ErrorCounter _errCnt = ErrorCounter.getGlobalErrorCounter();

    /**
     * A new instance of SRecordSequence is created.
     *   @throws {@inheritDoc}
     * The exception is thrown if 
     *   @param i
     * The ... or: {@inheritDoc}
     *   @see SRecordSequence#
     */
    //TODO: Fill in the method header.
    public SRecordSequence() {
    } /* SRecordSequence.SRecordSequence */

    /**
     * Add a new SRecord to the list.
     *   @return
     * We don't support repeatedly used addresses, or, with other words, overlapping memory
     * areas. If the new record r ovelaps with at least one of the already contained
     * records, then the function returns false and the process should be aborted.
     *   @param r
     * The s-record to add to the memory map.
     */
    boolean add(SRecord r) {
    
        if (recordList_.size() == 0) {
            recordList_.addLast(r);
            return true;
        }
        
        /* Our use case is reading srec files, where 99% of all records are ordered and
           don't have gaps in between. So the normal operation is trivial. Let's check for
           this cheapest action before we enter the general operation. */
        final SRecord lastR = recordList_.peekLast();
        if (lastR.connectsBefore(r)) {
            lastR.join(r);
            return true;
        }
        
        /* Still a common situation in srec files: Some address space is unused but the
           next record is still at a higher address. In this case, we just begin with a new
           record in the list. */
        if (lastR.isBefore(r)) {
            recordList_.addLast(r);
            return true;
        }
        
        /* The address in the new record is somewhere before or in the middle of address
           ranges, we had already collected. A search in the list for the right location is
           required. */
        boolean success = true;
        ListIterator<SRecord> it = recordList_.listIterator();
        while (it.hasNext()) {
            final SRecord srecInList = it.next();
            Range overlap = null;
            
            if (r.connectsBefore(srecInList)) {
                /* The new record can be merged with the current one in the list. */
                srecInList.join(r);
                break;
            } else if (r.isBefore(srecInList)) {
                /* The new block couldn't be merge with the predecessor in the list, it
                   doesn't touch the current one but it comes before it - so we need to
                   insert it into the list before the current one. */
                it.previous();
                
                /* Add leaves the iterator behind r and before srecInList. */
                it.add(r);
                
                break;
                
            } else if(r.overlaps(srecInList)) {
                /* Failure, overlap, abort. Feedback is handled below together with the
                   other overlap situations. */
                overlap = Range.intersect(r, srecInList);

            } else if(r.connectsBehind(srecInList)) {
                /* The new record can be merged with the current one in the list, but it is
                   possible that we then overlap with the successor in the list or that we
                   have to join with the successor. But anyway, r is inserted and we are
                   done. */
                srecInList.join(r);
                if (it.hasNext()) {
                    final SRecord successor = it.next();
                    if (srecInList.overlaps(successor)) {
                        overlap = Range.intersect(r, successor);
                    } else if (srecInList.connectsBefore(successor)) {
                        /* Merge current record into successor and delete current one. */
                        successor.join(srecInList);
                        it.remove();
                        break;
                    }
                } else {
                    break;
                }
            } else {
                /* The new record is neither before the current one nor is it connected to
                   it and nor does it overlap with it; it sits somewhere behind. This is
                   now checked by doing the same comparisons with the next record in the
                   list - if any. If there is no successor then we can simply add the new
                   element to the list. */
                assert it.hasNext(): "!it.hasNext has already been check prior to the loop";
                if (!it.hasNext()) {
                    /* Add leaves the iterator behind r at the end of the list. */
                    it.add(r);
                    assert !it.hasNext();
                }
            }

            if (overlap != null) {
                /* We can't safely report the size of the overlapping region, has
                   the overlap can extend over one or more successors. */
                success = false;
                _errCnt.error();
                _logger.error( "Overlapping s-records detected in input file."
                               + " Reprogramming the same addresses is not supported."
                               + " First overlapping address is 0x{}. Number of"
                               + " overlapping bytes is at least {}."
                             , Long.toHexString(overlap.from())
                             , overlap.size()
                             );
                break;
            }
        } /* while(Still another s-record in the list, to compare with the new one) */
        
        return success;
    }
    
    /**
     * Write a summary about all found and collected memory chunks to the application log.
     */
    void logSections(Level logLevel) {
        ListIterator<SRecord> it = recordList_.listIterator();
        if (it.hasNext()) {
            _logger.log( logLevel
                       , "{} memory sections found in input file."
                       , recordList_.size()
                       );
        } else {
            _logger.log(logLevel , "Input file contains no memory sections.");
        }

        while (it.hasNext()) {
            final SRecord r = it.next();
            _logger.printf( logLevel
                          , "Section %02d: 0x%06X .. 0x%06X (length %d Byte)"
                          , it.nextIndex()
                          , r.from()
                          , r.till()
                          , r.size()
                          );
            assert r.size() == r.data().length;
        }
    }
} /* End of class SRecordSequence definition. */






