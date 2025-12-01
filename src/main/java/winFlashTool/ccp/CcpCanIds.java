/**
 * @file CcpCanIds.java
 * The CAN Ids of CCP CRO and DTO messages.
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
/* Interface of class CcpCanIds
 *   CcpCanIds
 */

package winFlashTool.ccp;

import org.apache.logging.log4j.*;
import peak.can.basic.*;

/**
 * The CAN Ids of CCP CRO and DTO messages.
 */
class CcpCanIds
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCanIds.class);

// @todo Need to become two time CanId
    /** The CAN ID of all CRO messages. */
    public final int canIdCro_;

    /** PCAN_MESSAGE_EXTENDED if canIdCro_ is an extended 29 Bit ID, PCAN_MESSAGE_STANDARD
        otherwise. */
    public final TPCANMode kindOfCroId_;

    /** The CAN ID of all CRO messages in textual representation. */
    public String canIdCroAsStr_ = null;

    /** The CAN ID of all DTO messages. */
    public final int canIdDto_;

    /** PCAN_MESSAGE_EXTENDED if canIdDto_ is an extended 29 Bit ID, PCAN_MESSAGE_STANDARD
        otherwise. */
    public final TPCANMode kindOfDtoId_;

    /** The CAN ID of all DTO messages in textual representation. */
    public String canIdDtoAsStr_ = null;

    /**
     * Set the CAN IDs of a CCP connection.
     *   @param canIdCro
     * The CAN ID of all CRO messages.
     *   @param isExtCroId
     * Pass true if an extended 29 Bit ID is used for the CRO messages and false if a 11
     * Bit ID is used.
     *   @param canIdDto
     * The CAN ID of all DTO messages. This CAN ID is required because the CAN recepion
     * filtering is accordingly configured in the CAN device by this method.
     *   @param isExtDtoId
     * Pass true if an extended 29 Bit ID is used for the DTO messages and false if a 11
     * Bit ID is used.
     */
    public CcpCanIds( int canIdCro
                    , boolean isExtCroId
                    , int canIdDto
                    , boolean isExtDtoId
                    )
    {
        canIdCro_    = canIdCro;
        kindOfCroId_ = isExtCroId? TPCANMode.PCAN_MODE_EXTENDED
                                 : TPCANMode.PCAN_MODE_STANDARD;
        canIdDto_    = canIdDto;
        kindOfDtoId_ = isExtDtoId? TPCANMode.PCAN_MODE_EXTENDED
                                 : TPCANMode.PCAN_MODE_STANDARD;

    } /* CcpCanIds.CcpCanIds */

    /**
     * Get the CAN ID of the CRO message.
     *   @return
     * Get the ID as raw number.
     */
    public int getCroCanId()
    {
        return canIdCro_;
    }

    /**
     * Get the CAN ID of the DTO message.
     *   @return
     * Get the ID as raw number.
     */
    public int getDtoCanId()
    {
        return canIdDto_;
    }

    /**
     * Get the message type (standard, extended) for the CRO message.
     *   @return
     * Get either TPCANMessageType.PCAN_MESSAGE_STANDARD or
     * TPCANMessageType.PCAN_MESSAGE_EXTENDED.
     */
    public TPCANMessageType getCroMsgType()
    {
        if(kindOfCroId_ == TPCANMode.PCAN_MODE_STANDARD)
            return TPCANMessageType.PCAN_MESSAGE_STANDARD;
        else
        {
            assert kindOfCroId_ == TPCANMode.PCAN_MODE_EXTENDED;
            return TPCANMessageType.PCAN_MESSAGE_EXTENDED;
        }
    }

    /**
     * Get the message type (standard, extended) for the DTO message.
     *   @return
     * Get either TPCANMessageType.PCAN_MESSAGE_STANDARD or
     * TPCANMessageType.PCAN_MESSAGE_EXTENDED.
     */
    public TPCANMessageType getDtoMsgType()
    {
        if(kindOfDtoId_ == TPCANMode.PCAN_MODE_STANDARD)
            return TPCANMessageType.PCAN_MESSAGE_STANDARD;
        else
        {
            assert kindOfDtoId_ == TPCANMode.PCAN_MODE_EXTENDED;
            return TPCANMessageType.PCAN_MESSAGE_EXTENDED;
        }
    }

    /**
     * Get the message mode (standard, extended) for the CRO message.
     *   @return
     * Get either TPCANMode.PCAN_MESSAGE_STANDARD or
     * TPCANMode.PCAN_MESSAGE_EXTENDED.
     */
    public TPCANMode getCroMsgMode()
    {
        return kindOfCroId_;
    }

    /**
     * Get the message mode (standard, extended) for the DTO message.
     *   @return
     * Get either TPCANMode.PCAN_MESSAGE_STANDARD or
     * TPCANMode.PCAN_MESSAGE_EXTENDED.
     */
    public TPCANMode getDtoMsgMode()
    {
        return kindOfDtoId_;
    }

    /**
     * Get the CAN ID of the CRO messages in textual representation.
     *   @return
     * The CAN ID in decimal and hex as String.
     */
    public String croIdToString()
    {
        if(canIdCroAsStr_ == null)
        {
            canIdCroAsStr_ = "" + canIdCro_
                             + (kindOfCroId_ == TPCANMode.PCAN_MODE_EXTENDED? "x": "")
                             + " (0x" + Integer.toHexString(canIdCro_)
                             + (kindOfCroId_ == TPCANMode.PCAN_MODE_EXTENDED? "x": "")
                             + ")";
        }
        return canIdCroAsStr_;
    }

    /**
     * Get the CAN ID of the DTO messages in textual representation.
     *   @return
     * The CAN ID in decimal and hex as String.
     */
    public String dtoIdToString()
    {
        if(canIdDtoAsStr_ == null)
        {
            canIdDtoAsStr_ = "" + canIdDto_
                             + (kindOfDtoId_ == TPCANMode.PCAN_MODE_EXTENDED? "x": "")
                             + " (0x" + Integer.toHexString(canIdDto_)
                             + (kindOfDtoId_ == TPCANMode.PCAN_MODE_EXTENDED? "x": "")
                             + ")";
        }
        return canIdDtoAsStr_;
    }
} /* End of class CcpCanIds definition. */









