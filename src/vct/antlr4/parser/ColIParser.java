package vct.antlr4.parser;

import static hre.System.*;
import hre.ast.FileOrigin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import pv.parser.PVFullLexer;
import pv.parser.PVFullParser;
import vct.java.printer.JavaDialect;
import vct.java.printer.JavaSyntax;
import vct.parsers.*;
import vct.util.Configuration;
import vct.col.ast.ASTClass;
import vct.col.ast.ASTClass.ClassKind;
import vct.col.ast.ASTNode;
import vct.col.ast.CompilationUnit;
import vct.col.ast.ProgramUnit;
import vct.col.rewrite.AbstractRewriter;
import vct.col.rewrite.AnnotationInterpreter;
import vct.col.rewrite.FlattenVariableDeclarations;

/**
 * Parse specified code and convert the contents to COL. 
 */
public class ColIParser implements vct.col.util.Parser {

  protected CompilationUnit parse(String file_name,InputStream stream) throws IOException{
    ANTLRInputStream input = new ANTLRInputStream(stream);
    
    CLexer lexer = new CLexer(input);
    
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    
    CParser parser = new CParser(tokens);
    
    ParseTree tree = parser.compilationUnit();
    
    Debug("parser got: %s",tree.toStringTree(parser));

    CompilationUnit cu=CtoCOL.convert(tree,file_name,tokens,parser);
    
    ProgramUnit pu=new ProgramUnit();
    pu.add(cu);

    //vct.util.Configuration.getDiagSyntax().print(System.out,pu);
    
    pu=new CommentRewriter(pu,new CMLCommentParser()).rewriteAll();
    pu=new FlattenVariableDeclarations(pu).rewriteAll();
    pu=new SpecificationCollector(pu).rewriteAll();

    if (pu.size()!=1){
      Fail("bad program unit size");
    }
    
    cu=new CompilationUnit(file_name);
    ASTClass cl=new ASTClass("Ref",ClassKind.Plain);
    cl.setOrigin(new FileOrigin(file_name,1,1));
    cu.add(cl);
    for(ASTNode n:pu.get(0).get()){
      cl.add_dynamic(n);
    }
    return cu;
  }
  
  @Override
  public CompilationUnit parse(File file) {
    String file_name=file.toString();
    try {
      InputStream stream =new FileInputStream(file);
      return parse(file_name,stream);
    } catch (FileNotFoundException e) {
      Fail("File %s has not been found",file_name);
    } catch (Exception e) {
      e.printStackTrace();
      Abort("Exception %s while parsing %s",e.getClass(),file_name);
    }
    return null;
  }

}

