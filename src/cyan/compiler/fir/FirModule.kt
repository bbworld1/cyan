package cyan.compiler.fir

import cyan.compiler.fir.functions.FirFunctionDeclaration
import cyan.compiler.lower.ast2fir.SourceLower
import cyan.compiler.parser.CyanModuleParser

import com.github.h0tk3y.betterParse.grammar.parseToEnd

import com.andreapivetta.kolor.lightGreen

import java.io.File

class FirModule (
    override val declaredSymbols: MutableSet<FirSymbol> = mutableSetOf(),
    val name: String
) : FirScope {

    override val isInheriting = false

    lateinit var source: FirSource

    override val localFunctions get() = declaredSymbols.filterIsInstance<FirFunctionDeclaration>().toMutableSet()

    override val parent: FirNode? get() = null

    override fun allReferredSymbols() = source.allReferredSymbols()

    fun findModuleByReference(reference: FirReference): FirModule? {
        val moduleInCache = cachedModules[reference.text]

        return if (moduleInCache != null) moduleInCache else {
            val moduleFileInCompilerResources = File("resources/runtime/${reference.text}.cy").takeIf { it.exists() }
                ?: File("resources/runtime/stdlib/${reference.text}.cy").takeIf { it.exists() }

            val loadedModule = moduleFileInCompilerResources?.let { compileModuleFromFile(it) }

            loadedModule?.let { cachedModules[reference.text] = loadedModule }

            loadedModule
        }
    }

    companion object Loader {
        private val moduleParser = CyanModuleParser()

        private val cachedModules = mutableMapOf<String, FirModule>()

        private val runtimeModule get() = cachedModules["__runtime__"]

        fun compileModuleFromFile(it: File): FirModule {
            println("Compiling".lightGreen() + "\t\t'${it.name}'")

            val moduleText = it.readText()
            val parsedModule = moduleParser.parseToEnd(moduleText)

            val moduleName = parsedModule.declaration.name.value

            val compiledModule = FirModule(name = moduleName)

            if (moduleName != "__runtime__")
                compiledModule.declaredSymbols += runtimeModule?.declaredSymbols ?: emptySet()

            compiledModule.source = SourceLower.lower(parsedModule.source, compiledModule)

            return compiledModule.also { it.declaredSymbols += compiledModule.source.declaredSymbols }
        }

        init {
            val runtimeModuleFile = File("resources/runtime/stdlib/runtime.cy").takeIf { it.exists() } ?: error("fatal: could not find runtime.cy module")

            cachedModules["__runtime__"] = compileModuleFromFile(runtimeModuleFile)
        }
    }

}
