/**
 * @file CanDevice.java
 * Support of using a PEAK PCAN device, mainly open and close device and setting the
 * configuration parameters.
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
/* Interface of class CanDevice
 *   initClass
 *   CanDevice
 *   open (2 variants)
 *   close
 *   read (2 variants)
 *   readFd
 *   write
 *   writeFd
 *   onCanMsgRx
 */

package winFlashTool.can;

import java.util.*;
import java.io.IOException;
import org.apache.logging.log4j.*;
import peak.can.basic.*;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.basics.SignalWithAutoReset;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.Pointer;

/**
 * The listener class for the CAN Rx events.<p>
 *   The class has a single method, which receives the notifications. The mechanism in the
 * PCAN Basic API is static, not CAN channel related. This eans that we need a singlton
 * object of this class for cooperating with the PCAN Basic APIa and that this class
 * needs to implement the needed dispatching code for delegation of the notifications to
 * the affected CAN devices.
 */
class CanRxNotificationDispatcher implements peak.can.basic.IRcvEventProcessor {

    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger =
                                    LogManager.getLogger(CanRxNotificationDispatcher.class);

    /** A map of PCAN Basic CAN channels onto registered CAN devices, which require the
        notification from the given PCAN Basic CAN channel. */
    private static Map<TPCANHandle, CanDevice> _mapPcanApiHandleToCanDevice;

    /** The reference to the listener object. */
    private static CanRxNotificationDispatcher _theListener = null;

    /**
     * One-time initialization; the one and only listener object is created and registered
     * at the PCAN Basic API.
     */
    static void initClass() {
        assert _theListener == null: "Re-initialization of class is not intended";
        _theListener = new CanRxNotificationDispatcher();
        _mapPcanApiHandleToCanDevice = new HashMap<TPCANHandle, CanDevice>();
        RcvEventDispatcher.setListener(_theListener);
    }

    /**
     * Constructor for the one and only object of this class, which is registered as
     * listener at the PCAN Basic API.
     */
    private CanRxNotificationDispatcher() {
    }

    /**
     * Register a particular CAN device for receiving Rx notifications.
     *   @param pcanChnHandle
     * The representation of the CAN device, which wants to get Rx notifications, at the
     * PCAN Basic API.
     *   @param canDev
     * The CAN device object, which wants to get Rx notifications.
     */
    static void registerCanDevForRxNotifications(TPCANHandle pcanChnHandle, CanDevice canDev) {
        /* Add the CAN device to the dispatching map. */
        final CanDevice prevSetting = _mapPcanApiHandleToCanDevice.put(pcanChnHandle, canDev);

        /* It's just a coding error if one registers the same CAN device twice without
           un-registering intermediately. */
        assert prevSetting == null: "CAN device doubly registered for Rx notifications";

        /* Enable CAN Rx events for this device at the PCAN Basic API. */
        PCANBasicEx.getPcanBasicApi().SetRcvEvent(pcanChnHandle);

    } /* registerCanDevForRxNotifications */

    /**
     * Unregister a particular CAN device from further receiving Rx notifications.
     *   @param pcanChnHandle
     * The representation of the CAN device, which wants to get Rx notifications, at the
     * PCAN Basic API.
     */
    static void unregisterCanDevFromRxNotifications(TPCANHandle pcanChnHandle) {
        /* Check if call is valid: Had the device been registered? */
        assert _mapPcanApiHandleToCanDevice.containsKey(pcanChnHandle)
             : "Unregistered CAN device had not been registered before";

        /* Disable CAN Rx events for this device at the PCAN Basic API. */
        PCANBasicEx.getPcanBasicApi().ResetRcvEvent(pcanChnHandle);

        /* Remove the CAN device from the dispatching map. */
        _mapPcanApiHandleToCanDevice.remove(pcanChnHandle);

    } /* unregisterCanDevFromRxNotifications */

