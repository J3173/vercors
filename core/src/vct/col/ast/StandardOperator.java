// -*- tab-width:2 ; indent-tabs-mode:nil -*-
package vct.col.ast;

public enum StandardOperator {
  /** get a location */
  Get(1),
  /** set a location */
  Set(2),
  /** Unary plus. */
  UPlus(1),
  /** Unary minus. */
  UMinus(1),
  /** Exponentiation */
  Exp(2),
  /** Addition. */
  Plus(2),
  /** Binary minus or subtraction. */
  Minus(2),
  /** Multiplication. */
  Mult(2),
  /** Division. */
  Div(2),
  /** Modulo or remainder. */
  Mod(2),
  /** Bitwise and. */
  BitAnd(2),
  /** Bitwise or. */
  BitOr(2),
  /** Bitwise eXclusive OR. */
  BitXor(2),
  /** Bitwise negation or complement. */
  BitNot(2),
  /** Logical and. May or may not mean 'and also' */
  And(2),
  /** Logical or. May or may not mean 'or else' */
  Or(2),
  /** Logical negation. */
  Not(1),
  /** Logical implication. */
  Implies(2),
  /** Logical if and only if. */  
  IFF(2),
  /** Equality test. */
  EQ(2),
  /** Inequality test. */
  NEQ(2),
  /** Greater Than test. */
  GT(2),
  /** Greater Than or Equal te09794e43d04f6fc4c662f45eff301fa02599c1f6st. */
  GTE(2),
  /** Lesss Than test. */
  LT(2),
  /** Lesss Than or Equal test. */
  LTE(2),
  /** If then else operator or conditional. */
  ITE(3),
  /** return the type of a value */
  TypeOf(1),
  /** Instance of test. */
  Instance(2),
  /** Type Cast Expression. */
  Cast(2),
  /** Sub type relation. */
  SubType(2),
  /** Super type relation. */
  SuperType(2),
  /** Intersection type */
  InterSect(-1),
  /** Simple assignment operator. */
  Assign(2),
  /** Multiply with */
  MulAssign(2),
  /** Divide by */
  DivAssign(2),
  /** Assign modulo */
  RemAssign(2),
  /** Add to */
  AddAssign(2),
  /** Subtract */
  SubAssign(2),
  /** shift left */
  ShlAssign(2),
  /** shift right */
  ShrAssign(2),
  /** signed shift right */
  SShrAssign(2),
  /** bitwise and */
  AndAssign(2),
  /** bitwise xor */
  XorAssign(2),
  /** bitwise or */
  OrAssign(2), 
  /** Increment and return new value. */
  PreIncr(1),
  /** Decrement and return new value. */
  PreDecr(1),
  /** Increment and return old value. */
  PostIncr(1),
  /** Decrement and return old value. */
  PostDecr(1),
  /** Shift left. */
  LeftShift(2),
  /** (signed) shift right. */
  RightShift(2),
  /** Unsigned shift right. */
  UnsignedRightShift(2),
  /** Separating conjunction. */
  Star(2),
  /** Separating implication. */
  Wand(2),
  /** Fractional permission predicate. */
  Perm(2),
  /** Fractional permission predicate with value access. */ 
  PointsTo(3),
  /** Immutable permission predicate.  */
  Value(1),
  /**
   * Permission to a field that is part of an active history.
   */
  HistoryPerm(2),
  /**
   * Declare a location to be volatile. That is, accessible by multiple threads.
   */
  Volatile(1),
  /** Array permission predicate.
   *  ArrayPerm(name,first,step,count,p);
   *  The arguments are
   *  <UL>
   *   <li> the name of the array
   *   <li> the first index to which access is denoted
   *   <li> the step by which the indices are increased
   *   <li> the count of elements to which access is granted
   *   <li> the fraction p access for every index
   *  </UL>
   *  the first argument is the name of the array.
   */
  ArrayPerm(5),
  /** Select a member from a struct.
   * Member selection form classes is represented by Dereference */
  StructSelect(2),
  /**
   * dereference a pointer to a struct and select a member.
   */
  StructDeref(2),
  /*
  Guarded member selection. (Selection with built-in null test.)
  GuardedSelect(2),*/
  /**
   * Declare that a volatile field has been incremented by adding another value.
   */
  AddsTo(2),
  /** Array subscript. */
  Subscript(2),
  /** Lock statement. */
  Lock(1),
  /** Unfold statement. */
  Unlock(1),
  /** Direct proof statement. */
  DirectProof(1),
  /** Open a predicate family. */
  Open(1),
  /** Close a predicate family. */
  Close(1),
  /** Fold statement. */
  Fold(1),
  /** Unfold statement. */
  Unfold(1),
  /**
   * Refute statement. Refute a fact at a point in the program.
   */
  Refute(1),
  /** Assert Statement. */
  Assert(1),
  /** Assume statement. */
  Assume(1),
  /** Access statement for use in magic wand proofs */
  Access(1),
  /** Use statement for magic wand proofs */
  Use(1),
  /** QED statement for magic wand proofs */
  QED(1),
  /** Apply statement for magic wands */
  Apply(1),
  /** Declare a witness variable, for use in witness proofs. */
  Witness(1),
  /** Havoc statement. */
  Havoc(1),
  /** Hoare Predicate statement. This is the main ingredient of a Hoare Logic proof. */
  HoarePredicate(1),
  /** Evaluate argument in pre-execution(old) state. */
  Old(1),
  /** Continue with next value in loop */
  Continue(1),
  /** Create a new uninitialized object, note that Java Constructors are encoded as a MethodInvokation. */
  New(1),
  /** Create a new uninitialized object, Silver style. */
  NewSilver(-1),
  /** Create a new uninitialized array */
  NewArray(2),
  /** Length of an array */
  Length(1),
  /** Get the size of a container, such as a sequence. */
  Size(1),
  /** Empty list */
  Nil(1),
  /** pre-pre element to list */
  Cons(2),
  /** Drop elements from a list */
  Drop(2),
  /** Take elements from a list */
  Take(2),
  /** append two lists */
  Append(2),
  /** check if an element is a member of a container. */
  Member(2),
  AddrOf(1),
  /** head of a list. */
  Head(1),
  /** tail of a list. */
  Tail(1),
  /** Build list/sequence constant. */
  Build(-1),
  /** Bind an output argument of a method to this pattern.
   *  E.g. <code>?x</code> and <code>?(x,y)M</code>. 
   */
  BindOutput(1),
  /**
   * Building a tuple, also used to represent parenthesized expressions.
   */
  Tuple(-1),
  /**
   * send permission statement for parallel loops : /DRB
   */
  Send(3),
  /**
   * receive permission statement for parallel loops : /DRB
   */
  Recv(3),  
  /**
   * Current permission on a location.
   */
  CurrentPerm(1),
  /**
   * Scale the permissions on a resource.
   */
  Scale(2),
  /**
   * Build a range [low,high).
   */
  RangeSeq(2),
  /**
   * Unfold in expression temporarily.
   */
  Unfolding(2),
  /**
   * The process algebra operator left merge.
   */
  LeftMerge(2),
  /**
   * The history predicate takes a history, a fraction and a process as arguments.
   */
  History(3),
  /**
   * The future predicate takes a history, a fraction and a process as arguments.
   */
  Future(3),
  /**
   * The abstract state operator for Histories and Futures
   */
  AbstractState(2),
  /**
   * Specifies that a sub-term in a higher order rewrite patterns is independent of a variable.
   */
  IndependentOf(2),
  /**
   * contribution to a reduction variable.
   */
  Contribution(2),
  /**
   * Declare variable to be sum-reducible.
   */
  ReducibleSum(1),
  ReducibleMax(1),
  ReducibleMin(1),
  /**
   * Standard predicate that indicates that a non-reentrant lock is held on an object. 
   */
  Held(1),
  /**
   * The identity operator.
   */
  Identity(1)
  ;

  private final int arity;
  
  StandardOperator(int arity){
    this.arity=arity;
  }
  
  public int arity(){ return arity; }

/*
Java Operators 	Precedence
14 postfix 	expr++ expr--
13 unary 	++expr --expr +expr -expr ~ !
12 multiplicative 	* / %
11 additive 	+ -
10 shift 	<< >> >>>
 9 relational 	< > <= >= instanceof
 8 equality 	== !=
 7 bitwise AND 	&
 6 bitwise exclusive OR 	^
 5 bitwise inclusive OR 	|
 4 logical AND 	&&
 3 logical OR 	||
 2 ternary 	? :
 1 assignment 	= += -= *= /= %= &= ^= |= <<= >>= >>>=
*/

}

