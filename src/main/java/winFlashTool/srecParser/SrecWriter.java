/**
 * @file SrecWriter.java
 * Simple writer for s-record files.
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
/* Interface of class SrecWriter
 *   initClass
 *   getNoReqAddrBytes
 *   formatS0Record
 *   formatSRecord (2 variants)
 *   write
 */

package winFlashTool.srecParser;

import java.util.*;
import org.apache.logging.log4j.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.can.PCANBasicEx;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple writer for s-record files.
 */
public class SrecWriter {

    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(SrecWriter.class);

    /** The global error counter for this static class. */
    private static ErrorCounter _errCnt = null;

    /**
     * Initialization of module. Needs to be successfully called prior to using any other
     * method.
     *   @return
     * Get true, if initialization succeeds. If \a false is returned then operation
     * won't be possible and the application shouldn't start up.
     *   @param errCnt
     * The error counter to be used for all problem reporting by this (static) class.
     */
    public static boolean initClass(ErrorCounter errCnt)
    {
        _errCnt = errCnt;
        return true;

    } /* initClass */

    /**
     * Figure out, how many address bytes are required. The srec format allow 2..4 bytes
     * for the address representation.
     *   @return
     * Get the number of bytes, which is appropriate to represent the addresses in the data.
     *   @param srecSequence
     * The binary data to be written into the file.
     */
    private static int getNoReqAddrBytes(SRecordSequence srecSequence) {
        /* Find largest address to support. */
        long largestAddr = 0;
        for (SRecord section: srecSequence) {
            if(section.till() > largestAddr) {
                largestAddr = section.till();
            }
        }
        if ((largestAddr & 0xFFFFFFFFFF000000l) != 0) {
            return 4;
        } else if ((largestAddr & 0xFFFFFFFFFFFF0000l) != 0) {
            return 3;
        } else {
            return 2;
        }
    } /* getNoReqAddrBytes */


    /**
     * Format an S0 record with some text information inside.
     *   @return
     * Get the s-record as string.
     *   @param textInfo
     * Some text, which is written to the srec file.
     */
    private static String formatS0Record(String textInfo) {
        final int noAddrBytes = 2;
        final byte[] data = textInfo.getBytes(StandardCharsets.US_ASCII);
        int noBytes = data.length;
        if (noAddrBytes + noBytes + 1 > 255) {
            noBytes = 255 - noAddrBytes - 1;
            _logger.warn( "Descriptive text \"{}\" in generated s-record file is too long and"
                          + " needs to be truncated."
                        , textInfo
                        );
        }
        return formatSRecord( /*kindOfRecord*/ 0
                            , /*address*/ 0
                            , noAddrBytes
                            , data
                            , noBytes 
                            );
    } /* formatS0Record */
    

    /**
     * Format an s-record.
     *   @return
     * Get the s-record as string.
     *   @param kindOfRecord
     * The type of the s-record, a number in the range 0..9.
     *   @param address
     * The address of the first byte of the s-record.
     *   @param noAddressBytes
     * The number of bytes for the representation of address. Range is 2..4.
     *   @param data
     * The data bytes in the record. Note, not all bytes from the byte array are in
     * necessarily in use.
     *   @param noBytes
     * The number of data bytes in the record. It is noBytes <= data.length.
     */
    private static String formatSRecord( int kindOfRecord
                                       , long address
                                       , int noAddrBytes
                                       , byte[] data
                                       , int noBytes
                                       ) {
        assert noAddrBytes >= 2  &&  noAddrBytes <= 4: "size of address out of bounds";
        assert address >= 0  &&  address <= 0x00000000FFFFFFFFl: "Address out of bounds";
        
        String srec = "S" + kindOfRecord;

        int sumOfBytes = 0;

        /* Write number of bytes in the record. It is the number of bytes after this length
           byte. */
        final int len = noAddrBytes + noBytes + 1;
        assert len <= 255: "Record length out of bounds";
        srec += String.format("%02X", len);
        sumOfBytes += len;

        /* Write address and accumulate byte for later checksum calculation. */
        long mask = 0x00000000000000FFl << 8*(noAddrBytes - 1);
        for (int i=0; i<noAddrBytes; ++i) {
            final int addrByte = (int)((address & mask) >> 8*(noAddrBytes-1-i));
            mask >>= 8;
            srec += String.format("%02X", addrByte);
            sumOfBytes += addrByte;
        }

        /* Write the data bytes. */
        for (int idxByte=0; idxByte<noBytes; ++idxByte) {
            final int i = PCANBasicEx.b2i(data[idxByte]);
            srec += String.format("%02X", i);
            sumOfBytes += i;
        }

        /* Write the checksum. The checksum complements the sum of bytes to 255. */
        srec += String.format("%02X", 255 - (sumOfBytes & 0xFF));

        /* EOL */
        srec += '\n';

        return srec;

    } /* formatSRecord */

