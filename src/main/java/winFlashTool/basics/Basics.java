/**
 * @file Basics.java
 * A collection of some basic helpers.
 *
 * Copyright (C) 2026 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
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
/* Interface of class Basics
 *   b2i
 *   byteArrayToHex
 */

package winFlashTool.basics;

import java.util.*;
import org.apache.logging.log4j.*;

/**
 * A collction of some basic helpers.
 */
public class Basics {
//    /** The global logger object for all progress and error reporting. */
//    private static final Logger _logger = LogManager.getLogger(Basics.class);

    /** A lookup table nibble to hex representation. */    
    final static char[] _hexLokupTable = "0123456789ABCDEF".toCharArray();
    
    /**
     * Convert the frequently used byte type into an unsigned integer.
     *   @return
     * Get the value of b as an int in the range 0..255.
     *   @param b
     * The byte value, which is a number -127..128 for Java.
     */
    public static int b2i(byte b) {
        return (int)b & 0xFF;
    }


    /**
     * Convert bytes to lowercase hexadecimal.
     *   @return
     * Get the byte array as text. Each byte is represented as a blank-separated 2-digit
     * hexadecimal number.
     *   @param in
     * The converted byte array.
     */
    public static String byteArrayToHex(final byte[] in) {
        final int maxLen = 128
                , len;        
        if (in.length < maxLen) {
            len = in.length;
        } else {
            len = maxLen;
        }
        
        char[] out = new char[len * 3];
        for (int i = 0, j = 0; i < len; i++) {
            int v = in[i] & 0xff;
            out[j++] = ' ';
            out[j++] = _hexLokupTable[v >>> 4];
            out[j++] = _hexLokupTable[v & 0x0f];
        }
        
        String arrayStr = new String(out);
        if (len < in.length) {
            arrayStr += " ...";
        }
        return arrayStr;
        
    } /* byteArrayToHex */

} /* Basics */




