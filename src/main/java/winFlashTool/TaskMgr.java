/**
 * @file TaskMgr.java
 * Evaluate the command line with respect to the used demanded tasks.
 *
 * Copyright (C) 2026 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
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
/* Interface of class TaskMgr
 *   TaskMgr
 *   evaluateCmdLine
 *   isNormalFlashTaskSpecified
 *   isAnyTaskSpecified
 *   resetAfterUploadVersion
 *   resetAfterUpload
 *   resetAfterProgram
 *   resetAfterVerify
 *   resetAfterEraseOnly
 */

package winFlashTool;

import java.util.*;
import org.apache.logging.log4j.*;
import winFlashTool.basics.ErrorCounter;
import winFlashTool.applicationInterface.cmdLineParser.CmdLineParser;

/**
 * Evaluate the command line with respect to the used demanded tasks.
 */
class TaskMgr {
    /** The global logger object for all progress and error reporting. */
    private static final Logger _logger = LogManager.getLogger(TaskMgr.class);
    
    /* A single error counter is used for all operations. A reference to this error counter
       is passed to involved modules and objects. */
    final ErrorCounter errCnt_;

    /** Special task "Enumerate CAN devices" is demanded by command? */ 
    public boolean taskEnumCanDevices;
    
    /** Special task "Generate key pair for authentication" is demanded by command? */ 
    public boolean taskGenerateKeyPair;

    /** CCP communication task "Upload SW version of target" is demanded by command? */ 
    public boolean taskUploadVersion;

    /** CCP communication task "Upload data from target" is demanded by command? */ 
    public boolean taskUpload;

    /** CCP communication task "Erase, program and verify" is demanded by command? */ 
    public boolean taskProgram;

    /** CCP communication task "Verify" is demanded by command? */ 
    public boolean taskVerify;

    /** CCP communication task "Erase all flash ROM" is demanded by command? */ 
    public boolean taskEraseOnly;

    /** CCP communication task "Reset target ECU" is demanded by command? */ 
    public boolean taskReset;

    /** Argument of reset task: Shall we start the flashed application after reset? (FBL
        otherwise.) */
    public boolean resetToApp;

    /** Is the reset command executed at the end of the demanded task? */
    private boolean doReset_;

    /**
     * A new instance of TaskMgr is created.
     *   @param errCnt
     * The error counter to be used for problem reporting.
     */
    TaskMgr(ErrorCounter errCnt) {
        errCnt_ = errCnt;
    }

