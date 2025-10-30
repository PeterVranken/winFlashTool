/**
 * @file CcpCommandBase.java
 * The common functions of the implementation of a CCP command.
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
/* Interface of class CcpCommandBase
 *   CcpCommandBase
 */

package winFlashTool.ccp;

import java.util.*;
import java.util.Set;
import java.util.HashSet;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import peak.can.basic.TPCANMsg;
import java.util.HashSet;

/**
 * Base class of all CCP command implementations.<p>
 *   Concept of the class is a static implementation of CCP command objects, which can
 * share state information and data through their common base class. The concept is static,
 * because:<p>
 *   - The problem is static. We have a single CAN channel and can process only a single
 * CCP command at a time. There is no need to have several instances of a CCP command at a
 * time.<p>
 *   - Static data is the simplest way to share data beween different objects.<p>
 *   Since there is no need to ever have more than one instance of a CCP command, the class
 * implement the singleton pattern. CCP commands are created once and buffered. The client
 * code just fetches the command from the buffer prior to use.
 */
abstract class CcpCommandBase
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCommandBase.class);

    /* The error counter to be used for error reporting. */
    static ErrorCounter _errCnt;
    
    /** An always available buffer to prepare the payload of a CRO, which is then sent out
        using sendCro(). */
    static protected final byte[] _payloadCroAry = new byte[8];
    
    /** An always available buffer, which contains the payload of the last recently
        received DTO message. */
    static protected final byte[] _payloadDtoAry;

    /** The CAN message representation from the PCAN Basic API of the last recently
        received DTO message. Note, it is _payloadDtoAry = _msgDto.getData(). */
    static final TPCANMsg _msgDto;

    /** Current memory address, initially set with SET_MTA and modified with the DOWNLOAD
       and PROGRAMM commands. */
    static protected int _Mta0;
    
    static        
    {
        /* Create a forever reused PCAN Basic message object for DTO reception and make its
           data buffer accessible via a field. */
        _msgDto = new TPCANMsg();
        _payloadDtoAry = _msgDto.getData();
    }

    /**
     * A new instance of CcpCommandBase is created.
     *   @param croTransmitter
     * The fully initialized CRO transmitter, which will be used for exchanging all CRO/DTO
     * messages of all CCP commands.
     */
    protected CcpCommandBase()
    {
        for(CroCommandId ccpCmd: myCcpCmdIds())
            ccpCmd.setCmd(this);
            
    } /* CcpCommandBase.CcpCommandBase */


    /**
     * Set the error counter, which is globally used by all failure reporting in this base
     * class and the derived classes.
     *   @param errCnt
     * The error counter to be used for problem reporting.
     */
    static public void setErrorCounter(ErrorCounter errCnt)
    {
        _errCnt = errCnt;
    }

    
    /**
     * Send a CRO message. The payload of the CAN message is taken from _payloadCroAry
     *   @param noContentBytes
     * The number of meaningful payload bytes in _payloadCroAry. The remaining bytes will
     * be set to a don't care value.
     */
    static protected void sendCro(int noContentBytes)
    {
        CcpCroTransmitter.getCroTransmitter().sendCro(_payloadCroAry, noContentBytes);
    }

    
    /**
     * Check for reception of the DTO, which belongs to the previously sent CRO. The
     * function result tells, whether or not the DTO has been received yet. If it has been
     * received, then the payload data is available in _payloadDtoAry.
     *   @return
     * Get the reception status for the DTO. This can be either succes or "still waiting"
     * or an error code.
     */
    static protected CcpCroTransmitter.ResultTransmission checkRxDto()
    {
        return CcpCroTransmitter.getCroTransmitter().getDto(_msgDto);
    }

    /**
     * Inform the base class, which particular CCP commands are implemented by this class.
     *   @return
     * Get the set of enumerated values, which represent CCP commands that are implemented
     * by the derived class.
     */
    protected abstract Set<CroCommandId> myCcpCmdIds();

    /**
     * The CCP command is started. After return from start(), the caller will repeatedly
     * call step() - until step() indicates completion of the command.
     *   @param argAry
     * A list of optional arguments. Which one, will depend on the actual command to
     * implement.
     */
    public abstract void start(Object... argAry);

    /**
     * All CCP commands are implemented as state machines. This method implements a single
     * calculation step of the FSM. It needs to be called regularly.
     *   @return
     * The method returns "pending" until the command has completed. The first time this
     * method returns anything other than "pending" needs to be the last time this method
     * is called -- until the command is re-started and executed again.
     */
    public abstract CcpCroTransmitter.ResultTransmission step();
    
} /* End of class CcpCommandBase definition. */




