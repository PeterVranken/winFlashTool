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
 *   setToolbox
 *   errCnt
 *   payloadCroAry
 *   payloadDtoAry
 *   mta0
 *   setMta0
 *   invalidateMta0
 *   isValidMta0
 *   sendCro
 *   checkRxDto
 *   isSkippedInDryRun
 *   getRequiredTimeoutCroTillDto
 *   start
 *   setup
 *   step
 */

package winFlashTool.ccp;

import java.util.*;
import java.util.Set;
import java.util.HashSet;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.can.CanDevice;
import peak.can.basic.TPCANMsg;
import java.util.HashSet;

/**
 * All actual, derived CCP commands need to share some data, in particular the CAN device
 * to use. The shared data is called the Toolbox of the commands.
 *   @param canDev
 * The initialized, ready to use CAN device for sending all CRO and receiving all DTO
 * messages.
 */
final class CcpCommandToolbox {
    /** The error counter to be used for error reporting. */
    final ErrorCounter errCnt_;

    /** An always available buffer to prepare the payload of a CRO, which is then sent out
        using sendCro(). */
    final byte[] payloadCroAry_;

    /** An always available buffer, which contains the payload of the last recently
        received DTO message. */
    final byte[] payloadDtoAry_;

    /** The CAN message representation from the PCAN Basic API of the last recently
        received DTO message. Note, it is _payloadDtoAry = _msgDto.getData(). */
    final TPCANMsg msgDto_;

    /** A value of the MTA, which can never occur in CCP processing. This value is assigned
        to mta0_ to indicate that the flash tool has no awareness of the MTA in the target. */
    final static long MTA_INVALID_VALUE = 0xFFFFFFFFFFFFFFFFl;

    /** The current memory address, initially set with SET_MTA and modified with the
        DOWNLOAD and PROGRAMM commands. */
    long mta0_;

    /** The CRO transmitter, which is globally used by all CCP protocol operations in
        this base class and the derived classes. */
    final CcpCroTransmitter croTransmitter_;

