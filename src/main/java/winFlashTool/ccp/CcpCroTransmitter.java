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
        final TPCANStatus errCode = canDev_.Write(canMsg);
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
            final TPCANStatus errCode = canDev_.Read(canMsg, /*TimestampBuffer*/null);
            if(errCode != TPCANStatus.PCAN_ERROR_QRCVEMPTY)
            {
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
                            _logger.trace( "DTO for CRO no {} received after {}ns."
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
                
                /* Preliminary solution to avoid busy-wait with high CPU load. By
                   experience, it reduces the maximum achievable throughput from 30%
                   busload at 500 kBd to about 20%. */
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }

                /* We remain in state WAITING_FOR_DTO. */

            } /* if(CAN message received since last invokation?) */
            
        } /* if(CRO could be sent without failures?) */   
        
        return result;
        
    } /* getDto */
    
} /* End of class CcpCroTransmitter definition. */




