/**
 * @file CCP.java
 * CAN Calibration Protocol for download of binary and flashing. This module implements the
 * needed subset of CCP.
 *
 * Redesign concept:<p>
 *   CCP statically initializes the PCANBasic API.<p>
 *   A CCP object creates a CroTransmitter object and owns thus a CAN channel. Maybe, the
 * tranmitter object should have the open/close device logic, as it is the only instance,
 * which uses the CAN channel. (Downside could be that the error context and the logged
 * feedback is poor.)<p>
 *   A CCP object has a command sequence. It opens the CAN device, or uses the
 * CroTransmitter object to do so, processes all commands and closes the CAN device.<p>
 *   Processing a command, means fetching the command implementation object, initialize it
 * with the arguments of the command taken from the command sequence and run the command
 * implementation object's step function until it signals completion.<p>
 *   The CroTransmitter is the major element of the context, all the command implementation
 * object use to communicate via the right channel.<p>
 *   Fetching the command implementation object can mean a constructor call every time or
 * some pooling can be applied; one and the same object can (as it is now) be
 * re-initialized and used repeatedly if the same command appears more than once in the
 * sequence. The sequences don't have many entries, so optimization by pooling has no
 * particular advantage.<p>
 *   Elegant processing of the sequence requires simple decision, which command
 * implementation object is required. The reference to the constructor could become element
 * of the command in the sequence. Or a map is applied, which relates a command to the
 * right constructor.<p>
 *   The current association of CRO command IDs to command implementation object is
 * improper, as a single implementation object can handle more than one CRO command ID.
 * Moreover, the implementation object doesn't necessarily processes a single CRO/DTO.
 * "Program" for example will send many CRO commands PROGRAM in order to program a byte
 * sequence of any length. Consequently, we need to have two enumerations: CRO command ID
 * just for the CroTransmitter and something new like CcpCommandId for the use in the
 * command sequence.
 *
 * Copilot proposes this code for mapping a command implementation object constructor with
 * a byte encoded command ID:<p>
 *   public class CcpCommandRegistry {
 *       private final Map<Byte, Function<CcpContext, CcpCommandProcessor>> registry = new HashMap<>();
 *   
 *       public CcpCommandRegistry() {
 *           register((byte) 0x01, ConnectCommand::new);
 *           // Add more registrations here
 *       }
 *   
 *       public void register(byte commandId, Function<CcpContext, CcpCommandProcessor> constructor) {
 *           registry.put(commandId, constructor);
 *       }
 *   
 *       public CcpCommandProcessor createProcessor(byte commandId, CcpContext context) {
 *           Function<CcpContext, CcpCommandProcessor> constructor = registry.get(commandId);
 *           if (constructor == null) {
 *               throw new IllegalArgumentException("Unknown command ID: " + commandId);
 *           }
 *           return constructor.apply(context);
 *       }
 *   }
 * where "ConnectCommand::new" is equivalent to "(context) -> new ConnectCommand(context)".
 * Particularly the latter notation makes apparent that the constructor of any of the
 * command implementation object takes the "context" as any argument. The context would be
 * our CAN channel, represented by the CroTransmitter in use, among more.<p>
 *   If we don't use the lambda expression then we will likely end up with a switch case
 * along all command IDs - which has the advantage of allowing individual argument lists
 * for the constructors. (At the moment, the shared implementation of DOWNLOAD and PROGRAM
 * still has the additional argument isDownload.) This traditional way is easy to combine
 * with pooling (call constructor only if field with command implementation object is still
 * null). The objects would be owned by the CCP object, because it needs them to process
 * the command sequence.
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
import java.util.concurrent.ThreadLocalRandom;
import winFlashTool.can.CanId;
import winFlashTool.can.PCANBasicEx;

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
        SETTING_MTA,
        ERASING,
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

    /** The current state of the communication process. */
    private StateFlashProcess state_ = StateFlashProcess.UNDEFINED;

