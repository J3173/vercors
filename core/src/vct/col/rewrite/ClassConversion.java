package vct.col.rewrite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

import vct.col.ast.*;
import vct.col.ast.Method.Kind;

/**
 * This rewriter converts a program with classes into
 * a program with records.
 * 
 * 
 * @author Stefan Blom
 *
 */
public class ClassConversion extends AbstractRewriter {

  private static final String SEP="__";
      
  public ClassConversion(ProgramUnit source) {
    super(source);
  }

  private Stack<Boolean> constructor_this=new Stack();
  {
    constructor_this.push(false);
  }
  
  public static final String THIS="diz";
  @Override
  public void visit(NameExpression e){
    if (e.isReserved(ASTReserved.This)){
      if (constructor_this.peek()){
        result=create.reserved_name(ASTReserved.Result);
      } else {
        result=create.unresolved_name(THIS);
      }
    } else {
      super.visit(e);
    }
  }
  @Override
  public void visit(ASTClass cl){
    ASTClass res=create.ast_class(cl.name, ASTClass.ClassKind.Record,null, null, null);
    for(DeclarationStatement decl:cl.staticFields()){
      String name=cl.name+SEP+decl.name;
      create.enter();
      create(decl);
      target().add(create.field_decl(name, rewrite(decl.getType()), rewrite(decl.getInit())));
      create.leave();
    }
    for(DeclarationStatement decl:cl.dynamicFields()){
      res.add(rewrite(decl));
    }
    for(Method m:cl.methods()){
      create.enter();
      create.setOrigin(m.getOrigin());
      Method.Kind kind;
      Type returns;
      if (m.kind==Method.Kind.Constructor){
        Debug("constructor %s",m.name);
        returns=create.class_type(cl.name);
        kind=Method.Kind.Plain;
      } else {
        returns=rewrite(m.getReturnType());
        kind=m.kind;
      }
      ContractBuilder cb=new ContractBuilder();
      String name=cl.name+SEP+m.name;
      ArrayList<DeclarationStatement> args=new ArrayList();
      if (m.kind!=Method.Kind.Constructor && !m.isStatic()){
        args.add(create.field_decl(THIS,create.class_type(cl.name)));
        cb.requires(create.expression(StandardOperator.NEQ,
            create.local_name(THIS),
            create.reserved_name(ASTReserved.Null)));
      }
      for(DeclarationStatement d:m.getArgs()){
        args.add(rewrite(d));
      }
      ASTNode body=rewrite(m.getBody());
      if (m.kind==Method.Kind.Constructor){
        if (body!=null){
          body=create.block(
            create.field_decl(THIS,create.class_type(cl.name)),
            create.assignment(
                create.local_name(THIS),
                create.new_record(create.class_type(cl.name))
            ),
            body,
            create.return_statement(create.local_name(THIS))
          );
        }
        cb.ensures(create.expression(StandardOperator.NEQ,
            create.reserved_name(ASTReserved.Result),
            create.reserved_name(ASTReserved.Null)));
      }
      boolean varArgs=m.usesVarArgs();
      if (m.kind==Method.Kind.Constructor) {
        constructor_this.push(true);
        rewrite(m.getContract(),cb);
        constructor_this.pop();
      } else {
        rewrite(m.getContract(),cb);
      }
      Method p=create.method_kind(kind, returns,cb.getContract(), name, args.toArray(new DeclarationStatement[0]), varArgs, body);
      create.leave();
      p.setStatic(true);
      target().add(p);
    }
    result=res;
  }
  
  @Override
  public void visit(OperatorExpression e){
    if (e.isa(StandardOperator.Build) && (e.getArg(0) instanceof ClassType)){
      // If this is actually a constructor call.
      String method=e.getArg(0)+SEP+e.getArg(0);
      ASTNode args[]=e.getArguments();
      ASTNode rw_args[]=new ASTNode[args.length-1];
      for(int i=1;i<args.length;i++){
        rw_args[i-1]=rewrite(args[i]);
      }
      MethodInvokation res=create.invokation(null, null, method, rw_args);
      res.set_before(rewrite(e.get_before()));
      res.set_after(rewrite(e.get_after()));
      result=res;
    } else {
      super.visit(e);
    }
  }
  
  @Override
  public void visit(MethodInvokation s){
    String method;
    ArrayList<ASTNode> args=new ArrayList();
    Method def=s.getDefinition();
    ASTNode extra=null;
    ClassType dispatch=s.dispatch;
    if (def.getParent()==null){
      method=s.method;
    } else if (s.object instanceof ClassType){
      if (s.method.equals(Method.JavaConstructor)){
        method=s.dispatch.getName()+SEP+s.dispatch.getName();
        dispatch=null;
      } else if (def.getParent() instanceof AxiomaticDataType){
        method=s.method;
      } else {
        method=((ClassType)s.object).getName()+SEP+s.method;
      }
    } else if (s.object==null){
      if (s.method.equals(Method.JavaConstructor)){
        method=s.dispatch.getName()+SEP+s.dispatch.getName();
        dispatch=null;
      } else {
        method=s.method;
      }
    } else {
      method=((ClassType)s.object.getType()).getName();
      if (method.equals("<<adt>>") || def.getParent() instanceof AxiomaticDataType){
        method=s.method;
      } else {
        method+=SEP+s.method;
        if (!def.isStatic()){
          args.add(rewrite(s.object));
        }
        if (def.kind==Kind.Predicate && !s.object.isReserved(ASTReserved.This) && (!fold_unfold) ){
          //extra=create.expression(StandardOperator.NEQ,rewrite(s.object),create.reserved_name(ASTReserved.Null));
        }
      }      
    }
    for(ASTNode arg :s.getArgs()){
      args.add(rewrite(arg));
    }
    MethodInvokation res=create.invokation(null, dispatch, method, args.toArray(new ASTNode[0]));
    res.set_before(rewrite(s.get_before()));
    res.set_after(rewrite(s.get_after()));
    if (extra!=null){
      result=create.expression(StandardOperator.Star,extra,res);
    } else {
      result=res;
    }
  }
}
