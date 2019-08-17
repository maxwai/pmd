/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import net.sourceforge.pmd.lang.dfa.report.ReportTree;
import net.sourceforge.pmd.renderers.AbstractAccumulatingRenderer;
import net.sourceforge.pmd.util.DateTimeUtil;
import net.sourceforge.pmd.util.EmptyIterator;
import net.sourceforge.pmd.util.NumericConstants;

/**
 * A {@link Report} collects all informations during a PMD execution. This
 * includes violations, suppressed violations, metrics, error during processing
 * and configuration errors.
 */
public class Report implements Iterable<RuleViolation> {

    /*
     * The idea is to store the violations in a tree instead of a list, to do
     * better and faster sort and filter mechanism and to visualize the result
     * as tree. (ide plugins).
     */
    private final ReportTree violationTree = new ReportTree();

    // Note that this and the above data structure are both being maintained for
    // a bit
    private final List<RuleViolation> violations = new ArrayList<>();
    private final List<ThreadSafeReportListener> listeners = new ArrayList<>();
    private List<ProcessingError> errors;
    private List<ConfigurationError> configErrors;
    private long start;
    private long end;
    private List<SuppressedViolation> suppressedRuleViolations = new ArrayList<>();

    /**
     * Creates a new, initialized, empty report for the given file name.
     *
     * @param ctx
     *            The context to use to connect to the report
     * @param fileName
     *            the filename used to report any violations
     * @return the new report
     */
    public static Report createReport(RuleContext ctx, String fileName) {
        Report report = new Report();

        // overtake the listener
        report.addListeners(ctx.getReport().getListeners());

        ctx.setReport(report);
        ctx.setSourceCodeFile(new File(fileName));
        return report;
    }

    /**
     * Represents a duration. Useful for reporting processing time.
     */
    public static class ReadableDuration {
        private final long duration;

        /**
         * Creates a new duration.
         *
         * @param duration
         *            the duration in milliseconds.
         */
        public ReadableDuration(long duration) {
            this.duration = duration;
        }

        /**
         * Gets a human readable representation of the duration, such as "1h 3m
         * 5s".
         *
         * @return human readable representation of the duration
         */
        public String getTime() {
            return DateTimeUtil.asHoursMinutesSeconds(duration);
        }
    }

    /**
     * Represents a configuration error.
     */
    public static class ConfigurationError {
        private final Rule rule;
        private final String issue;

        /**
         * Creates a new configuration error for a specific rule.
         *
         * @param theRule
         *            the rule which is configured wrongly
         * @param theIssue
         *            the reason, why the configuration is wrong
         */
        public ConfigurationError(Rule theRule, String theIssue) {
            rule = theRule;
            issue = theIssue;
        }

        /**
         * Gets the wrongly configured rule
         *
         * @return the wrongly configured rule
         */
        public Rule rule() {
            return rule;
        }

        /**
         * Gets the reason for the configuration error.
         *
         * @return the issue
         */
        public String issue() {
            return issue;
        }
    }

    /**
     * Represents a processing error, such as a parse error.
     */
    public static class ProcessingError {
        private final Throwable error;
        private final String file;

        /**
         * Creates a new processing error
         *
         * @param error
         *            the error
         * @param file
         *            the file during which the error occurred
         */
        public ProcessingError(Throwable error, String file) {
            this.error = error;
            this.file = file;
        }

        public String getMsg() {
            return error.getClass().getSimpleName() + ": " + error.getMessage();
        }
        
        public String getDetail() {
            try (StringWriter stringWriter = new StringWriter();
                    PrintWriter writer = new PrintWriter(stringWriter)) {
                error.printStackTrace(writer);
                return stringWriter.toString();
            } catch (IOException e) {
                // IOException on close - should never happen when using StringWriter
                throw new RuntimeException(e);
            }
        }

        public String getFile() {
            return file;
        }

        public Throwable getError() {
            return error;
        }
    }


    /**
     * Configure the lines, that are suppressed via a NOPMD comment.
     *
     * @param lines
     *            the suppressed lines
     */
    public void suppress(Map<Integer, String> lines) {
    }

    private static String keyFor(RuleViolation rv) {

        return StringUtils.isNotBlank(rv.getPackageName()) ? rv.getPackageName() + '.' + rv.getClassName() : "";
    }

    /**
     * Calculate a summary of violation counts per fully classified class name.
     *
     * @return violations per class name
     */
    public Map<String, Integer> getCountSummary() {
        Map<String, Integer> summary = new HashMap<>();
        for (RuleViolation rv : violationTree) {
            String key = keyFor(rv);
            Integer o = summary.get(key);
            summary.put(key, o == null ? NumericConstants.ONE : o + 1);
        }
        return summary;
    }

    public ReportTree getViolationTree() {
        return this.violationTree;
    }

    /**
     * Calculate a summary of violations per rule.
     *
     * @return a Map summarizing the Report: String (rule name) -&gt; Integer (count
     *         of violations)
     */
    public Map<String, Integer> getSummary() {
        Map<String, Integer> summary = new HashMap<>();
        for (RuleViolation rv : violations) {
            String name = rv.getRule().getName();
            if (!summary.containsKey(name)) {
                summary.put(name, NumericConstants.ZERO);
            }
            Integer count = summary.get(name);
            summary.put(name, count + 1);
        }
        return summary;
    }

    /**
     * Registers a report listener
     *
     * @param listener
     *            the listener
     */
    public void addListener(ThreadSafeReportListener listener) {
        listeners.add(listener);
    }

    public List<SuppressedViolation> getSuppressedRuleViolations() {
        return suppressedRuleViolations;
    }

