package firepile.compiler.util

import java.util.StringTokenizer

import scala.tools.scalap._
import scala.tools.scalap.{Main => Scalap}
import scala.tools.scalap.scalax.rules.scalasig._

import soot.{Type => SootType}

import java.util.ArrayList
import scala.collection.mutable.Queue
import scala.collection.mutable.HashSet
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

//import soot.Type

import scala.Seq
import scala.collection.mutable.HashMap

object ScalaTypeGen{

  def main(args: Array[String]) = {
    if (args.length != 1) {
      println("usage: className")
      exit(1)
    }

    var cd=getScalaSignature(args(0))


    }

  def isStatic(flags: Int) = (flags & 0x0008) != 0
  def flagsToStr(clazz: Boolean, flags: Int): String = {
    val buffer = new StringBuffer()
      var x: StringBuffer = buffer
    if (((flags & 0x0007) == 0) &&
      ((flags & 0x0002) != 0))
    x = buffer.append("private ")
    if ((flags & 0x0004) != 0)
      x = buffer.append("protected ")
    if ((flags & 0x0010) != 0)
      x = buffer.append("final ")
    if ((flags & 0x0400) != 0)
      x = if (clazz) buffer.append("abstract ")
    else buffer.append("/*deferred*/ ")
    buffer.toString()
  }


  implicit def cf2cf(cf: Classfile) = new Cf(cf)

  class Cf(val cf: Classfile) {
    def getName(n: Int): String = {
      import cf.pool._

      cf.pool(n) match {
        case UTF8(str) => str
        case StringConst(m) => getName(m)
        case ClassRef(m) => getName(m)
        case _ => "<error>"
      }
    }



    def getType(n: Int): String = getName(n)


      def getFormals(str: String): List[ScalaType] = sigToType(str) match {
      case MTyp(_, ts, _) => ts
    }
    def getReturn(str: String): ScalaType = sigToType(str) match {
      case MTyp(_, _, t) => t
    }

    def nameToClass(str: String) = str.replace('/', '.')

      def sigToType(str: String): ScalaType = sigToType(str, 0)._1

    def sigToType(str: String, i: Int): (ScalaType, Int) = str.charAt(i) match {
      case 'B' => (NamedTyp("scala.Byte"), i+1)
      case 'S' => (NamedTyp("scala.Short"), i+1)
      case 'C' => (NamedTyp("scala.Char"), i+1)
      case 'Z' => (NamedTyp("scala.Boolean"), i+1)
      case 'I' => (NamedTyp("scala.Int"), i+1)
      case 'J' => (NamedTyp("scala.Long"), i+1)
      case 'F' => (NamedTyp("scala.Float"), i+1)
      case 'D' => (NamedTyp("scala.Double"), i+1)
      case 'V' => (NamedTyp("scala.Unit"), i+1)
      case 'L' =>
      val j = str.indexOf(';', i)
        (NamedTyp(nameToClass(str.substring(i + 1, j))), j + 1)
      case '[' =>
      val (tpe, j) = sigToType(str, i + 1)
        (InstTyp(NamedTyp("scala.Array"), tpe :: Nil), j)
      case '(' =>
        val (tpes, tpe, j) = sigToType0(str, i + 1)
          (MTyp(Nil, tpes, tpe), j)
      }

      def sigToType0(str: String, i: Int): (List[ScalaType], ScalaType, Int) =
        if (str.charAt(i) == ')') {
      val (tpe, j) = sigToType(str, i+1)
        (Nil, tpe, j)
    }
    else {
      val (tpe, j) = sigToType(str, i)
        val (rest, ret, k) = sigToType0(str, j)
        (tpe :: rest, ret, k)
    }


