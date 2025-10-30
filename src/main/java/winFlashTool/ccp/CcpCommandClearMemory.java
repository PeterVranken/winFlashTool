/**
 * @file CcpCommandClearMemory.java
 * CCP command CLEAR_MEMORY.
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
/* Interface of class CcpCommandClearMemory
 *   CcpCommandClearMemory
 */

package winFlashTool.ccp;

import java.util.*;
import java.util.Set;
import java.util.HashSet;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import java.util.HashSet;


/**
 * CCP command CLEAR_MEMORY.
 */
public class CcpCommandClearMemory extends CcpCommandBase
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCommandClearMemory.class);

    /**
     * A new instance of CcpCommandClearMemory is created.
     */
    protected CcpCommandClearMemory()
    {
    } /* CcpCommandClearMemory.CcpCommandClearMemory */


    /**
     * Inform the base class, which particular CCP commands are implemented by this class.
     *   @return
     * Get the set of enumerated values, which represent CCP commands that are implemented
     * by this derived class.
     */
    protected Set<CroCommandId> myCcpCmdIds()
    {
        final Set<CroCommandId> setOfCmdIds = new HashSet<CroCommandId>(1);
        setOfCmdIds.add(CroCommandId.CLEAR_MEMORY);
        return setOfCmdIds;
    }
    
    /**
     * The CCP command is started. After return from start(), the caller will repeatedly
     * call step() - until step() indicates completion of the command.
     *   @param argAry
     * The argument list consists of a single argument: The number of Byte to erase in the
     * memory. It is a 32 Bit value, expected as an Integer.
     */
    public void start(Object... argAry)
    {
        /* Parse argument list. */
        assert argAry.length == 1
               &&  argAry[0] instanceof Integer
             : "Bad argument list. Expect a single Integer object";
        final int memorySize = ((Integer)argAry[0]).intValue();
        
        /* Send CAN CRO message with command CLEAR_MEMORY. */
        payloadCroAry_[0] = CroCommandId.CLEAR_MEMORY.getCode();
        
        /* Memory block size in MSB endianess. */ 
        payloadCroAry_[2] = (byte)((memorySize >> 24) & 0xFF);
        payloadCroAry_[3] = (byte)((memorySize >> 16) & 0xFF);
        payloadCroAry_[4] = (byte)((memorySize >>  8) & 0xFF);
        payloadCroAry_[5] = (byte)((memorySize >>  0) & 0xFF);
        
        sendCro(/*noContentBytes*/ 6);
        _logger.printf( Level.DEBUG
                      , "CRO message CLEAR_MEMORY(0x%06X) sent to ECU."
                      , memorySize
                      );
    }

    /**
     * All CCP commands are implemented as state machines. This method implements a single
     * calculation step of the FSM. It needs to be called regularly.
     *   @return
     * The method returns "pending" until the command has completed. The first time this
     * method returns anything other than "pending" needs to be the last time this method
     * is called -- until the command is re-started and executed again.
     */
    public CcpCroTransmitter.ResultTransmission step()
    {
        final CcpCroTransmitter.ResultTransmission resultTxRx = checkRxDto();
        if(resultTxRx == CcpCroTransmitter.ResultTransmission.SUCCESS)
        {
            _logger.debug("ECU acknowledged CLEAR_MEMORY.");
        }
        else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
        {
            /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
               else to do. */
            _errCnt.error();
            _logger.error("Can't erase ECU memory. See previous error messages for"
                          + " details."
                         );
        }
        else
        {
            /* DTO has not been received yet. We continue polling. */
        }
        
        return resultTxRx;
        
    } /* step */

} /* End of class CcpCommandClearMemory definition. */




