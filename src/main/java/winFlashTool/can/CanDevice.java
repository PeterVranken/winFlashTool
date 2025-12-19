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
 *   closeWinRxHandle
 *   createAndSetRxEvent
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
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.Pointer;

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

    /** A Windows event, used for optimizing the responsiveness on CAN Rx. */
    private HANDLE hWinRxEvent_;

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
        hWinRxEvent_ = null;
        
    } /* CanDevice.CanDevice */


    /**
     * Destroy/cleanup: Close the Windows CAN Rx handle, if it had been created.
     */
    private void closeWinRxHandle() {
        if (hWinRxEvent_ != null) {
            if (!Kernel32.INSTANCE.CloseHandle(hWinRxEvent_)) {
                _errCnt.error();
                final String errMsg = Kernel32Util.getLastErrorMessage();
                _logger.error( "Can't close Windows event for optimal CAN Rx performance"
                               + " after use. {}"
                             , errMsg
                             );
            }
            hWinRxEvent_ = null;
        }
    } /* closeWinRxHandle */
    
    /**
     * Create and add a Windows event to a CAN channel.<p>
     *   Using an event allows high speed response to Rx events without busy wait in a
     * polling loop.
     *   @return
     * Get true if the event could be created and passed to the CAN device for use. False
     * otherwise. An error message has been logged in this case.
     */
    private boolean createAndSetRxEvent() {
        boolean success = true;
        
        /* We create a Windows event with automatic reset. Security attributes aren't used.
           The automatic reset avoids race conditions. It may happen, that we get awoken
           without finding an Rx message in the queue but we won't ever remain suspended
           although there is a message available. */
        hWinRxEvent_ = Kernel32.INSTANCE.CreateEvent( /*securityAttributes*/ null
                                                    , /*manualReset*/ false
                                                    , /*initialState*/ false
                                                    , /*name*/ null
                                                    );
        if (hWinRxEvent_ == null || WinBase.INVALID_HANDLE_VALUE.equals(hWinRxEvent_)) {
            hWinRxEvent_ = null;
            success = false;
            _errCnt.error();
            //final int winErr = Kernel32.INSTANCE.GetLastError();
            //final String errMsg = Kernel32Util.formatMessage(code);
            final String errMsg = Kernel32Util.getLastErrorMessage();
            _logger.error( "Can't create Windows event for optimal CAN Rx performance. {}"
                         , errMsg
                         );
        } else {
            _logger.debug( "Windows event for optimal CAN Rx performance sucessfully"
                           + " created. Handle is {}, handle as Pointer object is {},"
                           + " address is {}."
                         , hWinRxEvent_.toString() 
                         , hWinRxEvent_.getPointer().toString()
                         , Pointer.nativeValue(hWinRxEvent_.getPointer())
                         );
        }

        /* Make this CAN device use the new event for Rx notification. */
        if (success) {
            /* Convert HANDLE to pointer-sized integer. handleValue is the address of the
               Windows handle object, which is wrapped in
               com.sun.jna.platform.win32.WinNT.HANDLE. */
            final long handleValue = Pointer.nativeValue(hWinRxEvent_.getPointer());
            
            /* The Windows handle is a 32 Bit value. */
            final int sizeOfParameter = 4;
            final int handleDWORD = (int)(handleValue & 0x00000000FFFFFFFF);
            
            TPCANStatus status = _pcanApi.SetValue
                                            ( canDev_
                                            , TPCANParameter.PCAN_RECEIVE_EVENT
                                            , Integer.valueOf(handleDWORD)
                                            , sizeOfParameter
                                            );
            if (!PCANBasicEx.checkReturnCode( status
                                            , "Error providing the Windows CAN Rx event to"
                                              + " the PEAK PCAN-USB CAN device."
                                            )
               ) {
                success = false;
            }
        }

        if (!success) {
            closeWinRxHandle();
        }
        
        return success;
        
    } /* createAndSetRxEvent */
    
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
        
        /* If we got a CAN device, we configure it to notify a Windows event in case of CAN
           Rx events. Note, the operation returns an error code if this fails. We don't
           react on the error as CAN reception is possible even without the event, we will
           just loose some time when needlessy poll for messages. */
        if (success) {
            if (!createAndSetRxEvent()) {
                _errCnt.warning();
                _logger.warn( "CAN Rx operation is not possible with Rx event notification."
                              + " The appication will continue to work with degraded"
                              + " performance."
                            );
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
            canDev_ = null;
        } else {
            _logger.debug("Can't close PEAK PCAN-USB CAN device because it is not opened");
        }
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




