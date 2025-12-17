/**
 * @file Log4j2Configurator.java
 * The configuration of the log4j 2 logger is done in this class.
 *
 * Copyright (C) 2015-2025 Peter Vranken (mailto:Peter_Vranken@Yahoo.de)
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
/* Interface of class Log4j2Configurator
 *   configLog4j
 *   Log4j2Configurator
 *   defineArguments
 *   getUseStdConfigSequence
 *   getLogFileName
 *   getLogLevel
 *   getLogPattern
 */

package winFlashTool.applicationInterface.loggerConfiguration;

import java.util.*;
import java.io.*;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.*;
import winFlashTool.applicationInterface.cmdLineParser.CmdLineParser;
import winFlashTool.basics.ErrorCounter;

/**
 * Initialize the application logging with an appropriate configuration.
 *   This class is a bit special in use. It has a constructor but the actual operation is
 * done as side effect of the intended one and only call of the constructor. The new
 * object created by this call is of no interest and can be discarded immediately.\n
 *   The class has static members to prepare the command line evaluation as far as logging
 * is concerned. After defining the related command line arguments the command line can be
 * parsed (externally) and the parse result is passed to this class' constructor, which
 * generates a temporary object with the according settings. The temporary object is passed
 * to the render process. A temporary XML file with the command line demanded configuration
 * is written and log4j 2 is initialized with this configuration. The temporary object and
 * XML file become immediately obsolete.\n
 *   The log4j 2 initialization sequence is performed in the instance of the first logger
 * creation. Consequently, the mechanism implemented in the constructor of this class will
 * succeed only if there is no use of a log4j 2 logger prior to its call. The normal use
 * case is to call this class in the main function as part of the application
 * initialization. If so, then the typical code pattern of using log4j must not be applied
 * to the main class: It's common practice to have a final static logger object in each
 * class, which is initialized with the class' name. This must hence not be done in the
 * main class; loading of the main class requires initialization of its static objects and
 * the log4j class would be initialized before the program flow reaches the code
 * implemented here. The main class needs to use a none final static logger with initial
 * value null. Only after running the log4j initialization with this class it'll fetch a
 * logger object and assign it to its static logger variable.\n
 *   For the same reasons, there must be no static field in the application main class,
 * which is initialized to a value other than null if the field's class has a statically
 * initialized log4j logger. This would again mean to load the log4j class at load time of
 * the main class.<p>
 *   Remark: The use of StringTemplate V4 for generation of the XML code requires the
 * existence of a (temporary) object although the operation of this class actually is of
 * static nature. An alternative could be a private sub-class and an object of this class,
 * which is created in a static member of the visible class. This would avoid the unusual
 * call of the class constructor.
 */

public class Log4j2Configurator
{
    /** The logger instance for this class. It is only initialized after we have prepared
        the configuration. By default it is set to null. */
    private static Logger _logger = null;

    /** Boolean Flag: Use unbiased log4j2 configuration sequence. */
    private boolean useStdConfigSequence_ = true;

    /** The name of the logFile. Used from the StringTemplate V4 template when generating
        the XML configuration code. null means not to write a log file at all. This is the
        default behavior. */
    public String logFileName_ = null;

    /** The log level as a String. Used from the StringTemplate V4 template when generating
        the XML configuration code. */
    public String logLevel_ = null;

    /** The log level in case of a wrongly specified value. */
    private static final Level _logLevelDefault = Level.INFO;

    /** The log4j2 message pattern used for the file appender. Used from the StringTemplate
        V4 template when generating the XML configuration code. */
    public String logPattern_ = null;

    /** The default look and feel of logged messages. Can be overridden by command line. */
    private static final String _layoutPatternDefault = 
                                            "%7r %-5level %c{-3}: %msg%n%throwable";
    //"%d %-5p - %m%n";


