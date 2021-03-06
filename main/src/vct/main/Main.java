// -*- tab-width:2 ; indent-tabs-mode:nil -*-

package vct.main;

import hre.HREError;
import hre.ast.FileOrigin;
import hre.ast.MessageOrigin;
import hre.config.BooleanSetting;
import hre.config.OptionParser;
import hre.config.StringListSetting;
import hre.config.StringSetting;
import hre.debug.HeapDump;
import hre.io.PrefixPrintStream;
import hre.lang.Ref;
import hre.util.CompositeReport;
import hre.util.TestReport;
import hre.util.TestReport.Verdict;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import vct.antlr4.parser.JavaResolver;
import vct.clang.printer.CPrinter;
import vct.col.annotate.DeriveModifies;
import vct.col.ast.*;
import vct.col.rewrite.AbstractMethodResolver;
import vct.col.rewrite.AbstractRewriter;
import vct.col.rewrite.AccessIntroduce;
import vct.col.rewrite.AddTypeADT;
import vct.col.rewrite.AssignmentRewriter;
import vct.col.rewrite.BoxingRewriter;
import vct.col.rewrite.CSLencoder;
import vct.col.rewrite.ChalicePreProcess;
import vct.col.rewrite.CheckHistoryAlgebra;
import vct.col.rewrite.CheckHistoryAlgebra.Mode;
import vct.col.rewrite.CheckProcessAlgebra;
import vct.col.rewrite.ClassConversion;
import vct.col.rewrite.ConstructorRewriter;
import vct.col.rewrite.CurrentThreadRewriter;
import vct.col.rewrite.DefineDouble;
import vct.col.rewrite.DynamicStaticInheritance;
import vct.col.rewrite.ExplicitPermissionEncoding;
import vct.col.rewrite.FilterClass;
import vct.col.rewrite.FilterSpecIgnore;
import vct.col.rewrite.FinalizeArguments;
import vct.col.rewrite.Flatten;
import vct.col.rewrite.FlattenBeforeAfter;
import vct.col.rewrite.ForkJoinEncode;
import vct.col.rewrite.GenericPass1;
import vct.col.rewrite.GhostLifter;
import vct.col.rewrite.GlobalizeStaticsField;
import vct.col.rewrite.GlobalizeStaticsParameter;
import vct.col.rewrite.InlinePredicatesRewriter;
import vct.col.rewrite.IterationContractEncoder;
import vct.col.rewrite.KernelRewriter;
import vct.col.rewrite.LockEncoder;
import vct.col.rewrite.MergeLoops;
import vct.col.rewrite.OptimizeQuantifiers;
import vct.col.rewrite.PVLCompiler;
import vct.col.rewrite.ParallelBlockEncoder;
import vct.col.rewrite.PureMethodsAsFunctions;
import vct.col.rewrite.RandomizedIf;
import vct.col.rewrite.RecognizeLoops;
import vct.col.rewrite.RecognizeMultiDim;
import vct.col.rewrite.ReorderAssignments;
import vct.col.rewrite.RewriteArrayRef;
import vct.col.rewrite.RewriteSystem;
import vct.col.rewrite.SatCheckRewriter;
import vct.col.rewrite.ScaleAlways;
import vct.col.rewrite.SetGetIntroduce;
import vct.col.rewrite.SilverClassReduction;
import vct.col.rewrite.SilverConstructors;
import vct.col.rewrite.SilverImplementIdentity;
import vct.col.rewrite.SilverReorder;
import vct.col.rewrite.SimplifyCalls;
import vct.col.rewrite.Standardize;
import vct.col.rewrite.StripConstructors;
import vct.col.rewrite.VoidCalls;
import vct.col.rewrite.VoidCallsThrown;
import vct.col.rewrite.WandEncoder;
import vct.col.util.FeatureScanner;
import vct.col.util.SimpleTypeCheck;
import vct.java.printer.JavaDialect;
import vct.java.printer.JavaSyntax;
import vct.util.ClassName;
import vct.util.Configuration;
import vct.util.Syntax;
import static hre.System.*;

/**
 * VerCors Tool main verifier.
 * @author Stefan Blom
 *
 */
public class Main
{
  private static ProgramUnit program=new ProgramUnit();
  
  private static List<ClassName> classes;
  
  static class ChaliceTask implements Callable<TestReport> {
    private ClassName class_name;
    private ProgramUnit program;

    public ChaliceTask(ProgramUnit program,ClassName class_name){
      this.program=program;
      this.class_name=class_name;
    }
    @Override
    public TestReport call() {
      System.err.printf("Validating class %s...%n",class_name.toString("."));
      long start=System.currentTimeMillis();
      ProgramUnit task=new FilterClass(program,class_name.name).rewriteAll();
      task=new Standardize(task).rewriteAll();
      new SimpleTypeCheck(task).check();
      TestReport report=vct.boogie.Main.TestChalice(task);
      System.err.printf("%s: result is %s (%dms)%n",class_name.toString("."),
          report.getVerdict(),System.currentTimeMillis()-start);
      return report;
    }
    
  }

