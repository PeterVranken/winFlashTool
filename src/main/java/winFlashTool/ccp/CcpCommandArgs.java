/**
 * @file CcpCommandArgs.java
 * Definition of arguments for the different CCP commands.
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
/* Interface of class CcpCommandArgs
 *   Connect
 *   Disconnect
 *   SetMta
 *   ClearMemory
 *   Download
 *   Upload
 *   Program
 *   DiagService
 */

package winFlashTool.ccp;

import java.util.function.Supplier;
import java.util.function.LongSupplier;
import winFlashTool.digitalSignature.DigitalSignature;

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
     * The arguments of CCP command Disconnect.
     *   @param stationAddr
     * The 16 Bit station address of the CCP device, which we want to disconnect from.
     */
    record Connect(int stationAddr) implements CcpCommandArgs {
    }
    
    /**
     * The arguments of CCP command Disconnect.
     *   @param stationAddr
     * The 16 Bit station address of the CCP device, which we want to disconnect from.
     *   @param isEndOfSession
     * A CCP session can be paused or terminated. Pass true to terminate it.
     */
    record Disconnect(int stationAddr, boolean isEndOfSession) implements CcpCommandArgs {
    }
    
    /**
     * The arguments of CCP command SET_MTA.
     *   @param supplierAddress
     * A lambda, which provides the address where to subsequently program or erase some
     * bytes. The lambda is evaluated in setup(), when the transmission of CCP command
     * SET_MTA starts.
     *   @param addressExt
     * The target specific address extension. Only the least significant eight bit matter,
     * the rest is expected to be all zeros.<p>
     *   Caution, address extension are not supported and zero needs to be supplied.
     *   @param idxMta
     * CPP knows two memory transfer addresses. Which one is meant? The x in MTAx, x=0..1.
     */
    record SetMta( LongSupplier supplierAddress
                 , int addressExt
                 , int idxMta
                 ) implements CcpCommandArgs {
    }
    
    /**
     * The arguments of CCP command CLEAR_MEMORY.
     *   @param noBytesToErase
     * The minimum number of bytes to erase. (The target device may need to erase more
     * bytes in order to align with its flash sector address boundaries.)
     */
    record ClearMemory(int noBytesToErase) implements CcpCommandArgs {
    }
        
    /**
     * The arguments of CCP command DOWNLOAD.
     *   @param data
     * The bytes to program at current MTA0. Any number of bytes can be specified. The
     * implementation of the command will break it down into a series of #DOWNLOAD and
     * #DOWNLOAD_6 commands.<p>
     *   data may be null, if the data is provided later using method
     * CcpCommandsDownloadProgram.setData().
     */
    record Download(byte[] data) implements CcpCommandArgs {
    }
    
    /**
     * The arguments of CCP command DIAG_DOWNLOAD (in the context of downloading the key
     * for authentication).
     *   @param digitalSignature
     * This object is used for calculating the digital signature of the target provided
     * seed.
     *   @param supplierDataBuffer
     * A lambda, which provides the seed. The lambda is evaluated when the download begins.
     * Use case: The seed for the key calculation will dynamically depend on the result of
     * another, preceding CCP command.
     */
    record CcpCommandDownloadKey( DigitalSignature digitalSignature
                                , Supplier<byte[]> supplierSeed
                                ) implements CcpCommandArgs {
    }

    /**
     * The arguments of CCP command UPLOAD.
     *   @param supplierDataBuffer
     * A lambda, which provides the data buffer for the uploaded bytes. (The number of
     * bytes to upload is implicitly supplied by the size of the received buffer.) The
     * lambda is evaluated when the upload begins. Use case: The number of bytes to upload
     * may dynamically depend on the result of another, preceding CCP command.
     *   @param verify
     * Pass false for normal upload operation. The uploaded data is stored in byte array
     * data.<p>
     *   If verify is true then data needs to hold the expected memory contents. The
     * uploaded data is not stored inside data but compared to the contents of data. An
     * error is reported and the command is aborted when the first deviation is found.
     */
    record Upload( Supplier<byte[]> supplierDataBuffer
                 , boolean verify
                 ) implements CcpCommandArgs {
    }
    
    /**
     * The arguments of CCP command PROGRAM.
     *   @param data
     * The bytes to program at current MTA0. Any number of bytes can be specified. The
     * implementation of the command will break it down into a series of #PROGRAM and
     * #PROGRAM_6 commands.<p>
     *   data may be null, if the data is provided later using method
     * CcpCommandsDownloadProgram.setData().
     */
    record Program(byte[] data) implements CcpCommandArgs {
    }
    
    /**
     * The arguments of CCP command DIAG_SERVICE.
     *   @param serviceNum
     * The service number. Range is 0..255.
     *   @param argAry
     * The arguments as an array of up to 5 Byte. May be null if no args are required by the
     * service.
     */
    record DiagService(byte serviceNum, byte[] argAry) implements CcpCommandArgs {
    }
        
} /* End of interface CcpCommandArgs definition. */
