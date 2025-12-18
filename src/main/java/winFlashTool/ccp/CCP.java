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
 *   stateConnectToTarget
 *   stateDisconnectFromTarget
 *   stateProcessCcpCmdSequence
 *   step
 */

package winFlashTool.ccp;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.can.CanId;
import winFlashTool.can.CanDevice;
import winFlashTool.can.PCANBasicEx;
import winFlashTool.srecParser.MemoryMap;

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
        START,
        CONNECTING,
        WAITING_FOR_RECONNECT,
        COMMUNICATING_WITH_TARGET,
        DISCONNECTING,
        COMPLETED;

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
        } /* fromString */
    } /* enum StateFlashProcess */

    /** The current state of the communication process. */
    private StateFlashProcess state_ = StateFlashProcess.UNDEFINED;

    /** A general purpose counter variable for counting states. The meaning of the counter
        depends on the state, which applies it. */
    private int cntState_;

    /** The 16 Bit station address of the connected ECU. */
    private final int stationAddr_;

    /** The maximum number of retries for CCP CONNECT. */
    private static final int MAX_NO_RETRIES_CONNECT = 10;

    /** The time between two CCP CONNECT attempty in Milliseconds. */
    private static final int TIME_BETWEEN_CONNECT_IN_MS = 1000;

    /* If true, then most CCP commands are not really executed; the CRO is not sent out and
       we don't wait for a DTO. */
    boolean isDryRun_;
     
    /** If CCP command CONNECT fails, it can be repeated a number of time, with a second of
        pause in between. This is the demanded number of attempts. Value 1 would mean no
        retry. */
    private final int noAttemptsToConnect_;

    /** The down-counter of CONNECT attempts. */
    private int cntAttemptsToConnect_;

    /** The currently processed CCP command sequence. */
    CcpCmdSequence ccpCmdSequence_;

    /** The currently processed CCP command. */
    CcpCommandBase currentCcpCmd_;

    /** A general purpose timeout counter to measure some state dependent time span. */
    private TimeoutTimer timerState_;

    /** The CCP command object factory used by this CCP object. */
    final CcpCommandFactory ccpCmdFactory_;

    /**
     * A new instance of CCP is created. It represents a CCP connection with a ECU.
     *   @param canDev
     * All CAN communication will be done with this CAN device. Pass an already opened and
     * initialized device.
     *   @param canIdCro
     * The CAN ID of the CCP CRO Tx messages.
     *   @param canIdDto
     * The CAN ID of the CCP DTO Rx messages.
     *   @param stationAddr
     * The 16 Bit station address of the connected ECU. If the supplied value exceeds the
     * 16 Bit range, then the more significant bits are ignored.
     *   @param noRetriesConnect
     * If CCP command CONNECT fails, it can be repeated a number of time, with a second of
     * pause in between. This is the demanded number of retries. Range is
     * 0..MAX_NO_RETRIES_CONNECT.
     *   @param errCnt
     * The error counter to be used for problem reporting.
     */
    public CCP( CanDevice canDev
              , CanId canIdCro
              , CanId canIdDto
              , int stationAddr
              , int noRetriesConnect
              , ErrorCounter errCnt ) {
        stationAddr_ = stationAddr & 0xFFFF;

        if (noRetriesConnect < 0  ||  noRetriesConnect > MAX_NO_RETRIES_CONNECT) {
            if (noRetriesConnect < 0) {
                noRetriesConnect = 0;
            } else {
                noRetriesConnect = MAX_NO_RETRIES_CONNECT;
            }
            errCnt.warning();
            _logger.warn( "The number of retries of the CCP command CONNECT is out of the"
                          + " permitted range [0, {}]. The value has been corrected to {}."
                        , MAX_NO_RETRIES_CONNECT
                        , noRetriesConnect
                        );
        }
        noAttemptsToConnect_ = noRetriesConnect + 1;
        cntAttemptsToConnect_ = 0;

        errCnt_ = errCnt;
        state_ = StateFlashProcess.COMPLETED;
        cntState_ = 0;
        ccpCmdSequence_ = null;
        currentCcpCmd_ = null;
        timerState_ = null;

        final CcpCroTransmitter croTransmitter = new CcpCroTransmitter( canDev
                                                                      , canIdCro
                                                                      , canIdDto
                                                                      , errCnt
                                                                      );
        final CcpCommandToolbox toolbox = new CcpCommandToolbox(croTransmitter, errCnt);
        ccpCmdFactory_ = new CcpCommandFactory(toolbox);

    } /* CCP.CCP */

    /**
     * Test the application configuration and the current CCP command to see if the command
     * should be executed or; if we are in "dry run" then it may be required to skipp the
     * command.
     */
    private boolean executeCcpCmd() {
        return !isDryRun_ || !currentCcpCmd_.isSkippedInDryRun();
    }

    /**
     * Initiate the CCP protocol sequence, which is required to programm a new binary into
     * the target.
     *   @param program
     * The representation of the memory area(s) to erase and (re-)program.
     *   @param isDryRun
     * If true, then most CCP commands are not really executed; the CRO is not sent out and
     * we don't wait for a DTO. The success of the suppressed CCP is assumed true. However,
     * the complete state machine is stepped through.
     */
    public void eraseAndProgram(MemoryMap program, boolean isDryRun) {
        assert ccpCmdSequence_ == null  &&  state_ == StateFlashProcess.COMPLETED
             : "Can't start a new CCP communication if there is still one running";
        isDryRun_ = isDryRun;
        ccpCmdSequence_ = new CcpCmdSequence(ccpCmdFactory_);
        ccpCmdSequence_.eraseAndProgram(program);
        state_ = StateFlashProcess.START;
    }

    /**
     * This function implements the activities while we are in state CONNECTING.<p>
     *   The CCP CONNECT command is sent once or repeatedly, until we get a valid response
     * or the number of allowed retries is exhausted.<p>
     *   The success and error conditions are directly evaluated and the next state is
     * accordingly set by side-effect.
     */
    private void stateConnectToTarget() {

        /* Do we require a new CCP CONNECT command? This will happen at the beginning and
           on every retry. */
        if (currentCcpCmd_ == null) {
            assert cntAttemptsToConnect_ > 0;
            -- cntAttemptsToConnect_;

            final CcpCommandArgs.Connect args = new CcpCommandArgs.Connect(stationAddr_);
            currentCcpCmd_ = ccpCmdFactory_.create(args);
            assert executeCcpCmd(): "CONNECT is assumed to be always executed";
            _logger.debug("Next executed CCP command: {}", currentCcpCmd_);
            currentCcpCmd_.start();
        }

        final CcpCroTransmitter.ResultTransmission resultTxRx = currentCcpCmd_.step();
        if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
        {
            currentCcpCmd_ = null;
            state_ = StateFlashProcess.COMMUNICATING_WITH_TARGET;
        }
        else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
        {
            /* The connect CRO/DTO exchange failed. The reason has been logged. We can
               retry after a while. */
            currentCcpCmd_ = null;
            if (cntAttemptsToConnect_ > 0) {
                timerState_ = new TimeoutTimer(TIME_BETWEEN_CONNECT_IN_MS);
                _logger.info( "CCP CONNECT failed. Waiting {} ms until next attempt to"
                              + " connect."
                            , TIME_BETWEEN_CONNECT_IN_MS
                            );
                state_ = StateFlashProcess.WAITING_FOR_RECONNECT;
            } else {
                /* All retries are exhausted. Nothing else to do. */
                state_ = StateFlashProcess.COMPLETED;
            }
        }
        else
        {
            /* DTO has not been received yet. We remain in this state. */
        }
    } /* stateConnectToTarget */

    /**
     * This function implements the activities while we are in state DISCONNECTING.<p>
     *   The CCP DISCONNECT command is sent to terminate the session.<p>
     *   The success and error conditions are directly evaluated and the next state is
     * accordingly set by side-effect.
     */
    private void stateDisconnectFromTarget() {

        /* Do we require a new CCP CONNECT command? This will happen once on entry into
           state DISCONNECTING. */
        if (currentCcpCmd_ == null) {
            final CcpCommandArgs.Disconnect args = 
                                        new CcpCommandArgs.Disconnect( stationAddr_
                                                                     , /*isEndOfSession*/ true
                                                                     );
            currentCcpCmd_ = ccpCmdFactory_.create(args);
            assert executeCcpCmd(): "DISCONNECT is assumed to be always executed";
            _logger.debug("Next executed CCP command: {}", currentCcpCmd_);
            currentCcpCmd_.start();
        }

        final CcpCroTransmitter.ResultTransmission resultTxRx = currentCcpCmd_.step();
        if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING) {
            /* Successful termination or error doesn't make a difference any more. We did
               all we can do. */
            currentCcpCmd_ = null;
            ccpCmdSequence_ = null;
            state_ = StateFlashProcess.COMPLETED;
        } else {
            /* DTO has not been received yet. We remain in this state. */
        }
    } /* stateDisconnectFromTarget */


    /**
     * This function implements the ongoing communication with the target.<p>
     *   All CCP commands in the CCP protocol sequence are executed.<p>
     *   The success and error conditions are directly evaluated and the next state is
     * accordingly set by side-effect.
     */
    private void stateProcessCcpCmdSequence() {

        /* Do we require a new CCP command? This will happen every time after a new element
           from the sequence has completed. */
        if (currentCcpCmd_ == null) {
            assert ccpCmdSequence_.size() > 0;
            // TODO Consider using a list with pop instead of always get+remove.
            currentCcpCmd_ = ccpCmdSequence_.get(0);
            ccpCmdSequence_.remove(0);

            if (executeCcpCmd()) {
                _logger.debug("Next executed CCP command: {}", currentCcpCmd_);
                currentCcpCmd_.start();
            } else {
                _logger.info("Dry run: CCP command {} is skipped.", currentCcpCmd_);
            }
        }

        final CcpCroTransmitter.ResultTransmission resultTxRx;
        if (executeCcpCmd()) {
            resultTxRx = currentCcpCmd_.step();
        } else {
            resultTxRx = CcpCroTransmitter.ResultTransmission.SUCCESS;
        }
            
        if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
        {
            currentCcpCmd_ = null;
            if (ccpCmdSequence_.size() > 0) {
                /* There is still another CCP command to process, no state change. */
            } else {
                state_ = StateFlashProcess.DISCONNECTING;
            }
        }
        else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
        {
            /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
               else to do. */
            currentCcpCmd_ = null;
            assert errCnt_.getNoErrors() > 0;
            errCnt_.warning();
            _logger.warn("Prematurely disconnecting after previous errors.");
            state_ = StateFlashProcess.DISCONNECTING;
        }
        else
        {
            /* DTO has not been received yet. We remain in this state. */
        }
    } /* stateProcessCcpCmdSequence */


    /**
     * Step function of the CCP protocol state machine.<p>
     *   The communication, the exchange of request and response CAN messages,
     * is processed in this state machine.<p>
     *   Once a CCP protocol sequence has been initiated (see, e.g., eraseAndProgram()),
     * this function needs to be called regularly until it reports completion of the CCP
     * protocol sequence.<p>
     *   Must be called only after eraseAndProgram() has been successfully called.
     *   @return
     * Get true if the process has completed, i.e., if the state machine has reached
     * COMPLETED (either after successful completion of the configured CCP protocol
     * sequence or because of an error).
     */
    public boolean step()
    {
        assert ccpCmdSequence_ != null  ||  state_ == StateFlashProcess.COMPLETED;
        CcpCroTransmitter.ResultTransmission resultTxRx;
        switch(state_)
        {
        case START:
            assert currentCcpCmd_ == null;
            cntAttemptsToConnect_ = noAttemptsToConnect_;
            state_ = StateFlashProcess.CONNECTING;
            break;

        case CONNECTING:
            /* The state function will itself set the next state, depending on what
               happens. Here, we just have to call it regularly. */
            stateConnectToTarget();
            break;

        case WAITING_FOR_RECONNECT:
            assert cntAttemptsToConnect_ > 0;
            if (timerState_.hasTimedOut()) {
                state_ = StateFlashProcess.CONNECTING;
            }
            break;

        case COMMUNICATING_WITH_TARGET:
            /* The state function will itself set the next state, depending on what
               happens. Here, we just have to call it regularly. */
            stateProcessCcpCmdSequence();
            break;

        case DISCONNECTING:
            /* The state function will itself set the next state, depending on what
               happens. Here, we just have to call it regularly. */
            stateDisconnectFromTarget();
            break;
            
        case COMPLETED:
            return true;
            
        } /* switch(state) */
        
        return false;
        
    } /* step */

} /* End of class CCP definition. */




