/**
 * @file CcpCommandFactory.java
 * Constructor for CCP command objects.
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
/* Interface of class CcpCommandFactory
 *   CcpCommandFactory
 */

package winFlashTool.ccp;

import java.util.*;
import org.apache.logging.log4j.*;


/** Function signature of all CC commands. Required to implement the generic constructor
    CcpCommandFactory. */
@FunctionalInterface
interface CcpCommandMaker< TCcpCommandArgs extends CcpCommandArgs
                         , TCcpCommand extends CcpCommandBase
                         > {
    TCcpCommand make(TCcpCommandArgs args);
}

/**
 * Generic constructor for all CCP command objects.<p>
 *   All CCP command, which are used for one CCP communication channel, share some data, in
 * particular the CAN device. Object construction through this factory ensures that all
 * emitted command object get the same shared data set. The factory's own cosntructor takes
 * the shared data set and it equipes all emmitted command objects with this data set.
 */
final class CcpCommandFactory {
    /** The data set, which is shared between all CCP comands, which are created by the
        factory. */
    private final CcpCommandToolbox toolbox;
    
    /** The factory decides the "which command to create" by looking at the arguments
        of the CCP command - each command has its own, individual argument object. This
        maps associates a CCP command's constructor with the same commands argument class. */
    private final Map< Class<? extends CcpCommandArgs>
                     , CcpCommandMaker< ? extends CcpCommandArgs
                                      , ? extends CcpCommandBase
                                      >
                     > registry = new HashMap<>();

    /**
     * Create a new CCP command factory.<p>
     *   One an the same factory needs to be applied for all CCP commands, which operate on
     * the same CCP connection with an ECU. If several CCP connections are maintained at
     * the same time than the same number of factory objects will be required.
     *   @param toolbox
     * The data set, which is shared between all CCP comands, which are created by the
     * factory. 
     */
    public CcpCommandFactory(CcpCommandToolbox toolbox) {
        assert toolbox != null;
        this.toolbox = toolbox;
        
        /* Put the constructor of all known CCP commands into the map so that they can be
           created with public method create. */
        register(CcpCommandArgs.Connect.class, CcpCommandConnect::new);
        register(CcpCommandArgs.Disconnect.class, CcpCommandDisconnect::new);
        register(CcpCommandArgs.SetMta.class, CcpCommandSetMta::new);
        register(CcpCommandArgs.ClearMemory.class, CcpCommandClearMemory::new);
        register(CcpCommandArgs.Download.class, CcpCommandsDownloadProgram::new);
        register(CcpCommandArgs.Upload.class, CcpCommandUpload::new);
        register(CcpCommandArgs.Program.class, CcpCommandsDownloadProgram::new);
        register(CcpCommandArgs.DiagService.class, CcpCommandDiagService::new);
        register(CcpCommandArgs.CcpCommandDownloadKey.class, CcpCommandDownloadKey::new);
        
    } /* CcpCommandFactory */

    /**
     * A new association CCP command constructor by argument class is added to the map.
     *   @param argsType
     * The class of the arguments object of the CCP command. This class is the
     * identification of the CCP object to create.
     *   @param objMaker
     * The FunctionalInterface instance, which encapsulates and represents the constructor
     * of the CCP command.
     */
    private <TCcpCommandArgs extends CcpCommandArgs, TCcpCommand extends CcpCommandBase>
    void register( Class<TCcpCommandArgs> argsType
                 , CcpCommandMaker<TCcpCommandArgs, TCcpCommand> objMaker
                 ) {
        registry.put(argsType, objMaker);
    }

    /**
     * Constructor for new CCP command objects.
     *   @param ccpCmdArgs
     * The arguments for the constructor of the CCP command.<p>
     *   Each CCP command has its own class for its constructor arguments. Therefore the
     * passed object instance implicitly decides, which CCP command is created.
     */
    @SuppressWarnings("unchecked")
    <TCcpCommandArgs extends CcpCommandArgs, TCcpCommand extends CcpCommandBase> 
    TCcpCommand create(TCcpCommandArgs ccpCmdArgs) {
    
        /* Fetch the needed constructor from the map. The type of the arguments object is
           the key for the lookup. The returned value is the FunctionInterface object,
           whose method make is the needed constructor, or which calls it. (Creating the
           class and the instance of the FunctionInterface object is the magic of the Java
           compiler behind the syntax "CcpCommandSetMta::new" in the context of the
           function argument objMaker of method register and having type
           CcpCommandMaker.) */
        CcpCommandMaker<TCcpCommandArgs, TCcpCommand> maker = 
            (CcpCommandMaker<TCcpCommandArgs, TCcpCommand>)registry.get(ccpCmdArgs.getClass());
        assert maker != null: "No command registered for " + ccpCmdArgs.getClass();
        
        /* The rest is simple, we run the FunctionInterface instance and get the needed
           object, which is then still equipped with the toolbox before returning it. */
        final TCcpCommand newCcpCmd = maker.make(ccpCmdArgs);
        newCcpCmd.setToolbox(toolbox);
        return newCcpCmd;

    } /* create */
    
} /* CcpCommandFactory */

