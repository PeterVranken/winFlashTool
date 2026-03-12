/**
 * @file CCP.java
 * CAN Calibration Protocol for download of binary and flashing. This module implements the
 * needed subset of CCP. The needed processes are decomposed into sequences of required CCP
 * commands and these commands are then sequentially executed.
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
/* Interface of class CCP
 *   CCP
 *   erase
 *   eraseAndProgram
 *   verify
 *   uploadVersionFbl
 *   upload
 *   resetTarget
 *   stateConnectToTarget
 *   stateDisconnectFromTarget
 *   stateProcessCcpCmdSequence
 *   step
 *   run
 *   getFinalSuccess
 */

package winFlashTool.ccp;

import java.util.*;
import java.util.function.Supplier;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.can.CanId;
import winFlashTool.can.CanDevice;
import winFlashTool.can.PCANBasicEx;
import winFlashTool.srecParser.SRecord;
import winFlashTool.srecParser.MemoryMap;
import winFlashTool.srecParser.EraseSectorSequence;
import winFlashTool.digitalSignature.DigitalSignature;

/**
 * State machine for the subset of CCP, whichis require for the flash tool.
 */
public class CCP {

    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CCP.class);

    /* The error counter to be used for error reporting. */
    final ErrorCounter errCnt_;

    /** The states of the processing. */
    public enum StateFlashProcess {
        UNDEFINED,
        START,
        CONNECTING,
        WAITING_FOR_RECONNECT,
        COMMUNICATING_WITH_TARGET,
        DISCONNECTING,
        DISCONNECTING_AFTER_ERROR,
        COMPLETED,
        COMPLETED_WITH_ERRORS;

        /** String to enum conversion. Illegal strings are translated into enumeration
            value UNDEFINED. */
        public static StateFlashProcess fromString(String name) {
            if (name == null) {
                return UNDEFINED;
            }
            try {
                return StateFlashProcess.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
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
    private static final int MAX_NO_RETRIES_CONNECT = 1000;

    /** The time between two CCP CONNECT attempty in Milliseconds. */
    private static final int TIME_BETWEEN_CONNECT_IN_MS = 5;

    /* If true, then most CCP commands are not really executed; the CRO is not sent out and
       we don't wait for a DTO. */
    private boolean isDryRun_;
    
    /** Object to calculate the digital signature for the target provided seed, or null if
        no authentication is required. */
    private DigitalSignature digitalSignature_;
    
    /** If CCP command CONNECT fails, it can be repeated a number of time, with a second of
        pause in between. This is the demanded number of attempts. Value 1 would mean no
        retry. */
    private final int noAttemptsToConnect_;

    /** The down-counter of CONNECT attempts. */
    private int cntAttemptsToConnect_;

    /** We can ignore CAN errors when checking for the reception of the DTO message for a
        CCP CONNECT command. If this flag is set and if sending the CONNECT CRO leads to an
        acknowledge error, we don't abort the communication but let the CAN transceiver
        re-transmit the CRO message at maximum frequency. This mode is most useful when
        connecting with a target, where the FBL becomes active on reset of the ECU but
        checks the CAN bus for CCP requests only shortly. Using this mode, we can start the
        flash tool and reset the target, while the flash tool is in ACK error and permanent
        re-transmission. The FBL will safely catch one of the re-transmissions and accept
        the connection. Note, this requires that there is no other CAN node connected to
        the bus. */
    private boolean ignoreCanErrsDuringConnect_;
    
    /** The minimum timeout from CRO till DTO in case a CONNECT is sent and acknowledge
        errors should be ignored. Unit is Millisecond. */
    private int timeoutCroToDtoWhenIgnoreCanErrs_;
    
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
     *  @param digitalSignature
     * The object used for calculating the digital signature for authentication. Can be
     * null if no authetication is required for the FBL in the connected target.
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
              , DigitalSignature digitalSignature
              , int noRetriesConnect
              , boolean ignoreCanErrsDuringConnect
              , ErrorCounter errCnt 
              ) {
        stationAddr_ = stationAddr & 0xFFFF;
        digitalSignature_ = digitalSignature;
        
        /* Different retries with a short pause in between make little sense when we use
           the mode with ignoring CAN Ack errors while waiting for the target being reset.
           Now, it's more to the point to have a single attempt with very long timeout. We
           (ab)use the command line option to set the number of attempts for controlling
           the length of the timeout. */
        timeoutCroToDtoWhenIgnoreCanErrs_ = (noRetriesConnect+1) * 1000;
        ignoreCanErrsDuringConnect_ = ignoreCanErrsDuringConnect;
        if (ignoreCanErrsDuringConnect) {
            noRetriesConnect = 0;
        }
        
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
     * Initiate the CCP protocol sequence, which erases the flash ROM in the target.
     *   @param eraseSectorSequence
     * A list of flash blocks to erase.
     *   @param isDryRun
     * If true, then most CCP commands are not really executed; the CRO is not sent out and
     * we don't wait for a DTO. The success of the suppressed CCP command is assumed true.
     * However, the complete state machine is stepped through.
     */
    public void erase(EraseSectorSequence eraseSectorSequence, boolean isDryRun) {
        assert ccpCmdSequence_ == null  &&  state_ == StateFlashProcess.COMPLETED
             : "Can't start a new CCP communication if there is still one running";
        isDryRun_ = isDryRun;

        ccpCmdSequence_ = new CcpCmdSequence(ccpCmdFactory_, digitalSignature_);
        ccpCmdSequence_.erase(eraseSectorSequence);
        state_ = StateFlashProcess.START;

    } /* erase */

    /**
     * Initiate the CCP protocol sequence, which is required to programm a new binary into
     * the target.
     *   @param program
     * The representation of the memory area(s) to erase and (re-)program.
     *   @param eraseAll
     * Set this switch to true to let the FBL erase all managed flash ROM, not only the
     * portions needed to house the program.
     *   @param doVerify
     * If true, then the programming is followed by an upload for verification of the
     * programmed data. The execution time of the CCP protocol sequence is roughly doubled.
     *   @param isDryRun
     * If true, then most CCP commands are not really executed; the CRO is not sent out and
     * we don't wait for a DTO. The success of the suppressed CCP command is assumed true.
     * However, the complete state machine is stepped through.
     */
    public void eraseAndProgram( MemoryMap program
                               , boolean eraseAll
                               , boolean doVerify
                               , boolean isDryRun
                               ) {
        assert ccpCmdSequence_ == null  &&  state_ == StateFlashProcess.COMPLETED
             : "Can't start a new CCP communication if there is still one running";
        isDryRun_ = isDryRun;
        ccpCmdSequence_ = new CcpCmdSequence(ccpCmdFactory_, digitalSignature_);
        ccpCmdSequence_.eraseProgramAndVerify( /*doErase*/ true
                                             , eraseAll
                                             , /*doProgram*/ true
                                             , doVerify
                                             , program
                                             );
        state_ = StateFlashProcess.START;

    } /* eraseAndProgram */

    /**
     * Initiate the CCP protocol sequence, which verifies by upload and data compare, if
     * the target contains a given programm.
     *   @param program
     * The representation of the memory area(s), which are expected to be found in the
     * target.
     *   @param isDryRun
     * If true, then most CCP commands are not really executed; the CRO is not sent out and
     * we don't wait for a DTO. The success of the suppressed CCP command is assumed true.
     * However, the complete state machine is stepped through.
     */
    public void verify(MemoryMap program, boolean isDryRun) {
        assert ccpCmdSequence_ == null  &&  state_ == StateFlashProcess.COMPLETED
             : "Can't start a new CCP communication if there is still one running";
        isDryRun_ = isDryRun;
        ccpCmdSequence_ = new CcpCmdSequence(ccpCmdFactory_, digitalSignature_);
        ccpCmdSequence_.eraseProgramAndVerify( /*doErase*/ false
                                             , /*eraseAll*/ false
                                             , /*doProgram*/ false
                                             , /*doVerify*/ true
                                             , program
                                             );
        state_ = StateFlashProcess.START;

    } /* verify */

    /**
     * Add the CCP command sequence needed for uploading the version of the FBL on the
     * target ECU.<p>
     *   Note, this service is FBL specific and not working in general. It assumes that CCP
     * DIAG_SERVICE with service number 0 will make the FBL provide its version
     * designation.
     *   @return
     * Get a String supplier, which will deliver the version information after completion
     * of the command sequence. The delivered string is empty if the CCP communication
     * fails.<p>
     *   The supplier must not be used before the command sequence has completed, otherwise
     * the result is undefined.
     *   @param isDryRun
     * If true, then most CCP commands are not really executed; the CRO is not sent out and
     * we don't wait for a DTO. The success of the suppressed CCP command is assumed true.
     * However, the complete state machine is stepped through.
     */
    public Supplier<String> uploadVersionFbl(boolean isDryRun) {
        assert ccpCmdSequence_ == null  &&  state_ == StateFlashProcess.COMPLETED
             : "Can't start a new CCP communication if there is still one running";
        isDryRun_ = isDryRun;
        state_ = StateFlashProcess.START;
        ccpCmdSequence_ = new CcpCmdSequence(ccpCmdFactory_, digitalSignature_);
        final Supplier<String> supplierVersionInfo = ccpCmdSequence_.diagServiceGetVersion();
        return supplierVersionInfo;

    } /* uploadVersionFbl */

    /**
     * Add the CCP command sequence needed for uploading data from the flash.
     *   @param memAreas
     * The representation of the memory area(s) to upload.
     *   @param isDryRun
     * If true, then most CCP commands are not really executed; the CRO is not sent out and
     * we don't wait for a DTO. The success of the suppressed CCP command is assumed true.
     * However, the complete state machine is stepped through.
     */
    public void upload(Iterable<SRecord> memAreas, boolean isDryRun) {
        assert ccpCmdSequence_ == null  &&  state_ == StateFlashProcess.COMPLETED
             : "Can't start a new CCP communication if there is still one running";
        isDryRun_ = isDryRun;
        ccpCmdSequence_ = new CcpCmdSequence(ccpCmdFactory_, digitalSignature_);
        ccpCmdSequence_.upload(memAreas);
        state_ = StateFlashProcess.START;

    } /* upload */

    /** 
     * Append the target ECU reset command to the CCO command sequence. If no command
     * sequence has been chosen before then the reset is the ony action of a new command
     * sequence.
     *   @param resetToApplication
     * After reset, the ECU can launch either the flashed application (if any) or the FBL.
     * Pass true for launching the application (normal use-case).
     *   @param isDryRun
     * If true, then most CCP commands are not really executed; the CRO is not sent out and
     * we don't wait for a DTO. The success of the suppressed CCP command is assumed true.
     * However, the complete state machine is stepped through.<p>
     *   Caution, if the reset command is appended to an existing non-empty CCP command
     * sequence, then isDryRun needs to match the setting, which had been specified before
     * for the sequence.
     */ 
    public void resetTarget(boolean resetToApplication, boolean isDryRun) {
        if (ccpCmdSequence_ == null) {
            isDryRun_ = isDryRun;
            assert state_ == StateFlashProcess.COMPLETED
                 : "Can't start a new CCP communication if there is still one running";
            ccpCmdSequence_ = new CcpCmdSequence(ccpCmdFactory_, digitalSignature_);
            state_ = StateFlashProcess.START;
        } else {    
            assert isDryRun_ == isDryRun
                 : "Inconsistent specification of 'isDryRun'";
            assert state_ == StateFlashProcess.START
                 : "Can't compose a new CCP communication if there is still one running";
        }
        ccpCmdSequence_.diagServiceResetTarget(resetToApplication);

    } /* resetTarget */
    
    /**
     * This function implements the activities while we are in state CONNECTING.<p>
     *   The CCP CONNECT command is sent once or repeatedly, until we get a valid response
     * or the number of allowed retries is reached.<p>
     *   The success and error conditions are directly evaluated and the next state is
     * accordingly set by side-effect.
     */
    private void stateConnectToTarget() {

        /* Do we require a new CCP CONNECT command? This will happen at the beginning and
           on every retry. */
        final CcpCroTransmitter.ResultTransmission resultTxRx;
        if (currentCcpCmd_ == null) {
            assert cntAttemptsToConnect_ > 0;
            -- cntAttemptsToConnect_;

            final CcpCommandArgs.Connect args = new CcpCommandArgs.Connect(stationAddr_);
            currentCcpCmd_ = ccpCmdFactory_.create(args);
            currentCcpCmd_.setIgnoreCanRxErrors( ignoreCanErrsDuringConnect_
                                               , timeoutCroToDtoWhenIgnoreCanErrs_
                                               );
            assert executeCcpCmd(): "CONNECT is assumed to be always executed";
            _logger.debug("Next executed CCP command: {}", currentCcpCmd_);
            resultTxRx = currentCcpCmd_.start();
        } else {
            resultTxRx = currentCcpCmd_.step();
        }

        if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS) {
            currentCcpCmd_.setIgnoreCanRxErrors(false, 0 /*doesn't care*/);
            currentCcpCmd_ = null;
            state_ = StateFlashProcess.COMMUNICATING_WITH_TARGET;

        } else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING) {
            /* The connect CRO/DTO exchange failed. The reason has been logged. We can
               retry after a while. */
            currentCcpCmd_.setIgnoreCanRxErrors(false, 0 /*doesn't care*/);
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
                state_ = StateFlashProcess.COMPLETED_WITH_ERRORS;
            }
        } else {
            /* DTO has not been received yet. We remain in this state. */
        }
    } /* stateConnectToTarget */

    /**
     * This function implements the activities while we are in state DISCONNECTING.<p>
     *   The CCP DISCONNECT command is sent to terminate the session.<p>
     *   The success and error conditions are directly evaluated and the next state is
     * accordingly set by side-effect.
     *   @param successful
     * Pass true if we discoonect after sucessful operation and false otherwise. The only
     * impact is the final result of the CCP protocol sequence, which is reported to the
     * caller.
     */
    private void stateDisconnectFromTarget(boolean successful) {

        /* Do we require a new CCP CONNECT command? This will happen once on entry into
           state DISCONNECTING. */
        final CcpCroTransmitter.ResultTransmission resultTxRx;
        if (currentCcpCmd_ == null) {
            final CcpCommandArgs.Disconnect args =
                                        new CcpCommandArgs.Disconnect( stationAddr_
                                                                     , /*isEndOfSession*/ true
                                                                     );
            currentCcpCmd_ = ccpCmdFactory_.create(args);
            assert executeCcpCmd(): "DISCONNECT is assumed to be always executed";
            _logger.debug("Next executed CCP command: {}", currentCcpCmd_);
            resultTxRx = currentCcpCmd_.start();
        } else {
            resultTxRx = currentCcpCmd_.step();
        }

        if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING) {
            /* Successful termination or error doesn't make a difference any more. We did
               all we can do. */
            currentCcpCmd_ = null;
            ccpCmdSequence_ = null;
            state_ = successful? StateFlashProcess.COMPLETED
                               : StateFlashProcess.COMPLETED_WITH_ERRORS;
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
        final CcpCroTransmitter.ResultTransmission resultTxRx;
        if (currentCcpCmd_ == null) {
            assert ccpCmdSequence_.size() > 0;
            currentCcpCmd_ = ccpCmdSequence_.get(0);
            ccpCmdSequence_.remove(0);

            if (executeCcpCmd()) {
                _logger.debug("Next executed CCP command: {}", currentCcpCmd_);
                resultTxRx = currentCcpCmd_.start();
            } else {
                _logger.info("Dry run: CCP command {} is skipped.", currentCcpCmd_);
                resultTxRx = CcpCroTransmitter.ResultTransmission.SUCCESS;
            }
        } else if (executeCcpCmd()) {
            resultTxRx = currentCcpCmd_.step();
        } else {
            assert false;
            resultTxRx = CcpCroTransmitter.ResultTransmission.SUCCESS;
        }

        if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS) {
            currentCcpCmd_ = null;
            if (ccpCmdSequence_.size() > 0) {
                /* There is still another CCP command to process, no state change. */
            } else {
                state_ = StateFlashProcess.DISCONNECTING;
            }
        } else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING) {
            /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
               else to do. */
            currentCcpCmd_ = null;
            assert errCnt_.getNoErrors() > 0;
            errCnt_.warning();
            _logger.warn("Prematurely disconnecting after previous errors.");
            state_ = StateFlashProcess.DISCONNECTING_AFTER_ERROR;
        } else {
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
    public boolean step() {
        assert ccpCmdSequence_ != null  ||  state_ == StateFlashProcess.COMPLETED
               ||  state_ == StateFlashProcess.COMPLETED_WITH_ERRORS;
        CcpCroTransmitter.ResultTransmission resultTxRx;
        switch(state_) {
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
            stateDisconnectFromTarget(/*successful*/ true);
            break;

        case DISCONNECTING_AFTER_ERROR:
            /* The state function will itself set the next state, depending on what
               happens. Here, we just have to call it regularly. */
            stateDisconnectFromTarget(/*successful*/ false);
            break;

        case COMPLETED:
        case COMPLETED_WITH_ERRORS:
            return true;

        } /* switch(state) */

        return false;

    } /* step */


    /**
     * The CCP communication is performed as a synchronous, blocking operation.<p>
     *   The function step() of the CCP object is called until it signals the completion of
     * the communication sequence.<p>
     *   Please note that this is a convenience method only, which wraps step() in a loop.
     * Normally, the client code will prefer to call the step function itself; this allows
     * intermingling the state checks of the communication state machine with any other
     * activities, e.g., serving a GUI or providing progress information.
     *   @return
     * Get getFinalResult().
     */
    public boolean run() {
    
        /* Clock the state machine, which runs the CCP communication. */
        while(!step()) {
            /* Here, we could do other, non-blocking things, e.g., print some
               progress information. */
        }
        return getFinalSuccess();
    }
    
    /**
     * After termination of the iteration with step(), the owner can ask for the final
     * result of the completed CCP protocol sequence.<p>
     *   Note, this method must not be used before termination of the CCP protocol
     * sequence. The result would be undefined.
     *   @return
     * Get true if the CCP communication succeeded and false if it had been aborted for the
     * one or other reason.<p>
     *   If false is returned then some communication error occurred and has been logged.
     * Upload results must not be evaluated and the success of a program procedure on the
     * target must be doubted.
     */
    public boolean getFinalSuccess() {
        assert state_ == StateFlashProcess.COMPLETED
               ||  state_ == StateFlashProcess.COMPLETED_WITH_ERRORS;
        return state_ == StateFlashProcess.COMPLETED;
    }

} /* End of class CCP definition. */