/**
 * @file CcpCroTransmitter.java
 * Base element of CCP communication: A command receive object (CRO message) is assembled
 * and sent to the CAN bus. Each CRO message gets a reponse message, the data transmission
 * object (DTO). This module handles the reception of the related DTO message and the
 * possible error conditions. The implementation is a state machine. It has a non-blocking
 * main function, which the client code will regularly invoke until the final result is
 * available (i.e., response or error condition).
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
/* Interface of class CcpCroTransmitter
 *   CcpCroTransmitter
 *   setTimeoutCroTillDto
 *   sendCro
 *   getDto
 */

package winFlashTool.ccp;

import java.util.*;
import org.apache.logging.log4j.*;
import peak.can.basic.*;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.basics.SignalWithAutoReset;
import winFlashTool.can.CanId;
import winFlashTool.can.CanDevice;
import winFlashTool.can.PCANBasicEx;

/**
 * Base class of CCP communication: A command receive object (CRO message) is assembled
 * and sent to the CAN bus. Each CRO message gets a reponse message, the data transmission
 * object (DTO). This class handles the reception of the related DTO message and the
 * possible error conditions. The implementation is a state machine. It has a non-blocking
 * main function, which the client code will regularly invoke until the final result is
 * available (i.e., response or error condition).<p>
 *   Derived classes can implement specific CRO messages and add command related response
 * evaluation.
 */
/*tmp only*/ public
class CcpCroTransmitter
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCroTransmitter.class);

    /* The error counter to be used for error reporting. */
    final ErrorCounter errCnt_;
    
    /** The states of the processing. */
    private enum StateTransmission
    {
        UNDEFINED,
        IDLE,
        ERROR_TX_CRO,
        WAITING_FOR_DTO;

        /** String to enum conversion. Illegal strings are translated into enumeration
            value UNDEFINED. */
        public static StateTransmission fromString(String name)
        {
            if(name == null)
                return UNDEFINED;

            try
            {
                return StateTransmission.valueOf(name.toUpperCase());
            }
            catch(IllegalArgumentException e)
            {
                return UNDEFINED;
            }
        }
    };
    
    /** The results of the processing. */
    public enum ResultTransmission
    {
        UNDEFINED,
        SUCCESS,
        PENDING,
        ERROR_TIMEOUT,
        ERROR_CAN_TX_TRANSMISSION_FAILED,
        ERROR_CAN_RX_TRANSMISSION_FAILED,
        ERROR_WRONG_CAN_MSG,
        ERROR_NEGATIVE_RESPONSE,
        ERROR_BAD_MTA_UPDATE,
        ERROR_BAD_DTO_HDR;

        /** String to enum conversion. Illegal strings are translated into enumeration
            value UNDEFINED. */
        public static ResultTransmission fromString(String name)
        {
            if(name == null)
                return UNDEFINED;

            try
            {
                return ResultTransmission.valueOf(name.toUpperCase());
            }
            catch(IllegalArgumentException e)
            {
                return UNDEFINED;
            }
        }
    };
    
    /** The state of the state machine. */
    private StateTransmission state_ = StateTransmission.UNDEFINED;
    
    /** The CAN device, which is used for CAN Tx and Rx. */
    private final CanDevice canDev_;
    
    /** The command counter. It cycles from 0 till 255. */
    private byte cmdCntr_ = 0;
    
    /** Timeout counter to measure the time it may take to receive the DTO message. */
    private TimeoutTimer timerRxDtoTO_;
    
    /** Debugging: A time variable to measure the response time of the ECU. It is the time
        from sending the CRO until reception of DTO. */
    public long tiResponseEcuInNs_;
    
    /** The CAN ID of all CRO messages. */
    private final CanId ccpCanIdCro_;
    
    /** The CAN ID of all DTO messages. */
    private final CanId ccpCanIdDto_;
    
    /** The length of all CRO and DTO messages. */
    private final static int MSG_LEN = 8;
    
    /** The maximum time, which may elapse after sending the CRO until the DTO arrives. Unit
        is Milliseconds. */
    private int timeoutTillRxDtoInMs_;

    /**
     * A new instance of CcpCroTransmitter is created.
     *   @param canDev
     * All CAN communication will be done with this CAN device. Pass an already opened and
     * initialized device.
     *   @param timeoutTillRxDtoInMs
     * The maximum time, which may elapse after sending the CRO until the DTO arrives. Unit
     * is Milliseconds.<p>
     *   Note, this value can be modified later, e.g., temporarily for the execution of a
     * long lasting CCP command like Erase.
     *   @param ccpCanIdCro
     * The CAN ID of all CRO messages.
     *   @param ccpCanIdDto
     * The CAN ID of all DTO messages.
     *   @param errCnt
     * The error counter to be used for problem reporting.
     */
    CcpCroTransmitter( CanDevice canDev
                     , CanId ccpCanIdCro
                     , CanId ccpCanIdDto
                     , ErrorCounter errCnt ) {
        errCnt_ = errCnt;
        canDev_ = canDev;
        ccpCanIdCro_ = ccpCanIdCro;
        ccpCanIdDto_ = ccpCanIdDto; 
        timeoutTillRxDtoInMs_ = 1000;
        timerRxDtoTO_ = new TimeoutTimer(timeoutTillRxDtoInMs_);

        state_ = StateTransmission.IDLE;

    } /* CcpCroTransmitter.CcpCroTransmitter */

    
    /**
     * Set the timeout for time span between sending CRO and receiving DTO.
     *   @param timeoutTillRxDtoInMs
     * The maximum time, which may elapse after sending the CRO until the DTO arrives. Unit
     * is Milliseconds.
     *   @note
     * The method can be used at any time, but it takes effect only with transmitting the
     * next CRO. A currently pending CRO/DTO exchange is not affected.
     */
    void setTimeoutCroTillDto(int timeoutTillRxDtoInMs) {
        timeoutTillRxDtoInMs_ = timeoutTillRxDtoInMs;

    } /* CcpCroTransmitter.CcpCroTransmitter */

