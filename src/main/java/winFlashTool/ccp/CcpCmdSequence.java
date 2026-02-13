/**
 * @file CcpCmdSequence.java
 * A sequence of of CCP commands to be executed, e.g., for flashing a program.
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
/* Interface of class CcpCmdSequence
 *   CcpCmdSequence
 *   erase
 *   eraseProgramAndVerify
 *   upload
 *   diagServiceGetVersion
 */

package winFlashTool.ccp;

import java.util.*;
import java.util.function.Supplier;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.*;
import winFlashTool.basics.Range;
import winFlashTool.srecParser.SRecord;
import winFlashTool.srecParser.SRecordSequence;
import winFlashTool.srecParser.MemoryMap;
import winFlashTool.srecParser.EraseSectorSequence;

/**
 * The representation of a sequence of of CCP commands to be executed, e.g., for flashing a
 * program.
 */
class CcpCmdSequence extends ArrayList<CcpCommandBase> {
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCmdSequence.class);

    /** This CCP command factory is used for all CCP commands, which will go into the
        command sequence. The use of a particular factory guarantees that all CCP command
        will make use of the right CAN bus and using the same configuration. */
    private final CcpCommandFactory ccpCmdFactory_;

    /** The diagnostic service number for uploading the FBL's version information. */
    private final byte DIAG_SN_UPLOAD_VERSION_FBL = 0x00;

    /** The diagnostic service number for uploading the seed for the authorization. */
    private final byte DIAG_SN_UPLOAD_SEED = 0x01;

    /**
     * A new instance of CcpCmdSequence is created.
     *   @param ccpCmdFactory
     * This CCP command factory is used for all CCP commands, which will go into the
     * command sequence. The use of a particular factory guarantees that all CCP command
     * will make use of the right CAN bus and using the same configuration.
     */
    CcpCmdSequence(CcpCommandFactory ccpCmdFactory)
    {
        super(10);
        ccpCmdFactory_ = ccpCmdFactory;

    } /* CcpCmdSequence.CcpCmdSequence */

    /**
     * Add the CCP command sequence needed for erasing a list of flash flash blocks.
     *   @param eraseSectorSequence
     * The list of address ranges to erase. Each contained range is the union of one or
     * more flash blocks.
     */
    void erase(EraseSectorSequence eraseSectorSequence) {
        /* CCP Connect and disconnect are handled outside of the command sequence. */

        /* Add a CCP erase command to the sequence for each sector in the list. */
        for (Range eraseRange: eraseSectorSequence) {
            /* CCP's CLEAR_MEMORY operates at the bytes from MTA0. */
            final CcpCommandArgs.SetMta argsSetMta =
                        new CcpCommandArgs.SetMta( /*supplierAddress*/ () -> eraseRange.from()
                                                 , /*addressExt*/ 0
                                                 , /*idxMta*/ 0
                                                 );
            add(ccpCmdFactory_.create(argsSetMta));

            /* Our address ranges apply long for addresses. Not to allow addresses
               outside the 32 Bit adddress range but only to make even the last address
               0xFFFFFFFF easily handable without overflow. Ranges store the end
               address plus one, so using "int" a Range till the end of the address
               space would have the end address 0. Moreover, comparing addresses would
               suffer from the signedness of int. We never use the upper 32 bits in
               long, with the only exception of bit 32 for a range till the end of the
               address space. */
            // TODO This is more complex, signedness means that we can only erase and
            // program sectors of up to 2 GB, which is not actual a limitation but
            // should be handled properly by error message and early rejection of the
            // data set.
            assert eraseRange.size() <= Integer.MAX_VALUE
                 : "The implementation of the flash tool supports only the 32 Bit address"
                   + " range";
            final int noBytesToEraseAtMta = (int)eraseRange.size();
            final CcpCommandArgs.ClearMemory argsClear =
                                    new CcpCommandArgs.ClearMemory(noBytesToEraseAtMta);
            add(ccpCmdFactory_.create(argsClear));

        } /* for (All address ranges to erase) */
    } /* erase */

    /**
     * Add the CCP command sequence needed for erasing the flash and re-programming a given
     * binary.
     *   @param doErase
     * If true, then the task begins with erasure of those flash blocks, which are touched
     * by the data in program.
     *   @param doProgram
     * If true, then the task programs the flash array with the data in program.
     *   @param eraseAll
     * Set this switch to true to let the FBL erase all managed flash ROM, not only the
     * portions needed to house the program.
     *   @param doVerify
     * If true, then the programming is followed by an upload for verification of the
     * programmed data. The execution time of the CCP protocol sequence is roughly
     * doubled.\n
     *   This flag can be used with program being false. Then only a verify step is
     * executed. It is allowed but barely useful to have doErase set at the same time.
     *   @param program
     * The representation of the memory area(s) to erase and program.
     */
    void eraseProgramAndVerify( boolean doErase
                              , boolean eraseAll
                              , boolean doProgram
                              , boolean doVerify
                              , MemoryMap program
                              ) {
        /* CCP Connect and disconnect are handled outside of the command sequence. */

        /* Add a CCP erase command to the sequence for each sector in the list. */
        if (doErase) {
            erase(eraseAll? program.eraseAllSectorSequence(): program.eraseSectorSequence());
        }

        /* Add a CCP program command to the sequence for each sector in the list. */
        if (doProgram) {
            for (SRecord section: program.srecSequence()) {
                /* The CCP PROGRAM and PROGRAM_6 commands operate sequentially at the
                   initially set MTA0. We need to refresh the MTA0 for each sector, because
                   different sectors typically have a gap in between them. */
                final CcpCommandArgs.SetMta argsSetMta =
                            new CcpCommandArgs.SetMta( /*supplierAddress*/ () -> section.from()
                                                     , /*addressExt*/ 0
                                                     , /*idxMta*/ 0
                                                     );
                add(ccpCmdFactory_.create(argsSetMta));

                final CcpCommandArgs.Program argsPrg =
                                                new CcpCommandArgs.Program(section.data());
                add(ccpCmdFactory_.create(argsPrg));

            } /* for (All programm sections to download and program) */
        } /* if(Do we need to program the flash ROM?) */

        /* If desired, add a CCP upload-and-verify command to the sequence for each sector
           in the list. */
        if (doVerify) {
            for (SRecord section: program.srecSequence()) {
                /* The CCP UPLOAD commands operate sequentially at the initially set MTA0.
                   We need to refresh the MTA0 for each sector, because different sectors
                   typically have a gap in between them. */
                final CcpCommandArgs.SetMta argsSetMta =
                            new CcpCommandArgs.SetMta( /*supplierAddress*/ () -> section.from()
                                                     , /*addressExt*/ 0
                                                     , /*idxMta*/ 0
                                                     );
                add(ccpCmdFactory_.create(argsSetMta));

                final CcpCommandArgs.Upload argsPrg =
                        new CcpCommandArgs.Upload(() -> section.data(), /*isVerify*/ true);
                add(ccpCmdFactory_.create(argsPrg));

            } /* for (All memory sections to upload and verify) */
        } /* if (Verification of programmed data demanded?) */
    } /* eraseProgramAndVerify */

    /**
     * Add the CCP command sequence needed for uploading data from the flash.
     *   @param memAreas
     * The representation of the memory area(s) to upload.
     */
    void upload(Iterable<SRecord> memAreas) {
        /* CCP Connect and disconnect are handled outside of the command sequence. */

        /* Add a CCP upload command to the sequence for each sector in the list. */
        for (SRecord section: memAreas) {
            /* The CCP UPLOAD commands operate sequentially at the initially set MTA0. We
               need to refresh the MTA0 for each sector, because different sectors
               typically have a gap in between them. */
            final CcpCommandArgs.SetMta argsSetMta =
                            new CcpCommandArgs.SetMta( /*supplierAddress*/ () -> section.from()
                                                     , /*addressExt*/ 0
                                                     , /*idxMta*/ 0
                                                     );
            add(ccpCmdFactory_.create(argsSetMta));

            final CcpCommandArgs.Upload argsUpload =
                        new CcpCommandArgs.Upload(() -> section.data(), /*isVerify*/ false);
            add(ccpCmdFactory_.create(argsUpload));

        } /* for (All memory sections to upload) */
    } /* upload */

    /**
     * Add the CCP command sequence needed for requesting the FBL version designation with
     * a diagnostic service.
     *   @return
     * Get a lambda object, which has the method get to fetch the uploaded version
     * information as a String after completion of the CCP communication.
     */
    Supplier<String> diagServiceGetVersion() {
        /* CCP Connect and disconnect are handled outside of the command sequence. */

        /* We request the upload of the FLB's version string. */
        final CcpCommandArgs.DiagService argsDiagService =
                    new CcpCommandArgs.DiagService( /*serviceNum*/ DIAG_SN_UPLOAD_VERSION_FBL
                                                  , /*argAry*/ null
                                                  );
        final CcpCommandDiagService ccpCmdDiagService = ccpCmdFactory_.create(argsDiagService);
        add(ccpCmdDiagService);

        /* Add a CCP upload command, which fetches the response of the diagnostic service.
           The MTA has already been set by the DIAG_SERVICE. */
        final CcpCommandArgs.Upload argsUpload =
                        new CcpCommandArgs.Upload( /*supplierDataBuffer*/
                                                   () -> ccpCmdDiagService.getServiceResult()
                                                 , /*isVerify*/ false
                                                 );
        add(ccpCmdFactory_.create(argsUpload));

        /* The returned lambda object stores a reference to the CCP DIAG_SERVICE command
           object. Once it has finished, it will contain the uploaded result data, which
           the lambda can query and return after conversion to String. */
        return () -> {
            final byte[] dataBuffer = ccpCmdDiagService.getServiceResult();
            if (dataBuffer != null) {
                return new String(dataBuffer);
            } else {
                return "";
            }
        };
    } /* diagServiceGetVersion */

    /**
     * Add the CCP command sequence needed for authentication (request and upload seed,
     * downlaod key).
     */
    void diagServiceAuthenticate() {
        /* CCP Connect and disconnect are handled outside of the command sequence. */

        /* We request the upload of the seed. */
        final CcpCommandArgs.DiagService argsDiagService =
                            new CcpCommandArgs.DiagService( /*serviceNum*/ DIAG_SN_UPLOAD_SEED
                                                          , /*argAry*/ null
                                                          );
        final CcpCommandDiagService ccpCmdDiagService = ccpCmdFactory_.create(argsDiagService);
        add(ccpCmdDiagService);

        /* We create a supplier object, which provides the length of the result data of the
           DIAG_SERVICE command and the buffer, where to put the uploaded result data into. */
        final Supplier<byte[]> supplierDiagServiceResultBuf = () -> {
            return ccpCmdDiagService.getServiceResult();
        };

        /* We create a supplier object, which can later fetch the seed from the at that
           time uploaded response of the CCP command DIAG_SERVICE. */
        final Supplier<byte[]> supplierSeed = () -> {
            /* The diagnostic service returns the 4 Byte seed followed by the 4 Byte upload
               address. (Upload length is fixed and known due to the chosen crypto
               algorithm.) */
            final byte[] diagServiceResponse = ccpCmdDiagService.getServiceResult()
                       , seed = new byte[4];
            if (diagServiceResponse.length == 8) {
                seed[0] = diagServiceResponse[0];
                seed[1] = diagServiceResponse[1];
                seed[2] = diagServiceResponse[2];
                seed[3] = diagServiceResponse[3];
            }
            return seed;
        };
        
        /* We create a supplier object, which can later fetch the upload address for the
           key from the at that time uploaded response of the CCP command DIAG_SERVICE. */
        final LongSupplier supplierMta = () -> {
            /* The diagnostic service returns the 4 Byte seed followed by the 4 Byte upload
               address (big endian). (Upload length is fixed and known due to the chosen
               crypto algorithm.) */
            final byte[] diagServiceResponse = ccpCmdDiagService.getServiceResult();
            final long mta;
            if (diagServiceResponse.length == 8) {
                mta = (long)((((((((int)diagServiceResponse[4] & 0xFF) << 8)
                                 | ((int)diagServiceResponse[5] & 0xFF)
                                ) << 8
                               )
                               | ((int)diagServiceResponse[6] & 0xFF)
                              ) << 8
                             )
                             | ((int)diagServiceResponse[7] & 0xFF)
                            );
            } else {
                /* We set the MTA to an invalid value so that SET_MTA is informed about the
                   failure. */
                mta = 0xFFFFFFFFFFFFFFFFl;
            }
_logger.info("Supply this mta: {}", mta);
            return mta;
        };
        
        /* Add a CCP upload command, which fetches the response of the diagnostic service.
           The MTA has already been set by the DIAG_SERVICE command. */
        final CcpCommandArgs.Upload argsUpload =
                                    new CcpCommandArgs.Upload( supplierDiagServiceResultBuf
                                                             , /*isVerify*/ false
                                                             );
        add(ccpCmdFactory_.create(argsUpload));
        
        /* Add a CCP SET_MTA command to set the upload address for the key in the target. */
        final CcpCommandArgs.SetMta argsSetMta = new CcpCommandArgs.SetMta( supplierMta
                                                                          , /*addressExt*/ 0
                                                                          , /*idxMta*/ 0
                                                                          );
        add(ccpCmdFactory_.create(argsSetMta));

        /* Add a CCP DOWNLOAD command to transfer the key to the target. */
        final CcpCommandArgs.CcpCommandDownloadKey argsDownload = 
                                        new CcpCommandArgs.CcpCommandDownloadKey(supplierSeed);
        add(ccpCmdFactory_.create(argsDownload));
        
    } /* diagServiceAuthenticate */

} /* End of class CcpCmdSequence definition. */