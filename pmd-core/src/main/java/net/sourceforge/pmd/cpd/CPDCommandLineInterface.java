/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.cpd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.PMDVersion;
import net.sourceforge.pmd.annotation.InternalApi;
import net.sourceforge.pmd.cli.internal.CliMessages;
import net.sourceforge.pmd.cpd.CPD.StatusCode;
import net.sourceforge.pmd.util.FileUtil;
import net.sourceforge.pmd.util.database.DBURI;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

/**
 * @deprecated Internal API. Use {@link CPD#runCpd(String...)} or {@link CPD#main(String[])}
 *      in order to execute CPD.
 */
@Deprecated
@InternalApi
public final class CPDCommandLineInterface {
    private static final Logger LOG = LoggerFactory.getLogger(CPDCommandLineInterface.class);

    /**
     * @deprecated This is used for testing, but support for it will be removed in PMD 7.
     * Use {@link CPD#runCpd(String...)} to avoid exiting the VM. In PMD 7,
     * {@link CPD#main(String[])} will call {@link System#exit(int)} always.
     */
    @Deprecated
    public static final String NO_EXIT_AFTER_RUN = "net.sourceforge.pmd.cli.noExit";

    /**
     * @deprecated This is used for testing, but support for it will be removed in PMD 7.
     * Use {@link CPD#runCpd(String...)} to avoid exiting the VM. In PMD 7,
     * {@link CPD#main(String[])} will call {@link System#exit(int)} always.
     */
    @Deprecated
    public static final String STATUS_CODE_PROPERTY = "net.sourceforge.pmd.cli.status";

    static final String PROGRAM_NAME = "cpd";

    private CPDCommandLineInterface() { }

    @Deprecated
    public static void setStatusCodeOrExit(int status) {
        if (isExitAfterRunSet()) {
            System.exit(status);
        } else {
            setStatusCode(status);
        }
    }

    private static boolean isExitAfterRunSet() {
        String noExit = System.getenv(NO_EXIT_AFTER_RUN);
        if (noExit == null) {
            noExit = System.getProperty(NO_EXIT_AFTER_RUN);
        }
        return noExit == null;
    }

    private static void setStatusCode(int statusCode) {
        System.setProperty(STATUS_CODE_PROPERTY, Integer.toString(statusCode));
    }

    static StatusCode parseArgs(CPDConfiguration arguments, String... args) {
        JCommander jcommander = new JCommander(arguments);
        jcommander.setProgramName(PROGRAM_NAME);

        try {
            jcommander.parse(args);
            if (arguments.isHelp()) {
                jcommander.usage();
                System.out.println(buildUsageText());
                return StatusCode.OK;
            }
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            System.err.println(CliMessages.runWithHelpFlagMessage());
            return StatusCode.ERROR;
        }

        Map<String, String> deprecatedOptions = filterDeprecatedOptions(args);
        if (!deprecatedOptions.isEmpty()) {
            Entry<String, String> first = deprecatedOptions.entrySet().iterator().next();
            LOG.warn("Some deprecated options were used on the command-line, including {}", first.getKey());
            LOG.warn("Consider replacing it with {}", first.getValue());
        }

        arguments.postContruct();
        // Pass extra parameters as System properties to allow language
        // implementation to retrieve their associate values...
        CPDConfiguration.setSystemProperties(arguments);

        return null;
    }

    /**
     * @deprecated Use {@link CPD#main(String[])}
     */
    @Deprecated
    public static void main(String[] args) {
        setStatusCodeOrExit(CPD.runCpd(args).toInt());
    }

    private static Map<String, String> filterDeprecatedOptions(String... args) {
        Map<String, String> argSet = new LinkedHashMap<>(SUGGESTED_REPLACEMENT);
        argSet.keySet().retainAll(new HashSet<>(Arrays.asList(args)));
        return Collections.unmodifiableMap(argSet);
    }

    /** Map of deprecated option to suggested replacement. */
    private static final Map<String, String> SUGGESTED_REPLACEMENT;

    static {
        Map<String, String> m = new LinkedHashMap<>();

        m.put("--failOnViolation", "--fail-on-violation");
        m.put("-failOnViolation", "--fail-on-violation");
        m.put("--filelist", "--file-list");
        SUGGESTED_REPLACEMENT = Collections.unmodifiableMap(m);
    }

    public static void addSourceFilesToCPD(CPD cpd, CPDConfiguration arguments) {
        // Add files
        if (null != arguments.getFiles() && !arguments.getFiles().isEmpty()) {
            addSourcesFilesToCPD(arguments.getFiles(), cpd, !arguments.isNonRecursive());
        }

        // Add Database URIS
        if (null != arguments.getURI() && !"".equals(arguments.getURI())) {
            addSourceURIToCPD(arguments.getURI(), cpd);
        }

        if (null != arguments.getFileListPath() && !"".equals(arguments.getFileListPath())) {
            addFilesFromFilelist(arguments.getFileListPath(), cpd, !arguments.isNonRecursive());
        }
    }

    private static void addSourcesFilesToCPD(List<File> files, CPD cpd, boolean recursive) {
        try {
            for (File file : files) {
                if (!file.exists()) {
                    throw new FileNotFoundException("Couldn't find directory/file '" + file + "'");
                } else if (file.isDirectory()) {
                    if (recursive) {
                        cpd.addRecursively(file);
                    } else {
                        cpd.addAllInDirectory(file);
                    }
                } else {
                    cpd.add(file);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void addFilesFromFilelist(String inputFilePath, CPD cpd, boolean recursive) {
        List<File> files = new ArrayList<>();
        try {
            Path file = FileUtil.toExistingPath(inputFilePath);
            for (String param : FileUtil.readFilelistEntries(file)) {
                @NonNull Path fileToAdd = FileUtil.toExistingPath(param);
                files.add(fileToAdd.toFile());
            }
            addSourcesFilesToCPD(files, cpd, recursive);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void addSourceURIToCPD(String uri, CPD cpd) {
        try {
            LOG.debug("Attempting DBURI={}", uri);
            DBURI dburi = new DBURI(uri);
            LOG.debug("Initialised DBURI={}", dburi);
            LOG.debug("Adding DBURI={} with DBType={}", dburi, dburi.getDbType());
            cpd.add(dburi);
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("uri=" + uri, e);
        }
    }

    @Deprecated
    @InternalApi
    public static String buildUsageText() {
        String helpText = " For example on Windows:" + PMD.EOL;

        helpText += " C:\\>" + "pmd-bin-" + PMDVersion.VERSION + "\\bin\\cpd.bat"
                + " --minimum-tokens 100 --files c:\\jdk18\\src\\java" + PMD.EOL;
        helpText += PMD.EOL;

        helpText += " For example on *nix:" + PMD.EOL;
        helpText += " $ " + "pmd-bin-" + PMDVersion.VERSION + "/bin/run.sh cpd"
                + " --minimum-tokens 100 --files /path/to/java/code" + PMD.EOL;
        helpText += PMD.EOL;

        helpText += " Supported languages: " + Arrays.toString(LanguageFactory.supportedLanguages) + PMD.EOL;
        helpText += " Formats: " + Arrays.toString(CPDConfiguration.getRenderers()) + PMD.EOL;
        return helpText;
    }

}
