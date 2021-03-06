package cyan.compiler.codegen.wasm.lower

import cyan.compiler.codegen.FirItemLower
import cyan.compiler.codegen.wasm.WasmLoweringContext
import cyan.compiler.codegen.wasm.dsl.*
import cyan.compiler.codegen.wasm.utils.AllocationResult
import cyan.compiler.codegen.wasm.utils.size
import cyan.compiler.common.types.CyanType
import cyan.compiler.common.types.Type
import cyan.compiler.fir.FirResolvedReference
import cyan.compiler.fir.FirSource
import cyan.compiler.fir.FirStatement
import cyan.compiler.fir.FirVariableDeclaration
import cyan.compiler.fir.expression.FirExpression
import cyan.compiler.fir.functions.FirFunctionArgument
import cyan.compiler.fir.functions.FirFunctionDeclaration
import cyan.compiler.fir.functions.FirFunctionReceiver
import cyan.compiler.parser.ast.operator.*
import kotlin.math.exp

object WasmExpressionLower : FirItemLower<WasmLoweringContext, FirExpression, Wasm.OrderedElement> {

    override fun lower(context: WasmLoweringContext, item: FirExpression): Wasm.OrderedElement {
        return when (val expr = item.realExpr) {
            is FirExpression.Literal -> instructions {
                if (expr.isConstant) when (val allocationResult = context.allocator.preAllocate(expr)) {
                    // Static allocation
                    is AllocationResult.Stack -> i32.const(allocationResult.literal)
                    is AllocationResult.Heap  -> i32.const(allocationResult.pointer)
                } else { // Dynamic allocation
                    val isInVariableDeclaration = expr.parent is FirVariableDeclaration

                    val localName = if (isInVariableDeclaration) {
                        // Avoid creating a temp local if this is for a variable declaration
                        context.locals[expr.parent].toString()
                    } else {
                        val structureBasePtr = "structure_" + expr.hashCode().toString(16)

                        local.new(structureBasePtr, Wasm.Type.i32)

                        structureBasePtr
                    }

                    val typeSize = when (expr) {
                        is FirExpression.Literal.Array -> 4 * expr.elements.size
                        else -> expr.type().size
                    }

                    cy.malloc(typeSize)
                    local.set(localName)

                    val elements = when (expr) {
                        is FirExpression.Literal.Array  -> expr.elements.withIndex()
                        is FirExpression.Literal.Struct -> expr.elements.values.withIndex()
                        else -> error("fir2wasm: cannot dynamically allocate expression of type '${expr::class.simpleName}'")
                    }

                    for ((index, element) in elements) {
                        if (index == 0) {
                            local.get(localName)
                        } else {
                            i32.const(index * 4)
                            local.get(localName)
                            i32.add
                        }

                        +context.backend.lowerExpression(element, context)

                        i32.store // Store there
                    }

                    local.get(localName)
                }
            }
            is FirExpression.FunctionCall -> {
                val function = expr.callee.resolvedSymbol as FirFunctionDeclaration

                instructions {
                    for (argument in (expr.args + expr.receiver).filterNotNull().map { context.backend.lowerExpression(it, context) }) {
                        +argument
                    }

                    call(function.name)
                    if ((expr.callee.resolvedSymbol as FirFunctionDeclaration).returnType != Type.Primitive(CyanType.Void) && expr.parent is FirSource)
                        drop
                }
            }
            is FirExpression.MemberAccess -> instructions {
                val baseStruct = expr.base.type() as Type.Struct
                val baseStructField = baseStruct.properties.first { it.name == expr.member }

                val fieldIndex = baseStruct.properties.indexOfFirst { it == baseStructField }.takeIf { it >= 0 }
                        ?: error("fir2wasm: base struct field index was -1")

                require (expr.base is FirResolvedReference)

                val baseSymbol = expr.base.resolvedSymbol

                require (
                    baseSymbol is FirVariableDeclaration || baseSymbol is FirFunctionArgument || baseSymbol is FirFunctionReceiver
                ) { "fir2wasm: member access is currently only supported on references to local variables, function arguments or function receivers" }

                when (baseSymbol) {
                    is FirVariableDeclaration -> local.get(context.locals[baseSymbol]!!)
                    is FirFunctionArgument    -> local.get(baseSymbol.name)
                    is FirFunctionReceiver    -> local.get("_r")
                    else -> error("fir2wasm: cannot lower access to a reference to '${baseSymbol::class.simpleName}'")
                }

                val fieldOffset = fieldIndex * 4

                if (fieldOffset > 1) {
                    i32.const(fieldOffset)
                    i32.add
                }
                i32.load
            }
            is FirExpression.ArrayIndex -> when (val base = expr.base) {
                is FirResolvedReference -> {
                    val originalDeclaration = base.resolvedSymbol

                    require(originalDeclaration is FirVariableDeclaration)

                    val ptr = context.locals[originalDeclaration]
                        ?: error("fir2wasm: no local generated for '${originalDeclaration.name}'")

                    when (val declType = originalDeclaration.initializationExpr.type()) {
                        Type.Primitive(CyanType.Str, true),
                        Type.Primitive(CyanType.I32, true) -> instructions {
                            local.get(ptr)
                            +context.backend.lowerExpression(expr.index, context)
                            cy.array_get
                        }
                        else -> error("fir2wasm: cannot lower array index on array of type '$declType'")
                    }
                }
                else -> error("fir2wasm: array indexes are currently only supported on references")
            }
            is FirExpression.Binary -> when (expr.commonType) {
                null -> error("fir2wasm: cannot lower binary expression with different operands")
                Type.Primitive(CyanType.I32) -> instructions {
                    +lower(context, expr.lhs)
                    +lower(context, expr.rhs)

                    when (expr.operator) {
                        CyanBinaryPlusOperator          -> i32.add
                        CyanBinaryMinusOperator         -> i32.sub
                        CyanBinaryTimesOperator         -> i32.mul
                        CyanBinaryLesserOperator        -> i32.lt_u
                        CyanBinaryLesserEqualsOperator  -> i32.le_u
                        CyanBinaryGreaterOperator       -> i32.gt_u
                        CyanBinaryGreaterEqualsOperator -> i32.ge_u
                        else -> error("fir2wasm: cannot lower binary expression with operator '${expr.operator::class.simpleName}'")
                    }
                }
                Type.Primitive(CyanType.Str) -> instructions {
                    require (expr.operator == CyanBinaryPlusOperator) { "fir2wasm: cannot lower string binary expression with operator '${expr.operator::class.simpleName}'" }

                    +lower(context, expr.lhs)
                    +lower(context, expr.rhs)

                    cy.strcat
                }
                else -> error("fir2wasm: cannot lower binary expression operand type '${expr.lhs.realExpr.type()}'")
            }
            is FirResolvedReference -> instructions {
                when (val symbol = expr.resolvedSymbol) {
                    is FirVariableDeclaration -> local.get(context.locals[symbol] ?: error("no local was set for variable '${symbol.name}'"))
                    is FirFunctionArgument    -> local.get(symbol.name)
                    is FirFunctionReceiver    -> local.get("_r")
                    else -> error("fir2wasm: cannot lower reference to symbol of type '${symbol::class.simpleName}'")
                }
            }
            else -> error("fir2wasm: cannot lower expression of type '${expr::class.simpleName}'")
        }

    }

}
