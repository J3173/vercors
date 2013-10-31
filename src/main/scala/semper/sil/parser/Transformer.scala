package semper.sil.parser

/* TODO: This is basically a copy of sil.ast.utility.Transformer. Can we share code?
 *       This could be done by using tree visiting and rewriting functionality from Kiama,
  *      or to implement something generic ourselves. Shapeless or Scala Macros might be
  *      useful because they might help us to abstract over arity when it comes to
  *      copying existing nodes, i.e., case classes.
 */
object Transformer {
  def transform[A <: PNode](node: A,
                           pre: PartialFunction[PNode, PNode] = PartialFunction.empty)(
                            recursive: PNode => Boolean = !pre.isDefinedAt(_),
                            post: PartialFunction[PNode, PNode] = PartialFunction.empty): A = {

    @inline
    def go[B <: PNode](root: B): B = {
      transform(root, pre)(recursive, post)
    }

    def recurse(parent: PNode): PNode = {
      parent match {
        case _: PIdnDef => parent
        case _: PIdnUse => parent
        case PFormalArgDecl(idndef, typ) => PFormalArgDecl(go(idndef), go(typ))

        case _: PPrimitiv => parent
        case PDomainType(domain, args) => PDomainType(go(domain), args map go)
        case PSeqType(elementType) => PSeqType(go(elementType))
        case PSetType(elementType) => PSetType(go(elementType))
        case PMultisetType(elementType) => PMultisetType(go(elementType))
        case _: PUnknown => parent
        case  _: PPredicateType => parent

        case PBinExp(left, op, right) => PBinExp(go(left), op, go(right))
        case PUnExp(op, exp) => PUnExp(op, go(exp))
        case _: PIntLit => parent
        case _: PResultLit => parent
        case _: PBoolLit => parent
        case _: PNullLit => parent
        case PFieldAccess(rcv, idnuse) => PFieldAccess(go(rcv), go(idnuse))
        case PPredicateAccess(args, idnuse) => PPredicateAccess( args map go, go(idnuse))
        case PFunctApp(func, args) => PFunctApp(go(func), args map go)
        case PUnfolding(acc, exp) => PUnfolding(go(acc), go(exp))
        case PFolding(acc, exp) => PFolding(go(acc), go(exp))
        case PApplying(wand, exp) => PApplying(go(wand), go(exp))
        case PExists(vars, exp) => PExists(vars map go, go(exp))
        case PForall(vars, triggers, exp) => PForall(vars map go, triggers map (_ map go), go(exp))
        case PCondExp(cond, thn, els) => PCondExp(go(cond), go(thn), go(els))
        case PInhaleExhaleExp(in, ex) => PInhaleExhaleExp(go(in), go(ex))
        case PCurPerm(loc) => PCurPerm(go(loc))
        case _: PNoPerm => parent
        case _: PFullPerm => parent
        case _: PWildcard => parent
        case _: PEpsilon => parent
        case PAccPred(loc, perm) => PAccPred(go(loc), go(perm))
        case POld(e) => POld(go(e))
        case PPackageOld(e) => PPackageOld(go(e))
        case PEmptySeq(t) => PEmptySeq(go(t))
        case PExplicitSeq(elems) => PExplicitSeq(elems map go)
        case PRangeSeq(low, high) => PRangeSeq(go(low), go(high))
        case PSeqIndex(seq, idx) => PSeqIndex(go(seq), go(idx))
        case PSeqTake(seq, n) => PSeqTake(go(seq), go(n))
        case PSeqDrop(seq, n) => PSeqDrop(go(seq), go(n))
        case PSeqUpdate(seq, idx, elem) => PSeqUpdate(go(seq), go(idx), go(elem))
        case PSize(seq) => PSize(go(seq))
        case _: PEmptySet => parent
        case PExplicitSet(elems) => PExplicitSet(elems map go)
        case _: PEmptyMultiset => parent
        case PExplicitMultiset(elems) => PExplicitMultiset(elems map go)

        case PSeqn(ss) => PSeqn(ss map go)
        case PFold(e) => PFold(go(e))
        case PUnfold(e) => PUnfold(go(e))
        case PPackageWand(e) => PPackageWand(go(e))
        case PApplyWand(e) => PApplyWand(go(e))
        case PExhale(e) => PExhale(go(e))
        case PAssert(e) => PAssert(go(e))
        case PInhale(e) => PInhale(go(e))
        case PNewStmt(target) => PNewStmt(go(target))
        case PVarAssign(idnuse, rhs) => PVarAssign(go(idnuse), go(rhs))
        case PFieldAssign(fieldAcc, rhs) => PFieldAssign(go(fieldAcc), go(rhs))
        case PIf(cond, thn, els) => PIf(go(cond), go(thn), go(els))
        case PWhile(cond, invs, body) => PWhile(go(cond), invs  map go, go(body))
        case PFreshReadPerm(vars, stmt) => PFreshReadPerm(vars map go, go(stmt))
        case PLocalVarDecl(idndef, typ, init) => PLocalVarDecl(go(idndef), go(typ), init map go)
        case PMethodCall(targets, method, args) => PMethodCall(targets map go, go(method), args map go)
        case PLabel(idndef) => PLabel(go(idndef))
        case PGoto(target) => PGoto(go(target))
        case PLetAss(idndef, exp) => PLetAss(go(idndef), go(exp))
        case _: PSkip => parent

        case PProgram(file, domains, fields, functions, predicates, methods) => PProgram(file, domains map go, fields map go, functions map go, predicates map go, methods map go)
        case PMethod(idndef, formalArgs, formalReturns, pres, posts, body) => PMethod(go(idndef), formalArgs map go, formalReturns map go, pres map go, posts map go, go(body))
        case PDomain(idndef, typVars, funcs, axioms) => PDomain(go(idndef), typVars map go, funcs map go, axioms map go)
        case PField(idndef, typ) => PField(go(idndef), go(typ))
        case PFunction(idndef, formalArgs, typ, pres, posts, exp) => PFunction(go(idndef), formalArgs map go, go(typ), pres map go, posts map go, go(exp))
        case PDomainFunction(idndef, formalArgs, typ, unique) => PDomainFunction(go(idndef), formalArgs map go, go(typ), unique)
        case PPredicate(idndef, formalArgs, body) => PPredicate(go(idndef), formalArgs map go, go(body))
        case PAxiom(idndef, exp) => PAxiom(go(idndef), go(exp))
      }
    }

    val beforeRecursion = pre.applyOrElse(node, identity[PNode])
    val afterRecursion = if (recursive(node)) {
      recurse(beforeRecursion)
    } else {
      beforeRecursion
    }
    post.applyOrElse(afterRecursion, identity[PNode]).asInstanceOf[A]
  }
}