    def getSig(flags: Int, name: Int, tpe: Int, attribs: List[cf.Attribute]) : Sig = {
      attribs find {
        case cf.Attribute(name, _) => getName(name) == "JacoMeta"
      } match {
        case Some(cf.Attribute(_, data)) =>
        val mp = new MetaParser(getName(
          ((data(0) & 0xff) << 8) + (data(1) & 0xff)).trim())
        mp.parse match {
          case None =>
          if (getName(name) == "<init>") {
            Sig("this", Nil, getFormals(getType(tpe)), getReturn(getType(tpe)))
          } else {
            Sig(Names.decode(getName(name)), Nil, getFormals(getType(tpe)), getReturn(getType(tpe)))
          }
          case Some(str) =>
          if (getName(name) == "<init>")
            Sig("this", Nil, getFormals(str), getReturn(str))
          else
            Sig(Names.decode(getName(name)), Nil, getFormals(str), getReturn(str))
        }
        case None =>
        if (getName(name) == "<init>") {
          Sig("this", Nil, getFormals(getType(tpe)), getReturn(getType(tpe)))
        } else {
          Sig(Names.decode(getName(name)), Nil, getFormals(getType(tpe)), getReturn(getType(tpe)))
        }
      }
    }

  }



  def parseScalaSignature(scalaSig: ScalaSig, isPackageObject: Boolean) = {
    import java.io.{PrintStream, OutputStreamWriter, ByteArrayOutputStream}

    val baos = new ByteArrayOutputStream
    val stream = new PrintStream(baos)
      val syms = scalaSig.topLevelClasses ::: scalaSig.topLevelObjects
    syms.head.parent match {
      //Partial match
      case Some(p) if (p.name != "<empty>") => {
        val path = p.path
        if (!isPackageObject) {
          stream.print("package ");
          stream.print(path);
          stream.print("\n")
        } else {
          val i = path.lastIndexOf(".")
            if (i > 0) {
            stream.print("package ");
            stream.print(path.substring(0, i))
            stream.print("\n")
          }
        }
      }
      case _ =>
    }
    // Print classes
    val printer = new ScalaSigPrinter(stream, false)
      for (c <- syms) {
      printer.printSymbol(c)
    }
    baos.toString
  }

  class ScalaSigPrinter(stream: java.io.PrintStream, printPrivates: Boolean) {
    import java.io.{PrintStream, ByteArrayOutputStream}
    import java.util.regex.Pattern

    import scala.tools.scalap.scalax.util.StringUtil
    import reflect.NameTransformer
    import java.lang.String

    import stream._

    val CONSTRUCTOR_NAME = "<init>"

    case class TypeFlags(printRep: Boolean)

    def printSymbol(symbol: Symbol) {printSymbol(0, symbol)}

    //added

    def printSymbolAttributes(s: Symbol, onNewLine: Boolean, indent: => Unit) = s match {
      case t: SymbolInfoSymbol => {
        for (a <- t.attributes) {
          indent; print(toString(a))
          if (onNewLine) print("\n") else print(" ")
          }
      }
      case _ =>
    }



    def printSymbol(level: Int, symbol: Symbol) {
      if (!symbol.isLocal &&
        !(symbol.isPrivate && !printPrivates)) {
        def indent() {for (i <- 1 to level) print("  ")}

        printSymbolAttributes(symbol, true, indent)
        symbol match {
          case o: ObjectSymbol =>
          if (!isCaseClassObject(o)) {
            indent
            if (o.name == "package") {
              // print package object
              printPackageObject(level, o)
            } else {
              printObject(level, o)
            }
          }
          case c: ClassSymbol if !refinementClass(c) && !c.isModule =>
          indent
          printClass(level, c)
          case m: MethodSymbol =>
          printMethod(level, m, indent)
          case a: AliasSymbol =>
          indent
          printAlias(level, a)
          case t: TypeSymbol if !t.isParam && !t.name.matches("_\\$\\d+")=>
          indent
          printTypeSymbol(level, t)
          case s =>
        }
      }
    }


    def isCaseClassObject(o: ObjectSymbol): Boolean = {
      val TypeRefType(prefix, classSymbol: ClassSymbol, typeArgs) = o.infoType
      o.isFinal && (classSymbol.children.find(x => x.isCase && x.isInstanceOf[MethodSymbol]) match {
          case Some(_) => true
          case None => false
        })
    }


    private def underCaseClass(m: MethodSymbol) = m.parent match {
      case Some(c: ClassSymbol) => c.isCase
      case _ => false
    }


    private def printChildren(level: Int, symbol: Symbol) {
      for (child <- symbol.children) printSymbol(level + 1, child)
      }


    def printWithIndent(level: Int, s: String) {
      def indent() {for (i <- 1 to level) print("  ")}
      indent;
      print(s)
    }