//    /** The PEAK CAN API; the connection of Java to the external DLL. */
//    private PCANBasic pcanApi_ = null;

    /** The handle of the CAN device or channel, which we are going to use. */
    // TODO Could become an application parameter
    private final TPCANHandle canDev_ = TPCANHandle.PCAN_USBBUS1;

    /** The 16 Bit station address of the connected ECU. */
    // TODO Needs to become an application parameter
    private final short stationAddr_ = 0x0000;

    /** The currently processed CCP command. */
    CcpCommandBase currentCcpCmd_ = null;
    
//    /** The CAN IDs of the CCP connection. */
//    final CcpCanIds ccpCanIds_;

    /* Temporary test code: We generate some random code for flashing. */
    final byte[] progData_;
    
    
    /**
     * A new instance of CCP is created. It represents a CCP connection with a ECU.
     *   @param canIdCro
     * The CAN ID of the CCP CRO Tx messages.
     *   @param canIdDto
     * The CAN ID of the CCP DTO Rx messages.
     *   @param errCnt
     * The error counter to be used for problem reporting.
     */
    public CCP( CanId canIdCro
              , CanId canIdDto
              , ErrorCounter errCnt
              )
    {
        errCnt_ = errCnt;
        state_ = StateFlashProcess.DISCONNECTED;
        
        /* In this project, we just have a single, global error counter, which is shared
           with all modules. */
        CcpCommandBase.setErrorCounter(errCnt);

        // TODO CLEAR_MEMORY requires a timeout of at least 10s. All other commands
        // don't. We can add an API to CroTransmitter to temporarily select another
        // timeout.
        CcpCroTransmitter.CreateCcpCroTransmitter( PCANBasicEx.getPcanBasicApi()
                                                 , canDev_
                                                 , /*timeoutTillRxDtoInMs*/ 1000 * 20
                                                 , canIdCro
                                                 , canIdDto
                                                 , errCnt
                                                 );

        /* Temporary test code: We generate some random code for flashing. */
        final int noBytesToProgram = (ThreadLocalRandom.current().nextInt(12, 66) + 7) & ~0x7;
        progData_ = new byte[noBytesToProgram];
        for(int i=0; i<noBytesToProgram; ++i)
            progData_[i] = (byte)ThreadLocalRandom.current().nextInt(0, 256);

    } /* CCP.CCP */