public static int _maxNoPolls = 0, _noCro = 0, _totalNoPolls = 0;
private int noPolls_ = 0;

    /**
     * Initiate a CRO Tx and DTO Rx exchange with the connected ECU.<p>
     *   This function sends the CRO and initailizes the state machine to handle the later
     * reception of the response message (or timeouts and other errors).
     *   @param payloadAry
     * The always 8 Byte of payload of the CRO message. Note, filling the unused bytes and
     * setting the command counter is done by this method and should not be done by the
     * caller.
     *   @param noContentBytes
     * The number of used bytes in payloadAry. Range is 2..#MSG_LEN.
     */
    public void sendCro(byte[] payloadAry, int noContentBytes)
    {
        assert noContentBytes >= 2  &&  noContentBytes <= MSG_LEN
             : "The number of payload bytes of a CRO message is limited.";

        assert state_ == StateTransmission.IDLE: "Bad use of class interface";

        payloadAry[1] = cmdCntr_++;
        for(int idxByte=noContentBytes; idxByte<MSG_LEN; ++idxByte)
            payloadAry[idxByte] = (byte)0xFF;

        final TPCANMsg canMsg = new TPCANMsg( ccpCanIdCro_.getCanId()
                                            , ccpCanIdCro_.getMsgType().getValue()
                                            , (byte)MSG_LEN
                                            , payloadAry
                                            );
        final TPCANStatus errCode = canDev_.write(canMsg);
noPolls_ = 0;
++ _noCro;
        tiResponseEcuInNs_ = System.nanoTime();
        if(PCANBasicEx.checkReturnCode(errCode))
        {
            /* Start timeout measurement till Rx of DTO. */
            timerRxDtoTO_.restart(timeoutTillRxDtoInMs_);
            _logger.trace( "Command {} with {} Byte payload send in CRO no {}."
                         , PCANBasicEx.b2i(payloadAry[0])
                         , noContentBytes
                         , PCANBasicEx.b2i(payloadAry[1])
                         );
            state_ = StateTransmission.WAITING_FOR_DTO;
        }
        else
        {
            errCnt_.error();
            _logger.error( "Error sending CRO message {} with {} Byte payload."
                         , payloadAry[0]
                         , noContentBytes 
                         );
            
            /* By design, we do not return the error message immediately. Instead, we
               return it like all later Rx related errors in method getDto(). This makes
               the usage of this class simpler. At the caller side, on entry into a CCP
               state, the next state can unconditionally be entered and it is left using
               the anyway implemented transition logic of the enetred state. Otherwise we
               would have two transition logics, on entry and during the state. */
            state_ = StateTransmission.ERROR_TX_CRO;
        }
    } /* sendCro */
    
    
    /**
     * Main function of state machine.<p>
     *   Regularly call this method after sending a CRO until the DTO is received.
     *   @return
     *   If and only if method returns ResultTransmission.SUCCESS, the transmission of
     * command and response have completed and this object is in state IDLE again, which
     * permits using sendCro() the next time.<p>
     *   If the method returns ResultTransmission.PENDING, then it is still waiting for the
     * reception of the DTO. In this case, the caller needs to continue calling this
     * method. Again using sendCro() is not allowed yet in this state. Note, due to a
     * timeout condition, it won't last forever to leave this state.<p>
     *   All other return values are error messages. A meaningful error report has been
     * logged. This object returns into state IDLE and a further or repeated command
     * transmission initiated with sendCro() could be tried.
     *   @param canMsg
     * A CAN message is provided to the method. It must be evaluated as result only if the
     * method returns ResultTransmission.SUCCESS.
     */
    public ResultTransmission getDto(TPCANMsg canMsg)
    {
        ResultTransmission result;
        if(state_ == StateTransmission.ERROR_TX_CRO)
        {
            /* Here, we have the delayed reporting of the error in sendCro(). */
            result = ResultTransmission.ERROR_CAN_RX_TRANSMISSION_FAILED;
            
            /* After error reporting, we return to IDLE to allow a repeated attempt. */
            state_ = StateTransmission.IDLE;
        }
        else
        {
            assert state_ == StateTransmission.WAITING_FOR_DTO: "Bad use of class interface";
             
            /* Check PCANBasic API for Rx event. */
++_totalNoPolls;
if (++noPolls_ > _maxNoPolls) {_maxNoPolls = noPolls_;}
            final TPCANStatus errCode = canDev_.read( canMsg
                                                    , /*TimestampBuffer*/ null
                                                    , /*timeoutInMs*/ 10
                                                    );
            if(errCode != TPCANStatus.PCAN_ERROR_QRCVEMPTY)
            {
_logger.trace("Fetched message from queue at {}.", System.nanoTime());

                tiResponseEcuInNs_ = System.nanoTime() - tiResponseEcuInNs_;
                if(PCANBasicEx.checkReturnCode(errCode))
                {
                    /* Checking the CAN ID is actually useless as the reception filter
                       should hinder that we ever see a wrong one, but this is external
                       code and we don't have a guarantee how it behaves. Better to
                       double-check it. */
                    if(canMsg.getID() == ccpCanIdDto_.getCanId()
                       &&  canMsg.getType() == ccpCanIdDto_.getMsgType().getValue()
                       &&  (int)canMsg.getLength() == MSG_LEN
                      )
                    {
                        /* Check packet ID for 0xFF, response code for 0 and identity of
                           counter. */
                        byte[] payloadAry = canMsg.getData();
                        byte cmdCntrExpected = cmdCntr_;
                        -- cmdCntrExpected;
                        if(payloadAry[0] != (byte)0xFF /* PACKET_ID of CRM */
                           ||  payloadAry[1] != (byte)0 /* 0: CMD_RET_CODE_ACKNOWLEDGE */
                           ||  payloadAry[2] != cmdCntrExpected
                          )
                        {
                            /* Report error and return to IDLE for a repeated attempt. */
                            errCnt_.error();
                            _logger.error( "Invalid DTO message received. Expected packed"
                                           + " ID 255, response code 0 and command counter"
                                           + " {}, but received packed"
                                           + " ID {}, response code {} and command counter"
                                           + " {}."
                                         , PCANBasicEx.b2i(cmdCntrExpected)
                                         , PCANBasicEx.b2i(payloadAry[0])
                                         , PCANBasicEx.b2i(payloadAry[1])
                                         , PCANBasicEx.b2i(payloadAry[2])
                                         );
                                  
                            result = payloadAry[1] != (byte)0
                                     ? ResultTransmission.ERROR_NEGATIVE_RESPONSE
                                     : ResultTransmission.ERROR_BAD_DTO_HDR;
                            state_ = StateTransmission.IDLE;
                        }
                        else
                        {
                            /* We received a proper DTO. It is returned to the caller. */
//                            _logger.trace( "DTO for CRO no {} received after {}ns."
_logger.debug( "DTO for CRO no {} received after {}ns."
                                         , PCANBasicEx.b2i(cmdCntrExpected)
                                         , tiResponseEcuInNs_
                                         );
                            result = ResultTransmission.SUCCESS;
                            state_ = StateTransmission.IDLE;

                        } /* if(CAN message is expected DTO?) */
                    }
                    else
                    {
                        /* Report error and return to IDLE for a repeated attempt. */
                        errCnt_.error();
                        _logger.error( "Invalid DTO message received. Expected CAN"
                                       + " ID {} and length {}, but received{}"
                                       + " ID {} and length {}."
                                     , ccpCanIdDto_
                                     , MSG_LEN
                                     , canMsg.getType() 
                                       == TPCANMessageType.PCAN_MESSAGE_EXTENDED.getValue()
                                       ? " extended"
                                       : ""
                                     , canMsg.getID()
                                     , (int)canMsg.getLength()
                                     );
                        result = ResultTransmission.ERROR_WRONG_CAN_MSG;
                        state_ = StateTransmission.IDLE;
                        
                    } /* if(CAN message is expected DTO?) */
                }
                else
                {
                    /* Report error and return to IDLE for a repeated attempt. */
                    result = ResultTransmission.ERROR_CAN_RX_TRANSMISSION_FAILED;
                    state_ = StateTransmission.IDLE;
                     
                } /* if(CAN message reception without errors?) */
            }
            else if(timerRxDtoTO_.hasTimedOut())
            {
                /* It took too long to receive the DTO. Report error and return to IDLE for
                   a repeated attempt. */
                final int cmdCntrExpected = (PCANBasicEx.b2i(cmdCntr_) - 1) & 0xFF;
                _logger.error( "No DTO message received for CRO no {}. Timeout elapsed."
                             , cmdCntrExpected
                             );
                result = ResultTransmission.ERROR_TIMEOUT;
                state_ = StateTransmission.IDLE;
            }
            else
            {
                result = ResultTransmission.PENDING;
                
//                /* Preliminary solution to avoid busy-wait with high CPU load. By
//                   experience, it reduces the maximum achievable throughput from 30%
//                   busload at 500 kBd to about 20%. */
//                try {
//                    Thread.sleep(1);
//                } catch (InterruptedException e) {
//                }

// Pattern 1, counting semaphore:
// 
// import java.util.concurrent.Semaphore;
// import java.util.concurrent.TimeUnit;
// 
// public class RxSignal {
//     // 0 permits => A will block until B releases
//     private final Semaphore sem = new Semaphore(0 /*initialPermits*/, false /*fairness*/);
// 
//     // Called by your PCAN callback thread (B). KEEP THIS LIGHT!
//     public void signalRx() {
//         sem.release();            // wake exactly one waiter; multiple signals accumulate
//     }
// 
//     // Called by your main thread (A), repeatedly in its loop
//     public boolean waitForRx(long timeout, TimeUnit unit) throws InterruptedException {
//         return sem.tryAcquire(1, timeout, unit);  // returns true if signaled, false on timeout
//     }
// }
// 
// 
// Main Thread, when waiting for DTO:
// RxSignal rx = new RxSignal();
// 
// while (!shutdown) {
//     if (rx.waitForRx(5, TimeUnit.MILLISECONDS)) {
//         // -> got the signal: drain CAN receive queue until empty
//         // (read-until-empty pattern)
//     }
//     // -> housekeeping work here when timed out
// }
// 
// Callback thread, in PCAN BAsic context:
// @Override
// public void processRcvEvent(TPCANHandle ch) {
//     // DO NOT block here; just signal and return:
//     // rx is a counting event. Each tryAcquire will consume just one count. Main thread
//     // knowns number of CAN Rx
//     //   Consider using a java.util.concurrent.atomic.AtomicBoolean; to implement a
// max-count of 1: Semaphore is is incremented only if Boolean not yet set.
//     rx.signalRx();
// }
// 
// Patter 1a: Using a Boolean to overcome the accumulating semaphore.
// 
// import java.util.concurrent.Semaphore;
// import java.util.concurrent.TimeUnit;
// import java.util.concurrent.atomic.AtomicBoolean;
// 
// /** Auto-reset signal with max=1 outstanding permit. */
// public final class OneShotSignal {
//     private final Semaphore sem = new Semaphore(0, false);
//     private final AtomicBoolean armed = new AtomicBoolean(false);
// 
//     /** B: called from your PCAN Rx callback - wake A if not already armed. */
//     public void signal() {
//         // If we transition false -> true, release a single permit.
//         if (armed.compareAndSet(false, true)) {
//             sem.release();
//         }
//         // If already armed, do nothing: we keep exactly one outstanding signal.
//     }
// 
//     /** A: wait with timeout; returns true if signaled, false if timed out. */
//     public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
//         if (!sem.tryAcquire(1, timeout, unit)) {
//             return false;                 // timeout: no signal
//         }
//         // We consumed the single signal; clear "armed" for next cycle.
//         armed.set(false);
//         return true;
//     }
// }
// 
// Pattern 2: Main thread suspends, signalling is implemented by release from callback thread.
// Advantage: No complexity due to counting.
// 
// Key properties you should know:
// 
// There is at most one permit per thread; extra unpark calls before park can be lost if you
// don't guard with an atomic flag. 
// park may return for no reason -> always loop and recheck
// the condition. [docs.oracle.com]
// 
// Use this if you want the absolute lowest overhead and you're comfortable with a small
// amount of bespoke concurrency code.
// 
// import java.util.concurrent.locks.LockSupport;
// import java.util.concurrent.atomic.AtomicBoolean;
// 
// class AutoResetSignal {
//     private final AtomicBoolean signaled = new AtomicBoolean(false);
//     private volatile Thread waiter;  // the thread that will park
// 
//     // A (main thread):
//     boolean awaitNanos(long timeoutNanos) {
//         final long deadline = System.nanoTime() + timeoutNanos;
//         waiter = Thread.currentThread();
//         while (true) {
//             // fast-path: consume signal if present
//             if (signaled.compareAndSet(true, false)) return true;
// 
//             long remaining = deadline - System.nanoTime();
//             if (remaining <= 0) return false;
// 
//             LockSupport.parkNanos(this, remaining); // may wake spuriously
// 
//             // parkNanos can return for multiple reasons:
//             //   the signal arrived (unpark),
//             //   the timeout expired,
//             //   or the thread was interrupted,
//             //   or even spuriously (no reason).
//             // Did someone interrupt me? If yes, I noted it, but I'll put the flag back so upstream code can also see it.
//             if (Thread.interrupted()) Thread.currentThread().interrupt();
//         }
//     }
// 
//     // B (callback):
//     void signal() {
//         signaled.set(true);                // publish signal
//         Thread w = waiter;
//         if (w != null) LockSupport.unpark(w);  // wake the parked thread quickly
//     }
// }
// 
// Pattern 3:
// 
// 
// 
// import java.util.concurrent.TimeUnit;
// import java.util.concurrent.locks.Condition;
// import java.util.concurrent.locks.ReentrantLock;
// 
// /**
//  * Auto-Reset-Event auf Basis von ReentrantLock + Condition.
//  * Maximal ein "offenes" Signal; A verbraucht es beim erfolgreichen Warten.
//  */
// public final class AutoResetEventCondition {
// 
//     private final ReentrantLock lock = new ReentrantLock(false); // non-fair fuer geringe Latenz
//     private final Condition condition = lock.newCondition();
//     private boolean signaled = false;  // "bewaffnetes" Signal
// 
//     /** B (Callback): loest das Ereignis aus und weckt einen Wartenden. */
//     public void signal() {
//         lock.lock();
//         try {
//             // Edge-Trigger: setze auf true (mehrere Signals bleiben true; es wird nur einmal konsumiert)
//             signaled = true;
//             condition.signal();  // weckt genau einen Wartenden
//         } finally {
//             lock.unlock();
//         }
//     }
// 
//     /**
//      * A (Main-Thread): wartet bis zu timeout auf ein Signal.
//      * @return true, wenn Signal erhalten und konsumiert, false bei Timeout.
//      */
//     public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
//         long nanos = unit.toNanos(timeout);
//         lock.lock();
//         try {
//             // Solange kein Signal ansteht, warten mit Timeout
//             while (!signaled) {
//                 if (nanos <= 0L) {
//                     return false;  // Timeout erreicht
//                 }
//                 nanos = condition.awaitNanos(nanos); // kann spurios aufwachen -> Schleife
//             }
//             // Ereignis konsumieren -> Auto-Reset
//             signaled = false;
//             return true;
//         } finally {
//             lock.unlock();
//         }
//     }
// 
//     /** Optional: sofortige Nicht-blockierende Abfrage */
//     public boolean tryConsume() {
//         lock.lock();
//         try {
//             if (!signaled) return false;
//             signaled = false;
//             return true;
//         } finally {
//             lock.unlock();
//         }
//     }
// }
// 
// Practical advice:
// 
// Set A to Thread.MAX_PRIORITY and B to Thread.NORM_PRIORITY (or lower).
// Keep the callback very short (just signalRx()), so the OS can schedule A immediately when
// unblocked. 
// Prefer Semaphore or LockSupport; both unblock A quickly without heavy locking.

                /* We remain in state WAITING_FOR_DTO. */

            } /* if(CAN message received since last invokation?) */
            
        } /* if(CRO could be sent without failures?) */   
        
        return result;
        
    } /* getDto */
    
} /* End of class CcpCroTransmitter definition. */




