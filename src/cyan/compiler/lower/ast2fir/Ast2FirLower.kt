package cyan.compiler.lower.ast2fir

import cyan.compiler.fir.FirNode
import cyan.compiler.parser.ast.CyanItem

interface Ast2FirLower<TAstNode : CyanItem, TFirNode : FirNode> {

    fun lower(astNode: TAstNode): TFirNode

}
