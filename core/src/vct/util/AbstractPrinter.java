// -*- tab-width:2 ; indent-tabs-mode:nil -*-
package vct.util;

import hre.ast.MessageOrigin;
import hre.ast.Origin;
import hre.ast.TrackingOutput;
import java.util.*;
import vct.col.ast.*;
import vct.util.Syntax.Associativity;
import static hre.System.*;

/**
 * This class contains the generic code for pretty printing expressions
 * based on a given syntax.
 * 
 * This class will use the precedences supplied by the syntax to minimize the
 * number of parenthesis.
 * 
 * @author sccblom
 *
 */
public class AbstractPrinter extends AbstractVisitor {

  protected Syntax syntax;
  protected int current_precedence;
  protected TrackingOutput out;
  protected boolean in_expr;
  protected int expr_level;

  // use expression mode until exit from current visit
  public void setExpr(){
    if (!in_expr){
      in_expr = true;
      expr_level=1;
    }
  }
  // use inline mode in next accept call
  public void nextExpr(){
    if (!in_expr){
      in_expr = true;
      expr_level=0;
    }
  }
  
  private static final Origin missing=new MessageOrigin("unknown location");
  
  public void pre_visit(ASTNode node){
    super.pre_visit(node);
    if (in_expr) {
      expr_level++;
    }
    Origin o=node.getOrigin();
    if (o==null){
      //throw new Error("found "+node.getClass()+" without origin");
      o=missing;
    }
    out.enter(node.getOrigin());
  }

  public void post_visit(ASTNode node){
    out.leave(node.getOrigin());
    if (in_expr) {
      expr_level--;
      in_expr=(expr_level>0);
    }
    super.post_visit(node);
  }

  public AbstractPrinter(Syntax syntax,TrackingOutput out){
    this.syntax=syntax;
    this.out=out;
    current_precedence=0;
  }

  public void visit(Hole hole){
    out.printf("[.]");  
  }
  
  public void visit(PrimitiveType t){
    String s=syntax.getPrimitiveType(t.sort);
    if (s==null) throw new Error("unsupported primitive type: "+t.sort);
    out.printf(s);
  }

  public void visit(NameExpression e){
    ASTReserved word=e.reserved();
    if (word==null){
      out.print(e.getName());
    } else {
      String s=syntax.getSyntax(word);
      if (s==null) {
        throw Failure("reserved word %s not part of langauge",word);
      }
      out.print(s);
    }
    if (!in_expr) {
      out.lnprintf(";");
    }
  }

  public void visit(MethodInvokation e){
    //boolean statement=!in_expr;
    setExpr();
    if (e.object!=null) {
      // TODO: manage precedence properly.
      e.object.accept(this);
      out.printf(".");
    }
    out.printf("%s",e.method);
    if (e.dispatch!=null){
      out.printf("@");
      e.dispatch.accept(this);
    }
    out.printf("(");
    int N=e.getArity();
    if(N>0){
      int precedence=current_precedence;
      current_precedence=0;
      e.getArg(0).accept(this);
      for(int i=1;i<N;i++){
        out.print(",");
        current_precedence=0;
        e.getArg(i).accept(this);
      }
      current_precedence=precedence;
    }
    out.print(")");
    //if (statement) {
    //  out.lnprintf(";/*abs invoke*/");
    //}
  }

  public void visit(OperatorExpression e){
    StandardOperator op=e.getOperator();
    String op_syntax[]=syntax.getSyntax(op);
    if (op_syntax==null){
      throw new Error("no syntax defined for "+op+" operation");
    }
    int N=op.arity();
    ASTNode args[]=e.getArguments();
    setExpr();    
    if (N<0){
      out.print(op_syntax[0]);
      if(args.length>0){
        args[0].accept(this);
      }
      for(int i=1;i<args.length;i++){
        out.print(op_syntax[1]);
        args[i].accept(this);
      }      
      out.print(op_syntax[2]);
    } else {
      if (op_syntax[0]!=null && op_syntax[0].length()>0) out.printf("%s ",op_syntax[0]);
      for(int i=0;i<N;i++){
        if (i>0) out.printf(" %s ",op_syntax[i]);
        args[i].accept(this);
      }
      if (op_syntax[0]!=null && op_syntax[N].length()>0) out.printf(" %s",op_syntax[N]);
    }
  }

  public void visit(ConstantExpression ce){
    if (!in_expr) Abort("constant %s outside of expression for %s",ce,ce.getOrigin());
    out.print(ce.toString());
  }
  
  public void visit(ASTSpecial s){
    switch(s.kind){
    default:
      Abort("unimplemented special %s",s.kind);
    }
  }
  
  public void visit(ASTSpecialDeclaration s){
    switch(s.kind){
    case Invariant:
    case Given:
    case Yields:
    case Requires:
    case Ensures:
      setExpr();
      out.printf("%s ",s.kind);
      if (s.args[0]==null) {
        out.print("null");
      } else {
        s.args[0].accept(this);
      }
      out.println(";");
      break;
    case Comment:
      String lines[]=s.args[0].toString().split("\n");
      for(int i=0;i<lines.length;i++){
        out.println(lines[i]);
      }
      break;
    default:
      Abort("unimplemented special declaration %s",s.kind);
    }
  }
}