    /**
     * Configure log4j for this plug-in. The log channel and the layout of the logged lines
     * are set.
     *   @param logLevelStr
     * The verbosity level of all logging. Additionally to the normal, known values
     * "TRACE", "DEBUG", "INFO", "WARN", "EEROR" and "FATAL", the additional value
     * "default" is allowed, which means that no configuration of log4j is done but log4j's
     * default configuration is applied, which founds on a spectrum of possible external
     * configuration and property files or directly on Java properties. If "default" is
     * specified then all other arguments become meaningless.
     *   @param layoutPattern
     * The format string for the look of a single logged message. See
     * https://logging.apache.org/log4j/2.x/manual/layouts.html for details.
     *   @param logFileName
     * The name and path of a log file or null if no log file is needed. If not null then
     * the log file will contain the same output as is written to the console.
     *   @param xmlCfgFileName
     * The resulting configuration of log4j can be dumped as an XML file. The format is as
     * needed for an external configuration file (see \a level, value "default") and the
     * dumped file can be used as starting point for a more complex configuration of log4j.
     * If needed, this parameter may state the name and path of the XML file. If \a
     * xmlCfgFileName is null then no XML file is written.
     *   @param errCnt
     * The error counter to be used during the operation.
     */
    static private void configLog4j( String logLevelStr
                                   , String layoutPattern
                                   , String logFileName
                                   , String xmlCfgFileName
                                   , ErrorCounter errCnt
                                   )
    {
        /* Validate the user input against known enum values. */
        String badLevelStr = null;
        Level logLevel = Level.getLevel(logLevelStr.toUpperCase());
        if(logLevel == null)
        {
            badLevelStr = logLevelStr;
            logLevel = _logLevelDefault;
        }

        ConfigurationBuilder<BuiltConfiguration> cfgBuilder
                                    = ConfigurationBuilderFactory.newConfigurationBuilder();

        LayoutComponentBuilder layout = cfgBuilder.newLayout("PatternLayout");
        /* %throwable: The call stack in case of exceptions. Normally the empty string.
             See https://logging.apache.org/log4j/2.x/manual/layouts.html for details. */
        layout.addAttribute("pattern", layoutPattern);

        /* Create a Console type of appender named stdout. The name is required later to
           specifically configure the appender. */
        AppenderComponentBuilder consoleAppender = cfgBuilder.newAppender( "consoleAppender"
                                                                         , "Console"
                                                                         );
        consoleAppender.add(layout);
        cfgBuilder.add(consoleAppender);

        /* Create a File type of appender named stdout. The name is required later to
           specifically configure the appender. We can directly set the file name.
             Note, if the application logs huge amounts of output then a file appender of
           type RollingFile could suit better. */
        if(logFileName != null)
        {
            AppenderComponentBuilder fileAppender = cfgBuilder.newAppender( "fileAppender"
                                                                          , "File"
                                                                          );
            fileAppender.addAttribute("fileName", logFileName);
            fileAppender.add(layout);
            cfgBuilder.add(fileAppender);
        }

        /* See class FilterComponentBuilder flow and cfgBuilder.newFilter() for creating
           filters, which can be attached to appenders in order to control their output. */

        /* Attach the new appenders to the global root logger. All loggers in the plug-in
           will inherit its configuration. */
        RootLoggerComponentBuilder rootLogger = cfgBuilder.newRootLogger(logLevel);
        rootLogger.add(cfgBuilder.newAppenderRef("consoleAppender"));
        if(logFileName != null)
            rootLogger.add(cfgBuilder.newAppenderRef("fileAppender"));
        cfgBuilder.add(rootLogger);

        Configurator.initialize(cfgBuilder.build());

        /* Now we get a logger instance for this class. This must be the first use of
           log4j2 in this Java application - only then it'll cause the initialization with
           the prepared settings. This condition can not be tested here. To meet this
           demand one will run this class early in the main class and will not initialize a
           static logger variable in that class. */
        assert _logger == null;
        _logger = LogManager.getLogger(Log4j2Configurator.class);

        if(badLevelStr != null)
        {
            errCnt.warning();
            _logger.warn("{} is not a valid verbosity level of logging. Use --help to"
                         + " get an overview of supported values. Level INFO is used"
                         + " instead."
                        , badLevelStr
                        );
        }

        /* For cross checking and as starting point for more complex, externally provided
           log4j configurations, the assembled configuration can be printed as XML code. */
        if(xmlCfgFileName != null)
        {
            try
            {
                FileOutputStream xmlCfgFile = new FileOutputStream(xmlCfgFileName);
                cfgBuilder.writeXmlConfiguration(xmlCfgFile);
                _logger.info( "The log4j configuration has been written to file {}."
                            , xmlCfgFileName
                            );
            }
            catch(java.io.IOException ex)
            {
                final String excMsg = ex.getMessage();
                errCnt.error();
                _logger.error( "Can't write log4j configuration to XML file {}.{}"
                             , xmlCfgFileName
                             , excMsg != null? " " + excMsg: ""
                             );
            }
        }
    } /* configLog4j */