    /**
     * Create the new toolbox.<p>
     *   This toolbox will be owned by a CCP command factory and all CCP command, which are
     * created by the factory use this toolbox.
     *   @param croTransmitter
     * The CRO transmitter, which is used by all CCP protocol operations, using this
     * tolbox.
     *   @param errCnt
     * The error counter to be used for error reporting by all dependent CCP commands.
     */
    CcpCommandToolbox(CcpCroTransmitter croTransmitter, ErrorCounter errCnt) {
        errCnt_ = errCnt;
        payloadCroAry_ = new byte[8];

        /* Create a forever reused PCAN Basic message object for DTO reception and make its
           data buffer accessible via a field. */
        msgDto_ = new TPCANMsg();
        payloadDtoAry_ = msgDto_.getData();

        mta0_ = MTA_INVALID_VALUE;
        croTransmitter_ = croTransmitter;
    }
}


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

    /** The data, which is shared between all instances of the class, which have been
        created by the same factory object. */
    private CcpCommandToolbox toolbox_;

    /**
     * A new instance of CcpCommandBase is created.
     */
    protected CcpCommandBase()
    {
        toolbox_ = null;

    } /* CcpCommandBase.CcpCommandBase */


    /**
     * Add the toolbox to a (newly created) CCP command object.
     *   @param toolbox
     * The toolbox. To make it immutable, it is not allowed to ever use this method more
     * than once for a given object.
     */
    void setToolbox(CcpCommandToolbox toolbox)
    {
        if(toolbox_ == null) {
            toolbox_ = toolbox;
        } else {
            assert false;
        }
    } /* CcpCommandBase.setToolbox */


    /**
     * Get the @{linkplain CcpCommandToolbox#errCnt_ error counter} to use by this CCP
     * command.
     */
    protected ErrorCounter errCnt() {
        return toolbox_.errCnt_;
    }

    /**
     * Get a @{linkplain CcpCommandToolbox#payloadCroAry_ buffer to prepare the payload of
     * a CRO}.
     */
    protected byte[] payloadCroAry() {
        return toolbox_.payloadCroAry_;
    }

    /**
     * Get a @{linkplain CcpCommandToolbox#payloadDtoAry_ buffer} with the payload of the
     * last recently received DTO message.
     */
    protected byte[] payloadDtoAry() {
        return toolbox_.payloadDtoAry_;
    }

    /**
     * Get the current memory address @{linkplain CcpCommandToolbox#mta0_ MTA0}, which is
     * shared between all CCP commands emitted by the same factory.
     */
    protected long mta0() {
        assert isValidMta0(): "MTA0 requested although not known";
        return toolbox_.mta0_;
    }

    /**
     * Set the current memory address @{linkplain CcpCommandToolbox#mta0_ MTA0}, which is
     * shared between all CCP commands emitted by the same factory.
     */
    protected void setMta0(long mta0) {
        toolbox_.mta0_ = mta0;
    }

    /**
     * Set the @{linkplain CcpCommandToolbox#mta0_ MTA0} to an non-existing value to
     * indicate that the flash tool lost knowledge about the MTA 0 on the target, e.g,
     * after disconnect or when a diagnostic service is requested.
     */
    protected void invalidateMta0() {
        toolbox_.mta0_ = CcpCommandToolbox.MTA_INVALID_VALUE;
    }

    /**
     * Check if the @{linkplain CcpCommandToolbox#mta0_ MTA0} is currently set to a known
     * value.
     *   @return
     * Get true if the value returned by mta0() corresponds with the value MTA 0 on the
     * target.
     */
    protected boolean isValidMta0() {
        return toolbox_.mta0_ != CcpCommandToolbox.MTA_INVALID_VALUE;
    }

    /**
     * Send a CRO message. The payload of the CAN message is taken from _payloadCroAry
     *   @param noContentBytes
     * The number of meaningful payload bytes in _payloadCroAry. The remaining bytes will
     * be set to a don't care value.
     */
    protected void sendCro(int noContentBytes)
    {
        toolbox_.croTransmitter_.sendCro(toolbox_.payloadCroAry_, noContentBytes);
    }

    /**
     * Check for reception of the DTO, which belongs to the previously sent CRO. The
     * function result tells, whether or not the DTO has been received yet. If it has been
     * received, then the payload data is available in _payloadDtoAry.
     *   @return
     * Get the reception status for the DTO. This can be either succes or "still waiting"
     * or an error code.
     */
    protected CcpCroTransmitter.ResultTransmission checkRxDto()
    {
        return toolbox_.croTransmitter_.getDto(toolbox_.msgDto_);
    }

    /**
     * If the user enables the "dry run" of the flash application (a configuration test of
     * the tool setup), a CCP command is usually not send to the target. However,
     * significance of the dry rises, if CCP commands are not skipped or suppressed. CCP
     * commands, which don't have an impact on the state of flash of the target, may
     * override this method and return false - and they will be executed even in dry
     * run.<p>
     *   Consider implementing this method for harmless commands like CONNECT/DISCONNECT or
     * SET_MTA.
     */
    protected boolean isSkippedInDryRun() {
        return true;
    }

    /**
     * Get the timeout value for the time span between sending CRO and receiving DTO, that
     * is required by the CCP command.<p>
     *   The data streaming commands like DOWNLOAD and PROGRAM may reuire a short timeout,
     * CONNECT could be more tolerant and selected commands like CLEAR_MEMORY may even
     * require very high timeouts. (Which depends unfortuantely on the behavior of the
     * flash bootloader on the target MCU.)
     *   @return
     * The maximum time, which may elapse after sending the CRO until the DTO arrives. Unit
     * is Milliseconds.
     *   @note
     * The method is intended for being overridden by the actual commands. The default
     * implementation in the base class requests a timeout of 500 ms.
     */
    int getRequiredTimeoutCroTillDto() {
        return 500;
    }

    /**
     * The CCP command is started. After return from start(), the caller will repeatedly
     * call step() - until step() indicates completion of the command.
     *   @return
     * Normally, the method returns "pending" to indicate that the CCP communication has
     * been successfully initiated but is still ongoing. In this case, the other method
     * step() will be called as long as it indicates as still ongoing communication
     * process.<p>
     *   If the initialization fails, it'll return an error code. In this situation,
     * everything is done and step() won't be called.<p>
     *   In rare situations, it may even return success. CCP communication has successfully
     * completed and step() must not be called any more. This may happen, e.g., if a
     * pointless UPLOAD of zero Byte is commanded.
     */
    public final CcpCroTransmitter.ResultTransmission start() {
        /* Configure the CCP timeout as required by the actual, derived CCP command. */
        final int timeoutTillRxDtoInMs = getRequiredTimeoutCroTillDto();
        _logger.debug("Setting timeout CRO to DTO to {} ms.", timeoutTillRxDtoInMs);
        toolbox_.croTransmitter_.setTimeoutCroTillDto(timeoutTillRxDtoInMs);

        /* Call the initialization routine of the actual, derived CCP command. */
        return setup();
    }

    /**
     * The actual, derived CCP command is initialized.<p>
     *   On return from this method, the CCP command is ready for the first call of
     * step().<p>
     *   This method is called from the base class method start().
     *   @return
     * Normally, the method returns "pending" to indicate that the CCP communication has
     * been successfully initiated but is still ongoing. In this case, the other method
     * step() will be called as long as it indicates as still ongoing communication
     * process.<p>
     *   If the initialization fails, it'll return an error code. In this situation,
     * everything is done and step() won't be called.<p>
     *   In rare situations, it may even return success. CCP communication has successfully
     * completed and step() must not be called any more. This may happen, e.g., if a
     * pointless UPLOAD of zero Byte is commanded.
     */
    public abstract CcpCroTransmitter.ResultTransmission setup();

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




