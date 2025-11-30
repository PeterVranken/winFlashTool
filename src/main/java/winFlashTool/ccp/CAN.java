/**
 * @file CAN.java
 * Support of using a PEAK PCAN device, mainly open and close device and setting the
 * configuration parameters.
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
/* Interface of class CCP
 *   CAN
 *   openCanDevice
 *   closeCanDevice
 */

package winFlashTool.ccp;

import java.util.*;
import java.io.IOException;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import peak.can.basic.*;

/**
 * Support of using a PEAK PCAN device, mainly open and close device and setting the
 * configuration parameters.<p>
 *   Reading and writing of CAN messages is not element of this file; having an opened and
 * configured device, this can directly be done with the PCANBasics API.
 */
public class CAN
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CAN.class);

    /** The error counter to be used for all CAN device related problems. */
    private final ErrorCounter errCnt_;

    /** The PEAK CAN API; the connection of Java to the external DLL. */
    private PCANBasic canApi_ = null;

    /** The handle of the CAN device or channel, which we are going to use. */
    private TPCANHandle canDev_ = null;

    /** The Rx CAN IDs. These messages are configured for reception. */
    private List<CanId> listOfRxCanIds_ = null;

    
    /**
     * A new instance of CCP is created. It represents a CCP connection with a ECU.
     *   @param errCnt
     * The error counter to be used for problem reporting.
     *   @throws IOException
     * Get an exception if the PCAN I/O is not accessible at all. The application shouldn't
     * start up in this case.
     */
    public CAN(ErrorCounter errCnt)
        throws IOException
    {
        errCnt_ = errCnt;

        /* Initialize the API opject, which connects us to the PEAK DLLs. */
        assert canApi_ == null;
        canApi_ = new PCANBasic();
        if(canApi_.initializeAPI())
        {
            _logger.debug("PCANBasic API successfully initialized.");
            PCANBasicEx.setCanApi(canApi_);
        }
        else
        {
            errCnt_.error();
            canApi_ = null;
            _logger.fatal("Unable to initialize the PEAK PCAN Basic API. Most probable"
                          + " reason is the application installation; the PEAK DLLs might"
                          + " be not localized. Check application configuration and"
                          + " Windows search path. Files PCANBasic.dll and"
                          + " PCANBasic_JNI.dll need to be found."
                         );
            throw new IOException("PCANBasic API not accessible");
        }
    } /* CAN.CAN */


//        assert canApi_ != null;
            /* Print all connected devices. */
            // TODO Make this an option. Could be done on log level DEBUG always
            // TODO The function doesn't have proper error handling yet
//            PCANBasicEx.printAttachedChannels(canApi_);
         

    /**
     * Try to connect to a PEAK CAN device.
     *   @param canDev
     * CAN communication will be done with this CAN device.
     */
    public boolean openCanDevice(TPCANHandle canDev)
    {
        assert canApi_ != null;
        
        // TODO Set list of Rx msgs from fct argument
        // We need to uses ranges to group neigboured IDs. Better: directly pass as list of ranges
        /* Set and remind the CN Ids to use for CCP communication. */
        final CanId canRxId_ = new CanId(/*canId*/ 100, /*isExtId*/ false);
        final int noRxCanMsgs = 1;
        
        if (listOfRxCanIds_ == null) {
            listOfRxCanIds_ = new ArrayList<CanId>(noRxCanMsgs);
        }
        listOfRxCanIds_.add(canRxId_);

        boolean success = true;
        if (canDev_ != null) {
            success = false;
            errCnt_.error();
            _logger.error( "Can't open CAN device {} while another device ({}) is still opened"
                         , canDev
                         , canDev_
                         );
        }
        
        if (success) {
            /* Arguments 3..5 are not used for the Plug&Play device PEAK-USB and
               PEAK-USB-FD. We set them to "don't care". */
            final TPCANStatus errCode = canApi_.Initialize( canDev_
                                                          , TPCANBaudrate.PCAN_BAUD_500K
                                                          , /*HwType*/ TPCANType.PCAN_TYPE_NONE
                                                          , /*IOPort*/ 0
                                                          , /*Interrupt*/ (short)0
                                                          );
            if(PCANBasicEx.checkReturnCode(errCode))
                _logger.debug("PCANBasic device {} successfully initialized.", canDev_);
            else
            {
                success = false;
                errCnt_.error();
                _logger.fatal( "Can't open PEAK PCAN-USB CAN device {}. This application"
                               + " expects a PCAN-USB or PCAN-USB FD device connected to"
                               + " a USB port. The device must not be allocated to another"
                               + " application, e.g., the PCAN Explorer."
                             , canDev_
                             );
            }
        }
        
        /* Set the CAN acceptance filter for Rx messages. */
        for (CanId canId: listOfRxCanIds_) {
            if (success != true) {
                break;
            }

            final TPCANStatus errCode = canApi_.FilterMessages
                                                    ( canDev_
                                                    , /*FromID*/ canId.getCanId()
                                                    , /*ToID*/ canId.getCanId()
                                                    , /*Mode*/ canId.getMsgMode()
                                                    );
            if(PCANBasicEx.checkReturnCode(errCode))
            {
                _logger.debug( "CAN acceptance filter for ID {} configured for Rx DTO"
                               + " messages."
                             , canId.canIdToString()
                             );
            }
            else
            {
                success = false;
                errCnt_.error();
                _logger.fatal( "Configuring the CAN acceptance filter for PEAK PCAN-USB"
                               + " CAN device {} failed."
                             , canDev_
                             );
            }
        }
        
        return success;
        
    } /* openCanDevice */


    /**
     * Close CAN device after use. This operation should release the device such that other
     * applications can acquire and use it, e.g., the PCAN Explorer.
     */
    public boolean closeCanDevice()
    {
        boolean success = true;
        if (canDev_ != null) {
            final TPCANStatus errCode = canApi_.Uninitialize(canDev_);
            if(!PCANBasicEx.checkReturnCode(errCode))
            {
                errCnt_.error();
                _logger.error("Can't close PEAK PCAN-USB CAN device. Error in PCANBasic API");
            }
        } else {
            success = false;
            errCnt_.error();
            _logger.error("Can't close PEAK PCAN-USB CAN device because it is not opened");
        }
        return success;

    } /* closeCanDevice */

} /* End of class CAN definition. */