    /**
     * This method is called by the RcvEventDispatcher to process the CAN Receive-Event
     * by the current implementor.
     *   @param pcanChnHandle
     * The representation of the CAN device, which received a CAN message, at the
     * PCAN Basic API.
     */
    public void processRcvEvent(TPCANHandle pcanChnHandle) {
        _logger.trace( "CAN device {} received a message at {} ns."
                     , pcanChnHandle
                     , System.nanoTime()
                     );

        /* Fetch the CAN device object, which wraps the PCAN Basic channel. */
        final CanDevice canDev = _mapPcanApiHandleToCanDevice.get(pcanChnHandle);

        /* It's just a coding error if Rx notifications had been enabled for a channel with
           registering a CAN device object at the same time. */
        assert canDev != null: "CAN device not properly registered for Rx notifications";

        /* Delegate the notification to the affected CAN device object. */
        canDev.onCanMsgRx();

    } /* processRcvEvent */

} /* class CanRxNotificationDispatcher */


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
    private TPCANHandle pcanDevHandle_;

    /** A signal, which is set by the PCAN driver when a new CAN message is received by
        this device. */
    private SignalWithAutoReset rxNotification_;


    /**
     * Initialization of module. Needs to be successfully called prior to using any other
     * method.
     *   @return
     * Get true, if initialization succeeds. If false is returned then CAN operation
     * won't be possible and the application shouldn't start up.
     *   @param errCnt
     * The error counter to be used for all CAN transmission related problem reporting.
     */
    public static boolean initClass(ErrorCounter errCnt)
    {
        _errCnt = errCnt;

        /* Get the reference to the API object, which connects us to the PEAK DLLs. */
        assert _pcanApi == null;
        _pcanApi = PCANBasicEx.getPcanBasicApi();
        assert _pcanApi != null: "PCANBasicEx used prior to initialization";

        /* Register the CAN Rx event dispatcher at the PCAN Basic API. */
        CanRxNotificationDispatcher.initClass();

        return true;

    } /* initClass */

    /**
     * A new instance of CCAN is created. It can be used to open a CAN device to transmit
     * or receive messages with that device.
     */
    public CanDevice()
    {
        assert _pcanApi != null: "Class not initialized";
        pcanDevHandle_ = null;
        rxNotification_ = null;

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
     *   @param pcanDevHandle
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
    public boolean open( TPCANHandle pcanDevHandle
                       , TPCANBaudrate baudRate
                       , List<CanId> listOfRxCanIds
                       )
    {
        assert _pcanApi != null: "Class not initialized";

        boolean success = true;
        if (pcanDevHandle_ != null) {
            success = false;
            _errCnt.error();
            _logger.error( "Can't open PEAK PCAN-USB CAN device {} while another device ({})"
                           + " is still opened"
                         , pcanDevHandle
                         , pcanDevHandle_
                         );
        }

        /* No particular device is specified. Use the first available in the iteration of
           all PEAK devices. */
        if (success &&  pcanDevHandle == null) {
            pcanDevHandle = PCANBasicEx.getFirstAvailableChannel();
            if (pcanDevHandle != null) {
                _logger.info( "PEAK PCAN-USB device {} has been automatically chosen for"
                              + " operation."
                            , pcanDevHandle
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
                                                    ( pcanDevHandle
                                                    , baudRate
                                                    , /*HwType*/ TPCANType.PCAN_TYPE_NONE
                                                    , /*IOPort*/ 0
                                                    , /*Interrupt*/ (short)0
                                                    );
            if(PCANBasicEx.checkReturnCode(errCode))
                _logger.debug("PCANBasic device {} successfully initialized.", pcanDevHandle);
            else
            {
                success = false;
                _errCnt.error();
                _logger.fatal( "Can't open PEAK PCAN-USB CAN device {}. This application"
                               + " expects a PCAN-USB or PCAN-USB FD device connected to"
                               + " a USB port. The device must not be allocated to another"
                               + " application, e.g., the PCAN Explorer."
                             , pcanDevHandle
                             );
            }
        }
        
        /* Enable bus-off auto recovery. The device clears it serror counters and
           reconnects after a short bus idle time. */
        if (success) {
            final TPCANStatus errCode = 
                        _pcanApi.SetValue( pcanDevHandle
                                         , TPCANParameter.PCAN_BUSOFF_AUTORESET
                                         , TPCANParameterValue.PCAN_PARAMETER_ON
                                         , 4 /* Size of int in underlaying PCANBasic lib. */
                                         );
            if(PCANBasicEx.checkReturnCode(errCode)) {
                _logger.debug( "Automatic BUSOFF recovery enabled for {}."
                             , pcanDevHandle
                             );
            } else {
                success = false;
                _errCnt.error();
                _logger.fatal( "Can't configure PEAK PCAN-USB CAN device {}. Enabling"
                               + " automatic BUSOFF recovery failed."
                             , pcanDevHandle
                             );
            }
        }

        /* Set the CAN acceptance filter for Rx messages. */
        if (success) {
            /* Can device is acquired. */
            pcanDevHandle_ = pcanDevHandle;

            for (CanId canId: listOfRxCanIds) {
                final TPCANStatus errCode = _pcanApi.FilterMessages
                                                        ( pcanDevHandle
                                                        , /*FromID*/ canId.getCanIdFirst()
                                                        , /*ToID*/ canId.getCanIdLast()
                                                        , /*Mode*/ canId.getMsgMode()
                                                        );
                if(PCANBasicEx.checkReturnCode(errCode)) {
                    _logger.debug( "CAN acceptance filter for ID {} configured for Rx DTO"
                                   + " messages."
                                 , canId
                                 );
                } else {
                    success = false;
                    _errCnt.error();
                    _logger.fatal( "Configuring the CAN acceptance filter for PEAK PCAN-USB"
                                   + " CAN device {} failed."
                                 , pcanDevHandle
                                 );
                    break;
                }
            } /* for(All CAN Rx IDs to register) */

            /* On failures, free the unusable device again. */
            if (!success) {
                close();
            }
        }

        if (success) {
            /* Register the listener for CAN Rx events at the PCAN basic library. Such
               events will notify the signal-with-auto-reset. */
            rxNotification_ = new SignalWithAutoReset();
            CanRxNotificationDispatcher.registerCanDevForRxNotifications(pcanDevHandle, this);

            /* Pop all messages from the queue, which could have been received during
               configuration of the device. In particular, there can be message with unexpected
               CAN IDs, if they had been received between acquisition of device and setting the
               acceptance filters. */
            final TPCANMsg tmpCanMsg = new TPCANMsg();
            TPCANStatus errCode;
            int noDroppedMsgs = -1;
            do {
                errCode = read(tmpCanMsg, /*TimestampBuffer*/ null, /*timeoutInMs*/ 0);
                ++ noDroppedMsgs;
            } while (errCode == TPCANStatus.PCAN_ERROR_OK);
            
            if (noDroppedMsgs > 0) {
                _logger.debug( "Open CAN device {}: Discard {} prematurely received CAN"
                               + " messages"
                             , pcanDevHandle_
                             , noDroppedMsgs
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
        if (pcanDevHandle_ != null) {
            /* Don't send more CAN Rx notifications to this device. */
            CanRxNotificationDispatcher.unregisterCanDevFromRxNotifications(pcanDevHandle_);
            rxNotification_ = null;

            final TPCANStatus errCode = _pcanApi.Uninitialize(pcanDevHandle_);
            if(!PCANBasicEx.checkReturnCode(errCode))
            {
                success = false;
                _errCnt.error();
                _logger.error("Can't close PEAK PCAN-USB CAN device. Error in PCANBasic API");
            }
            pcanDevHandle_ = null;

        } else {
            _logger.debug("Can't close PEAK PCAN-USB CAN device because it is not opened");
        }
        return success;

    } /* close */


    /**
     * Waits for and Reads a CAN message from the receive queue of the PCAN Channel, which
     * has been acquired and opened by this CanDevice object.
     *   @return A TPCANStatus error code.
     *   @param messageBuffer
     * A TPCANMsg buffer with the message to be read.
     *   @param timestampBuffer
     * A TPCANTimestamp structure buffer to get the reception time of the message. If this
     * value is not desired, this parameter should be passed as null.
     *  @param timeoutInMs
     * The function returns immediately if a message has already been received. Otherwise
     * it'll wait for reception of a message but no longer than this arguments allows. If
     * after this time span still no message is received then the function returns with
     * error code {@link PCANStatus.PCAN_ERROR_TIMEOUT}. Unit is Milliseconds.<p>
     *   The function will return TPCANStatus.PCAN_ERROR_QRCVEMPTY only if timeoutInMs is
     * zero - and if no message is currently in the Rx queue.
     *   @note
     * Several messages may have been queued since the previous invocation of this method.
     * If the method returns a message then it is recommended to poll for all potentially
     * also queued other ones in a loop. The argument timeoutInMs should be set to zero in
     * these subsequent polling calls, in order to not unnecessarily block when the end of
     * the queue is reached.
     */
    public TPCANStatus read( TPCANMsg messageBuffer
                           , TPCANTimestamp timestampBuffer
                           , int timeoutInMs
                           ) {
        assert _pcanApi != null: "Class not initialized";
        assert pcanDevHandle_ != null: "No device opened";

        /* Wait for the signal being notified. Note, a notified signal can mean more than
           one message in the queue. */
        boolean gotSignal;
        try {
            gotSignal = rxNotification_.await(timeoutInMs);
        } catch(InterruptedException e) {
            /* If we get here, then the wait time has not elapsed but aborted. This can
               happen if some code would use the Thread API to interrupt our thread. We
               don't report this as a special situation as it basically means the same as
               TIMEOUT, "no message received during wait time", and because it'll anyway
               never happen in this application. */
            gotSignal = false;
        }

        /* If more than one messages had been queued before this message checks the signal
           then only the first invocation of this method will see a notified signal. The
           subsequent calls should still be able to return the remaining queued messages -
           therefore we must call the read API regardless of the check of the signal. */
        TPCANStatus readStatus = _pcanApi.Read( pcanDevHandle_
                                              , messageBuffer
                                              , timestampBuffer
                                              );
        if (readStatus == TPCANStatus.PCAN_ERROR_QRCVEMPTY  && timeoutInMs > 0) {
            readStatus = TPCANStatus.PCAN_ERROR_TIMEOUT;
        }

        return readStatus;

    } /* read, 1st variant */

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
    public TPCANStatus read(TPCANMsg messageBuffer, TPCANTimestamp timestampBuffer) {
        assert _pcanApi != null: "Class not initialized";
        assert pcanDevHandle_ != null: "No device opened";
        return _pcanApi.Read(pcanDevHandle_, messageBuffer, timestampBuffer);

    } /* read, 2nd variant */

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
    public TPCANStatus readFd(TPCANMsgFD messageBuffer, TPCANTimestampFD timestampBuffer) {
        assert _pcanApi != null: "Class not initialized";
        assert pcanDevHandle_ != null: "No device opened";
        return _pcanApi.ReadFD(pcanDevHandle_, messageBuffer, timestampBuffer);
    }

    /**
     * Transmits a CAN message on the PCAN Channel, which has been acquired and opened by
     * this CanDevice object.
     *   @return A TPCANStatus error code.
     *   @param messageBuffer
     * A TPCANMsg buffer with the message to be sent.
     */
    public TPCANStatus write(TPCANMsg messageBuffer) {
        assert _pcanApi != null: "Class not initialized";
        assert pcanDevHandle_ != null: "No device opened";
        return _pcanApi.Write(pcanDevHandle_, messageBuffer);
    }

    /**
     * Transmits a CAN message over the FD capable PCAN Channel, which has been acquired
     * and opened by this CanDevice object.
     *   @return A TPCANStatus error code.
     *   @param messageBuffer
     * A TPCANMsg buffer with the message to be sent.
     */
    public TPCANStatus writeFd(TPCANMsgFD messageBuffer) {
        assert _pcanApi != null: "Class not initialized";
        assert pcanDevHandle_ != null: "No device opened";
        return _pcanApi.WriteFD(pcanDevHandle_, messageBuffer);
    }

    /**
     * On every CAN message received by this CAN device, this method is called by the PCAN
     * Basic API via class RcvEventDispatcher and via the there registered listener object.
     */
    public void onCanMsgRx() {
        rxNotification_.signal();
    }
} /* End of class CanDevice definition. */




