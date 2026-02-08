/**
 * @file EraseSectorSequence.java
 * Collect all flash ROM sectors, which need to be erased in order to be
 * able to program a given SRecordSequence.
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
/* Interface of class EraseSectorSequence
 *   EraseSectorSequence
 *   eraseAll
 *   findSectorsToErase
 *   iterator
 *   listIterator
 *   logSectors
 */

package winFlashTool.srecParser;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.basics.Range;
import winFlashTool.mcu.Flash;
import winFlashTool.mcu.Sector;

/**
 * An object of this class has a list of Ranges. These ranges match erasable flash ROM
 * sectors, such that the set of all listed ranges is the minimal set of sectors, which need
 * to be erased in order to be able to program the data, which has been read from the srec
 * file.
 */
public class EraseSectorSequence implements Iterable<Range> {

    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(EraseSectorSequence.class);

    /** A list of memory areas. Each area corresponds to one or more adjacent sectors of the
        flash ROM. */
    private final LinkedList<Range> sectorList_ = new LinkedList<Range>();

    /** The description of the flash ROM of the targeted device. Basically a list of
        independently erasable sector. */
    private final Flash flashRom_;

    /** The error counter to be used for all problem reporting. */
    private final ErrorCounter errCnt_;

    /**
     * A new instance of EraseSectorSequence is created.
     *   @param flashRom
     * The description of the flash ROM of the targeted device. Basically a list of
     * independently erasable sectors.
     *   @param errCnt
     * The error counter to be used for problem reporting.
     */
    public EraseSectorSequence(Flash flashRom, ErrorCounter errCnt) {
        flashRom_ = flashRom;
        errCnt_ = errCnt;

    } /* EraseSectorSequence.EraseSectorSequence */
    
    /** 
     * Configure this list of erase sectors such that all flash ROM is erased.
     */
    public void eraseAll() {
        assert sectorList_.isEmpty(): "Erase sector list needs to be initially empty.";
        ListIterator<Sector> itBlk = flashRom_.listIterator();
        Range rangeErase = null;
        while (itBlk.hasNext()) {
            Sector blk = itBlk.next();
            
            if (rangeErase == null) {
                rangeErase = new Range(blk);
            } else if(rangeErase.connectsTo(blk)) {
                rangeErase.join(blk);
            } else {    
                sectorList_.add(rangeErase);
                rangeErase = new Range(blk);
            }
        }

        /* There's possibly a still buffered flash block, which belongs into the
           result. */
        if (rangeErase != null) {
            sectorList_.add(rangeErase);
        }
    } /* eraseAll */
    
