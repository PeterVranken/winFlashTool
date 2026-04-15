/**
 * @file MemoryMap.java
 * The result of parsing the srec file: An image of the memories to erase and their new
 * contents.
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
/* Interface of class MemoryMap
 *   MemoryMap
 *   readSrecFile
 *   eraseSectorSequence
 *   eraseAllSectorSequence
 *   srecSequence
 */

package winFlashTool.srecParser;

import java.io.*;
import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.mcu.Flash;


/**
 * The result of parsing the srec file: An image of the memories to erase and their new
 * contents.
 */
public class MemoryMap {

    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(MemoryMap.class);

    /** The list of memory sections to erase at least for succeesful programming of
        srecSequence_. */
    private final EraseSectorSequence eraseSectorSequence_;

    /** The list of memory sections to erase if erasing the complete managed flash ROM is
        requested. */
    private final EraseSectorSequence eraseAllSectorSequence_;

    /** The list of memory sections to program. */
    private final SRecordSequence srecSequence_;

    /** The error counter to be used for all problem reporting. */
    private final ErrorCounter errCnt_;

    /** Program start address as found in the srec input file. Or -1 if not set in the srec
        file. */
    private long programStartAddress_ = -1;


    /** A non-static inner class implements the listener of the srec parser. It can access
        the fields of the containing class, even if these are private. */
    class SrecListenerForMemMap implements SrecParser.ISrecListener {

        /** Count parser errors. */
        private ErrorCounter parseErrCnt_ = new ErrorCounter();

        /** After parsing, get the number of empty lines found during parsing. */
        private int getNoEmptyLines() {return parseErrCnt_.getNoWarnings();}

        /**
         * Listen function: It gets every parsed line, with data and potential failure
         * information.
         *   @return
         * Normally, the function returns true for success. Parsing more files is aborted
         * if this method returns false, e.g., after too many errors or warnings.
         */
        public boolean onRecordParsed( SrecParserError errCode
                                     , int lineNumber
                                     , int recordType
                                     , long address
                                     , byte[] dataBytes
                                     ) {
            final int maxNoErrors = 30;


            /* Record types with memory data
                   S1, S2, S3: Data records
                   Contain actual memory data, with 16, 24 and 32 Bit address field.
               Record types with no memory data
                   S0: Header record
                   Contains a descriptive header (e.g., file name, version). No memory
                 data; only ASCII text in the data field. We just print the information in
                 the application log.
                   S5: No memory data; just a count value. Count of processed S1 records
                 (16-bit address space).
                   S6: No memory data; just a count value. Count of processed S2 records
                 (24-bit address space)
                   (There is no equivalent for S3 because 32-bit systems rarely used count
                 records.)
                   S7, S8, S9: Termination records
                   Termination records specify the execution start address, with 16, 24 and
                 32 Bit address field. Used to signal end of file.
                   S4: Reserved
                   Historically reserved for future use. Current status: Not used in
                 standard SREC files. If encountered, most parsers either ignore it or
                 treat it as an error. No official payload definition in the standard. */

            boolean abortImmediately = false;
            if (errCode == SrecParserError.SUCCESS) {

                if(recordType >= 1  &&  recordType <= 3) {
                    _logger.trace( "SREC data at address 0x{}: {}."
                                 , Long.toHexString(address).toUpperCase()
                                 , Arrays.toString(dataBytes)
                                 );
                    // TODO We should offer the option to not program sequences of 0xFF.
                    // Perfectly filtering all such sequences with a minimum length would
                    // require some state machine or concatenation logic for S-records
                    // reported to the listener. Very simple would be dropping S-records,
                    // which consist entirely of 0xFF. Not so nice: The result would depend
                    // on the representation of the binary in an srec file (the number of
                    // bytes per line), but it would be effective, as the only use case are
                    // large areas of 0xFF, which often occur due to some filling of
                    // address gaps. For this use case, it doesn't matter if we have a few
                    // 0xFF left at the beginning and the end of such an area.
                    if(!srecSequence_.add(new SRecord(address, dataBytes))) {
                        parseErrCnt_.error();
                        abortImmediately = false;
                    }
                } else if (recordType == 4) {
                    assert false: "This record type should be reported as error";

                } else if (recordType == 5  ||  recordType == 6) {
                    errCnt_.warning();
                    _logger.warn( "Line {}: S{} record found. Count records are not"
                                  + " supported. This record is ignored."
                                , lineNumber
                                , recordType
                                );

                } else if (recordType >= 7  &&  recordType <= 9) {

                    if (programStartAddress_ == -1) {
                        programStartAddress_ = address;
                        _logger.info( "Program start address is 0x{}."
                                    , Long.toHexString(address).toUpperCase()
                                    );
                    } else if (programStartAddress_ != address) {
                        errCnt_.error();
                        _logger.error( "Line {}: Program start address is repeatedly"
                                       + " specified. Had 0x{}, find 0x{}."
                                     , lineNumber
                                     , Long.toHexString(programStartAddress_).toUpperCase()
                                     , Long.toHexString(address).toUpperCase()
                                     );
                    }
                } else {
                    assert recordType == 0;
                    _logger.info("SREC info: \"{}\"", new String(dataBytes));

                }
            } else if (errCode == SrecParserError.WARN_EMPTY_LINE) {

                /* Empty lines are unexpected and reported but tolerated. */
                parseErrCnt_.warning();
                _logger.debug("Line {}: Empty line in srec input file.", lineNumber);

            } else {
                parseErrCnt_.error();
                errCnt_.error();
                _logger.error( "Line {}: Error reading srec input file. Error {} ({})."
                             , lineNumber
                             , errCode.getErrorCode()
                             , errCode.getMessage()
                             );
            }

            return !abortImmediately &&  parseErrCnt_.getNoErrors() < maxNoErrors;

        } /* onRecordParsed */

