/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.sourceforge.pmd.util.ResourceLoader;

/**
 * Configurable ruleset parser. Note that this replaces the API of {@link RulesetsFactoryUtils}
 * and {@link RuleSetFactory}. This can be configured using a fluent
 * API, see eg {@link #warnDeprecated(boolean)}. To create a list of
 * rulesets, use {@link #parseFromResource(String)}.
 */
public final class RuleSetLoader {

    private ResourceLoader resourceLoader = new ResourceLoader(RuleSetLoader.class.getClassLoader());
    private RulePriority minimumPriority = RulePriority.LOW;
    private boolean warnDeprecated = true;
    private boolean enableCompatibility = true;
    private boolean includeDeprecatedRuleReferences = false;

    /**
     * Specify that the given classloader should be used to resolve
     * paths to external ruleset references. The default uses PMD's
     * own classpath.
     */
    public RuleSetLoader loadResourcesWith(ClassLoader classLoader) {
        this.resourceLoader = new ResourceLoader(classLoader);
        return this;
    }

    // internal
    RuleSetLoader loadResourcesWith(ResourceLoader loader) {
        this.resourceLoader = loader;
        return this;
    }

    /**
     * Filter loaded rules to only those that match or are above
     * the given priority. The default is {@link RulePriority#LOW},
     * ie, no filtering occurs.
     * @return This instance, modified
     */
    public RuleSetLoader filterAbovePriority(RulePriority minimumPriority) {
        this.minimumPriority = minimumPriority;
        return this;
    }

    /**
     * Log a warning when referencing a deprecated rule.
     * This is enabled by default.
     * @return This instance, modified
     */
    public RuleSetLoader warnDeprecated(boolean warn) {
        this.warnDeprecated = warn;
        return this;
    }

    /**
     * Enable translating old rule references to newer ones, if they have
     * been moved or renamed. This is enabled by default, if disabled,
     * unresolved references will not be translated and will produce an
     * error.
     * @return This instance, modified
     */
    public RuleSetLoader enableCompatibility(boolean enable) {
        this.enableCompatibility = enable;
        return this;
    }

    /**
     * Follow deprecated rule references. By default this is off,
     * and those references will be ignored (with a warning depending
     * on {@link #enableCompatibility(boolean)}).
     *
     * @return This instance, modified
     */
    public RuleSetLoader includeDeprecatedRuleReferences(boolean enable) {
        this.includeDeprecatedRuleReferences = enable;
        return this;
    }

    /**
     * Create a new rule set factory, if you have to (that class is deprecated).
     * That factory will use the configuration that was set using the setters of this.
     *
     * @deprecated {@link RuleSetFactory} is deprecated, replace its usages with usages of this class,
     *     or of static factory methods of {@link RuleSet}
     */
    @Deprecated
    public RuleSetFactory toFactory() {
        return new RuleSetFactory(
            this.resourceLoader,
            this.minimumPriority,
            this.warnDeprecated,
            this.enableCompatibility,
            this.includeDeprecatedRuleReferences
        );
    }


    /**
     * Parses and returns a ruleset from its location. The location may
     * be a file system path, or a resource path (see {@link #loadResourcesWith(ClassLoader)}).
     *
     * <p>This replaces {@link RuleSetFactory#createRuleSet(String)},
     * but does not split commas.
     *
     * @param rulesetPath A reference to a single ruleset
     *
     * @throws RuleSetNotFoundException If the path does not correspond to a resource
     */
    public RuleSet parseFromResource(String rulesetPath) throws RuleSetNotFoundException {
        return parseFromResource(new RuleSetReferenceId(rulesetPath));
    }

    /**
     * Parses several resources into a list of rulesets.
     *
     * @param paths Paths
     *
     * @throws RuleSetNotFoundException If any resource throws
     * @throws NullPointerException     If the parameter, or any component is null
     */
    public List<RuleSet> parseFromResources(Collection<String> paths) throws RuleSetNotFoundException {
        List<RuleSet> ruleSets = new ArrayList<>(paths.size());
        for (String path : paths) {
            ruleSets.add(parseFromResource(path));
        }
        return ruleSets;
    }

    /**
     * Parses several resources into a list of rulesets.
     *
     * @param paths Paths
     *
     * @throws RuleSetNotFoundException If any resource throws
     * @throws NullPointerException     If the parameter, or any component is null
     */
    public List<RuleSet> parseFromResources(String... paths) throws RuleSetNotFoundException {
        return parseFromResources(Arrays.asList(paths));
    }

    // package private
    RuleSet parseFromResource(RuleSetReferenceId ruleSetReferenceId) throws RuleSetNotFoundException {
        return toFactory().createRuleSet(ruleSetReferenceId);
    }


    /**
     * Configure a new ruleset factory builder according to the parameters
     * of the given PMD configuration.
     */
    public static RuleSetLoader fromPmdConfig(PMDConfiguration configuration) {
        return new RuleSetLoader().filterAbovePriority(configuration.getMinimumPriority())
                                  .enableCompatibility(configuration.isRuleSetFactoryCompatibilityEnabled());
    }
}
