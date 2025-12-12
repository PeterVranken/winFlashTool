/**
 * @file CcpCommandArgs.java
 * Definition of arguments for the different CCP commands.
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
/* Interface of class CcpCommandArgs
 *   SetMta
 *   Upload
 *   ClearMemory
 */

package winFlashTool.ccp;

/**
 * Definition of arguments for the different CCP commands.<p>
 *   Interface CcpCommandArgs creates an empty base for all CCP commands' arguments. As it
 * is empty, no restrictions are imposed on the different arguments. The interface is
 * sealed, which means that it is not possible to use it externally to derive further CCP
 * command arguments. All of them need to be specified down here.<p>
 *   The actual arguments, which implement the interface, are defined inside the interface
 * definition. Records are used instead of ordinary classes as this minimizes the typing
 * overhead. A record is a class, where you typically just write the constructor prototype -
 * therefore the braces are typically empty. The record implicitly has a field for each
 * constructor argument and a getter of same name to later access the field. The fields are
 * immutable, they can be set only once via the constructor.
 *   @remark
 * Nesting the records implies that the class name is qualified by the interface name,
 * e.g., CcpCommandArgs.SetMta. Therefore, the record name can be concise.
 *   @remark
 * Nested records are supported since Java 16.
 *   @remark
 * Keyword sealed is used without counterpart permits so that all CCP command arguments
 * need to be defined inside this file.
 */
sealed interface CcpCommandArgs {

    /**
     * The arguments of CCP command SET_MTA.
     *   @param address
     * The byte address, where to subsequently program or erase some bytes.
     *   @param addressExt
     * The target specific address extension. Only the least significant eight bit matter,
     * the rest is expected to be all zeros.<p>
     *   Caution, address extension ar e not supported and zero needs to be supplied.
     *   @param idxMta
     * CPP knows two memory transfer addresses. Which one is meant? The x in MTAx, x=0..1.
     */
    record SetMta(int address, int addressExt, int idxMta) implements CcpCommandArgs {
    }
    
    /**
     * The arguments of CCP command UPLOAD.
     *   @param address
     * The byte address, where to program the bytes.
     *   @param data
     * The up to five bytes to program at address.
     */
    record Upload(int address, byte[] data) implements CcpCommandArgs {
    }
    
    /**
     * The arguments of CCP command CLEAR_MEMORY.
     *   @param address
     * The address of the first byte to erase.
     *   @param noBytes
     * The number of bytes to erase.
     */
    record ClearMemory(int address, int length) implements CcpCommandArgs {
    }
        
} /* End of interface CcpCommandArgs definition. */
