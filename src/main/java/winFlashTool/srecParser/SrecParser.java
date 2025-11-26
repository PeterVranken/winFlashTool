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
    public interface SrecListener {
        void onRecordParsed( int errorCode
                           , int lineNumber
                           , Integer recordType
                           , Long address
                           , List<Integer> dataBytes
                           );
    }
    
    // Error codes
    public static final int SUCCESS = 0;
    public static final int WARN_EMPTY_LINE = 1;
    public static final int ERR_UNKNOWN_TYPE = 2;
    public static final int ERR_INVALID_CHECKSUM = 3;
    public static final int ERR_WRONG_DATA_LENGTH = 4;
    public static final int ERR_REGEX_MISMATCH = 5;

    private SrecListener listener;

    public void registerListener(SrecListener listener) {
        this.listener = listener;
    }

    public void parse(File srecFile) {
        if (listener == null) {
            throw new IllegalStateException("Listener not registered.");
        }

        Pattern pattern = Pattern.compile("^S([0-9])([0-9A-Fa-f]{2})([0-9A-Fa-f]+)$");

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
                    listener.onRecordParsed(WARN_EMPTY_LINE, lineNumber, null, null, null);
                    continue;
                }

                Matcher matcher = pattern.matcher(line);
                if (!matcher.matches()) {
                    listener.onRecordParsed(ERR_REGEX_MISMATCH, lineNumber, null, null, null);
                    continue;
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
                        listener.onRecordParsed(ERR_UNKNOWN_TYPE, lineNumber, null, null,null);
                        continue;
                }

                // Strict validation: expected hex chars after 'SxCC' should match
                // byteCount-1 data bytes and the checksum byte
                int expectedHexChars = byteCount * 2;
                if (remaining.length() != expectedHexChars) {
                    listener.onRecordParsed(ERR_WRONG_DATA_LENGTH, lineNumber, null,null,null);
                    continue;
                }

                if (remaining.length() < addressLength + 2) { // +2 for checksum
                    listener.onRecordParsed(ERR_WRONG_DATA_LENGTH, lineNumber, null,null,null);
                    continue;
                }

                String addressStr = remaining.substring(0, addressLength);
                Long address = Long.parseLong(addressStr, 16);

                String dataAndChecksum = remaining.substring(addressLength);
                String dataStr = dataAndChecksum.substring(0, dataAndChecksum.length() - 2);
                String checksumStr = dataAndChecksum.substring(dataAndChecksum.length() - 2);

                List<Integer> dataBytes = new ArrayList<>();
                for (int i = 0; i < dataStr.length(); i += 2) {
                    dataBytes.add(Integer.parseInt(dataStr.substring(i, i + 2), 16));
                }

                int checksum = Integer.parseInt(checksumStr, 16);

                // Validate checksum
                int sum = byteCount;
                for (int i = 0; i < addressStr.length(); i += 2) {
                    sum += Integer.parseInt(addressStr.substring(i, i + 2), 16);
                }
                for (int b : dataBytes) sum += b;
                sum += checksum;

                if ((sum & 0xFF) != 0xFF) {
                    listener.onRecordParsed(ERR_INVALID_CHECKSUM, lineNumber, null, null, null);
                    continue;
                }

                listener.onRecordParsed(SUCCESS, lineNumber, recordType, address, dataBytes);
            }
        } catch (IOException e) {
            System.out.println("SrecParser failed to parse " + srecFile.getAbsolutePath());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java SrecParser <file>");
            return;
        }

        File srecFile = new File(args[0]);
        SrecParser parser = new SrecParser();
        parser.registerListener((errorCode, lineNumber, recordType, address, dataBytes) -> {
            System.out.println("Line " + lineNumber + ": ErrorCode=" + errorCode);
            if (errorCode == SUCCESS) {
                System.out.println("  Type=" + recordType + ", Address=0x"
                                   + Long.toHexString(address)
                                   + ", Data=" + dataBytes
                                  );
            }            
        });
        parser.parse(srecFile);
    }
} /* End of class SrecParser definition. */
