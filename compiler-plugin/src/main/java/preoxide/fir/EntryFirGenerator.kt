package preoxide.fir

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.isClass
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.*
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import preoxide.Annotations
import preoxide.PluginKeys

@OptIn(
  SymbolInternals::class,
  UnresolvedExpressionTypeAccess::class,
  DirectDeclarationsAccess::class,
)
class EntryFirGenerator(
  session: FirSession,
  val messageCollector: MessageCollector,
) : FirDeclarationGenerationExtension(session) {
  companion object {
    fun factory(messageCollector: MessageCollector) = Factory { session ->
      EntryFirGenerator(session, messageCollector)
    }
  }

  fun processAll() =
    AnnoMarker.caches
      .map { (anno, symbols) ->
        anno.annotationClass to
          symbols.map { classSymbol ->
            classSymbol.classId.asSingleFqName() to
              anno.annotateds
                .filter {
                  (it.dispatchReceiverType as ConeClassLikeType).classId == classSymbol.classId
                }
                .map { it to it.getAnnotationByClassId(anno.annotationClass, session)!! }
          }
      }
      .toMap()

  // MutableMap<ClassId, MutableList<Pair<FqName, MutableList<>>>

  private val predicateBasedProvider = session.predicateBasedProvider

  fun ConeClassLikeType.toFir() = session.firProvider.getFirClassifierByFqName(classId) as? FirClass

  fun ConeClassLikeType.isClass() =
    toFir()?.classKind?.run {
      isClass && !isInterface
    } ?: false

  fun FirClass.parent() =
    superTypeRefs
      .asSequence()
      .filterIsInstance<FirResolvedTypeRef>()
      .map { it.coneType }
      .filterIsInstance<ConeClassLikeType>()
      .firstOrNull { it.isClass() }
      ?.toFir()

  val parentsFuncs = mutableMapOf<FqName, Map<Name, FirNamedFunctionSymbol>>()

  fun FirFunction.isSui(): Boolean {
    val status = this.status
    val modality = status.modality
    val visibility = status.visibility
    return visibility != Visibilities.Private
  }

  fun fillFatherFuncs(map: MutableMap<Name, FirNamedFunctionSymbol>, target: FirClass) {
    target.symbol.declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>().forEach {
      if (it.fir.isSui()) map.putIfAbsent(it.name, it)
    }
    target.parent()?.let { fillFatherFuncs(map, it) }
  }

  fun ancestorsOf(fir: FirClass): List<FirClass> =
    fir.superTypeRefs
      .filterIsInstance<FirResolvedTypeRef>()
      .map { it.coneType }
      .filterIsInstance<ConeClassLikeType>()
      .mapNotNull { it.toFir() }
      .filter { it.classKind.isInterface }
      .flatMap { listOf(it, *ancestorsOf(it).toTypedArray()) }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    val fir = classSymbol.fir
    if (fir !is FirRegularClass || !fir.classKind.isClass || fir.classKind.isInterface) {
      return emptySet()
    }
    val parents =
      fir.superTypeRefs
        .filterIsInstance<FirResolvedTypeRef>()
        .map { it.coneType }
        .filterIsInstance<ConeClassLikeType>()

    val ancestors = ancestorsOf(fir)
    val comps = ancestors.map { it.symbol.classId.asSingleFqName() }
    val fqName = classSymbol.classId.asSingleFqName()
    val all = processAll()
    if (!all.any { (_, symbols) -> symbols.any { (name, _) -> comps.contains(name) } }) {
      return emptySet()
    }

    val parent = parents.firstOrNull { it.isClass() }
    val pf = mutableMapOf<Name, FirNamedFunctionSymbol>()
    parent?.toFir()?.let { fillFatherFuncs(pf, it) }
    parentsFuncs[fqName] = pf

    val result = mutableSetOf<Name>()
    val tname = fqName.asString()
    info("@ImplEntries Process $tname")
    val fcomps = mutableListOf<String>()
    all.forEach { (anno, symbols) ->
      symbols.forEach { (name, functions) ->
        if (comps.contains(name)) {
          fcomps.add("`${name.asString()}`")
          functions.forEach {
            entryProcessors[anno]?.process(name, session, messageCollector, classSymbol, result, it)
          }
        }
      }
    }
    info("Found components for `$tname`: ${fcomps.joinToString()}")
    info("Rewrite functions in `$tname`: ${rewriteList.joinToString()}")
    info("Define functions in `$tname': ${result.joinToString { "`$it`" }}")
    rewriteList.clear()
    return result
  }

  fun info(text: String) {
    // 我没有其他办法输出信息了
    messageCollector.report(
      CompilerMessageSeverity.WARNING,
      "[PREOXIDE-INFO]: $text",
    )
  }

  fun interface EntryProcessor {
    fun process(
      origin: FqName,
      session: FirSession,
      messageCollector: MessageCollector,
      classSymbol: FirClassSymbol<*>,
      result: MutableSet<Name>,
      function: Pair<FirNamedFunctionSymbol, FirAnnotation>,
    )
  }

  fun FirAnnotationCall.firstA(): String? {
    if (arguments.isEmpty()) return null
    val first = arguments.first()
    if (first is FirLiteralExpression) return first.value as? String
    arguments.filterIsInstance<FirNamedArgumentExpression>().forEach {
      val exp = it.expression
      if (exp is FirLiteralExpression) return exp.value as? String
    }
    return null
  }

  val rewriteList = mutableSetOf<String>()

  val entryProcessors =
    mutableMapOf<ClassId, EntryProcessor>(
      Annotations.MethodEntry to
        EntryProcessor { rorigin, session, messageCollector, classSymbol, result, function ->
          val (_, annotation) = function
          ((annotation as? FirAnnotationCall)?.firstA())?.also { name ->
            val rname = Name.identifier(name)
            if (
              classSymbol.declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>().any {
                it.name.asString() == name
              }
            ) {
              rewriteList.add("`$name`")
            } else result.add(rname)
          }
            ?: run {
              messageCollector.report(
                CompilerMessageSeverity.ERROR,
                "@MethodEntry without arguement",
              )
            }
        }
    )

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
  ): List<FirNamedFunctionSymbol> {
    val classSymbol = context?.owner ?: return emptyList()
    val scope = context.declaredScope ?: return emptyList()

    val fqName = classSymbol.classId.asSingleFqName()
    val name = callableId.callableName

    val function =
      parentsFuncs[fqName]?.get(name)?.fir
        ?: run {
          messageCollector.report(
            CompilerMessageSeverity.ERROR,
            "@MethodEntry requires $name.But not found in ${fqName.asString()} or its supertypes",
          )
          return emptyList()
        }

    /*
    val res =       copyFirFunctionWithResolvePhase(
          function,
          callableId,
          PluginKeys.methodEntry,
          FirResolvePhase.STATUS,
        ) {}*/

    return listOf(
      createMemberFunction(
          classSymbol,
          PluginKeys.methodEntry,
          name,
          (function.returnTypeRef as FirResolvedTypeRef).coneType,
        ) {
          function.valueParameters.forEach {
            valueParameter(it.name, (it.returnTypeRef as FirResolvedTypeRef).coneType)
          }
          modality = Modality.OPEN
        }
        .symbol
    )
    // return listOf(res.symbol)
  }
}