    def printModifiers(symbol: Symbol) {
      // print private access modifier
      if (symbol.isPrivate) print("private ")
        else if (symbol.isProtected) print("protected ")
        else symbol match {
        case sym: SymbolInfoSymbol => sym.symbolInfo.privateWithin match {
          case Some(t: Symbol) => print("private[" + t.name +"] ")
          case _ =>
        }
        case _ =>
      }

      if (symbol.isSealed) print("sealed ")
        if (symbol.isImplicit) print("implicit ")
        if (symbol.isFinal && !symbol.isInstanceOf[ObjectSymbol]) print("final ")
        if (symbol.isOverride) print("override ")
        if (symbol.isAbstract) symbol match {
        case c@(_: ClassSymbol | _: ObjectSymbol) if !c.isTrait => print("abstract ")
        case _ => ()
      }
      if (symbol.isCase && !symbol.isMethod) print("case ")
      }


    private def refinementClass(c: ClassSymbol) = c.name == "<refinement>"


    def printClass(level: Int, c: ClassSymbol) {
      if (c.name == "<local child>" /*scala.tools.nsc.symtab.StdNames.LOCALCHILD.toString()*/ ) {
        print("\n")
      } else {
        printModifiers(c)
        val defaultConstructor = if (c.isCase) getPrinterByConstructor(c) else ""
        if (c.isTrait) print("trait ") else print("class ")
          print(processName(c.name))
        val it = c.infoType
        val classType = it match {
          case PolyType(typeRef, symbols) => PolyTypeWithCons(typeRef, symbols, defaultConstructor)
          case ClassInfoType(a, b) if c.isCase => ClassInfoTypeWithCons(a, b, defaultConstructor)
          case _ => it
        }
        printType(classType)
        print(" {")
        //Print class selftype
        c.selfType match {
          case Some(t: Type) => print("\n"); print(" this : " + toString(t) + " =>")
          case None =>
        }
        print("\n")
        printChildren(level, c)
        printWithIndent(level, "}\n")
      }
    }

    def getPrinterByConstructor(c: ClassSymbol) = {
      c.children.find {
        case m: MethodSymbol if m.name == CONSTRUCTOR_NAME => true
        case _ => false
      } match {
        case Some(m: MethodSymbol) =>
        val baos = new ByteArrayOutputStream
        val stream = new PrintStream(baos)
          val printer = new ScalaSigPrinter(stream, printPrivates)
          printer.printMethodType(m.infoType, false)(())
        baos.toString
        case None =>
        ""
      }
    }

    def printPackageObject(level: Int, o: ObjectSymbol) {
      printModifiers(o)
      print("package ")
      print("object ")
      val poName = o.symbolInfo.owner.name
      print(processName(poName))
      val TypeRefType(prefix, classSymbol: ClassSymbol, typeArgs) = o.infoType
      printType(classSymbol)
      print(" {\n")
      printChildren(level, classSymbol)
      printWithIndent(level, "}\n")

    }

    def printObject(level: Int, o: ObjectSymbol) {
      printModifiers(o)
      print("object ")
      print(processName(o.name))
      val TypeRefType(prefix, classSymbol: ClassSymbol, typeArgs) = o.infoType
      printType(classSymbol)
      print(" {\n")
      printChildren(level, classSymbol)
      printWithIndent(level, "}\n")
    }

    def genParamNames(t: {def paramTypes: Seq[Type]}): List[String] = t.paramTypes.toList.map(x => {
        var str = toString(x)
          val j = str.indexOf("[")
          if (j > 0) str = str.substring(0, j)
          str = StringUtil.trimStart(str, "=> ")
        var i = str.lastIndexOf(".")
          val res = if (i > 0) str.substring(i + 1) else str
        if (res.length > 1) StringUtil.decapitalize(res.substring(0, 1)) else res.toLowerCase
      })

    implicit object _tf extends TypeFlags(false)

