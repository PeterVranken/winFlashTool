/**
 * @file CcpCommandSetMta.java
 * CCP command SET_MTA.
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
/* Interface of class CcpCommandSetMta
 *   CcpCommandSetMta
 */

package winFlashTool.ccp;

import java.util.*;
import java.util.Set;
import java.util.HashSet;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;


/**
 * CCP command SET_MTA.
 */
public class CcpCommandSetMta extends CcpCommandBase
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCommandSetMta.class);

    /** Temporary storage of memory address to set with SET_MTA. Will become new MTA when
        the ECU acknowledges the command. */
    private int memoryAddr_;
    
    /**
     * A new instance of CcpCommandSetMta is created.
     */
    protected CcpCommandSetMta()
    {
        memoryAddr_ = 0x00000000;
        
    } /* CcpCommandSetMta.CcpCommandSetMta */


    /**
     * Inform the base class, which particular CCP commands are implemented by this class.
     *   @return
     * Get the set of enumerated values, which represent CCP commands that are implemented
     * by this derived class.
     */
    protected Set<CroCommandId> myCcpCmdIds()
    {
        final Set<CroCommandId> setOfCmdIds = new HashSet<CroCommandId>(1);
        setOfCmdIds.add(CroCommandId.SET_MTA);
        return setOfCmdIds;
    }
    
    /**
     * The CCP command is started. After return from start(), the caller will repeatedly
     * call step() - until step() indicates completion of the command.
     *   @param argAry
     * The argument list consists of two arguments:<p>
     *   - The memory address, which sub-sequent CCP command will relate to. It is a 32 Bit
     * value, expected as an Integer.
     *   - There are to target addresses, which can be set. Pass 0 or 1 as an Integer to
     * select either MTA0 or MTA1, respectively.
     */
    public void start(Object... argAry)
    {
        /* Parse argument list. */
        assert argAry.length == 2
               &&  argAry[0] instanceof Integer
               &&  argAry[1] instanceof Integer
             : "Bad argument list. Expect two Integer objects";
        memoryAddr_ = ((Integer)argAry[0]).intValue();
        final byte idxMta = ((Integer)argAry[1]).byteValue();

        /* Send CAN CRO message with command SET_MTA. */
        payloadCroAry_[0] = CroCommandId.SET_MTA.getCode();
        payloadCroAry_[2] = idxMta; /* The x in MTAx, x=0..1 */
        payloadCroAry_[3] = (byte)0; /* Address extension not used in PowerPC. */
        
        /* Memory address in MSB endianess. */ 
        payloadCroAry_[4] = (byte)((memoryAddr_ >> 24) & 0xFF);
        payloadCroAry_[5] = (byte)((memoryAddr_ >> 16) & 0xFF);
        payloadCroAry_[6] = (byte)((memoryAddr_ >>  8) & 0xFF);
        payloadCroAry_[7] = (byte)((memoryAddr_ >>  0) & 0xFF);
        sendCro(/*noContentBytes*/ 8);
        _logger.printf(Level.DEBUG, "CRO message SET_MTA(0x%06X) sent to ECU.", memoryAddr_);
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
            _logger.debug("ECU acknowledged SET_MTA.");
            
            /* Make new MTA available to other commands, e.g., DOWNLOAD and PROGRAM. */
            _Mta0 = memoryAddr_;
        }
        else if(resultTxRx != CcpCroTransmitter.ResultTransmission.PENDING)
        {
            /* The connect CRO/DTO exchange failed. The reason has been logged. Nothing
               else to do. */
            _errCnt.error();
            _logger.error("Can't set MTA in the ECU. See previous error messages"
                          + " for details."
                         );
        }
        else
        {
            /* DTO has not been received yet. We continue polling. */
        }
        
        return resultTxRx;
        
    } /* step */

} /* End of class CcpCommandSetMta definition. */




