package vct.col.rewrite;

import vct.col.ast.ASTNode;
import vct.col.ast.ClassType;
import vct.col.ast.Contract;
import vct.col.ast.ContractBuilder;
import vct.col.ast.DeclarationStatement;
import vct.col.ast.OperatorExpression;
import vct.col.ast.ProgramUnit;
import vct.col.ast.StandardOperator;
import vct.util.Syntax;
import vct.util.Syntax.Associativity;

public class Parenthesize extends AbstractRewriter {

  private final Syntax syntax;
  
  public Parenthesize(Syntax syntax){
    super(null,null);
    this.syntax=syntax;
  }
  
  public void rewrite(Contract c,ContractBuilder cb){
    if (c==null) return;
    cb.given(rewrite(c.given));
    cb.yields(rewrite(c.yields));
    if (c.modifies!=null) cb.modifies(rewrite(c.modifies)); 
    cb.appendInvariant(rewrite(c.invariant));
    cb.requires(rewrite(c.pre_condition));
    cb.ensures(rewrite(c.post_condition));
    if (c.signals!=null) for(DeclarationStatement decl:c.signals){
      cb.signals((ClassType)rewrite(decl.getType()),decl.getName(),rewrite(decl.getInit()));      
    }
  }

  public Parenthesize(Syntax syntax, ProgramUnit pu) {
    super(pu);
    this.syntax=syntax;
  }

  @Override
  public void visit(OperatorExpression e){
    StandardOperator op=e.getOperator();
    ASTNode args[]=rewrite(e.getArguments());
    if (syntax.isOperator(op)){
      for(int i=0;i<args.length;i++){
        if (args[i] instanceof OperatorExpression){
          StandardOperator child_op=((OperatorExpression)args[i]).getOperator();
          if (syntax.isOperator(child_op)){
            int prio=syntax.getPrecedence(child_op);
            if (i==0 && syntax.getAssociativity(op)==Associativity.Left
              ||i==(args.length-1) && syntax.getAssociativity(op)==Associativity.Right
            ){
              prio++;
            }
            if (prio<=syntax.getPrecedence(op)){
              args[i]=create.expression(StandardOperator.Tuple,args[i]);
            }
          }
        }
      }
    }
    result=create.expression(op,args);
  }
}