    def printMethodType(t: Type, printResult: Boolean)(cont: => Unit): Unit = {

      def _pmt(mt: Type {def resultType: Type; def paramSymbols: Seq[Symbol]}) = {

        val paramEntries = mt.paramSymbols.map({
            case ms: MethodSymbol => ms.name + " : " + toString(ms.infoType)(TypeFlags(true))
            case _ => "^___^"
          })

        // Print parameter clauses
        print(paramEntries.mkString(
          "(" + (mt match {case _: ImplicitMethodType => "implicit "; case _ => ""})
        , ", ", ")"))

      // Print result type
      mt.resultType match {
        case mt: MethodType => printMethodType(mt, printResult)({})
        case imt: ImplicitMethodType => printMethodType(imt, printResult)({})
        case x => if (printResult) {
          print(" : ");
          printType(x)
        }
      }
    }

    t match {
      case mt@MethodType(resType, paramSymbols) => _pmt(mt)
      case mt@ImplicitMethodType(resType, paramSymbols) => _pmt(mt)
      case pt@PolyType(mt, typeParams) => {
        print(typeParamString(typeParams))
        printMethodType(mt, printResult)({})
      }
      //todo consider another method types
      case x => print(" : "); printType(x)
    }

    // Print rest of the symbol output
    cont
  }

  def printMethod(level: Int, m: MethodSymbol, indent: () => Unit) {
    def cont = print(" = { /* compiled code */ }")

      val n = m.name
    if (underCaseClass(m) && n == CONSTRUCTOR_NAME) return
    if (n.matches(".+\\$default\\$\\d+")) return // skip default function parameters
    if (n.startsWith("super$")) return // do not print auxiliary qualified super accessors
    if (m.isAccessor && n.endsWith("_$eq")) return
    indent()
    printModifiers(m)
    if (m.isAccessor) {
      val indexOfSetter = m.parent.get.children.indexWhere(x => x.isInstanceOf[MethodSymbol] &&
        x.asInstanceOf[MethodSymbol].name == n + "_$eq")
      print(if (indexOfSetter > 0) "var " else "val ")
    } else {
      print("def ")
    }
    n match {
      case CONSTRUCTOR_NAME =>
      print("this")
      printMethodType(m.infoType, false)(cont)
      case name =>
      val nn = processName(name)
        print(nn)
      printMethodType(m.infoType, true)(
        {if (!m.isDeferred) print(" = { /* compiled code */ }" /* Print body only for non-abstract methods */ )}
      )
  }
  print("\n")
}

def printAlias(level: Int, a: AliasSymbol) {
  print("type ")
  print(processName(a.name))
  printType(a.infoType, " = ")
  print("\n")
  printChildren(level, a)
}

def printTypeSymbol(level: Int, t: TypeSymbol) {
  print("type ")
  print(processName(t.name))
  printType(t.infoType)
  print("\n")
}

def toString(attrib: AttributeInfo): String  = {
  val buffer = new StringBuffer
  buffer.append(toString(attrib.typeRef, "@"))
  if (attrib.value.isDefined) {
    buffer.append("(")
    val value = attrib.value.get
    val stringVal = value.isInstanceOf[String]
    if (stringVal) buffer.append("\"")
      val stringValue = valueToString(value)
      val isMultiline = stringVal && (stringValue.contains("\n")
        || stringValue.contains("\r"))
    if (isMultiline) buffer.append("\"\"")
      buffer.append(valueToString(value))
    if (isMultiline) buffer.append("\"\"")
      if (stringVal) buffer.append("\"")
      buffer.append(")")
  }
  if (!attrib.values.isEmpty) {
    buffer.append(" {")
    for (p <- attrib.values) {
      val name = p._1
      val value = p._2
      buffer.append(" val ")
      buffer.append(processName(name))
      buffer.append(" = ")
      buffer.append(valueToString(value))
    }
    buffer.append(valueToString(attrib.value))
    buffer.append(" }")
  }
  buffer.toString
}

def valueToString(value: Any): String = value match {
  case t: Type => toString(t)
  // TODO string, char, float, etc.
  case _ => value.toString
}

def printType(sym: SymbolInfoSymbol)(implicit flags: TypeFlags): Unit = printType(sym.infoType)(flags)

  def printType(t: Type)(implicit flags: TypeFlags): Unit = print(toString(t)(flags))

  def printType(t: Type, sep: String)(implicit flags: TypeFlags): Unit = print(toString(t, sep)(flags))

  def toString(t: Type)(implicit flags: TypeFlags): String = toString(t, "")(flags)

