package cyan.compiler.codegen.js.lower

import cyan.compiler.codegen.FirCompilerBackend
import cyan.compiler.codegen.FirItemLower
import cyan.compiler.fir.FirDocument
import cyan.compiler.fir.FirIfChain
import cyan.compiler.fir.FirStatement
import cyan.compiler.fir.FirVariableDeclaration
import cyan.compiler.fir.extensions.firstAncestorOfType
import cyan.compiler.fir.functions.FirFunctionCall

import java.lang.StringBuilder

object JsStatementLower : FirItemLower<FirStatement> {

    override fun lower(backend: FirCompilerBackend, item: FirStatement): String {
        return when (item) {
            is FirFunctionCall -> {
                val isBuiltin = item.firstAncestorOfType<FirDocument>()?.declaredSymbols?.contains(item.callee)
                    ?: error("fir2js: no FirDocument as ancestor of node")

                val jsName = if (isBuiltin) "builtins.${item.callee.name}" else item.callee.name

                "$jsName(${item.args.joinToString(", ", transform = backend::lowerExpression)});"
            }
            is FirVariableDeclaration -> {
                "const ${item.name} = ${backend.lowerExpression(item.initializationExpr)};"
            }
            is FirIfChain -> {
                val builder = StringBuilder()

                for ((index, branch) in item.branches.withIndex()) {
                    if (index > 0) builder.append(" ")
                    builder.append("if (${backend.lowerExpression(branch.first)}) {\n")
                    builder.append(backend.translateSource(branch.second).prependIndent("    "))
                    builder.append("\n}")
                }

                if (item.elseBranch != null) {
                    builder.append(" else {\n")
                    builder.append(backend.translateSource(item.elseBranch).prependIndent("    "))
                    builder.append("\n}")
                }

                builder.toString()
            }
            else -> error("fir2js: cannot lower statement of type '${item::class.simpleName}'")
        }
    }

}