//    /**
//     * A new instance of CCP is created. It represents a CCP connection with a ECU.
//     *   @param errCnt
//     * The error counter to be used for problem reporting.
//     */
//    public CCP(ErrorCounter errCnt)
//    {
//        errCnt_ = errCnt;
//        state_ = StateFlashProcess.DISCONNECTED;
//        
//        /* In this project, we just have a single, global error counter, which is shared
//           with all modules. */
//        CcpCommandBase.setErrorCounter(errCnt);
//
//        /* Set and remind the CN Ids to use for CCP communication. */
//        ccpCanIds_ = new CcpCanIds( /*canIdCro*/ 100
//                                  , /*isExtCroId*/ false
//                                  , /*canIdDto*/ 101
//                                  , /*isExtDtoId*/ false
//                                  );
//
//        /* Initialize the API opject, which connects us to the PEAK DLLs. */
//        assert canApi_ == null;
//        canApi_ = new PCANBasic();
//        if(canApi_.initializeAPI())
//        {
//            _logger.debug("PCANBasic API successfully initialized.");
//            
//            /* Print all connected devices. */
//            // TODO Make this an option. Could be done on log level DEBUG always
//            // TODO The function doesn't have proper error handling yet
//            PCANBasicEx.printAttachedChannels(canApi_);
//         
//            // TODO CLEAR_MEMORY requires a timeout of at least 10s. All other commands
//            // don't. We can add an API to CroTransmitter to temporarily select another
//            // timeout.
//            CcpCroTransmitter.CreateCcpCroTransmitter( canApi_
//                                                     , canDev_
//                                                     , /*timeoutTillRxDtoInMs*/ 1000 * 20
//                                                     , ccpCanIds_
//                                                     , errCnt
//                                                     );
//            PCANBasicEx.setCanApi(canApi_);
//        }
//        else
//        {
//            errCnt_.error();
//            canApi_ = null;
//            _logger.fatal("Unable to initialize the PEAK PCAN Basic API. Most probable"
//                          + " reason is the application installation; the PEAK DLLs might"
//                          + " be not localized. Check application configuration and"
//                          + " Windows search path. Files PCANBasic.dll and"
//                          + " PCANBasic_JNI.dll need to be found."
//                         );
//        }
//
//        /* Temporary test code: We generate some random code for flashing. */
//        final int noBytesToProgram = (ThreadLocalRandom.current().nextInt(12, 66) + 7) & ~0x7;
//        progData_ = new byte[noBytesToProgram];
//        for(int i=0; i<noBytesToProgram; ++i)
//            progData_[i] = (byte)ThreadLocalRandom.current().nextInt(0, 256);
//
//    } /* CCP.CCP */
//
//    /**
//     * Try to connect to a PEAK CAN device.
//     */
//    public boolean openCanDevice()
//    {
//        assert state_ == StateFlashProcess.DISCONNECTED;
//        boolean success = true;
//        
//        if(canApi_ != null)
//        {
//            /* Arguments 3..5 are not used for the Plug&Play device PEAK-USB and
//               PEAK-USB-FD. We set them to "don't care". */
//            final TPCANStatus errCode = canApi_.Initialize( canDev_
//                                                          , TPCANBaudrate.PCAN_BAUD_500K
//                                                          , /*HwType*/ TPCANType.PCAN_TYPE_NONE
//                                                          , /*IOPort*/ 0
//                                                          , /*Interrupt*/ (short)0
//                                                          );
//            if(PCANBasicEx.checkReturnCode(errCode))
//                _logger.debug("PCANBasic device {} successfully initialized.", canDev_);
//            else
//            {
//                success = false;
//                errCnt_.error();
//                _logger.fatal( "Can't open PEAK PCAN-USB CAN device {}. This application"
//                               + " expects a PCAN-USB or PCAN-USB FD device connected to"
//                               + " a USB port. The device must not be allocated to another"
//                               + " application, e.g., the PCAN Explorer."
//                             , canDev_
//                             );
//            }
//        }
//        else
//        {
//            success = false;
//            errCnt_.error();
//            _logger.fatal( "Can't open PEAK PCAN-USB CAN device {}. The PCANBasic API"
//                           + " is not available. See previous error messages for details."
//                         , canDev_
//                         );
//        }
//        
//        /* Set the CAN acceptance filter; this application just wants to receive the CAN ID
//           of the CCP DTO message. */
//        if(success == true)
//        {
//            final TPCANStatus errCode = canApi_.FilterMessages
//                                                        ( canDev_
//                                                        , /*FromID*/ ccpCanIds_.canIdDto_
//                                                        , /*ToID*/ ccpCanIds_.canIdDto_
//                                                        , /*Mode*/ ccpCanIds_.getDtoMsgMode()
//                                                        );
//            if(PCANBasicEx.checkReturnCode(errCode))
//            {
//                _logger.debug( "CAN acceptance filter for ID {} configured for Rx DTO"
//                               + " messages."
//                             , ccpCanIds_.dtoIdToString()
//                             );
//            }
//            else
//            {
//                success = false;
//                errCnt_.error();
//                _logger.fatal( "Configuring the CAN acceptance filter for PEAK PCAN-USB"
//                               + " CAN device {} failed."
//                             , canDev_
//                             );
//            }
//        }
//        
//        if(success == true)
//            setStateConnecting();
//        else
//        {
//            /* We remain in state DISCONNECTED. */
//        }
//
//        return success;
//        
//    } /* openCanDevice */
//
//
//    /**
//     * Close CAN device after use. This operation should release the device such that other
//     * applications can acquire and use it, e.g., the PCAN Explorer.
//     */
//    public boolean closeCanDevice()
//    {
//        assert state_ == StateFlashProcess.DISCONNECTED;
//        boolean success = false;
//        final TPCANStatus errCode = canApi_.Uninitialize(canDev_);
//        if(PCANBasicEx.checkReturnCode(errCode))
//        {
//            success = true;
//            state_ = StateFlashProcess.DISCONNECTED;
//        }
//        else
//        {
//            errCnt_.error();
//            _logger.fatal("Can't close PEAK PCAN-USB CAN device.");
//
//            /* We still return to state DISCONNECTED. There's nothing else to do and this
//               enables the client code to retry later. */
//            state_ = StateFlashProcess.DISCONNECTED;
//        }
//        return success;
//
//    } /* closeCanDevice */


    /**
     * Enter state connecting.<p>
     *   Execute state entry actions and update the state variable.
     */
    private void setStateConnecting()
    {
        /* On entry: Send CAN CRO message with command CONNECT. */
        currentCcpCmd_ = CroCommandId.CONNECT.getCmd();
        currentCcpCmd_.start(Integer.valueOf(stationAddr_));
        state_ = StateFlashProcess.CONNECTING;
    }

    /**
     * Enter state disconnecting.<p>
     *   Execute state entry actions and update the state variable.
     */
    private void setStateDisconnecting()
    {
        /* On entry: Send CAN CRO message with command DISCONNECT. */
        currentCcpCmd_ = CroCommandId.DISCONNECT.getCmd();
        final Integer stationAddr = Integer.valueOf(stationAddr_);
        final Boolean isEndOfSession = Boolean.valueOf(true);
        currentCcpCmd_.start(stationAddr, isEndOfSession);
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
        CcpCroTransmitter.ResultTransmission resultTxRx;
        switch(state_)
        {
        case CONNECTING:
            resultTxRx = currentCcpCmd_.step();
            if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
            {
                state_ = StateFlashProcess.SETTING_MTA;
                currentCcpCmd_ = CroCommandId.SET_MTA.getCmd();
                final Integer memoryAddr = Integer.valueOf(0xA00000);
                final Integer idxMta = Integer.valueOf(0);
                currentCcpCmd_.start(memoryAddr, idxMta);
            }
            else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
            {
                /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
                   else to do. We return to DISCONNECTED. */
                state_ = StateFlashProcess.DISCONNECTED;
            }
            else
            {
                /* DTO has not been received yet. We remain in this state. */
            }
            break;
        
        case SETTING_MTA:
            resultTxRx = currentCcpCmd_.step();
            if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
            {
_logger.warn("Test: Disconnect immediately after connect");
setStateDisconnecting();
//                currentCcpCmd_ = CroCommandId.CLEAR_MEMORY.getCmd();
//                final Integer noBytesToEraseAtMta = Integer.valueOf(progData_.length);
//                currentCcpCmd_.start(noBytesToEraseAtMta);
//
//                state_ = StateFlashProcess.ERASING;
            }
            else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
            {
                /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
                   else to do. We return to DISCONNECTED. */
                state_ = StateFlashProcess.DISCONNECTED;
            }
            else
            {
                /* DTO has not been received yet. We remain in this state. */
            }
            break;
        
        case ERASING:
            resultTxRx = currentCcpCmd_.step();
            if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
            {
                currentCcpCmd_ = CroCommandId.PROGRAM.getCmd();
                currentCcpCmd_.start(progData_);

                state_ = StateFlashProcess.DOWNLOADING;
            }
            else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
            {
                /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
                   else to do. We return to DISCONNECTED. */
                state_ = StateFlashProcess.DISCONNECTED;
            }
            else
            {
                /* DTO has not been received yet. We remain in this state. */
            }
            break;
        
        case DOWNLOADING:
            resultTxRx = currentCcpCmd_.step();
            if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
            {
                setStateDisconnecting();
            }
            else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
            {
                /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
                   else to do. We return to DISCONNECTED. */
                state_ = StateFlashProcess.DISCONNECTED;
            }
            else
            {
                /* DTO has not been received yet. We remain in this state. */
            }
        
            break;
        
        case DISCONNECTING:
        {
            resultTxRx = currentCcpCmd_.step();
            if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
            {
                state_ = StateFlashProcess.DISCONNECTED;
            }
            else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
            {
                /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
                   else to do. We return to DISCONNECTED. */
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




