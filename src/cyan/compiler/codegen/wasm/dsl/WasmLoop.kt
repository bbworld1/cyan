package cyan.compiler.codegen.wasm.dsl

class WasmLoop(val blockNum: Int) : WasmScope {

    override val elements = mutableListOf<Wasm.OrderedElement>()

    override fun toString(): String {
        var elements = ""

        for ((index, element) in this.elements.withIndex()) {
            elements += (if (index > 0) "\n" else "") + element.toString().prependIndent("    ")
        }

        return """
        |(loop ${"$"}L$blockNum
        |$elements
        |)
        """.trimMargin()
    }

}