    /**
     * Find the list of sectors to erase for the given program.
     *   @return
     * Get true if the program fits into the set of erasable flash ROM sectors. If the
     * method returns false, then the contents from the srec file can't be programmed.
     *   @param program
     * The program as a list of s-records with data.
     */
    boolean findSectorsToErase(SRecordSequence program) {
        assert sectorList_.isEmpty(): "Erase sector list needs to be initially empty.";

        boolean success = true;

        /* Finding the required sectors means comparing the list of flash ROM sectors (a
           device specific constant) with the list of sections to program. This is strongly
           facilitated by the fact that both lists are sorted in order of rising addresses
           and that both lists don't have overlapping ranges. */
        ListIterator<Sector> itBlk = flashRom_.listIterator();
        ListIterator<SRecord> itPrg = program.listIterator();

        Range secPrg;
        if (itPrg.hasNext()) {
            secPrg = itPrg.next();
        } else {
            secPrg = null;
        }

        if (itBlk.hasNext()) {
            Range rangeErase = null;
            Sector blk = itBlk.next();
            while (secPrg != null) {
//_logger.info("New loop cycle: blk: {}, prg: {}, erase: {}", blk, secPrg, rangeErase);
                if (blk.overlaps(secPrg)) {
                
                    /* The current block belongs into the result list. However, we wait
                       with adding it to the list until we know, if the next, probably
                       adjacent flash block also matches, so that we can join the two
                       before adding. */
                    if (rangeErase == null) {
                        rangeErase = new Range(blk);
                    } else if(rangeErase.connectsBefore(blk)) {
                        rangeErase.join(blk);
                    } else if(rangeErase.isBefore(blk)) {
                        sectorList_.add(rangeErase);
                        rangeErase = new Range(blk);
                    } else {
                        /* We have visited the same flash block again. Nothing to do, we
                           had already handled it in the previous loop cycle. */
                    }

                    if (!secPrg.isSubtractable(blk)) {
                        /* We can get here if the program begins before the first flash
                           block or if the flash blocks don't form a solid address space
                           and the program occupies some address space in between two flash
                           blocks. */
                        break;
                    }
                    secPrg = Range.subtract(secPrg, blk);
                    if (secPrg == null) {
                        /* The current sector to program fits entirely in the current flash
                           block. The next sector to program may also belong into the
                           current flash block. */
                        if (itPrg.hasNext()) {
                            /* Match next sector to program with still same flash block. */
                            secPrg = itPrg.next();
                        } else {
                            /* There is no other sector to program. We are successfully
                               done. The loop is left due to secPrg == null. */
                        }
                    } else {
                        /* The current sector to program doesn't entirely fit into the
                           current flash block, so try to match the rest with the next
                           flash block. */
                        if (itBlk.hasNext()) {
                            blk = itBlk.next();

                            /* Here, we could look, if blk > secPrg: If so and due to the
                               ordering of the flash blocks, we could abort with failure.
                                 However, for our MCU, where all programable flash ROM is a
                               single solid address range, this will never happen and in
                               general, the number of flash blocks is limited so that this
                               optimization won't bring much even on MCUs with scattered
                               address ranges. */
                        } else {
                            break;
                        }
                    }
                } else {
                    /* The sector to program isn't in the current flash block, but maybe in
                       the next one. */
                    if (itBlk.hasNext()) {
                        blk = itBlk.next();
                    } else {
                        /* There is no other flash block available. We are done. With
                           failure, since there is some program sector left. */
                        break;
                    }
                }
            } /* while(Program code left to match with flash blocks) */
            
            /* There's possibly a still buffered flash block, which belongs into the
               result. */
            if (rangeErase != null) {
                sectorList_.add(rangeErase);
            }
        } else if (secPrg != null) {
            /* There is no flash block at all available. This is an error condition only if
               the program is not empty. The error message is emitted below, here we print
               just an additional warning. */
            errCnt_.warning();
            _logger.warn("MCU has no programmable sector.");
        }

        if (secPrg == null) {
            return true;
        } else {
            errCnt_.error();
            _logger.error( "Program doesn't fit into the available flash blocks. First"
                           + " data out of programmable flash: {}."
                         , secPrg
                         );
            return false;
        }
    } /* findSectorsToErase */

    /**
     * Interface iterable: Get an iterator for visiting all flash ROM areas in this
     * sequence.
     *   @return
     * Get the iterator.
     */
    @Override
    public Iterator<Range> iterator() {
        return sectorList_.iterator();
    }

    /**
     * Get an advanced iterator for visiting all flash ROM areas in this sequence.
     *   @return
     * Get the iterator of class ListIterator, which provides more options for iterations
     * than the one returned by iterator().
     */
    public ListIterator<Range> listIterator() {
        return sectorList_.listIterator();
    }

    /**
     * Write a summary about all found and collected memory chunks to the application log.
     */
    void logSectors() {
        ListIterator<Range> it = sectorList_.listIterator();
        if (it.hasNext()) {
            _logger.info( "Input file requires {} flash ROM areas to be erased."
                        , sectorList_.size()
                        );
        } else {
            errCnt_.warning();
            _logger.warn("No flash ROM sector needs to be erased.");
        }

        while (it.hasNext()) {
            final Range r = it.next();
            _logger.printf( Level.INFO
                          , "Section %02d: 0x%06X .. 0x%06X (length %d Byte)"
                          , it.nextIndex()
                          , r.from()
                          , r.till()
                          , r.size()
                          );
        }
    }
} /* End of class EraseSectorSequence definition. */






