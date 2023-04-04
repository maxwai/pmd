/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.vm.ast;

import net.sourceforge.pmd.annotation.DeprecatedUntil700;
import net.sourceforge.pmd.lang.ast.Node;

@Deprecated
@DeprecatedUntil700
public interface VmParserVisitor extends VmVisitor<Object, Object> {

    @Override
    default Object visitNode(Node node, Object param) {
        node.children().forEach(it -> it.acceptVisitor(this, param));
        return param;
    }
}
