/**
 * @file CcpCmdSequence.java
 * A sequence of of CCP commands to be executed, e.g., for flashing a program.
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
/* Interface of class CcpCmdSequence
 *   CcpCmdSequence
 *   eraseAndProgram
 */

package winFlashTool.ccp;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.Range;
import winFlashTool.srecParser.SRecord;
import winFlashTool.srecParser.MemoryMap;

/**
 * The representation of a sequence of of CCP commands to be executed, e.g., for flashing a
 * program.
 */
class CcpCmdSequence extends ArrayList<CcpCommandBase>
{
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(CcpCmdSequence.class);

    /** This CCP command factory is used for all CCP commands, which will go into the
        command sequence. The use of a particular factory guarantees that all CCP command
        will make use of the right CAN bus and using the same configuration. */
    private final CcpCommandFactory ccpCmdFactory_;
    
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
     * Add the CCP command sequence needed for erasing the flash and re-programming a given
     * binary.
     *   @param program
     * The representation of the memory area(s) to earse and program.
     */
    void eraseAndProgram(MemoryMap program) {
        /* CCP Connect and disconnect are handled outside of the command sequence. */
        
        /* Add an CCP erase command sequence for each sector in the list. */
        for (Range eraseRange: program.eraseSectorSequence()) {
            /* CCP's CLEAR_MEMORY operates at the bytes from MTA0. */
            final CcpCommandArgs.SetMta argsSetMta =
                                    new CcpCommandArgs.SetMta( /*address*/ eraseRange.from()
                                                             , /*addressExt*/ 0
                                                             , /*idxMta*/ 0
                                                             );
            add(ccpCmdFactory_.create(argsSetMta));

            /* Our address ranges apply long for addresses. Not to allow addresses
               outside the 32 Bit adddress range but only to make even the last address
               0xFFFFFFFF easily handable without overflow. Ranges store the end address
               plus one, so using "int" a Range till the end of the address space would
               have the end address 0. Moreover, comparing addresses would suffer from the
               signedness of int. We never use the upper 32 bits in long, with the only
               exception of bit 32 for a Range till the end of the address space. */
            // TODO This is more complex, signedness means that we can only erase and
            // program sectors of up to 2 GB, which is not actual a limitation but should
            // be handled properly by error message and early rejection of the data set.
            assert eraseRange.size() <= Integer.MAX_VALUE
                 : "The implementation of the flash tool supports only the 32 Bit address"
                   + " range";
            final int noBytesToEraseAtMta = (int)eraseRange.size();
            final CcpCommandArgs.ClearMemory argsClear =
                                        new CcpCommandArgs.ClearMemory(noBytesToEraseAtMta);
            add(ccpCmdFactory_.create(argsClear));

        } /* for (All flash block to erase) */
        
        /* Here, we can add a blank test. */
        
        /* Add an CCP erase command sequence for each sector in the list. */
        for (SRecord section: program.srecSequence()) {
            /* The CCP PROGRAM an PROGRAM_6 commands operate sequentially at the initially
               set MTA0. We need to refresh the MTA0 for each sector, because different
               sectors typically have a gap in between them. */
            final CcpCommandArgs.SetMta argsSetMta =
                                    new CcpCommandArgs.SetMta( /*address*/ section.from()
                                                             , /*addressExt*/ 0
                                                             , /*idxMta*/ 0
                                                             );
            add(ccpCmdFactory_.create(argsSetMta));
            
            final CcpCommandArgs.Program argsPrg = new CcpCommandArgs.Program(section.data());
            add(ccpCmdFactory_.create(argsPrg));

        } /* for (All programm sections to download and program) */
    }
} /* End of class CcpCmdSequence definition. */