    /**
     * Format an s-record.
     *   @return
     * Get the s-record as string.
     *   @param address
     * The address of the first byte of the s-record.
     *   @param noAddressBytes
     * The number of bytes for the representation of address. Range is 2..4.
     *   @param data
     * The data bytes in the record. Note, not all bytes from the byte array are in
     * necessarily in use.
     *   @param noBytes
     * The number of data bytes in the record. It is noBytes <= data.length.
     */
    private static String formatSRecord( long address
                                       , int noAddrBytes
                                       , byte[] data
                                       , int noBytes
                                       ) {
        return formatSRecord( /*kindOfRecord*/ noAddrBytes - 1
                            , address
                            , noAddrBytes
                            , data
                            , noBytes
                            );
    }

    /**
     * Writes the given binary data into a text file in srec format.
     *   @return
     * Get true if file could be written, otherwise get false.
     *   @param fileName
     * Path/name of the output file.
     *   @param srecSequence
     * The binary data to be written into the file.
     *   @param noBytesPerLine
     * The number of data bytes, which will be outermost written into an s-record.
     */
    public static boolean write( String fileName
                               , SRecordSequence srecSequence
                               , int noBytesPerLine
                               ) {
        boolean success = true;

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName))) {

            /* First s-record is an encoded descriptive text. */
            // TODO Create text with address range and no bytes
            /* Get current date and time and format it as a readable string (e.g.,
               "2025-12-19 10:45:30"). */
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            final String dateTimeString = now.format(formatter);
            final String textInfo = "Data upload done by winFlashTool on " + dateTimeString + ".";
            bw.write(formatS0Record(textInfo));

            final int noAddrBytes = getNoReqAddrBytes(srecSequence);

            final byte[] data = new byte[noBytesPerLine];

            for (SRecord section: srecSequence) {
                long addr = section.from();
                long noBytesToGo = section.size();

                /* The first s-record doesn't have the full size; it is used to bring the
                   address of the record to a multiple of the full record length. This is
                   most useful for the common power-of-two lengths. */
                int noBytesSrec = (int)((addr + (long)noBytesPerLine - 1)
                                        / (long)noBytesPerLine
                                        * (long)noBytesPerLine
                                        - addr
                                       );
                assert noBytesSrec < noBytesPerLine
                       &&  (addr + (long)noBytesSrec) % (long)noBytesPerLine == 0;
                if (noBytesSrec == 0) {
                    noBytesSrec = noBytesPerLine;
                }
                if ((long)noBytesSrec > noBytesToGo) {
                    noBytesSrec = (int)noBytesToGo;
                }

                while (noBytesToGo > 0) {
                    /* Copy next block of bytes into the reused buffer "data". */
                    System.arraycopy( section.data()
                                    , /*idxByteRd*/ (int)(addr -  section.from())
                                    , data
                                    , /*idxByteWr*/ 0
                                    , noBytesSrec
                                    );

                    bw.write(formatSRecord(addr, noAddrBytes, data, noBytesSrec));
                    addr += noBytesSrec;
                    noBytesToGo -= (long)noBytesSrec;

                    if (noBytesToGo >= (long)noBytesPerLine) {
                        noBytesSrec = noBytesPerLine;
                    } else {
                        noBytesSrec = (int)noBytesToGo;
                    }
                }
            } /* for(All memory sections to write) */

            /* bw.close(), which closes FileWriter too, is called automatically on leaving
               this try-with-resources construct. */

        } catch (IOException e) {

            /* If the hidden close() in the try-with-resources construct caused an error,
               we'll get it as "suppressed" exception. */
            String msg = e.getMessage()
                 , sep = ". ";
            for (Throwable suppressedEx: e.getSuppressed()) {
                msg += sep + suppressedEx.getMessage();
            }

            success = false;
            _errCnt.error();
            _logger.error("Error writing s-record file with uploaded data. {}", msg);
        }

        if (success) {
            _logger.info("S-record file {} successfully written.", fileName);
        }

        return success;

    } /* write */

} /* Class SrecWriter */