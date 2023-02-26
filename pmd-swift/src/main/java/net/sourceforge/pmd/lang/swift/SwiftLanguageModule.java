/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.swift;

import net.sourceforge.pmd.cpd.Tokenizer;
import net.sourceforge.pmd.lang.LanguagePropertyBundle;
import net.sourceforge.pmd.lang.impl.SimpleLanguageModuleBase;
import net.sourceforge.pmd.lang.swift.cpd.SwiftTokenizer;

/**
 * Language Module for Swift
 */
public class SwiftLanguageModule extends SimpleLanguageModuleBase {

    private static final SwiftLanguageModule INSTANCE = new SwiftLanguageModule();

    /**
     * Create a new instance of Swift Language Module.
     */
    public SwiftLanguageModule() {
        super(LanguageMetadata.withId("swift").name("Swift")
                              .extensions("swift")
                              .addVersion("4.2")
                              .addVersion("5.0")
                              .addVersion("5.1")
                              .addVersion("5.2")
                              .addVersion("5.3")
                              .addVersion("5.4")
                              .addVersion("5.5")
                              .addVersion("5.6")
                              .addDefaultVersion("5.7"),
              new SwiftHandler());
    }

    @Override
    public Tokenizer createCpdTokenizer(LanguagePropertyBundle bundle) {
        return new SwiftTokenizer();
    }

    public static SwiftLanguageModule getInstance() {
        return INSTANCE;
    }
}