  def toString(t: Type, sep: String)(implicit flags: TypeFlags): String = {
  // print type itself
  t match {
    case ThisType(symbol) => sep + processName(symbol.path) + ".type"
    case SingleType(typeRef, symbol) => sep + processName(symbol.path) + ".type"
    case ConstantType(constant) => sep + (constant match {
        case null => "scala.Null"
        case _: Unit => "scala.Unit"
        case _: Boolean => "scala.Boolean"
        case _: Byte => "scala.Byte"
        case _: Char => "scala.Char"
        case _: Short => "scala.Short"
        case _: Int => "scala.Int"
        case _: Long => "scala.Long"
        case _: Float => "scala.Float"
        case _: Double => "scala.Double"
        case _: String => "java.lang.String"
        case c: Class[_] => "java.lang.Class[" + c.getComponentType.getCanonicalName.replace("$", ".") + "]"
      })
    case TypeRefType(prefix, symbol, typeArgs) => sep + (symbol.path match {
        case "scala.<repeated>" => flags match {
          case TypeFlags(true) => toString(typeArgs.head) + "*"
          case _ => "scala.Seq" + typeArgString(typeArgs)
        }
        case "scala.<byname>" => "=> " + toString(typeArgs.head)
        case _ => {
          val path = StringUtil.cutSubstring(symbol.path)(".package") //remove package object reference
          StringUtil.trimStart(processName(path) + typeArgString(typeArgs), "<empty>.")
        }
      })
    case TypeBoundsType(lower, upper) => {
      val lb = toString(lower)
        val ub = toString(upper)
        val lbs = if (!lb.equals("scala.Nothing")) " >: " + lb else ""
        val ubs = if (!ub.equals("scala.Any")) " <: " + ub else ""
          lbs + ubs
        }
        case RefinedType(classSym, typeRefs) => sep + typeRefs.map(toString).mkString("", " with ", "")
        case ClassInfoType(symbol, typeRefs) => sep + typeRefs.map(toString).mkString(" extends ", " with ", "")
        case ClassInfoTypeWithCons(symbol, typeRefs, cons) => sep + typeRefs.map(toString).
        mkString(cons + " extends ", " with ", "")

        case ImplicitMethodType(resultType, _) => toString(resultType, sep)
        case MethodType(resultType, _) => toString(resultType, sep)

        case PolyType(typeRef, symbols) => typeParamString(symbols) + toString(typeRef, sep)
        case PolyTypeWithCons(typeRef, symbols, cons) => typeParamString(symbols) + processName(cons) + toString(typeRef, sep)
        case AnnotatedType(typeRef, attribTreeRefs) => {
          toString(typeRef, sep)
        }
        case AnnotatedWithSelfType(typeRef, symbol, attribTreeRefs) => toString(typeRef, sep)
        //case DeBruijnIndexType(typeLevel, typeIndex) =>
        case ExistentialType(typeRef, symbols) => {
          val refs = symbols.map(toString _).filter(!_.startsWith("_")).map("type " + _)
              toString(typeRef, sep) + (if (refs.size > 0) refs.mkString(" forSome {", "; ", "}") else "")
            }
            case _ => sep + t.toString
          }
        }

        def getVariance(t: TypeSymbol) = if (t.isCovariant) "+" else if (t.isContravariant) "-" else ""

        def toString(symbol: Symbol): String = symbol match {
          case symbol: TypeSymbol => {
            val attrs = (for (a <- symbol.attributes) yield toString(a)).mkString(" ")
              val atrs = if (attrs.length > 0) attrs.trim + " " else ""
            atrs + getVariance(symbol) + processName(symbol.name) + toString(symbol.infoType)
          }
          case s => symbol.toString
        }

        def typeArgString(typeArgs: Seq[Type]): String =
          if (typeArgs.isEmpty) ""
        else typeArgs.map(toString).map(StringUtil.trimStart(_, "=> ")).mkString("[", ", ", "]")

          def typeParamString(params: Seq[Symbol]): String =
            if (params.isEmpty) ""
          else params.map(toString).mkString("[", ", ", "]")