    /**
     * Set the public flags depending on the command line arguments.    
     *   @return
     * Get false if contradictory command line arguments were found. The application should
     * not start up in this case, as it is not clear, what to do.
     *   @param cmdLine
     * The command line parser object, which provides access to the command line arguments.
     */
    boolean evaluateCmdLine(CmdLineParser cmdLine) {
        boolean success = true;
        
        /* Check command line to find out, which tasks are commanded. */
        final String srecInputFileName = cmdLine.getString("srec-input-file")
                   , srecOutputFileName = cmdLine.getString("srec-output-file")
                   , newPrivKeyFileName = cmdLine.getString("generate-key-pair")
                   , resetEcu = cmdLine.getString("reset-target-ECU");
        final boolean eraseAll = cmdLine.getBoolean("erase-all")
                    , verifyOnly = cmdLine.getBoolean("verify-only")
                    , noVerify = cmdLine.getBoolean("no-verify");

        taskEnumCanDevices = cmdLine.getBoolean("enumerate-CAN-devices");
        taskGenerateKeyPair = newPrivKeyFileName != null;

        taskUploadVersion = cmdLine.getBoolean("upload-version-fbl");
        taskUpload = srecOutputFileName != null;
        taskProgram = srecInputFileName != null  && !verifyOnly;
        taskVerify = srecInputFileName != null  && verifyOnly;
        taskEraseOnly = eraseAll &&  srecInputFileName == null;
        if (resetEcu != null) {
            resetToApp = resetEcu.equalsIgnoreCase("APP");
            if (!taskEraseOnly || !resetToApp) {
                doReset_ = true;
            } else {
                success = false;
                doReset_ = false;
                errCnt_.error();
                _logger.error("Erasing flash ROM without programming new code must"
                              + " not be combined with resetting the target."
                             );
            }
        }
        
        taskReset = false;
        taskReset = doReset_ && !isNormalFlashTaskSpecified();
        
        /* We can filter for some typical user misunderstandings. */
        if (success && !isAnyTaskSpecified()) {
            success = false;
            errCnt_.error();
            _logger.error( "Nothing to do."
                           + " No srec file is specified on the command line. You normally"
                           + " need to use argument --srec-output-file and/or"
                           + " --srec-input-file to command an up- or download,"
                           + " respectively. Other options are --erase-all to erase all"
                           + " flash ROM, --upload-version-fbl or --reset-target-ECU."
                           + " Please use -h for help."
                         );
        } 
        if (verifyOnly &&  srecInputFileName == null) {
            success = false;
            errCnt_.error();
            _logger.error("Specifying --verify-only without giving an input srec file"
                          + " is pointless. Nothing to do."
                         );
        }
        if (verifyOnly && noVerify) {
            success = false;
            errCnt_.error();
            _logger.error("Specifying both, --verify-only and --no-verify, is pointless."
                          + " Nothing is done."
                         );
        }
        if (!taskUpload &&  cmdLine.getNoValues("address-range") > 0) {
            success = false;
            errCnt_.error();
            _logger.error("Specifying an address range for upload without specifying an"
                          + " output srec file is pointless. Upload is not performed."
                         );
        }
        if (taskEnumCanDevices && (taskUpload || taskProgram)) {
            errCnt_.warning();
            _logger.warn( "Please note, if command line argument --enumerate-CAN-devices"
                          + " is used then no up- or download is performed. Arguments"
                          + " --srec-output-file and --srec-input-file are ignored."
                        );
        }
        if (taskVerify && eraseAll) {
            success = false;
            errCnt_.error();
            _logger.error("Verifying the memory contents before deleting them seems"
                          + " suspicious and not by intention. The operation is denied."
                         );
        }

        return success;

    } /* evaluateCmdLine */
    
    /**
     * Does the user demand a "normal" task, which means communication via CCP with the
     * target ECU, or only one of the special tasks?
     *   @return
     * Get true for a normal CCP communication task.
     */
    boolean isNormalFlashTaskSpecified() {
        return taskUploadVersion
               || taskUpload
               || taskProgram
               || taskVerify
               || taskEraseOnly
               || taskReset;
    }
    
    /**
     * Did the user demand a task or do we have an incorrect command line?
     *   @return 
     * Get true if we know at least one task to execute.
     */
    private boolean isAnyTaskSpecified() {
        return isNormalFlashTaskSpecified()
               || taskEnumCanDevices
               || taskGenerateKeyPair;
    }
    
    /**
     * Query of the Upload Version task, whether it should end with a target reset.
     *   @return
     * Get true if the target should evetually be reset.
     */
    boolean resetAfterUploadVersion() {
        return doReset_
               && (taskUploadVersion
                   && !taskUpload
                   && !taskProgram
                   && !taskVerify
                   && !taskEraseOnly
                  );
    }
    
    /**
     * Query of the Upload task, whether it should end with a target reset.
     *   @return
     * Get true if the target should evetually be reset.
     */
    boolean resetAfterUpload() {
        return doReset_
               && (taskUpload
                   && !taskProgram
                   && !taskVerify
                   && !taskEraseOnly
                  );
    }
    
    /**
     * Query of the Erase, Program and Verify task, whether it should end with a target
     * reset.
     *   @return
     * Get true if the target should evetually be reset.
     */
    boolean resetAfterProgram() {
        return doReset_;
    }
    
    /**
     * Query of the Verify task, whether it should end with a target reset.
     *   @return
     * Get true if the target should evetually be reset.
     */
    boolean resetAfterVerify() {
        return doReset_;
    }
    
    /**
     * Query of the Erase all flash ROM task, whether it should end with a target reset.
     *   @return
     * Get true if the target should evetually be reset.
     */
    boolean resetAfterEraseOnly() {
        return doReset_;
    }
} /* TaskMgr */
