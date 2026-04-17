/**
 * @file SrecParserError.java
 * Error codes, which are returned by the srec parser.
 *
 * Copyright (C) 2025-2026 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
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
/* Interface of class SrecErrorCodes
 */

package winFlashTool.srecParser;

import java.util.*;

/**
 * Enumeration of supported CCP commands.
 */
enum SrecParserError
{
    /** The supported eror codes. */
    SUCCESS("S-Record successfully read"),
    WARN_EMPTY_LINE("The line in the srec input file is empty"),
    ERR_UNKNOWN_TYPE("The second character of the S-record designates an unknown/unsupported"
                     + " kind of S-record"
                    ),
    ERR_INVALID_CHECKSUM("The checksum for the S-record doesn't match the data contents"),
    ERR_WRONG_DATA_LENGTH("The second byte of the S-record doesn't match the length of"
                          + " the S-record"
                         ),
    ERR_REGEX_MISMATCH("Syntax error. The line in the srec input file is not an S-record");
    
    /** A comprehensible explanation of what went wrong, when this error is seen. */
    final String errMsg_;

    /**
     * An enumeration in Java is a number of static singleton objects, that represent
     * the enumerated values and which are automatically created once and forever. If
     * we don't rely on the default constructor but place our own here, then we can
     * extend these objects with additional functionality, e.g, completing the enumerated
     * values with human friendly explanation.
     *   @param errMsg
     * A comprehensible explanation of what went wrong, when this error is seen.
     */
    SrecParserError(String errMsg)
    {
        errMsg_ = errMsg;

        /* Note, from the constructor, it is not allowed to access static fields of the
           enum class. The constructor is called for all enumerated values before the
           static fields of the class are initialized and accessing a static field would
           mean dealing with potentially uninitialized data. If there's anything left to do
           then we can place the operation in a static block, which is executed after all
           constructor calls and after all initialization of the class' static data. */
    }

    /**
     * Get the error code as text.
     *   @return
     * Error code as human readable text, i.e., the name of the enumerated value.
     */
    public String getErrorCode()
    {
        return toString();
    }

    /**
     * Get the error message.
     *   @return
     * A comprehensible explanation of what went wrong, when this error is seen.
     */
    public String getMessage() 
    {
        return errMsg_;
    }
} /* SrecParserError */