  public static void main(String[] args) throws Throwable
  {
    long globalStart = System.currentTimeMillis();
    OptionParser clops=new OptionParser();
    clops.add(clops.getHelpOption(),'h',"help");
    
    BooleanSetting boogie=new BooleanSetting(false);
    clops.add(boogie.getEnable("select Boogie backend"),"boogie");
    BooleanSetting chalice=new BooleanSetting(false);
    clops.add(chalice.getEnable("select Chalice backend"),"chalice");
    BooleanSetting chalice2sil=new BooleanSetting(false);
    clops.add(chalice2sil.getEnable("select Silicon backend via chalice2sil"),"chalice2sil");
    final StringSetting silver=new StringSetting("silver");
    clops.add(silver.getAssign("select Silver backend (silicon/carbon)"),"silver");
    clops.add(silver.getAssign("select Silicon backend","silicon"),"silicon");
    clops.add(silver.getAssign("select Carbon backend","carbon"),"carbon");
    BooleanSetting verifast=new BooleanSetting(false);
    clops.add(verifast.getEnable("select Verifast backend"),"verifast");
    BooleanSetting dafny=new BooleanSetting(false);
    clops.add(dafny.getEnable("select Dafny backend"),"dafny");
    
    CommandLineTesting.add_options(clops);

    final BooleanSetting check_defined=new BooleanSetting(false);
    clops.add(check_defined.getEnable("check if defined processes satisfy their contracts."),"check-defined");
    final BooleanSetting check_axioms=new BooleanSetting(false);
    clops.add(check_axioms.getEnable("check if defined processes satisfy their contracts."),"check-axioms");
    final BooleanSetting check_history=new BooleanSetting(false);
    clops.add(check_history.getEnable("check if defined processes satisfy their contracts."),"check-history");   
    
    final BooleanSetting separate_checks=new BooleanSetting(false);
    clops.add(separate_checks.getEnable("validate classes separately"),"separate");
    BooleanSetting help_passes=new BooleanSetting(false);
    clops.add(help_passes.getEnable("print help on available passes"),"help-passes");
    BooleanSetting sequential_spec=new BooleanSetting(false);
    clops.add(sequential_spec.getEnable("sequential specification instead of concurrent"),"sequential");
    StringListSetting pass_list=new StringListSetting();
    clops.add(pass_list.getAppendOption("add to the custom list of compilation passes"),"passes");
    StringListSetting show_before=new StringListSetting();
    clops.add(show_before.getAppendOption("Show source code before given passes"),"show-before");
    StringListSetting show_after=new StringListSetting();
    clops.add(show_after.getAppendOption("Show source code after given passes"),"show-after");
    StringSetting show_file=new StringSetting(null);
    clops.add(show_file.getAssign("redirect show output to files instead of stdout"),"save-show");
    StringListSetting stop_after=new StringListSetting();
    clops.add(stop_after.getAppendOption("Stop after given passes"),"stop-after");
    
    
    BooleanSetting explicit_encoding=new BooleanSetting(false);
    clops.add(explicit_encoding.getEnable("explicit encoding"),"explicit");
    BooleanSetting inline_predicates=new BooleanSetting(false);
    clops.add(inline_predicates.getEnable("inline predicates with arguments"),"inline");
    BooleanSetting global_with_field=new BooleanSetting(false);
    clops.add(global_with_field.getEnable("Encode global access with a field rather than a parameter. (expert option)"),"global-with-field");
    
    BooleanSetting infer_modifies=new BooleanSetting(false);
    clops.add(infer_modifies.getEnable("infer modifies clauses"),"infer-modifies");
    BooleanSetting no_context=new BooleanSetting(false);
    clops.add(no_context.getEnable("disable printing the context of errors"),"no-context");
    
    StringListSetting debug_list=new StringListSetting();
    clops.add(debug_list.getAppendOption("print debug message for given classes and/or packages"),"debug");
    BooleanSetting where=new BooleanSetting(false);
    clops.add(where.getEnable("report which class failed"),"where");
    
    BooleanSetting progress=new BooleanSetting(false);
    clops.add(progress.getEnable("print progress messages"),"progress");
    
    BooleanSetting sat_check=new BooleanSetting(true);
    clops.add(sat_check.getDisable("Disable checking if method pre-conditions are satisfiable"), "disable-sat");
    
    Configuration.add_options(clops);
    
    String input[]=clops.parse(args);
    hre.System.setProgressReporting(progress.get());
    
    for(String name:debug_list){
      hre.System.EnableDebug(name,java.lang.System.err,"vct("+name+")");
    }
    hre.System.EnableWhere(where.get());

    Hashtable<String,CompilerPass> defined_passes=new Hashtable<String,CompilerPass>();
    Hashtable<String,ValidationPass> defined_checks=new Hashtable<String,ValidationPass>();
    defined_passes.put("java",new CompilerPass("print AST in java syntax"){
        public ProgramUnit apply(ProgramUnit arg,String ... args){
          JavaSyntax.getJava(JavaDialect.JavaVerCors).print(System.out,arg);
          return arg;
        }
      });
    defined_passes.put("c",new CompilerPass("print AST in C syntax"){
        public ProgramUnit apply(ProgramUnit arg,String ... args){
          vct.clang.printer.CPrinter.dump(System.out,arg);
          return arg;
        }
      });
    defined_passes.put("dump",new CompilerPass("dump AST"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        PrefixPrintStream out=new PrefixPrintStream(System.out);
        HeapDump.tree_dump(out,arg,ASTNode.class);
        return arg;
      }
    });
    defined_passes.put("add-type-adt",new CompilerPass("Add an ADT that describes the types and use it to implement instanceof"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new AddTypeADT(arg).rewriteAll();
      }
    });   
    defined_passes.put("abstract-resolve",new CompilerPass("convert abstract methods to assume false bodies"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new AbstractMethodResolver(arg).rewriteAll();
      }
    });
    compiler_pass(defined_passes,"access","convert access expressions",AccessIntroduce.class);
    defined_passes.put("assign",new CompilerPass("change inline assignments to statements"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new AssignmentRewriter(arg).rewriteAll();
      }
    });
    defined_checks.put("boogie",new ValidationPass("verify with Boogie"){
      public TestReport apply(ProgramUnit arg,String ... args){
        return vct.boogie.Main.TestBoogie(arg);
      }
    });
    defined_checks.put("dafny",new ValidationPass("verify with Dafny"){
      public TestReport apply(ProgramUnit arg,String ... args){
        return vct.boogie.Main.TestDafny(arg);
      }
    });
    defined_checks.put("silicon-chalice",new ValidationPass("verify Chalice code with Silicon"){
      public TestReport apply(ProgramUnit arg,String ... args){
        return vct.boogie.Main.TestSilicon(arg);
      }
    });
    defined_checks.put("silver",new ValidationPass("verify input with Silver"){
      public TestReport apply(ProgramUnit arg,String ... args){
        return vct.silver.SilverBackend.TestSilicon(arg,silver.get());
      }
    });
    defined_passes.put("box",new CompilerPass("box class types with parameters"){
        public ProgramUnit apply(ProgramUnit arg,String ... args){
          return new BoxingRewriter(arg).rewriteAll();
        }
      });
    defined_checks.put("chalice",new ValidationPass("verify with Chalice"){
      public TestReport apply(ProgramUnit arg,String ... args){
        if (separate_checks.get()) {
          long start=System.currentTimeMillis();
          CompositeReport res=new CompositeReport();
          ExecutorService queue=Executors.newFixedThreadPool(4);
          ArrayList<Future<TestReport>> list=new ArrayList<Future<TestReport>>();
          for(ClassName class_name:arg.classNames()){
              Callable<TestReport> task=new ChaliceTask(arg,class_name);
              System.err.printf("submitting verification of %s%n",class_name.toString("."));
              list.add(queue.submit(task));
          }
          queue.shutdown();
          for(Future<TestReport> future:list){
            try {
              res.addReport(future.get());
            } catch (InterruptedException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
              Abort("%s",e);
            } catch (ExecutionException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
              Abort("%s",e);
            }
          }
          System.err.printf("verification took %dms%n", System.currentTimeMillis()-start);
          return res;
        } else {
          long start=System.currentTimeMillis();
          TestReport report=vct.boogie.Main.TestChalice(arg);
          System.err.printf("verification took %dms%n", System.currentTimeMillis()-start);
          return report;
        }
      }
    });
    defined_passes.put("check",new CompilerPass("run a type check"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        new SimpleTypeCheck(arg).check();
        return arg;
      }
    });
    defined_passes.put("check-defined",new CompilerPass("rewrite process algebra class to check if defined process match their contracts"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        ProgramUnit tmp=new CheckProcessAlgebra(arg).rewriteAll();
        return new RandomizedIf(tmp).rewriteAll();
      }
    });
    defined_passes.put("check-axioms",new CompilerPass("rewrite process algebra class to check if history axioms are correct"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        ProgramUnit tmp=new CheckHistoryAlgebra(arg,Mode.AxiomVerification).rewriteAll();
        return new RandomizedIf(tmp).rewriteAll();
      }
    });
    defined_passes.put("check-history",new CompilerPass("rewrite process algebra class to check if history accounting is correct"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        ProgramUnit tmp=new CheckHistoryAlgebra(arg,Mode.ProgramVerification).rewriteAll();
        return new RandomizedIf(tmp).rewriteAll();
      }
    });
    defined_passes.put("csl-encode",new CompilerPass("Encode CSL atomic regions with methods"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new CSLencoder(arg).rewriteAll();
      }
    });
    defined_passes.put("class-conversion",new CompilerPass("Convert classes into records and procedures"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new ClassConversion(arg).rewriteAll();
      }
    });
    defined_passes.put("codegen",new CompilerPass("Generate code"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        File dir=new File(args[0]);
        if (dir.exists()){
          if (!dir.isDirectory()){
            fail("%s is not a directory");
            return arg; 
          }
        } else {
          if (!dir.mkdirs()){
            fail("could not create %s",dir);
            return arg;
          }
        }
        Syntax syntax=JavaSyntax.getJava(JavaDialect.JavaVerCors);
        for(ASTNode node:arg){
          if (node instanceof ASTClass){
            PrintStream out;
            try {
              out = new PrintStream(new FileOutputStream(new File(dir,((ASTClass)node).name+".java")));
            } catch (FileNotFoundException e) {
              report.setException(e);
              return arg;
            }
            out.println("import col.lang.*;");
            syntax.print(out, node);
            out.close();
          } else if(node instanceof ASTSpecial){
            ASTSpecial S = (ASTSpecial)node;
            fail("cannot deal with special %s yet",S.kind);
            return arg;           
          } else if(node instanceof ASTSpecialDeclaration){
            ASTSpecialDeclaration S = (ASTSpecialDeclaration)node;
            switch(S.kind){
            case Comment:
              // TODO keep comments.
              break;
            default:
              fail("cannot deal with special declaration %s yet",S.kind);
              return arg; 
            }  
          } else {
            fail("cannot deal with %s yet",node.getClass());
            return arg;
          }
        }
        return arg;
      }
    });    
    defined_passes.put("current_thread",new CompilerPass("Encode references to current thread."){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new CurrentThreadRewriter(arg).rewriteAll();
      }
    });
    defined_passes.put("define_double",new CompilerPass("Rewrite double as a non-native data type."){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return DefineDouble.rewrite(arg);
      }
    });
    defined_passes.put("erase",new CompilerPass("Erase generic types"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        arg=new GenericPass1(arg).rewriteAll();
        return arg;
      }
    });   
    defined_passes.put("explicit_encoding",new CompilerPass("encode required and ensured permission as ghost arguments"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new ExplicitPermissionEncoding(arg).rewriteAll();
      }
    });
    defined_passes.put("finalize_args",new CompilerPass("???"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new FinalizeArguments(arg).rewriteAll();
      }
    });
    defined_passes.put("fork-join-encode",new CompilerPass("Encode fork/join operations using method calls."){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new ForkJoinEncode(arg).rewriteAll();
      }
    });
    defined_passes.put("flatten",new CompilerPass("remove nesting of expression"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new Flatten(arg).rewriteAll();
      }
    });
    defined_passes.put("ghost-lift",new CompilerPass("Lift ghost code to real code"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new GhostLifter(arg).rewriteAll();
      }
    });
    if (global_with_field.get()){
      Warning("Using the incomplete and experimental field access for globals.");
      defined_passes.put("globalize",new CompilerPass("split classes into static and dynamic parts"){
        public ProgramUnit apply(ProgramUnit arg,String ... args){
          return new GlobalizeStaticsField(arg).rewriteAll();
        }
      });      
    } else {
      defined_passes.put("globalize",new CompilerPass("split classes into static and dynamic parts"){
        public ProgramUnit apply(ProgramUnit arg,String ... args){
          return new GlobalizeStaticsParameter(arg).rewriteAll();
        }
      });
    }
    defined_passes.put("ds_inherit",new CompilerPass("rewrite contracts to reflect inheritance, predicate chaining"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new DynamicStaticInheritance(arg).rewriteOrdered();
      }
    });
    defined_passes.put("filter_spec_ignore",new CompilerPass("remove spec_ignore sections from code"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new FilterSpecIgnore(arg).rewriteAll();
      }
    });
    defined_passes.put("flatten_before_after",new CompilerPass("move before/after instructions"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new FlattenBeforeAfter(arg).rewriteAll();
      }
    });
    defined_passes.put("auto-inline",new CompilerPass("Inline all non-recursive predicates and inline methods"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new InlinePredicatesRewriter(arg,true).rewriteAll();
      }
    });
    defined_passes.put("user-inline",new CompilerPass("Inline all methods marked as inline"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new InlinePredicatesRewriter(arg,false).rewriteAll();
      }
    });
    defined_passes.put("iter",new CompilerPass("Encode iteration contracts as method calls"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new IterationContractEncoder(arg).rewriteAll();
      }
    });
    defined_passes.put("kernel-split",new CompilerPass("Split kernels into main, thread and barrier."){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new KernelRewriter(arg).rewriteAll();
      }
    });
    defined_passes.put("lock-encode",new CompilerPass("Encode lock/unlock statements."){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new LockEncoder(arg).rewriteAll();
      }
    });
    defined_passes.put("magicwand",new CompilerPass("Encode magic wand proofs with witnesses"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new WandEncoder(arg).rewriteAll();
      }
    });
    defined_passes.put("merge_loops",new CompilerPass("Merge nested loops into a single loop"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new MergeLoops(arg).rewriteAll();
      }
    });
    defined_passes.put("modifies",new CompilerPass("Derive modifies clauses for all contracts"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        new DeriveModifies().annotate(arg);
        return arg;
      }
    });
    defined_passes.put("parallel_blocks",new CompilerPass("Encoded the proof obligations for parallel blocks"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new ParallelBlockEncoder(arg).rewriteAll();
      }
    });
    defined_passes.put("pvl-compile",new CompilerPass("Compile PVL classes to Java classes"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new PVLCompiler(arg).rewriteAll();
      }
    });
    defined_passes.put("recognize_loops",new CompilerPass("Recognize for-each loops"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new RecognizeLoops(arg).rewriteAll();
      }
    });
    defined_passes.put("recognize_multidim",new CompilerPass("Recognize multi-dimensional arrays"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new RecognizeMultiDim(arg).rewriteAll();
      }
    });
   defined_passes.put("ref_array",new CompilerPass("rewrite array as a sequence of Refs"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new RewriteArrayRef(arg,RewriteArrayRef.Target.Ref).rewriteAll();
      }
    });
   defined_passes.put("reorder",new CompilerPass("reorder statements (e.g. all declarations at the start of a bock"){
     public ProgramUnit apply(ProgramUnit arg,String ... args){
       return new ReorderAssignments(arg).rewriteAll();
     }
   });
   defined_passes.put("standardize-functions",new CompilerPass("translate pure methods to function syntax."){
     public ProgramUnit apply(ProgramUnit arg,String ... args){
       return new PureMethodsAsFunctions(arg).rewriteAll();
     }
   });
   defined_passes.put("java_resolve",new CompilerPass("Resolve the library dependencies of a java program"){
     public ProgramUnit apply(ProgramUnit arg,String ... args){
       return new JavaResolver(arg).rewriteAll();
     }
   });
   defined_passes.put("quant-optimize",new CompilerPass("insert satisfyability checks for all methods"){
     public ProgramUnit apply(ProgramUnit arg,String ... args){
       return new OptimizeQuantifiers(arg).rewriteAll();
     }
   });
   defined_passes.put("rewrite_arrays",new CompilerPass("rewrite arrays to sequences of cells"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new RewriteArrayRef(arg,RewriteArrayRef.Target.Cell).rewriteAll();
      }
    });
    defined_passes.put("rm_cons",new CompilerPass("???"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new ConstructorRewriter(arg).rewriteAll();
      }
    });
    defined_passes.put("sat_check",new CompilerPass("insert satisfyability checks for all methods"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new SatCheckRewriter(arg).rewriteAll();
      }
    });
    defined_passes.put("setget",new CompilerPass("insert set and get operators"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new SetGetIntroduce(arg).rewriteAll();
      }
    });
    defined_passes.put("silver_constructors",new CompilerPass("convert constructors to silver style"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new SilverConstructors(arg).rewriteAll();
      }
    });
    defined_passes.put("silver-class-reduction",new CompilerPass("reduce classes to single Ref class"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new SilverClassReduction(arg).rewriteAll();
      }
    });
    defined_passes.put("silver-reorder",new CompilerPass("move declarations from inside if-then-else blocks to top"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new SilverReorder(arg).rewriteAll();
      }
    });
    defined_passes.put("scale-always",new CompilerPass("scale every predicate invokation"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new ScaleAlways(arg).rewriteAll();
      }
    });
    defined_passes.put("silver-identity",new CompilerPass("Implement identity operator for Silver"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new SilverImplementIdentity(arg).rewriteAll();
      }
    });
    defined_passes.put("silver-optimize",new CompilerPass("Optimize expressions for Silver"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        RewriteSystem trs=RewriteSystems.getRewriteSystem("silver_optimize");
        return trs.normalize(arg);
      }
    });
    defined_passes.put("chalice-optimize",new CompilerPass("Optimize expressions for Chalice"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        RewriteSystem trs=RewriteSystems.getRewriteSystem("chalice_optimize");
        return trs.normalize(arg);
      }
    });
    defined_passes.put("simplify_calls",new CompilerPass("???"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new SimplifyCalls(arg).rewriteAll();
      }
    });
    defined_passes.put("simplify_expr",new CompilerPass("Simplify expressions"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        RewriteSystem trs=RewriteSystems.getRewriteSystem("simplify_expr");
        return trs.normalize(arg);
      }
    });
    defined_passes.put("simplify_quant",new CompilerPass("Simplify quantifications"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        RewriteSystem trs=RewriteSystems.getRewriteSystem("simplify_quant_pass1");
        ProgramUnit res=trs.normalize(arg);
        // Configuration.getDiagSyntax().print(System.err,res);
        res=RewriteSystems.getRewriteSystem("simplify_quant_pass2").normalize(res);
        return res;
      }
    });
    defined_passes.put("simplify_sums",new CompilerPass("replace summations with provable functions"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        RewriteSystem trs=RewriteSystems.getRewriteSystem("summation");
        return trs.normalize(arg);
      }
    });
    defined_passes.put("standardize",new CompilerPass("Standardize representation"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new Standardize(arg).rewriteAll();
      }
    });
    defined_passes.put("strip_constructors",new CompilerPass("Strip constructors from classes"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new StripConstructors(arg).rewriteAll();
      }
    });
    defined_checks.put("verifast",new ValidationPass("verify with VeriFast"){
      public TestReport apply(ProgramUnit arg,String ... args){
        vct.java.printer.JavaPrinter.dump(System.out,JavaDialect.JavaVeriFast,arg);
        return vct.verifast.Main.TestVerifast(arg);
      }
    });
    defined_passes.put("voidcalls",new CompilerPass("Replace return value by out parameter."){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new VoidCalls(arg).rewriteAll();
      }
    });
    defined_passes.put("voidcallsthrown",new CompilerPass("Replace return value and thrown exceptions by out parameters."){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new VoidCallsThrown(arg).rewriteAll();
      }
    });
    defined_passes.put("chalice-preprocess",new CompilerPass("Pre processing for chalice"){
      public ProgramUnit apply(ProgramUnit arg,String ... args){
        return new ChalicePreProcess(arg).rewriteAll();
      }
    });
    if (help_passes.get()) {
      System.out.println("The following passes are available:"); 
      for (Entry<String, CompilerPass> entry:defined_passes.entrySet()){
        System.out.printf(" %-12s : %s%n",entry.getKey(),entry.getValue().getDescripion());
      }
      for (Entry<String, ValidationPass> entry:defined_checks.entrySet()){
        System.out.printf(" %-12s : %s%n",entry.getKey(),entry.getValue().getDescripion());
      }
      System.exit(0);
    }
    if (CommandLineTesting.enabled()){
      CommandLineTesting.run_testsuites();
      System.exit(0);
    }
    if (!(boogie.get() || chalice.get() || chalice2sil.get() || silver.used() || dafny.get() || verifast.get() || pass_list.iterator().hasNext())) {
      Fail("no back-end or passes specified");
    }
    if (silver.used()){
      switch(silver.get()){
      case "silicon_qp":
        Warning("silicon_qp has been merged into silicon, using silicon instead");
        silver.set("silicon");
        break;
      case "silicon":
      case "carbon": 
        break;
      default:
        Fail("unknown silver backend: %s",silver.get());
      }
    }
    Progress("parsing inputs...");
    int cnt = 0;
    long startTime = System.currentTimeMillis();
    for(String name : input){
      File f=new File(name);
      if (!no_context.get()){
        FileOrigin.add(name);
      }
      program.add(Parsers.parseFile(f.getPath()));
      cnt++;
    }
    System.err.printf("Parsed %d file(s) in: %dms%n",cnt,System.currentTimeMillis() - startTime);

    if (boogie.get() || sequential_spec.get()) {
      program.setSpecificationFormat(SpecificationFormat.Sequential);
    }
    FeatureScanner features=new FeatureScanner();
    program.accept(features);
    classes=new ArrayList();
    for (ClassName name:program.classNames()){
      classes.add(name);
    }
    List<String> passes=null;
    if (pass_list.iterator().hasNext()){
      // --passes option overrides --backend
    } else if (boogie.get()) {
    	passes=new ArrayList<String>();
    	passes.add("java_resolve");
      passes.add("standardize");
      passes.add("check");
      passes.add("flatten");
      passes.add("assign");
      passes.add("finalize_args");
      passes.add("reorder");
      passes.add("simplify_calls");
      if (infer_modifies.get()) {
        passes.add("standardize");
        passes.add("check");
        passes.add("modifies");
      }
      passes.add("standardize");
      passes.add("check");
      passes.add("voidcalls");
      passes.add("standardize");
      passes.add("check");
      passes.add("flatten");
      passes.add("reorder");
      passes.add("standardize");
      passes.add("check");
      passes.add("strip_constructors");
      passes.add("standardize");
      passes.add("check");
    	passes.add("boogie");
    } else if (chalice.get()||chalice2sil.get()) {
      passes=new ArrayList<String>();
      passes.add("java_resolve");
      if (sat_check.get()) passes.add("sat_check");
      passes.add("standardize");
      passes.add("check");        
      passes.add("magicwand");
      passes.add("standardize");
      passes.add("check");
      if (inline_predicates.get()){
        passes.add("auto-inline");
        passes.add("standardize");
        passes.add("check");        
      }
      if (features.hasStaticItems()){
        Warning("Encoding globals by means of an argument.");
        passes.add("standardize");
        passes.add("check");
        passes.add("globalize");
        passes.add("standardize");
        passes.add("check");
      }
      if (features.usesIterationContracts()){
        passes.add("recognize_multidim");
        passes.add("recognize_loops");
        passes.add("merge_loops");
        passes.add("standardize");
        passes.add("check");
        passes.add("iter");
        passes.add("simplify_quant");
        passes.add("standardize");
        passes.add("check");
      }
      if (features.usesKernels()){
        passes.add("kernel-split");
        //passes.add("simplify_expr");
        passes.add("standardize");
        passes.add("check");       
      }
      if (features.usesInheritance()){
        passes.add("standardize");
        passes.add("check");       
        passes.add("ds_inherit");
        passes.add("standardize");
        passes.add("check");       
      }
      if (check_defined.get()){
        passes.add("check-defined");
        passes.add("standardize");
        passes.add("check");
      }
      if (features.usesCSL()){
        passes.add("csl-encode");
        passes.add("standardize");
        passes.add("check");
      }
      if (explicit_encoding.get()){
        //passes.add("standardize");
        //passes.add("check");       
        passes.add("explicit_encoding");
        passes.add("standardize");
        passes.add("check");
      } else {
        passes.add("flatten_before_after");
        passes.add("standardize");
        passes.add("check");        
      }
      passes.add("rewrite_arrays");
      passes.add("standardize");
      passes.add("check");
      passes.add("flatten");
      passes.add("finalize_args");
      passes.add("reorder");
      passes.add("standardize");
      passes.add("check");
      if (features.usesDoubles()){
        Warning("defining Double");
        passes.add("define_double");
        passes.add("standardize");
        passes.add("check");
      }
    	passes.add("assign");
      passes.add("reorder");
    	passes.add("standardize");
    	passes.add("check");
      passes.add("rm_cons");
      passes.add("standardize");
      passes.add("check");
      passes.add("voidcalls");
      passes.add("standardize");
      passes.add("check");
      passes.add("flatten");
      passes.add("reorder");
      passes.add("check");
      passes.add("chalice-optimize");
      passes.add("standardize");
      passes.add("check");      
      passes.add("chalice-preprocess");
      passes.add("check");
      if (chalice.get()){
        passes.add("chalice");
      } else {
        passes.add("silicon-chalice");
      }
    } else if (dafny.get()) {
      passes=new ArrayList<String>();
      passes.add("java_resolve");
      passes.add("standardize");
      passes.add("check");
      passes.add("voidcalls");
      passes.add("standardize");
      passes.add("check");
      //passes.add("flatten");
      //passes.add("reorder");
      //passes.add("check");
      passes.add("dafny");
    } else if (verifast.get()) {
      passes=new ArrayList<String>();
      passes.add("java_resolve");
      passes.add("standardize");
      passes.add("check");
      passes.add("verifast");
    } else if (silver.used()) {
      passes=new ArrayList<String>();
//      passes.add("standardize");
//      passes.add("check");
      passes.add("filter_spec_ignore");
      passes.add("java_resolve");
      if (sat_check.get()) passes.add("sat_check");
      passes.add("lock-encode");
      passes.add("standardize");
      passes.add("check");
      if (features.usesOperator(StandardOperator.Wand)){
        passes.add("magicwand");
        passes.add("standardize");
        passes.add("check");
      }
      if (features.usesSpecial(ASTSpecial.Kind.Fork)){
        passes.add("fork-join-encode");
        passes.add("standardize");
        passes.add("check");
      }
      if (inline_predicates.get()){
        passes.add("auto-inline");
      } else {
        passes.add("user-inline");
      }
      passes.add("standardize");
      passes.add("check");
      if (features.usesCSL()){
        passes.add("csl-encode");
        passes.add("standardize");
        passes.add("check");
      }
      if (features.usesParallelBlocks()||features.usesCSL()){
        passes.add("parallel_blocks");
        passes.add("standardize");
        passes.add("check");        
      }
      if (features.usesIterationContracts()){
        //passes.add("recognize_multidim");
        passes.add("recognize_loops");
        passes.add("merge_loops");
        passes.add("standardize");
        passes.add("check");
        passes.add("iter");
      }// else {
        passes.add("standardize");
        passes.add("check");    
        passes.add("recognize_multidim");
      //}
      passes.add("simplify_quant");
      if (features.usesSummation()||features.usesIterationContracts()) passes.add("simplify_sums");
      passes.add("standardize");
      passes.add("check");
      if (features.usesKernels()){
        passes.add("kernel-split");
        //passes.add("simplify_expr");
        passes.add("simplify_quant");
        passes.add("standardize");
        passes.add("check");       
      }
      boolean has_type_adt=false;
      if (features.usesOperator(StandardOperator.Instance)||features.usesInheritance()){
        passes.add("add-type-adt");
        passes.add("standardize");
        passes.add("check");
        has_type_adt=true;   
      }
      if (features.usesInheritance()){
        passes.add("standardize");
        passes.add("check");       
        passes.add("ds_inherit");
        passes.add("standardize");
        passes.add("check");       
      }
      if (check_defined.get()){
        passes.add("check-defined");
        passes.add("standardize");
        passes.add("check");
      }
      if (check_axioms.get()){
        passes.add("check-axioms");
        passes.add("standardize");
        passes.add("check");
      }
      if (check_history.get()){
        passes.add("access");
        passes.add("standardize");
        passes.add("check");
        passes.add("check-history");
        passes.add("standardize");
        passes.add("check");
      }
      passes.add("current_thread");
      passes.add("standardize");
      passes.add("check");    
      passes.add("flatten");
      passes.add("assign");
      passes.add("reorder");
      passes.add("standardize");
      passes.add("check");
      // Split into class-conversion + silver-class-reduction
      // TODO: check if no other functionality destroyed.
      //passes.add("silver_constructors");
      //passes.add("standardize");
      //passes.add("check");
      //if (!check_csl.get()){
        passes.add("ref_array");
        passes.add("standardize");
        passes.add("check");
      //}
      passes.add("class-conversion");
      passes.add("standardize");
      passes.add("check");
      passes.add("silver-class-reduction");
      passes.add("standardize");
      passes.add("check");
      if (has_type_adt){
        passes.add("voidcallsthrown");
      } else {
        passes.add("voidcalls");
      }
      passes.add("standardize");
      passes.add("check");
      passes.add("ghost-lift");
      passes.add("standardize");
      passes.add("check");
      passes.add("flatten");
      passes.add("reorder");
      passes.add("flatten_before_after");
      passes.add("silver-reorder");
      passes.add("silver-identity");
      passes.add("standardize");
      passes.add("check"); 
      passes.add("silver-optimize");
      passes.add("quant-optimize");
      passes.add("standardize-functions");
      passes.add("standardize");
      passes.add("check");      
      passes.add("scale-always");
      passes.add("standardize");
      passes.add("check");
      passes.add("silver");     
    } else {
    	Abort("no back-end or passes specified");
    }
    {
      CompositeReport res=new CompositeReport();
      for(String pass:passes!=null?passes:pass_list){
        String[] pass_args=pass.split("=");
        pass=pass_args[0];
        if (pass_args.length==1){
          pass_args=new String[0];
        } else {
          pass_args=pass_args[1].split("\\+");
        }
        Progress("Pass %s %s ...",pass,new ArrayShow(" ",pass_args));
        CompilerPass task=defined_passes.get(pass);
        if (show_before.contains(pass)){
          String name=show_file.get();
          if (name!=null){
            String file=String.format(name, pass);
            PrintStream out=new PrintStream(new FileOutputStream(file));
            vct.util.Configuration.getDiagSyntax().print(out,program);
            out.close();
          } else {
            vct.util.Configuration.getDiagSyntax().print(System.out,program);
          }
        }
        if (task!=null){
          Progress("Applying %s ...",pass);
          startTime = System.currentTimeMillis();
          program=task.apply(program,pass_args);
          Progress(" ... pass took %d ms",System.currentTimeMillis()-startTime);
          if (task.report!=null){
            res.addReport(task.report);
          }
        } else {
          ValidationPass check=defined_checks.get(pass);
          if (check!=null){
            Progress("Applying %s ...", pass);
            startTime = System.currentTimeMillis();
            res.addReport(check.apply(program,pass_args));
            Progress(" ... pass took %d ms",System.currentTimeMillis()-startTime);
          } else {
            Fail("unknown pass %s",pass);
          }
        }
        if (show_after.contains(pass)){
          String name=show_file.get();
          if (name!=null){
            String file=String.format(name, pass);
            PrintStream out=new PrintStream(new FileOutputStream(file));
            vct.util.Configuration.getDiagSyntax().print(out,program);
            out.close();
          } else {
            vct.util.Configuration.getDiagSyntax().print(System.out,program);
          }
        }
        if (stop_after.contains(pass)){
          Fail("exit after pass %s",pass);
        }
        if (res.getVerdict()!=Verdict.Pass){
          break;
        }
      }
      Output("The final verdict is %s",res.getVerdict());
    }
    Output("entire run took %d ms",System.currentTimeMillis()-globalStart);
  }

  private static void compiler_pass(Hashtable<String, CompilerPass> defined_passes,
      String key, String description, final Class<? extends AbstractRewriter> class1) {
    try {
      defined_passes.put(key,new CompilerPass(description){
        
        Constructor cons=class1.getConstructor(ProgramUnit.class);
        
        @Override
        public ProgramUnit apply(ProgramUnit arg, String... args) {
          AbstractRewriter rw;
          try {
            rw = (AbstractRewriter) cons.newInstance(arg);
          } catch (Exception e) {
            throw new HREError("unexpected exception %s",e);
          }
          return rw.rewriteAll();
        }
        
      });
    } catch (NoSuchMethodException e) {
      Abort("bad rewriter pass %s",key);
    }
  }
}