    /**
     * Represents a violation, that has been suppressed.
     * TODO this should implement RuleViolation
     */
    public static class SuppressedViolation {
        private final RuleViolation rv;
        private final String userMessage;
        private final ViolationSuppressor suppressor;

        /**
         * Creates a suppressed violation.
         *
         * @param rv The violation, that has been suppressed
         * @param suppressor The suppressor which suppressed the violation
         * @param userMessage Any relevant info given by the suppressor
         */
        public SuppressedViolation(RuleViolation rv, ViolationSuppressor suppressor, String userMessage) {
            this.suppressor = suppressor;
            this.rv = rv;
            this.userMessage = userMessage;
        }

        public ViolationSuppressor getSuppressor() {
            return suppressor;
        }

        public RuleViolation getRuleViolation() {
            return this.rv;
        }

        public String getUserMessage() {
            return userMessage;
        }
    }


    public void addSuppressedViolation(SuppressedViolation sv) {
        suppressedRuleViolations.add(sv);
    }

    /**
     * Adds a new rule violation to the report and notify the listeners.
     *
     * @param violation
     *            the violation to add
     */
    public void addRuleViolation(RuleViolation violation) {
        int index = Collections.binarySearch(violations, violation, RuleViolationComparator.INSTANCE);
        violations.add(index < 0 ? -index - 1 : index, violation);
        violationTree.addRuleViolation(violation);
        for (ThreadSafeReportListener listener : listeners) {
            listener.ruleViolationAdded(violation);
        }
    }

    /**
     * Adds a new configuration error to the report.
     *
     * @param error
     *            the error to add
     */
    public void addConfigError(ConfigurationError error) {
        if (configErrors == null) {
            configErrors = new ArrayList<>();
        }
        configErrors.add(error);
    }

    /**
     * Adds a new processing error to the report.
     *
     * @param error
     *            the error to add
     */
    public void addError(ProcessingError error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }

    /**
     * Merges the given report into this report. This might be necessary, if a
     * summary over all violations is needed as PMD creates one report per file
     * by default.
     *
     * @param r
     *            the report to be merged into this.
     * @see AbstractAccumulatingRenderer
     */
    public void merge(Report r) {
        Iterator<ProcessingError> i = r.errors();
        while (i.hasNext()) {
            addError(i.next());
        }
        Iterator<ConfigurationError> ce = r.configErrors();
        while (ce.hasNext()) {
            addConfigError(ce.next());
        }
        Iterator<RuleViolation> v = r.iterator();
        while (v.hasNext()) {
            RuleViolation violation = v.next();
            int index = Collections.binarySearch(violations, violation, RuleViolationComparator.INSTANCE);
            violations.add(index < 0 ? -index - 1 : index, violation);
            violationTree.addRuleViolation(violation);
        }
        Iterator<SuppressedViolation> s = r.getSuppressedRuleViolations().iterator();
        while (s.hasNext()) {
            suppressedRuleViolations.add(s.next());
        }
    }

    public boolean isEmpty() {
        return !violations.iterator().hasNext() && !hasErrors();
    }

    /**
     * Checks whether any processing errors have been reported.
     *
     * @return <code>true</code> if there were any processing errors,
     *         <code>false</code> otherwise
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    /**
     * Checks whether any configuration errors have been reported.
     *
     * @return <code>true</code> if there were any configuration errors,
     *         <code>false</code> otherwise
     */
    public boolean hasConfigErrors() {
        return configErrors != null && !configErrors.isEmpty();
    }

    /**
     * Checks whether no violations have been reported.
     *
     * @return <code>true</code> if no violations have been reported,
     *         <code>false</code> otherwise
     */
    public boolean treeIsEmpty() {
        return !violationTree.iterator().hasNext();
    }

    /**
     * Returns an iteration over the reported violations.
     *
     * @return an iterator
     */
    public Iterator<RuleViolation> treeIterator() {
        return violationTree.iterator();
    }

    @Override
    public Iterator<RuleViolation> iterator() {
        return violations.iterator();
    }

    /**
     * Returns an iterator of the reported processing errors.
     *
     * @return the iterator
     */
    public Iterator<ProcessingError> errors() {
        return errors == null ? EmptyIterator.<ProcessingError>instance() : errors.iterator();
    }

    /**
     * Returns an iterator of the reported configuration errors.
     *
     * @return the iterator
     */
    public Iterator<ConfigurationError> configErrors() {
        return configErrors == null ? EmptyIterator.<ConfigurationError>instance() : configErrors.iterator();
    }

    /**
     * The number of violations.
     *
     * @return number of violations.
     */
    public int treeSize() {
        return violationTree.size();
    }

    /**
     * The number of violations.
     *
     * @return number of violations.
     */
    public int size() {
        return violations.size();
    }

    /**
     * Mark the start time of the report. This is used to get the elapsed time
     * in the end.
     *
     * @see #getElapsedTimeInMillis()
     */
    public void start() {
        start = System.currentTimeMillis();
    }

    /**
     * Mark the end time of the report. This is ued to get the elapsed time.
     *
     * @see #getElapsedTimeInMillis()
     */
    public void end() {
        end = System.currentTimeMillis();
    }

    public long getElapsedTimeInMillis() {
        return end - start;
    }

    public List<ThreadSafeReportListener> getListeners() {
        return listeners;
    }

    /**
     * Adds all given listeners to this report
     *
     * @param allListeners
     *            the report listeners
     */
    public void addListeners(List<ThreadSafeReportListener> allListeners) {
        listeners.addAll(allListeners);
    }
}
