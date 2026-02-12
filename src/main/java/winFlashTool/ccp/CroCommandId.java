/**
 * @file CroCommandId.java
 * Enumeration of supported CCP commands.
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
/* Interface of class CroCommandId
 *   getCode
 *   getCmdName
 *   fromCode
 */

package winFlashTool.ccp;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import peak.can.basic.TPCANMsg;


/**
 * Enumeration of supported CCP commands.
 */
enum CroCommandId {

    /** The IDs of the CCP commands in the CRO messages. */
    CONNECT((byte)0x01),
    SET_MTA((byte)0x02),
    CLEAR_MEMORY((byte)0x10),
    DOWNLOAD((byte)0x03),
    UPLOAD((byte)0x04),
    DOWNLOAD_6((byte)0x23),
    PROGRAM((byte)0x18),
    PROGRAM_6((byte)0x22),
    DIAG_SERVICE((byte)0x20),
    ACTION_SERVICE((byte)0x21),
    DISCONNECT((byte)0x07);

    private final byte cmdId_;
    private static final Map<Byte, CroCommandId> lookupMap_ = new HashMap<>();

    /**
     * An enumeration in Java is a number of static singleton objects, that represent
     * the enumerated values and which are automatically created once and forever.
     * After creation of all of these objects, we can create a hash map of all of these
     * values in order to later find them back by value.
     */
    static {
        /* Iterate all enumerated values and put them in a hash map. */
        for (CroCommandId cmd: values()) {
            lookupMap_.put(cmd.cmdId_, cmd);
        }
    }

    /**
     * An enumeration in Java is a number of static singleton objects, that represent
     * the enumerated values and which are automatically created once and forever. If
     * we don't rely on the default constructor but place our own here, then we can
     * extend these objects with additional functionality, e.g, setting a particular
     * value for enumerated values.
     *   @param cmdId
     * The byte value associated with a particular enumerated value from the
     * enumeration.
     */
    private CroCommandId(byte cmdId) {
        cmdId_ = cmdId;

        /* Note, from the constructor, it is not allowed to access static fields of the
           enum class. The constructor is called for all enumerated values before the
           static fields of the class are initialized and accessing a static field
           would mean dealing with potentially uninitialized data. We need to place the
           operation into a static block, which is executed after all constructor calls
           and after all initialization of the class' static data. */
        //lookupMap_.put(cmdId, this);
    }

    /**
     * Get the CCP command code (aka ID) from the enumerated value object.
     *   @return
     * CCP command code.
     */
    public byte getCode() {
        return cmdId_;
    }

    /**
     * Get the CCP command from the enumerated value object.
     *   @return
     * CCP command by human readable name.
     */
    public String getCmdName() {
        return toString();
    }

    /**
     * Get the very object, which represents the enumerated value, which has the given
     * CCP command code (aka ID).
     *   @param ccpCmdCode
     * The CCP command code.
     *   @return
     * Get the enumerated value object.
     *   @throws IllegalArgumentException
     * This runtime exception is thrown if ccpCmdCode is not the byte code of any
     * enumerated value.
     */
    public static CroCommandId fromCode(byte ccpCmdCode) {
        CroCommandId cmd = lookupMap_.get(ccpCmdCode);
        if (cmd == null) {
            throw new IllegalArgumentException("Unknown command ID: " + ccpCmdCode);
        }
        return cmd;
    }

    // Tmp to make it compilable
    public CcpCommandBase getCmd() {
        assert false;
        return null;
    }
} /* End of class CroCommandId definition. */