          val _syms = Map("\\$bar" -> "|", "\\$tilde" -> "~",
            "\\$bang" -> "!", "\\$up" -> "^", "\\$plus" -> "+",
            "\\$minus" -> "-", "\\$eq" -> "=", "\\$less" -> "<",
            "\\$times" -> "*", "\\$div" -> "/", "\\$bslash" -> "\\\\",
            "\\$greater" -> ">", "\\$qmark" -> "?", "\\$percent" -> "%",
            "\\$amp" -> "&", "\\$colon" -> ":", "\\$u2192" -> "→",
            "\\$hash" -> "#")
          val pattern = Pattern.compile(_syms.keys.foldLeft("")((x, y) => if (x == "") y else x + "|" + y))
            val placeholderPattern = "_\\$(\\d)+"

          private def stripPrivatePrefix(name: String) = {
            val i = name.lastIndexOf("$$")
              if (i > 0) name.substring(i + 2) else name
          }

          def processName(name: String) = {
            val stripped = stripPrivatePrefix(name)
              val m = pattern.matcher(stripped)
              var temp = stripped
            while (m.find) {
              val key = m.group
              val re = "\\" + key
              temp = temp.replaceAll(re, _syms(re))
            }
            val result = temp.replaceAll(placeholderPattern, "_")
              NameTransformer.decode(result)
          }

        }

        def unpickleFromAnnotation(classFile: ClassFile, isPackageObject: Boolean): String = {
          val SCALA_SIG_ANNOTATION = "Lscala/reflect/ScalaSignature;"
          val BYTES_VALUE = "bytes"
          import classFile._
          import scalax.rules.scalasig.ClassFileParser.{ConstValueIndex, Annotation}
          import scala.reflect.generic.ByteCodecs
          classFile.annotation(SCALA_SIG_ANNOTATION) match {
            case None => ""
            case Some(Annotation(_, elements)) =>
            val bytesElem = elements.find(elem => constant(elem.elementNameIndex) == BYTES_VALUE).get
            val bytes = ((bytesElem.elementValue match {case ConstValueIndex(index) => constantWrapped(index)})
                .asInstanceOf[StringBytesPair].bytes)
            val length = ByteCodecs.decode(bytes)
              val scalaSig = ScalaSigAttributeParsers.parse(ByteCode(bytes.take(length)))
              parseScalaSignature(scalaSig, isPackageObject)
          }
        }


        case class Sig(name: String, typ: MTyp)
        object Sig {
          def apply(name: String, typeFormals: List[Param], formals: List[ScalaType], returnType: ScalaType): Sig = Sig(name, MTyp(typeFormals, formals, returnType))
          }

        sealed class ScalaType
        case class MTyp(typeFormals: List[Param], formals: List[ScalaType], returnType: ScalaType) extends ScalaType
        case class Param(name: String)
        case class NamedTyp(name: String) extends ScalaType
        case class InstTyp(base: ScalaType, args: List[ScalaType]) extends ScalaType

        private val HACK = true
        private var sig =""

        def getScalaSignature(cname: String):ClassDef={


          //println(" cname :::"+cname+"    ::::::"+"   name::::"+name)

          val cl = java.lang.Class.forName(cname).getClassLoader
          val is = (if (cl == null) java.lang.ClassLoader.getSystemClassLoader else cl).getResourceAsStream(cname.replace('.', '/') + ".class")
            val bis = new java.io.ByteArrayOutputStream
          while (is.available > 0)
            bis.write(is.read)
          val bytes = bis.toByteArray
          val reader = new ByteArrayReader(bytes)
            val cf = new Classfile(reader)

            //val classname=name
          val classname=cname

          val encName = Names.encode(if (classname == "scala.AnyRef") "java.lang.Object" else classname)


              val isPackageObject = Scalap.isPackageObjectFile(encName)

              val classFile = ClassFileParser.parse(ByteCode(bytes))

              val SCALA_SIG = "ScalaSig"

            //println("printing scalasig")

            sig =
            classFile.attribute(SCALA_SIG).map(_.byteCode).map(ScalaSigAttributeParsers.parse) match {
              // No entries in ScalaSig attribute implies that the signature is stored in the annotation
              case Some(ScalaSig(_, _, entries)) if entries.length == 0 => unpickleFromAnnotation(classFile, isPackageObject)
              case Some(scalaSig) => Scalap.parseScalaSignature(scalaSig, isPackageObject)
              case None => "None"
            }
            println(" ****** Class Definition*************")
            println(sig.toString())
            println(" ************************************")

            parseSignature(cname,sig.asInstanceOf[String],bytes)
          }

