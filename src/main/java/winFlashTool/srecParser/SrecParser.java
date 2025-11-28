/**
 * @file SrecParser.java
 * Parser for srec data files.
 *
 * This source file has been made by Copilot. Here are the requirements for Copilot:
 *
 * Input arguments are the name and path of the srec file and a callback function (aka
 * listener).
 *   Basic construct is a loop, which iterates the lines of the text input file. Each line
 * is checked for being a known, supported record type, probably implemented using regular
 * expression.
 *   For each read line, the listener is called: It receives an error/success code for this
 * line, the line number in the input stream (for error messages and progress reporting),
 * the record type as an integer number, the address as an integer number and the data
 * bytes as integers in a suitable, ordered Java Collection.
 *   All arguments of the listener, except for line number and error code may be
 * empty/null/dummy value, if an error was recognized for the line.
 *   The checksum byte doesn't need to be passed to the callback, as it has been checked
 * before calling the listener.
 *   Error/success codes are: Record is valid, Unknown or unsupported record type, invalid
 * checksum, wrong number of data bytes found, regular expression doesn't match the line.
 *   Your code should contain the Java file handling, open file, create file buffer and
 * stream, handle I/O exceptions and the loop which iterates the lines. The file should be
 * properly closed at the end or in case of errors.
 *   Your code should contain an API to register the listener.
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
/* Interface of class SrecParser
 *   SrecParser
 */

package winFlashTool.srecParser;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;

/**
 * Parser for srec data files.
 *
 * This class implements the "outer" parts of the parser for srec files. It handles the
 * input file and stream, reads the input line by line, parses each line using regular
 * expressions and calls a listener about every found line.\n
 *   The listener is not element of this class. A useful listener will collect the parsed
 * data bytes from all lines and assemble the complete program from this.
 */