    /**
     * The one and only temporarily required instance of Log4j2Configurator is created. The
     * new object contains all command line arguments, which are related to application
     * logging; see related getters.<p>
     *   The object creation process configures the logger as a kind of side effect.
     *   @param clp
     * The command line parser object, which has already successfully parsed the
     * application command line. This implicitly means, that the other method {@link
     * #defineArguments} has been called before.
     *   @param errCnt
     * Configuration problems are counted in this error counter.
     */
    public Log4j2Configurator(CmdLineParser clp, ErrorCounter errCnt)
    {
        String configXml = null;
        useStdConfigSequence_ = clp.getBoolean("V");
        if(!useStdConfigSequence_)
        {
            logFileName_ = clp.getString("l");
            logPattern_  = clp.getString("p");
            logLevel_    = clp.getString("v");

            /* Validate the user input. */
            if(logPattern_ == null  ||  logPattern_.length() == 0)
                logPattern_ = _layoutPatternDefault;
        
            configLog4j(logLevel_, logPattern_, logFileName_, /*xmlCfgFileName*/ null, errCnt);
        }
        else        
        {
            /* Programatical configuration of logging by command line arguments is not
               desired. We create a logger in the standard way, which means configuration
               by XML file. */
            assert _logger == null;
            _logger = LogManager.getLogger(Log4j2Configurator.class);
        }
        
        if(useStdConfigSequence_)
        {
            if(clp.getBoolean("V"))
            {
                /* The standard configuration is wanted - just confirm it. The user should
                   know, what to do. */
                _logger.debug( "No user specified log settings are applied. The log4j 2"
                               + " standard configuration process is used."
                             );
            }
            else
            {
                /* The standard configuration is a fallback and there might be no
                   configuration. Give feedback. */
                System.err.println
                           ( "Previous errors in the log4j2 configuration cause the use"
                             + " of the log4j2 standard configuration sequence. See"
                             + " http://logging.apache.org/log4j/2.x/manual/configuration.html"
                             + " for details about properly configuring log4j 2."
                           );
            }
        }

    } /* configureLog4j */



    /**
     * Define all command line arguments, which relate to the logger configuration.\n
     *   This static method needs to be called prior to the constructor of the one and only
     * temporarily required object.
     *   @param clp
     * The command line parser object.
     */
    static public void defineArguments(CmdLineParser clp)
    {
        clp.defineArgument( "V"
                          , "use-standard-log4j2-configuration"
                          , /* cntMax */ 1
                          , "Use the standard configuration sequence of log4j2. See"
                            + " http://logging.apache.org/log4j/2.x/manual/configuration.html"
                            + " for details."
                            + " If given then no programmatic configuration of"
                            + " logging is done and the other arguments log-level, log-file"
                            + " and log4j2-pattern are ignored."
                            + "\nOptional. By default the programmatic configuration takes"
                            + " place."
                          );
        clp.defineArgument( "v"
                          , "log-level"
                          , /* cntMin, cntMax */ 0, 1
                          , /* defaultValue */ "INFO"
                          , "Verbosity of all logging. Specify one out of OFF,"
                            + " FATAL, ERROR, WARN, or INFO."
                            + "\nOptional, default is INFO."
                          );
        clp.defineArgument( "l"
                          , "log-file"
                          , /* cntMin, cntMax */ 0, 1
                          , /* defaultValue */ null
                          , "If given, a log file is written containing general"
                            + " program flow messages."
                            + "\nOptional. By default logging output only goes to the"
                            + " console."
                          );
        clp.defineArgument( "p"
                          , "log4j2-pattern"
                          , /* cntMin, cntMax */ 0, 1
                          , /* defaultValue */ null
                          , "A pattern for the log file entries may be specified,"
                            + " e.g., \"%d %C %p: %m%n\". See"
                            + " http://logging.apache.org/log4j/2.x/manual/layouts.html"
                                                                         + "#PatternLayout"
                            + " for details."
                            + "\nOptional; the default will be most often sufficient."
                            + "\nPlease note, the console output is not affected."
                          );
    } /* End of Log4j2Configurator.defineArguments */



    /**
     * Get the Boolean flag if the unbiased log4j2 configuration sequence should be used
     * rather than programatic configuration via command line arguments.
     *   @return Getthe Boolean flag.
     */
    public boolean getUseStdConfigSequence()
        {return useStdConfigSequence_;}

    /**
     * Get the configured name of the logFile.
     *   @return Get the file name. null means not to write a log file at all.
     */
    public String getLogFileName()
        {return logFileName_;}

    /**
     * Get the configured log level as a String.
     *   @return Get a string like WARN or INFO.
     */
    public String getLogLevel()
        {return logLevel_;}

    /**
     * Get the log4j2 message pattern, which is used for the file appender.
     *   @return Get a string like %d %C %p: %m%n
     */
    public String getLogPattern()
        {return logPattern_;}

} /* End of class Log4j2Configurator definition. */