@OptIn(SymbolInternals::class)
open class AnnoMarker(
  session: FirSession,
  val annotationClass: ClassId,
  val messageCollector: MessageCollector,
) : FirDeclarationGenerationExtension(session) {
  companion object {
    fun factory(annotation: ClassId, messageCollector: MessageCollector) = Factory { session ->
      AnnoMarker(session, annotation, messageCollector)
    }

    val caches = mutableMapOf<AnnoMarker, MutableList<FirClassSymbol<*>>>()
  }

  val annotation = annotationClass.asSingleFqName()

  private val predicateBasedProvider = session.predicateBasedProvider

  val predicate = DeclarationPredicate.create {
    hasAnnotated(annotation)
  }

  val lookup = LookupPredicate.create {
    annotated(annotation)
  }

  val annotateds by lazy {
    predicateBasedProvider
      .getSymbolsByPredicate(lookup)
      .filterIsInstance<FirNamedFunctionSymbol>()
      .filter { it.dispatchReceiverType is ConeClassLikeType }
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    val fir = classSymbol.fir
    if (!predicateBasedProvider.matches(predicate, fir)) {
      return emptySet()
    }
    if (fir !is FirRegularClass) {
      messageCollector.report(
        CompilerMessageSeverity.ERROR,
        "@MethodEntry found in ${classSymbol}. But it is for class only",
      )
      return emptySet()
    }
    caches.getOrPut(this) { mutableListOf() }.add(classSymbol)
    return emptySet()
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(predicate)
  }
}