public class SrecParser
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(SrecParser.class);

    /** This module uses the one and only global error counter. */
    private static ErrorCounter _errCnt = ErrorCounter.getGlobalErrorCounter();

    /** The interface some other entity needs to have, in order to be informed about all
        s-records read from the input file. */
    public interface ISrecListener {
        /* Set the name of the input file. Can be useful for error messages.<p>
             The implementation of this method is optional. */
        default void setInputFile(File srecInputFile) {
        }
        
        /* After parsing is done, this method asks the listener for the final result. The
           listener shall return true only if everything was fine for all seen records. */
        boolean getFinalSuccess();
        
        /* Each s-record (be it valid or not), which is read from the input file, is passed
           to this listener for further processing. Only if errorCode is
           SrecParserError.SUCCESS then the last three arguments are meaningful. */
        boolean onRecordParsed( SrecParserError errorCode
                              , int lineNumber
                              , int recordType
                              , long address
                              , byte[] dataBytes
                              );
    }
    
    /** The listener, which is used while reading the input file. It receives a
        notification about every read line and its contents as an s-reord. */
    private ISrecListener listener;

    /**
     * Prior to reading the input file using parse(), set the listener for parsed
     * s-records.
     *   @param listener
     * The listening object.
     */
    public void registerListener(ISrecListener listener) {
        this.listener = listener;
    }

    /**
     * Open, read and interpret the srec input file.
     *   @return
     * Get true if the entire file could be read and well-understood. In case of false,
     * don't make any use of the read contents.
     *   @param srecFile
     * The srec file designation.
     */
    public boolean parse(File srecFile) {

        assert listener != null: "Use of parser without listener";
        
        boolean success = true;
        final Pattern pattern = Pattern.compile("^S([0-9])([0-9A-Fa-f]{2})([0-9A-Fa-f]+)$");

        // What does try ( ... ) { ... } mean?
        //   What you're seeing here is a Java try-with-resources construct, introduced in
        // Java 7. Let me break it down: 
        //   The parentheses after try contain resource declarations - objects that
        // implement the AutoCloseable interface (like BufferedReader, FileReader,
        // InputStream, etc.).
        //   These resources are automatically closed at the end of the try block, even if
        // an exception occurs.
        //   This eliminates the need for a finally block to manually close resources.
        //   - The BufferedReader br is created and registered as a resource.
        //   - When the try block finishes (normally or due to an exception), br.close() is
        //     called automatically.

        try (BufferedReader br = new BufferedReader(new FileReader(srecFile))) {
            String line;
            int lineNumber = 0;
            
            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) {
                    if (listener.onRecordParsed( SrecParserError.WARN_EMPTY_LINE
                                               , lineNumber
                                               , -1
                                               , -1
                                               , null
                                               )
                       ) {
                        continue;
                    } else {
                        break;
                    }
                }

                Matcher matcher = pattern.matcher(line);
                if (!matcher.matches()) {
                    success = false;
                    if (listener.onRecordParsed( SrecParserError.ERR_REGEX_MISMATCH
                                               , lineNumber
                                               , -1
                                               , -1
                                               ,null
                                               )
                       ) {
                        continue;
                    } else {
                        break;
                    }
                }

                int recordType = Integer.parseInt(matcher.group(1));
                int byteCount = Integer.parseInt(matcher.group(2), 16);
                String remaining = matcher.group(3);

                // Determine address length based on record type
                int addressLength;
                switch (recordType) {
                    case 0: case 1: case 5: case 9: addressLength = 4; break; // 2 bytes
                    case 2: case 6: case 8: addressLength = 6; break; // 3 bytes
                    case 3: case 7: addressLength = 8; break; // 4 bytes
                    default:
                        /* Do to the syntax check with the regular expression, this can
                           only be s-record 4, which is reserved for the futures ad not
                           specified, yet. We can't simply go ahead and parse the rest of
                           the line and leave the decision to the listener, as we wouldn't
                           know, how to split the remaining bytes into address and data. */
                        success = false;
                        addressLength = 0;
                        if(listener.onRecordParsed( SrecParserError.ERR_UNKNOWN_TYPE
                                                  , lineNumber
                                                  , recordType
                                                  , -1
                                                  , null
                                                  )
                          ) {
                            continue;
                        } else {
                            break;
                        }
                }

                // Strict validation: expected hex chars after 'SxCC' should match
                // addressLength/2 address bytes, byteCount-addressLength/2-1 data bytes and
                // the checksum byte
                int expectedHexChars = byteCount * 2;
                if (remaining.length() != expectedHexChars) {
                    success = false;
                    if(listener.onRecordParsed( SrecParserError.ERR_WRONG_DATA_LENGTH
                                              , lineNumber
                                              , -1
                                              , -1
                                              , null
                                              )
                      ) {
                        continue;
                    } else {
                        break;
                    }
                }

                if (remaining.length() < addressLength + 2) { // +2 for checksum
                    success = false;
                    if(listener.onRecordParsed( SrecParserError.ERR_WRONG_DATA_LENGTH
                                              , lineNumber
                                              , -1
                                              , -1
                                              , null
                                              )
                      ) {
                        continue;
                    } else {
                        break;
                    }
                }

                String addressStr = remaining.substring(0, addressLength);
                long address = Long.parseLong(addressStr, 16);

                String dataAndChecksum = remaining.substring(addressLength);
                String dataStr = dataAndChecksum.substring(0, dataAndChecksum.length() - 2);
                String checksumStr = dataAndChecksum.substring(dataAndChecksum.length() - 2);
                int sum = 0;
                byte[] dataBytes = new byte[byteCount-addressLength/2-1];
                for (int i = 0; i < dataBytes.length; ++i) {
                    final short b = Short.parseShort(dataStr.substring(2*i, 2*(i+1)), 16);
                    dataBytes[i] = Byte.valueOf((byte)b);
                    sum += b;
                }

                int checksum = Integer.parseInt(checksumStr, 16);

                // Validate checksum
                sum += byteCount;
                sum += ((int)address & 0x000000FF) >>  0;
                sum += ((int)address & 0x0000FF00) >>  8;
                sum += ((int)address & 0x00FF0000) >> 16;
                sum += ((int)address & 0xFF000000) >> 24;
                sum += checksum;

                if ((sum & 0xFF) != 0xFF) {
                    success = false;
                    if(listener.onRecordParsed( SrecParserError.ERR_INVALID_CHECKSUM
                                              , lineNumber
                                              ,-1
                                              ,-1
                                              , null
                                              )
                      ) {
                        continue;
                    } else {
                        break;
                    }
                }

                if(!listener.onRecordParsed( SrecParserError.SUCCESS
                                           , lineNumber
                                           , recordType
                                           , address
                                           , dataBytes
                                           )
                  ) {
                    /* Interpreting the return value of the listener as parsing error is
                       not correct. The listener sends a false only if it wants to abort
                       further parsing. However, it may tolerate error for now, it order to
                       list more problems before parsing ends. It doesn't provide a final
                       statement after parsing whether everything was alright. For now,
                       this can be tolerated as we considered this problem in the listener. 
                         TODO Extend interface of listener and fetch a final error
                       condition after reading the last line. */
                    success = false;
                    break;
                }
            }
            
            /* After reading all lines, fetch the error state from the listener. */
            if (!listener.getFinalSuccess()) {
                success = false;
            }
        } catch (IOException e) {
            success = false;
            _errCnt.error();
            _logger.error("File system error reading {}", e.getMessage());
        }
        
        return success;

    } /* parse */

} /* End of class SrecParser definition. */
