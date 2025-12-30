/**
 * @file AddressRangeSequence.java
 * Command line interface: The address range(s) to upload from the target device.
 *
 * Copyright (C) 2015-2025 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
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
/* Interface of class AddressRangeSequence
 *   parseHexRange
 *   parseGetNextArg
 *   parseCmdLine
 */

package winFlashTool.applicationInterface;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.basics.Range;
import winFlashTool.applicationInterface.cmdLineParser.CmdLineParser;
import winFlashTool.srecParser.SRecord;
import winFlashTool.srecParser.SRecordSequence;

/**
 * The representation of a (scattered) address range.<p>
 *   The application command line can contain one or more arguments to specify the memory
 * addresses for a data upload from target device to the machine running the flash tool
 * application. This class parses the command line for those arguments and extracts the
 * required address information.
 */
public class AddressRangeSequence extends ArrayList<Range> {

    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(AddressRangeSequence.class);

    /** The error counter for parameter parsing. */
    private ErrorCounter errCnt_;

    /** The name of the command line argument for specifying an upload address range. */
    private static final String ARG_NAME_ADDR_RANGE = "address-range";

    /**
     * Create a new, empty instance of AddressRangeSequence.
     *   @param errCnt
     * The error counter to be used for all errors reported by the new object instance.
     */
    public AddressRangeSequence(ErrorCounter errCnt) {
        super(1);
        assert errCnt != null;
        errCnt_ = errCnt;
    }

    /**
     * Extract an integer range from the value of an according command line argument.
     *   @return The function either returns the pair of integers that designates the range or
     * null in case of errors. An error message has been logged in this case.
     *   @param argValue
     * The string value of the command line argument, which is decoded to a range of
     * integers.
     */
    private Range parseHexRange(String argValue)
    {
        // TODO Support optional 0x
        if(argValue.matches("\\s*\\p{XDigit}+(:\\p{XDigit}+)?\\s*"))
        {
            String[] numAry = argValue.split(":");

            try {
                assert numAry.length >= 1  &&  numAry.length <= 2;
                Long from = Long.valueOf(numAry[0], /*radix*/ 16)
                   , to;
                if (numAry.length == 2) {
                    to = Long.valueOf(numAry[1], /*radix*/ 16);
                    if (to.longValue() <= from.longValue()) {
                        errCnt_.error();
                        _logger.error( "The value {} of argument {} designates an empty"
                                       + " address range."
                                     , argValue
                                     , ARG_NAME_ADDR_RANGE
                                     );
                        return null;
                    }
                } else {
                    to = from + 1;
                }
                return new Range(from, to);
            }
            catch (NumberFormatException e) {
                /* Because of the regular expression check at the beginning this can happen
                   only due to a range overflow. We can emit a specific error message. */
                errCnt_.error();
                _logger.error( "Value {} of argument {} is out of range. A positive"
                               + " hexadecimal integer is expected in the range [0, 2^32]."
                             , argValue
                             , ARG_NAME_ADDR_RANGE
                             );
                return null;
            }
        } else {
            errCnt_.error();
            _logger.error( "The value of argument {} is either a hexadecimal"
                           + " integer or a colon-separated pair of those but found: \"{}\"."
                         , ARG_NAME_ADDR_RANGE
                         , argValue
                         );
            return null;
        }
    } /* parseHexRange */

    /**
     * Filter arguments, which relate to this class.
     *   @return Next relevant argument or null if there's no one left.
     *   @param argStream
     * The stream object, which delivers all arguments.
     */
    private String parseGetNextArg(Iterator<String> argStream) {
        while (argStream.hasNext()) {
            String arg = argStream.next();
            _logger.trace("Next command line argument: {}", arg);
            switch(arg) {
            case ARG_NAME_ADDR_RANGE:

                return arg;

            default:
            }
        }

        return null;

    } /* parseGetNextArg */

    /**
     * Fetch all upload address related arguments from the command line and evaluate them.
     *   @return
     * Get true if the command line could be parsed without errors. The application shoul
     * dnot atart up if the method returns false.
     *   @param clp
     * The command line parser object after succesful run of CmdLineParser.parseArgs.
     */
    public boolean parseCmdLine(CmdLineParser clp)
    {
        assert clp != null;

        boolean success = true;

        Iterator<String> it = clp.iterator();
        String argName;
        while ((argName=parseGetNextArg(it)) != null) {
            final String argVal = clp.getString(argName);
            final Range addrRange = parseHexRange(argVal);
            if (addrRange != null) {
                _logger.debug( "Add address range [0x{}, 0x{}) to the list of uploaded"
                               + " memory areas."
                             , Long.toHexString(addrRange.from())
                             , Long.toHexString(addrRange.till())
                             );
                add(addrRange);
            } else {
                success = false;
            }
        } /* while(For all command line arguments) */

        return success;

    } /* parseCmdLine */

    /**
     * Convert the sequence of address ranges into a sequence of s-records. The s-records
     * in the new sequence have the same address ranges but, additionally, they provide
     * memory buffers for storage of the contents of the address ranges.
     *   @return
     * Get the sequence of s-records. The memory buffers are allocated but still emtpy
     * (undefined contents).<p>
     *   S-record sequences don't allow overlapping address ranges. The function can fail
     * if this contains such ranges. null is returned in this case.
     */
    public SRecordSequence toSRecordSequence() {
        final SRecordSequence srecSeq = new SRecordSequence();
        for (Range r: this) {
            if (!srecSeq.add(new SRecord(r))) {
                errCnt_.error();
                _logger.error( "Can't upload data from the target. The specified memory"
                               + " area(s) aren't valid. First failing address range is {}."
                             , r
                             );
                return null;
            }
        }
        return srecSeq;
    }
} /* Class AddressRangeSequence */
