// -*- tab-width:2 ; indent-tabs-mode:nil -*-
package vct.col.ast;

import hre.ast.MessageOrigin;
import hre.ast.Origin;

import java.util.HashSet;

import vct.col.util.ASTUtils;

public class Contract extends ASTNode {

  @Override
  public <R,A> R accept_simple(ASTMapping1<R,A> map,A arg){
    return map.map(this,arg);
  }

  public final static ASTNode default_true;
  static {
    Origin origin=new MessageOrigin("contract default true");
    default_true=new ConstantExpression(true,origin);
  }

  public final ASTNode invariant;
  public final ASTNode pre_condition;
  public final ASTNode post_condition;
  public final DeclarationStatement given[];
  public final DeclarationStatement yields[];
  public final DeclarationStatement signals[];
  public final ASTNode modifies[];
  
  public boolean isEmpty() {
    return invariant.isConstant(default_true)
        && pre_condition.isConstant(default_true)
        && post_condition.isConstant(default_true)
        && given.length==0 && yields.length==0
        && (signals==null || signals.length==0)
        && modifies == null
        ;
  }

  private HashSet<String> labels=new HashSet<String>();
    
  public Contract(DeclarationStatement given[],DeclarationStatement yields[],ASTNode inv,ASTNode pre_condition,ASTNode post_condition){
    this.invariant=inv;
    this. pre_condition= pre_condition;
    this.post_condition=post_condition;
    this.given=given;
    this.yields=yields;
    this.signals=null;
    modifies=null;
    build_labels();
  }
  
  public Contract(DeclarationStatement given[],DeclarationStatement yields[],ASTNode modifies[],ASTNode inv,ASTNode pre_condition,ASTNode post_condition){
    this.invariant=inv;
    this. pre_condition= pre_condition;
    this.post_condition=post_condition;
    this.given=given;
    this.yields=yields;
    this.modifies=modifies;
    this.signals=null;
    build_labels();
  }
  
  public Contract(DeclarationStatement given[],DeclarationStatement yields[],ASTNode modifies[],ASTNode inv,
      ASTNode pre_condition,ASTNode post_condition,DeclarationStatement[]signals){
    this.invariant=inv;
    this. pre_condition= pre_condition;
    this.post_condition=post_condition;
    this.given=given;
    this.yields=yields;
    this.modifies=modifies;
    this.signals=signals;
    build_labels();
  }

  public void build_labels(){
    for(ASTNode part:ASTUtils.conjuncts(pre_condition,StandardOperator.Star)){
      for(NameExpression lbl:part.getLabels()){
        labels.add(lbl.getName());
      }
    }
    for(ASTNode part:ASTUtils.conjuncts(post_condition,StandardOperator.Star)){
      for(NameExpression lbl:part.getLabels()){
        labels.add(lbl.getName());
      }     
    }
  }
  public boolean hasLabel(String name) {
     return labels.contains(name);
  }

  
  @Override
  public <T> void accept_simple(ASTVisitor<T> visitor){
    try {
      visitor.visit(this);
    } catch (Throwable t){
      if (thrown.get()!=t){
        System.err.printf("Triggered by %s:%n",getOrigin());
        thrown.set(t);
     }
      throw t;
    }
  }
  
  @Override
  public <T> T accept_simple(ASTMapping<T> map){
    try {
      return map.map(this);
    } catch (Throwable t){
      if (thrown.get()!=t){
        System.err.printf("Triggered by %s:%n",getOrigin());
        thrown.set(t);
     }
      throw t;
    }
  }

 
  
}
