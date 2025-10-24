/**
 * @file PCANBasicEx.java
 * A collection of support functions for using the PCANBasic API.
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
/* Interface of class PCANBasicEx
 *   PCANBasicEx
 */

package winFlashTool.ccp;

import java.util.*;
import org.apache.logging.log4j.*;
import peak.can.basic.*;

/**
 * A collection of static support functions for using the PCANBasic API.
 */
public class PCANBasicEx
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(PCANBasicEx.class);

    /** The PEAK CAN API; the connection of Java to the external DLL. */
    private static PCANBasic canApi_ = null;

    /** No need to ever create an instance of this class. */
    private PCANBasicEx()
    {
    } /* PCANBasicEx.PCANBasicEx */

    /**
     * Initialization of module. Needs to be successfully called prior to using any other
     * method.
     *   @param pcanBasicAPI
     * The PEAK CAN API; the connection of Java to the external DLL. Needs to be already
     * initialized.
     */    
    public static void setCanApi(PCANBasic pcanBasicAPI)
    {
        canApi_ = pcanBasicAPI;
    }
    
    
    /**
     * Check the return code got from the PCAN API.<p>
     *   An error message is logged if it is not OK.
     *   @return
     * Get true if the status is alright or false if an error has been reported.
     *   @param errCode
     * The error code received from an API call of the PCAN Basic API.
     */
    public static boolean checkReturnCode(TPCANStatus errCode)
    {
        if(errCode == TPCANStatus.PCAN_ERROR_OK)
            return true;
        else
        {
            /* Translate last error into human understandable text. */
            final StringBuffer errMsg = new StringBuffer();
            TPCANStatus errCodeGet = canApi_.GetErrorText( errCode
                                                         , /*Language*/ (short)0
                                                         , errMsg
                                                         );
            if(errCodeGet == TPCANStatus.PCAN_ERROR_OK)
            {
                _logger.error( "PEAK PCAN Basic API returned error code {}: {}"
                             , errCode.toString()
                             , errMsg.toString()
                             );
            }
            else
            {
                _logger.error( "PEAK PCAN Basic API returned error code {} (No message"
                               + " text is available for this code.)"
                             , errCode.toString()
                             );
            }
            return false;
        }
    } /* checkStatus */
    
    
    /**
     * Convert the frequently used byte type into an unsigned integer.
     *   @return
     * Get the value of b as an int in the range 0..255.
     *   @param b
     * The byte value, which is a number -127..128 for Java.
     */
    public static int b2i(byte b)
    {
        return (int)b & 0xFF;
    }

} /* End of class PCANBasicEx definition. */





