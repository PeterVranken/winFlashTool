/**
 * @file CanDevice.java
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
 *   CanDevice
 *   listPeakPcanDevices
 *   open (2 variants)
 *   close
 */

package winFlashTool.can;

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
public class CanDevice
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CanDevice.class);

    /** All CAN devices use the one and only global error counter. */
    private static ErrorCounter _errCnt;

    /** The PEAK CAN API; the connection of Java to the external DLL. */
    private static PCANBasic _pcanApi;

    /** The handle of the CAN device or channel, which we are going to use. */
    private TPCANHandle canDev_;

    /**
     * Initialization of module. Needs to be successfully called prior to using any other
     * method.
     *   @return
     * Get true, if initialization succeeds. If \a false is returned then CAN operation
     * won't be possible and the application shouldn't start up.
     *   @param errCnt
     * The error counter to be used for all CAN transmission related problem reporting.
     */
    public static boolean initClass(ErrorCounter errCnt)
    {
        boolean success = false;
        
        _errCnt = errCnt;
        
        /* Initialize the API opject, which connects us to the PEAK DLLs. */
        assert _pcanApi == null;
        _pcanApi = PCANBasicEx.getPcanBasicApi();
        assert _pcanApi != null: "PCANBasicEx used prior to initialization";

        return true;

    } /* initClass */

    /**
     * A new instance of CCAN is created. It can be used to open a CAN device to transmit
     * or receive messages with that device.
     */
    public CanDevice()
    {
        assert _pcanApi != null: "Class not initialized";
        canDev_ = null;
        
    } /* CanDevice.CanDevice */


    /**
     * Try to connect to a PEAK CAN device in classic CAN mode. The device is identified by
     * name. (See openCanDeviceFd() for opening the device in CAN FD mode.)
     *   @param canDevName
     * CAN communication will be done with this CAN device.<p>
     *   If null or the empty string is passed then the first found available PEAK PCAN
     * device is selected and opened.
     *   @param baudRate
     * The wanted bitrate of the CAN transmission.
     *   @param listOfRxCanIds
     * A list of CAN Ids for reception. All Rx messages need to be registered in form of CAN
     * acceptance filters. The list contains objects, which can designate a single CAN ID
     * or a solid range of those, from..till. All of the IDs in the list will be configured
     * as accetance filters. (It's unclear, how many messages can be registered.)
     */
    public boolean open( String canDevName
                       , TPCANBaudrate baudRate
                       , List<CanId> listOfRxCanIds
                       )
    {
        boolean success = true;
        TPCANHandle pcanDevHandle = null;
        if (canDevName == null  ||  canDevName.trim().isEmpty()) {
            pcanDevHandle = null;
        } else {
            try {
                pcanDevHandle = TPCANHandle.valueOf(canDevName);
            } catch(IllegalArgumentException e) {
                success = false;
                _errCnt.error();
                _logger.error( "Can't open PEAK PCAN-USB CAN device {}. No such device is"
                               + " known. {}"
                             , canDevName
                             , e.getMessage()
                             );
            }
        }
        if (success) {
            return open(pcanDevHandle, baudRate, listOfRxCanIds);
        } else {
            return false;
        }        
    } /* open (by name) */
    
    /**
     * Try to connect to a PEAK PEAK PCAN-USB CAN device in classic CAN mode. (See
     * openCanDeviceFd() for opening the device in CAN FD mode.)
     *   @param canDev
     * CAN communication will be done with this PEAK PCAN-USB CAN device.<p>
     *   If null is passed then the first found available PEAK PCAN-USB device is selected
     * and opened.
     *   @param baudRate
     * The wanted bitrate of the CAN transmission.
     *   @param listOfRxCanIds
     * A list of CAN Ids for reception. All Rx messages need to be registered in form of CAN
     * acceptance filters. The list contains objects, which can designate a single CAN ID
     * or a solid range of those, from..till. All of the IDs in the list will be configured
     * as accetance filters. (It's unclear, how many messages can be registered.)
     */
    public boolean open( TPCANHandle canDev
                       , TPCANBaudrate baudRate
                       , List<CanId> listOfRxCanIds
                       )
    {
        assert _pcanApi != null: "Class not initialized";

        boolean success = true;
        if (canDev_ != null) {
            success = false;
            _errCnt.error();
            _logger.error( "Can't open PEAK PCAN-USB CAN device {} while another device ({})"
                           + " is still opened"
                         , canDev
                         , canDev_
                         );
        }
        
        /* No particular device is specified. Use the first available in the iteration of
           all PEAK devices. */
        if (success &&  canDev == null) {
            canDev = PCANBasicEx.getFirstAvailableChannel();
            if (canDev != null) {
                _logger.info( "PEAK PCAN-USB device {} has been automatically chosen for"
                              + " operation."
                            , canDev
                            );
            } else {
                success = false;
                _errCnt. error();
                _logger.error("Can't open PEAK PCAN-USB CAN device. A specific device hasn't"
                              + " been specified"
                              + " for operation and automatic selection fails: Either"
                              + " no such device is connected or all connected devices are"
                              + " occupied by other applications."
                             );
            }
        }
        
        if (success) {
            /* Arguments 3..5 are not used for the Plug&Play device PEAK-USB and
               PEAK-USB-FD. We set them to "don't care". */
            final TPCANStatus errCode = _pcanApi.Initialize
                                                    ( canDev
                                                    , baudRate
                                                    , /*HwType*/ TPCANType.PCAN_TYPE_NONE
                                                    , /*IOPort*/ 0
                                                    , /*Interrupt*/ (short)0
                                                    );
            if(PCANBasicEx.checkReturnCode(errCode))
                _logger.debug("PCANBasic device {} successfully initialized.", canDev);
            else
            {
                success = false;
                _errCnt.error();
                _logger.fatal( "Can't open PEAK PCAN-USB CAN device {}. This application"
                               + " expects a PCAN-USB or PCAN-USB FD device connected to"
                               + " a USB port. The device must not be allocated to another"
                               + " application, e.g., the PCAN Explorer."
                             , canDev
                             );
            }
        }
        
        /* Set the CAN acceptance filter for Rx messages. */
        if (success) {
            /* Can device is acquired. */
            canDev_ = canDev;
            
            for (CanId canId: listOfRxCanIds) {
                final TPCANStatus errCode = _pcanApi.FilterMessages
                                                        ( canDev
                                                        , /*FromID*/ canId.getCanIdFirst()
                                                        , /*ToID*/ canId.getCanIdLast()
                                                        , /*Mode*/ canId.getMsgMode()
                                                        );
                if(PCANBasicEx.checkReturnCode(errCode))
                {
                    _logger.debug( "CAN acceptance filter for ID {} configured for Rx DTO"
                                   + " messages."
                                 , canId
                                 );
                }
                else
                {
                    success = false;
                    _errCnt.error();
                    _logger.fatal( "Configuring the CAN acceptance filter for PEAK PCAN-USB"
                                   + " CAN device {} failed."
                                 , canDev
                                 );
                    break;
                }
            } /* for(All CAN Rx IDs to register) */
            
            /* On failures, free the unusable device again. */
            if (!success) {
                close();
            }
        }        
        return success;
        
    } /* open (by handle) */


    /**
     * Close PEAK PCAN-USB CAN device after use. This operation should release the device
     * such that other applications can acquire and use it, e.g., the PCAN Explorer.
     */
    public boolean close()
    {
        assert _pcanApi != null: "Class not initialized";
        boolean success = true;
        if (canDev_ != null) {
            final TPCANStatus errCode = _pcanApi.Uninitialize(canDev_);
            if(!PCANBasicEx.checkReturnCode(errCode))
            {
                success = false;
                _errCnt.error();
                _logger.error("Can't close PEAK PCAN-USB CAN device. Error in PCANBasic API");
            }
        } else {
            _errCnt.warning();
            _logger.warn("Can't close PEAK PCAN-USB CAN device because it is not opened");
        }
        canDev_ = null;
        return success;

    } /* close */


    /**
     * Reads a CAN message from the receive queue of the PCAN Channel, which has been
     * acquired and opened by this CanDevice object.
     *   @return A TPCANStatus error code.
     *   @param messageBuffer
     * A TPCANMsg buffer with the message to be read.
     *   @param timestampBuffer
     * A TPCANTimestamp structure buffer to get the reception time of the message. If this
     * value is not desired, this parameter should be passed as null.
     */
    public TPCANStatus Read(TPCANMsg messageBuffer, TPCANTimestamp timestampBuffer) {
        assert _pcanApi != null: "Class not initialized";
        assert canDev_ != null: "No device opened";
        return _pcanApi.Read(canDev_, messageBuffer, timestampBuffer);
    }
    
    /**
     * Reads a CAN message from the receive queue of a FD capable PCAN Channel, which has
     * been acquired and opened by this CanDevice object.
     *   @return A TPCANStatus error code.
     *   @param messageBuffer 
     * A TPCANMsgFD structure buffer to store the CAN message.
     *   @param timestampBuffer
     * A TPCANTimestampFD buffer to get the reception time of the message. If this value is
     * not desired, this parameter should be passed as null.
     */
    public TPCANStatus ReadFD(TPCANMsgFD messageBuffer, TPCANTimestampFD timestampBuffer) {
        assert _pcanApi != null: "Class not initialized";
        assert canDev_ != null: "No device opened";
        return _pcanApi.ReadFD(canDev_, messageBuffer, timestampBuffer);
    }
    
    /**
     * Transmits a CAN message on the PCAN Channel, which has been acquired and opened by
     * this CanDevice object.
     *   @return A TPCANStatus error code.
     *   @param messageBuffer 
     * A TPCANMsg buffer with the message to be sent.
     */
    public TPCANStatus Write(TPCANMsg messageBuffer) {
        assert _pcanApi != null: "Class not initialized";
        assert canDev_ != null: "No device opened";
        return _pcanApi.Write(canDev_, messageBuffer);
    }
    
    /**
     * Transmits a CAN message over the FD capable PCAN Channel, which has been acquired
     * and opened by this CanDevice object.
     *   @return A TPCANStatus error code.
     *   @param messageBuffer 
     * A TPCANMsg buffer with the message to be sent.
     */
    public TPCANStatus WriteFD(TPCANMsgFD messageBuffer) {
        assert _pcanApi != null: "Class not initialized";
        assert canDev_ != null: "No device opened";
        return _pcanApi.WriteFD(canDev_, messageBuffer);
    }
    
} /* End of class CanDevice definition. */