          def parseSignature(cname:String,sig:String,bytes:Array[Byte]):ClassDef={

            var al=new ArrayList[HashMap[String,String]]()

              var st = new StringTokenizer(sig,"\n")
              val temp=st.nextToken()
              var cshm=parseClassSig(temp)
              var tt ="main"

            while(st.hasMoreElements()){

              var token=st.nextToken()
                if((!token.equals("}"))){
                var s=token.split(" ")
                  tt=((s(3).replace("("," ")).split(" "))(0)
                  val hm=parseMethodSignature(token,tt)
                    if(hm!=null)
                    al.add(hm)
                }

              }

              getClassDef(al,cshm,getScalaSig(cname,bytes,tt))

            }

            def parseMethodSignature(m:String,name:String):HashMap[String,String] ={
              var hm= new HashMap[String, String]()

                hm.put("name",name)
              var temp=""
              var tempt=""
              var count=1
              var token=""
              var st = new StringTokenizer(m,"(): ")
                st.nextToken() 
              st.nextToken()
              while(st.hasMoreElements()){
                tempt=st.nextToken()
                if(tempt.equals("=")){
                  hm.put("return",token)
                  return(hm)
                }
                token=tempt
                count=count+1
                if(count%2==0){

                  if(token.startsWith("scala.")){
                    hm.put("return",token)
                    return(hm)
                  }else {
                    temp=token
                  }

                }else {

                  if(count==3)
                    hm.put("parameter",temp+":"+token)
                  else{
                    var tm= hm.get("parameter") match { 
                      case scala.Some(a)=> a; 
                      case _=> ""}

                      hm.put("parameter",tm+temp+":"+token)

                    }
                  }

                }
                null
              }

              def getScalaSig(cname:String,bytes:Array[Byte],name:String):List[Sig]={

                val reader = new ByteArrayReader(bytes)
                  val cf = new Classfile(reader)

                  val sigs = 
                  cf.methods flatMap {
                  case z@cf.Member(_, flags, name, tpe, attribs) if true =>
                  val w = new Cf(cf)
                    Some(w.getSig(flags, name, tpe, attribs.asInstanceOf[List[w.cf.Attribute]]))
                  case _ => None
                }

                println(sigs)
                sigs

              }

              def parseClassSig(c:String):HashMap[String,String] ={

                var hm=new HashMap[String,String]()

                  var token=""
                var s = new StringTokenizer(c," ")
                  var accessflag=false
                var classflag=false
                var extendflag=false
                var withflag=true

                while(s.hasMoreElements()){
                  token=s.nextToken()

                  token match {

                    case "object" => {hm.put("classtype","object"); classflag=true}
                  case "class" => {hm.put("classtype","class"); classflag=true}
                case "protected" => {hm.put("access","protected"); accessflag=true}
              case "private" => {hm.put("access","private"); accessflag=true}
            case "public" => { hm.put("access", "public"); accessflag=true}
          case "with" => withflag=true
          case "{" => ""
          case "extends" => extendflag=true
          case _ => { if(classflag) { hm.put("classname",token); classflag=false} else {
            if(extendflag) { if(withflag){ var tm= hm.get("superclass") match { 
              case scala.Some(a)=> a; 
              case _=> ""} 
              hm.put("superclass",tm+","+token)}}
              else hm.put("superclass",token)
            } }

          }
        }

        if(!accessflag) hm.put("access","public")
          hm
      }

      class ClassDef {

        var name:String=null
        var methods:List[MethodDef]=null
        var superclass:List[Class[_]]=null
        var traits:List[Class[_]]=null
        var access:String=null
        var classtype:String=null

      }

      class MethodDef {

        var name:String=null
        var returnType:FieldDef=null
        var params:List[FieldDef]=null

      }

      class FieldDef {

        var name:String=null
        var fieldType:Class[_]=null
        var fieldTypeAsString:String=null
        var fieldScalaType:ScalaType=null 

      }