        /**
         * After parsing is done, this method is invoked by the SrecParser to ask the
         * listener for the final result. The
         * listener shall return true only if everything was fine for all seen records.
         *   @return
         * true if the listener has not seen any problem with the records it got. false
         * otherwise.
         */
        public boolean getFinalSuccess() {
            boolean success = parseErrCnt_.getNoErrors() == 0;
            
            if (getNoEmptyLines() > 0) {
                errCnt_.warning();
                _logger.warn( "SREC input file contains {} empty lines. Use log level DEBUG"
                              + " to get detailed information."
                            , getNoEmptyLines()
                            );
            }
            
            /* Now knowing all s-records to program, we can calculate, which sector from the
               flash ROM need to be erased. */
            if (!eraseSectorSequence_.findSectorsToErase(srecSequence_)) {
                success = false;
            }

            return success;
        }
    } /* class MemoryMap.SrecListenerForMemMap */

    /**
     * A new instance of MemoryMap is created.
     *   @param flashRom
     * The description of the flash ROM of the targeted device. Basically a list of
     * independently erasable sectors.
     *   @param errCnt
     * The error counter to be used for problem reporting.
     */
    public MemoryMap(Flash flashRom, ErrorCounter errCnt) {
        errCnt_ = errCnt;
        eraseSectorSequence_ = new EraseSectorSequence(flashRom, errCnt);
        eraseAllSectorSequence_ = new EraseSectorSequence(flashRom, errCnt);
        eraseAllSectorSequence_.eraseAll();
        srecSequence_ = new SRecordSequence();
            
    } /* MemoryMap.MemoryMap */

    /**
     * Read an srec file and build-up the memory map.
     *   @param srecFileName
     * The name of the srec input file, which defines the memory map.
     *   @return
     * Get the overall result; true if the input file has been successfully parsed. If
     * false is returned, then this memory map with its section list must not be used.
     */
    public boolean readSrecFile(String srecFileName) {

        programStartAddress_ = -1;

        File srecFile = new File(srecFileName);
        SrecParser parser = new SrecParser();

        final SrecListenerForMemMap srecListener = new SrecListenerForMemMap();
        parser.registerListener(srecListener);

        _logger.info("Now reading s-record file {}", srecFileName);
        boolean success = parser.parse(srecFile);

        if (success) {
            // TODO If we have !success but this is due to the mapping of the s-records
            // onto the flash blocks, then we should still list the found s-records.
            // Particularly in this situation it is useful information for the user.
            // Boolean seems to be too weak to be able to take the right decision.
            eraseSectorSequence_.logSectors();
            srecSequence_.logSections();
        }

        return success;

    } /* readSrecFile */

    /**
     * Get the @{linkplain #eraseSectorSequence_ list of memory sections to erase}.
     */
    public EraseSectorSequence eraseSectorSequence() {
        return eraseSectorSequence_;
    }

    /**
     * Get the @{linkplain #eraseAllSectorSequence_ list of memory sections to erase}.
     */
    public EraseSectorSequence eraseAllSectorSequence() {
        return eraseAllSectorSequence_;
    }

    /**
     * Get the @{linkplain #srecSequence_ list of memory sections to program}.
     */
    public SRecordSequence srecSequence() {
        return srecSequence_;
    }
} /* End of class MemoryMap definition. */




