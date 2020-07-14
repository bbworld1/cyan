package cyan.compiler.lower.ast2fir

import cyan.compiler.fir.FirFunctionDeclaration
import cyan.compiler.fir.FirNode
import cyan.compiler.fir.FirNullNode
import cyan.compiler.fir.FirScope
import cyan.compiler.lower.ast2fir.checker.NoNamedFunctionClosures
import cyan.compiler.parser.ast.function.CyanFunctionDeclaration

object FunctionDeclarationLower : Ast2FirLower<CyanFunctionDeclaration, FirNullNode> {

    override fun lower(astNode: CyanFunctionDeclaration, parentFirNode: FirNode): FirNullNode {
        val firFunctionDeclaration = FirFunctionDeclaration(astNode.signature.name.value, astNode.signature.args.map { it.value }.toTypedArray())
        firFunctionDeclaration.block = SourceLower.lower(astNode.source, firFunctionDeclaration)

        require (parentFirNode is FirScope) { "ast2fir: parentFirNode must be FirSource but was ${parentFirNode::class.simpleName}" }
        require (!NoNamedFunctionClosures.check(firFunctionDeclaration, parentFirNode)) { "err: named local functions cannot be closures (refer to elements in it's containing scope)" }

        parentFirNode.localFunctions.add(firFunctionDeclaration)

        return FirNullNode
    }

}