      def getClassDef(al:ArrayList[HashMap[String,String]],hm:HashMap[String,String], sig:List[Sig]):ClassDef ={

        var cd=new ClassDef

        var sthm= buildScalaType(sig)

          cd.name=hm.get("classname") match {
          case Some(a) => a
          case _ => ""
        }
        cd.access=hm.get("access") match {
          case Some(a) => a
          case _ => ""
        }
        cd.classtype=hm.get("classtype") match {
          case Some(a) => a
          case _ => ""
        }
        var st=new StringTokenizer(hm.get("superclass") match {
            case Some(a) => a
            case _ => ""
          },",")


        while(st.hasMoreElements()){
          val t=st.nextToken()

            if(cd.superclass==null)
            cd.superclass=List(getClass(t.asInstanceOf[String]))
          else
            cd.superclass=cd.superclass:::List(getClass(t.asInstanceOf[String]))

        }
        var ml:List[MethodDef]=null
        var tf:FieldDef=null
        var tfs:List[FieldDef]=null
        var tm:MethodDef=null


        for( i <- al)
          {


          tm=new MethodDef
          tm.name=i.get("name") match {
            case Some(a) => a
            case _ => ""
          }
          var p=i.get("parameter") match {
            case Some(a) => a
            case _ => ""
          }

          //println(" Method name is ::::"+tm.name)

          var stal= sthm.get(tm.name)

            var count=1
          if(stal.size>1&& count<stal.size) {
            var st=new StringTokenizer(p,",")
              while(st.hasMoreElements()){
              var s=(st.nextToken()).split(":")
                tf=new FieldDef
              tf.name=s(0)
              tf.fieldType=getClass(s(1))
              tf.fieldTypeAsString=s(1)
              tf.fieldScalaType=stal.get(count)
              if(tm.params==null)
                tm.params=List(tf)
              else
                tm.params=tm.params:::List(tf)
              count=count+1
            } 

          }
          var r=i.get("return") match {
            case Some(a) => a
            case _ => ""
          }
          tf=new FieldDef
          tf.name="return"
          tf.fieldTypeAsString=r.asInstanceOf[String]
          //println(" get Class ::"+r)
          tf.fieldType=getClass(r.asInstanceOf[String])
          if((stal.size)>0)
            tf.fieldScalaType=stal.get(0)

          tm.returnType=tf

          if(ml==null)
            ml=List(tm)
          else
            ml=ml:::List(tm)


        }

        cd.methods=ml
        cd

      }

      def getClass(s:String):Class[_] =
        if(s.indexOf("Array")>0)
        Class.forName("scala.Array")
      else if(s.indexOf("?0")> -1||s.indexOf("scala.AnyRef")> -1)
        classOf[scala.AnyRef]
      else 
        s match {

        case "scala.Predef.String" => classOf[java.lang.String]
        case "scala.Int" => classOf[scala.Int]
        case "scala.Float" => classOf[scala.Float]
        case "scala.Long" => classOf[scala.Long]
        case "scala.Byte" => classOf[scala.Byte]
        case "scala.Char" => classOf[scala.Char]
        case "scala.Short" => classOf[scala.Short]
        case "scala.Double" => classOf[scala.Double]
        case "scala.Boolean" => classOf[scala.Boolean]
        case "scala.Unit" => classOf[scala.Unit]
        case "scala.Null" => classOf[scala.Null]
        case null => classOf[scala.Null]
        case "" => classOf[scala.Null]
        case _ => classOf[scala.Null] // Class.forName(s)

      }

      def buildScalaType(s:List[Sig]):HashMap[String,ArrayList[ScalaType]] ={

        var scalaList:HashMap[String,ArrayList[ScalaType]]=new HashMap[String,ArrayList[ScalaType]]()

          val count=(s.length)
          var sig=s(0)
          var i=0

        for(i <-0 until count){
          sig=s(i)
          sig match {

            case Sig(a:String,b:MTyp) => { 

              b match {

                case MTyp(d:List[Param],e:List[ScalaType],f:ScalaType) => { 
                  var pl=new ArrayList[ScalaType]()
                    var ct=e.length
                  var j=0
                  pl.add(f)
                  for(i <-0 until ct) {
                    pl.add(e(i))
                  }
                  scalaList.put(a,pl)
                }
                case  _ =>

              }

            }
            case _ => 

          }

        }

        scalaList

      }

    }
