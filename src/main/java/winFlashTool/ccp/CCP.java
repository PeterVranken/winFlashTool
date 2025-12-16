/**
 * @file CCP.java
 * CAN Calibration Protocol for download of binary and flashing. This module implements the
 * needed subset of CCP. The needed processes are decomposed into sequences of required CCP
 * commands and these commands are then sequentially executed. 
 *
 * Neuer Gedanke: Die Programmsequenz ist im Wesentlichen eine Liste von
 * Kommandoprozessoren.\n 
 *   CCP hat als API die Vorgabe der verschiedenen Aufgaben, jeweils mit allen Parametern.
 * Z.B. würde eine API "Erase" nur die von..bis Adressen als Parameter haben und sie würde
 * eine Programmsequenz aus vier Kommandoprozessoren aufbauen, Connect, SetMta, Erase,
 * Disconnect. Die Adressangaben würde zu Parametern von zweien der Prozessoren werden,
 * SetMta bekmmt die Startadresse, Erase die Anzahl Bytes.\n
 *   Weitere APIs dieser Art von CCP wären natürlich Flashen, Hochladen, Auslesen,
 * Verifizieren. Auslesen und Verifizieren, die bzgl. CCP Protokollgeschehen auf dem Bus
 * vielleicht völlig identisch sind, könnten sich in der Art der Parameter der
 * Prozessoren unterscheiden; Auslesen könnte einen Streambuffer bekommen, in den Daten
 * eingeschrieben werden, Verifizieren könnte einen Datensatz bekommen, gegen den
 * verglichen wird.\n
 *   CCP hat dann die Kern-API step, die die ganze Programmsequenz abarbeitet.\n
 *   Evtl. ist Disconnect nicht einfach das letzte Element der Sequenz, sondern hat den
 * besonderen Stellenwert eines "on-error" Zweiges des Programms, der immer ausgeführt
 * wird, auch wenn zuvor ausgeführte Programmschritte einen Fehler gemeldet haben.\n
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
import winFlashTool.can.CanDevice;
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

    /** The 16 Bit station address of the connected ECU. */
    // TODO Needs to become an application parameter
    private final int stationAddr_ = 0x0000;

    /** The currently processed CCP command. */
    CcpCommandBase currentCcpCmd_ = null;

    /** The currently processed CCP command sequence. */
    CcpCmdSequence ccpCmdSequence_ = null;

    /** The CCP command object factory used by this CCP object. */
    final CcpCommandFactory ccpCmdFactory_;

    /* Temporary test code: We generate some random code for flashing. */
    final byte[] progData_;


    /**
     * A new instance of CCP is created. It represents a CCP connection with a ECU.
     *   @param canDev
     * All CAN communication will be done with this CAN device. Pass an already opened and
     * initialized device.
     *   @param canIdCro
     * The CAN ID of the CCP CRO Tx messages.
     *   @param canIdDto
     * The CAN ID of the CCP DTO Rx messages.
     *   @param errCnt
     * The error counter to be used for problem reporting.
     */
    public CCP(CanDevice canDev, CanId canIdCro, CanId canIdDto, ErrorCounter errCnt) {
        errCnt_ = errCnt;
        state_ = StateFlashProcess.DISCONNECTED;
        ccpCmdSequence_ = null;
        
        // TODO CLEAR_MEMORY requires a timeout of at least 10s. All other commands
        // don't. We can add an API to CroTransmitter to temporarily select another
        // timeout.
        final CcpCroTransmitter croTransmitter = new CcpCroTransmitter
                                                        ( canDev
                                                        , /*timeoutTillRxDtoInMs*/ 1000 * 20
                                                        , canIdCro
                                                        , canIdDto
                                                        , errCnt
                                                        );
        final CcpCommandToolbox toolbox = new CcpCommandToolbox(croTransmitter, errCnt);
        ccpCmdFactory_ = new CcpCommandFactory(toolbox);

        /* Temporary test code: We generate some random code for flashing. */
        final int noBytesToProgram = (ThreadLocalRandom.current().nextInt(12, 66) + 7) & ~0x7;
        progData_ = new byte[noBytesToProgram];
        for(int i=0; i<noBytesToProgram; ++i)
            progData_[i] = (byte)ThreadLocalRandom.current().nextInt(0, 256);
        _logger.info("Dummy program assembled with {} Byte", noBytesToProgram);

        /* Initiate the state machine, which steps through the CCP protocol. The next
           command sends the first CRO for session connect. */
        setStateConnecting();

    } /* CCP.CCP */

    /**
     * Enter state connecting.<p>
     *   Execute state entry actions and update the state variable.
     */
    private void setStateConnecting()
    {
        /* On entry: Send CAN CRO message with command CONNECT. */
        final CcpCommandArgs.Connect args = new CcpCommandArgs.Connect(stationAddr_);
        currentCcpCmd_ = ccpCmdFactory_.create(args);
        currentCcpCmd_.start();
        state_ = StateFlashProcess.CONNECTING;
    }

    /**
     * Enter state disconnecting.<p>
     *   Execute state entry actions and update the state variable.
     */
    private void setStateDisconnecting()
    {
        /* On entry: Send CAN CRO message with command DISCONNECT. */
        final CcpCommandArgs.Disconnect args = 
                        new CcpCommandArgs.Disconnect(stationAddr_, /*isEndOfSession*/ true);
        currentCcpCmd_ = ccpCmdFactory_.create(args);
        currentCcpCmd_.start();
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

                final CcpCommandArgs.SetMta args = new CcpCommandArgs.SetMta
                                                                        ( /*address*/ 0xA00000
                                                                        , /*addressExt*/ 0
                                                                        , /*idxMta*/ 0
                                                                        );
                currentCcpCmd_ = ccpCmdFactory_.create(args);
                currentCcpCmd_.start();
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
//                final Integer noBytesToEraseAtMta = Integer.valueOf(progData_.length);
//                final CcpCommandArgs.ClearMemory args = 
//                                        new CcpCommandArgs.ClearMemory(noBytesToEraseAtMta);
//                currentCcpCmd_ = ccpCmdFactory_.create(args);
//                currentCcpCmd_.start();
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
                final CcpCommandArgs.Program args = new CcpCommandArgs.Program(progData_);
                currentCcpCmd_ = ccpCmdFactory_.create(args);
                currentCcpCmd_.start();
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




