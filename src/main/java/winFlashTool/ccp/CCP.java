/**
 * @file CCP.java
 * CAN Calibration Protocol for download of binary and flashing. This module implements the
 * needed subset of CCP.
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
 *   CCP
 *   openCanDevice
 *   closeCanDevice
 *   setStateConnecting
 *   setStateDisconnecting
 *   step
 */

package winFlashTool.ccp;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import peak.can.basic.*;

/**
 * State machine for the subset of CCP, whichis require for the flash tool.
 */
public class CCP
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CCP.class);

    /* The error counter to be used for error reporting. */
    final ErrorCounter errCnt_;
    
    /** The states of the processing. */
    public enum StateFlashProcess
    {
        UNDEFINED,
        DISCONNECTED,
        CONNECTING,
        DOWNLOADING,
        DISCONNECTING;

        /** String to enum conversion. Illegal strings are translated into enumeration
            value UNDEFINED. */
        public static StateFlashProcess fromString(String name)
        {
            if(name == null)
                return UNDEFINED;

            try
            {
                return StateFlashProcess.valueOf(name.toUpperCase());
            }
            catch(IllegalArgumentException e)
            {
                return UNDEFINED;
            }
        }
    }

    /** The IDs of the CCP commands in the CRO messages. */
    public enum CroCommandId
    {
        CONNECT((byte)0x01),
        SET_MTA((byte)0x02),
        DISCONNECT((byte)0x07);

        private final byte cmdId_;

        CroCommandId(byte cmdId)
        {
            this.cmdId_ = cmdId;
        }

        public byte getCode()
        {
            return cmdId_;
        }
    }

    /** The current state of the communication process. */
    private StateFlashProcess state_ = StateFlashProcess.UNDEFINED;

    /** The PEAK CAN API; the connection of Java to the external DLL. */
    private PCANBasic canApi_ = null;

    /** The handle of the CAN device or channel, which we are going to use. */
    // TODO Could become an application parameter
    private final TPCANHandle canDev_ = TPCANHandle.PCAN_USBBUS1;

    /** Sub-state machine for transmitting a CRO and waiting for the responding DTO. */
    private final CcpCroTransmitter croTransmitter_;

    /** The station address of the connected ECU. */
    // TODO Needs to become an application parameter
    private final short stationAddr = 0x1234;

    /** A general purpose timer, which is measures timeout conditions in temporary states. */
    private final TimeoutTimer timerTO_;

    /**
     * A new instance of CCP is created. It represents a CCP connection with a ECU.
     *   @param errCnt
     * The error counter to be used for problem reporting.
     */
    public CCP(ErrorCounter errCnt)
    {
        errCnt_ = errCnt;
        state_ = StateFlashProcess.DISCONNECTED;
        timerTO_ = new TimeoutTimer(0);
        
        /* Initialize the API opject, which connects us to the PEAK DLLs. */
        assert canApi_ == null;
        canApi_ = new PCANBasic();
        if(canApi_.initializeAPI())
        {
            _logger.debug("PCANBasic API successfully initialized.");
            croTransmitter_ = new CcpCroTransmitter( canApi_
                                                   , canDev_
                                                   , /*timeoutTillRxDtoInMs*/ 1000 * 20
                                                   , /*canIdCro*/ 100
                                                   , /*isExtCroId*/ false
                                                   , /*canIdDto*/ 101
                                                   , /*isExtDtoId*/ false
                                                   , errCnt
                                                   );
            PCANBasicEx.setCanApi(canApi_);
        }
        else
        {
            errCnt_.error();
            canApi_ = null;
            croTransmitter_ = null;
            _logger.fatal("Unable to initialize the PEAK PCAN Basic API. Most probable"
                          + " reason is the application installation; the PEAK DLLs might"
                          + " be not localized. Check application configuration and"
                          + " Windows search path. Files PCANBasic.dll and"
                          + " PCANBasic_JNI.dll need to be found."
                         );
        }

    } /* CCP.CCP */

    /**
     * Try to connect to a PEAK CAN device.
     */
    public boolean openCanDevice()
    {
        assert state_ == StateFlashProcess.DISCONNECTED;
        boolean success = false;
        
        if(canApi_ != null)
        {
            /* Arguments 3..5 are not used for the Plug&Play device PEAK-USB and
               PEAK-USB-FD. We set them to "don't care". */
            final TPCANStatus errCode = canApi_.Initialize( canDev_
                                                          , TPCANBaudrate.PCAN_BAUD_1M
                                                          , /*HwType*/ TPCANType.PCAN_TYPE_NONE
                                                          , /*IOPort*/ 0
                                                          , /*Interrupt*/ (short)0
                                                          );
            if(PCANBasicEx.checkReturnCode(errCode))
            {
                success = true;
                _logger.debug("PCANBasic device {} successfully initialized.", canDev_);
            }
            else
            {
                errCnt_.error();
                _logger.fatal( "Can't open PEAK PCAN-USB CAN device {}. This application"
                               + " expects a PCAN-USB or PCAN-USB FD device connected to"
                               + " a USB port. The device must not be allocated to another"
                               + " application, e.g., the PCAN Explorer."
                             , canDev_
                             );
            }
        }
        else
        {
            errCnt_.error();
            _logger.fatal( "Can't open PEAK PCAN-USB CAN device {}. The PCANBasic API"
                           + " is not available. See previous error messages for details."
                         , canDev_
                         );
        }
        
        if(success == true)
            setStateConnecting();
        else
        {
            /* We remain in state DISCONNECTED. */
        }

        return success;
        
    } /* openCanDevice */


    /**
     * Close CAN device after use. This operation should release the device such that other
     * applications can acquire and use it, e.g., the PCAN Explorer.
     */
    public boolean closeCanDevice()
    {
        assert state_ == StateFlashProcess.DISCONNECTED;
        boolean success = false;
        final TPCANStatus errCode = canApi_.Uninitialize(canDev_);
        if(PCANBasicEx.checkReturnCode(errCode))
        {
            success = true;
            state_ = StateFlashProcess.DISCONNECTED;
        }
        else
        {
            errCnt_.error();
            _logger.fatal("Can't close PEAK PCAN-USB CAN device.");

            /* We still return to state DISCONNECTED. There's nothing else to do and this
               enables the client code to retry later. */
            state_ = StateFlashProcess.DISCONNECTED;
        }
        return success;

    } /* closeCanDevice */


    /**
     * Enter state connecting.<p>
     *   Execute state entry actions and update the state variable.
     */
    private void setStateConnecting()
    {
        /* On entry: Send CAN CRO message with command CONNECT. */
        final byte[] payloadAry = new byte[8];
        payloadAry[0] = CroCommandId.CONNECT.getCode();
        payloadAry[2] = (byte)(stationAddr & 0x00FF);
        payloadAry[3] = (byte)((stationAddr & 0xFF00) >> 8);
        croTransmitter_.sendCro(payloadAry, /*noContentBytes*/ 4);
        _logger.debug("CRO message CONNECT sent to station {}.", stationAddr);
        state_ = StateFlashProcess.CONNECTING;
    }

    /**
     * Enter state disconnecting.<p>
     *   Execute state entry actions and update the state variable.
     */
    private void setStateDisconnecting()
    {
        /* On entry: Send CAN CRO message with command DISCONNECT. */
        final byte[] payloadAry = new byte[8];
        payloadAry[0] = CroCommandId.DISCONNECT.getCode();
        payloadAry[2] = (byte)0x01; /* 0: temporary, 1: end of session. */
        payloadAry[3] = 0;
        payloadAry[4] = (byte)(stationAddr & 0x00FF);
        payloadAry[5] = (byte)((stationAddr & 0xFF00) >> 8);
        croTransmitter_.sendCro(payloadAry, /*noContentBytes*/ 6);
        _logger.debug("CRO message DISCONNECT sent to station {}.", stationAddr);
        state_ = StateFlashProcess.DISCONNECTING;
    }


    /**
     * Step function of the state machine.<p>
     *   Once the CAN device has been acquired and initialized, the communication with the
     * ECU can begin. The communication, the exchange of request and response CAN messages,
     * is processed in this state machine.<p>
     *   Must be called only after openCanDevice() has been successfully called.
     *   @return
     * Get true if the process has completed, i.e., if the state machine has gone back to
     * DISCONNECTED (either after flashing or because of an error).
     */
    public boolean step()
    {
        switch(state_)
        {
        case CONNECTING:
        {
            final TPCANMsg msgDto = new TPCANMsg();
            final CcpCroTransmitter.ResultTransmission resultTxRx =
                                                            croTransmitter_.getDto(msgDto);
            if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
            {
                _logger.info("ECU is connected.");
                // setStateDownloading();
                state_ = StateFlashProcess.DOWNLOADING;
                timerTO_.restart(/*timeoutMillis*/ 1000*3);
            }
            else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
            {
                /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
                   else to do. We return to DISCONNECTED. */
                errCnt_.error();
                _logger.error("Can't connect to the ECU. See previous error messages for"
                              + " details."
                             );
                state_ = StateFlashProcess.DISCONNECTED;
            }
            else
            {
                /* DTO has not been received yet. We remain in this state. */
            }
            break;
        }
        case DOWNLOADING:
            // TODO This is a dummy. We just wait for a while before we disconnect.
            if(timerTO_.hasTimedOut())
                setStateDisconnecting();
            break;

        case DISCONNECTING:
        {
            final TPCANMsg msgDto = new TPCANMsg();
            final CcpCroTransmitter.ResultTransmission resultTxRx =
                                                            croTransmitter_.getDto(msgDto);
            if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
            {
                _logger.info("ECU is disconnected.");
                state_ = StateFlashProcess.DISCONNECTED;
            }
            else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
            {
                /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
                   else to do. We return to DISCONNECTED. */
                errCnt_.error();
                _logger.error("Can't disconnect from the ECU. See previous error messages"
                              + " for details."
                             );
                state_ = StateFlashProcess.DISCONNECTED;
            }
            else
            {
                /* DTO has not been received yet. We remain in this state. */
            }
            break;
        }

        default:
            assert false;
        }

        return state_ == StateFlashProcess.DISCONNECTED;
    }


    /**
     * Get the current state of the flash process.
     *   @return
     * The state of the state machine.
     */
    public StateFlashProcess getProcessState()
    {
        return state_;
    }

} /* End of class CCP definition. */




