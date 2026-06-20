package szczur4.patcher;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.commons.io.FileUtils;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
public class Main{
    ArrayList<Exception>exceptions=new ArrayList<>();
    File outDir,patchDir,srcDir;
    List<String>removals;
    int srcPathLen,patchPathLen;
    void main(String[]args)throws IOException{
        if(args.length==0)args=new String[]{"-h"};
        OptionParser parser=new OptionParser();
        parser.acceptsAll(List.of("h","help","?"),"Print this message").forHelp();
        OptionSpec<String>modeSpec=parser.acceptsAll(List.of("m","mode"),"\"patch\" - patches srcDir\n\"gen\", \"generate\" - generates patches for srcDir").requiredUnless("help").withRequiredArg();
        OptionSpec<File>outDirSpec=parser.acceptsAll(List.of("o","outDir"),"Specify output directory").withRequiredArg().ofType(File.class).defaultsTo(new File("out"));
        OptionSpec<File>patchDirSpec=parser.acceptsAll(List.of("p","patchDir"),"Specify patches directory").withRequiredArg().ofType(File.class).defaultsTo(new File("patches"));
        OptionSpec<File>srcDirSpec=parser.acceptsAll(List.of("s","srcDir"),"Specify source directory").withRequiredArg().ofType(File.class).defaultsTo(new File("src"));
        OptionSet options=parser.parse(args);
        if(options.has("help")){
            parser.printHelpOn(System.out);
            System.exit(0);
        }
        String mode=options.valueOf(modeSpec);
        outDir=options.valueOf(outDirSpec);
        if(!(patchDir=options.valueOf(patchDirSpec)).exists())exceptions.add(new FileNotFoundException("Patch directory does not exist"));
        if(!(srcDir=options.valueOf(srcDirSpec)).exists())exceptions.add(new FileNotFoundException("Source directory does not exist"));
        if(outDir.exists())try{FileUtils.deleteDirectory(outDir);}catch(IOException ex){exceptions.add(ex);}
        if(!outDir.mkdirs())exceptions.add(new IOException("Unable to create output directory"));
        checkErrors();
        patchPathLen=patchDir.getPath().length()+1;
        srcPathLen=srcDir.getPath().length()+1;
        if(mode.equals("patch"))patchInit();
        else if(mode.equals("gen")||mode.equals("generate"))genInit();
        else throw new IllegalArgumentException("Unknown mode");
    }
    void patchInit(){
        try{removals=Files.readAllLines(patchDir.toPath().resolve("removals"));}catch(IOException ex){exceptions.add(ex);}
        checkErrors();
        patch(outDir,patchDir.toPath().resolve("src").toFile(),patchDir.toPath().resolve("additions").toFile(),srcDir);
        checkErrors();
    }
    void patch(File outDir,File patchDir,File additionsDir,File srcDir){
        if(!outDir.exists()&&!outDir.mkdirs())exceptions.add(new IOException("Unable to create output directory"));
        File[]srcFiles=srcDir.listFiles();
        List<File>patchFiles=null;
        if(patchDir.exists()&&patchDir.isDirectory())patchFiles=new ArrayList<>(List.of(patchDir.listFiles()));
        if(patchFiles!=null)for(int i=0;i<patchFiles.size();i++)patchFiles.set(i,new File(patchFiles.get(i).getName()));
        for(File file:srcFiles){
            if(removals.contains(file.getPath().substring(srcPathLen)))continue;
            if(file.isDirectory())patch(outDir.toPath().resolve(file.getName()).toFile(),patchDir.toPath().resolve(file.getName()).toFile(),additionsDir.toPath().resolve(file.getName()).toFile(),file);
            else if(patchFiles!=null&&patchFiles.contains(new File(file.getName()+".patch")))try{patchFile(outDir.toPath().resolve(file.getName()).toFile(),patchDir.toPath().resolve(file.getName()+".patch").toFile(),file);}catch(IOException ex){exceptions.add(ex);}
            else try{Files.copy(file.toPath(),outDir.toPath().resolve(file.getName()),StandardCopyOption.REPLACE_EXISTING);}catch(IOException ex){exceptions.add(ex);}
        }
        if(additionsDir.exists())try{FileUtils.copyDirectory(additionsDir,outDir);}catch(IOException ex){exceptions.add(ex);}
    }
    void patchFile(File outFile,File patchFile,File srcFile)throws IOException{
        var srcLines=Files.readAllLines(srcFile.toPath());
        for(String patchLine:Files.readAllLines(patchFile.toPath())){
            var splitLine=patchLine.split("   ");
            if(splitLine[0].charAt(0)=='-')srcLines.remove(Integer.parseInt(splitLine[0].substring(1)));
            else if(splitLine[0].charAt(0)=='+')srcLines.add(Integer.parseInt(splitLine[0].substring(1)),splitLine.length==1?"":splitLine[1]);
            else{
                System.out.println(patchLine);
                exceptions.add(new RuntimeException("Invalid patch line"));
            }
        }
        FileUtils.writeLines(outFile,srcLines);
    }
    void genInit()throws IOException{
        removals=new ArrayList<>();
        genRemovals(patchDir,srcDir);
        FileUtils.writeLines(outDir.toPath().resolve("removals").toFile(),removals);
        genAdditions(outDir.toPath().resolve("additions").toFile(),patchDir,srcDir);
        genPatches(outDir.toPath().resolve("src").toFile(),patchDir,srcDir);
        checkErrors();
    }
    void genRemovals(File patchDir,File srcDir){
        ArrayList<String>patchFiles=new ArrayList<>();
        ArrayList<File>srcFiles=new ArrayList<>(List.of(srcDir.listFiles()));
        List.of(patchDir.listFiles()).forEach(f->patchFiles.add(f.getName()));
        for(File f:srcFiles){
            if(!patchFiles.contains(f.getName())){
                removals.add(f.getPath().substring(srcPathLen));
                continue;
            }
            if(f.isDirectory())genRemovals(patchDir.toPath().resolve(f.getName()).toFile(),f);
        }
    }
    void genAdditions(File additionsDir,File patchDir,File srcDir){
        ArrayList<File>patchFiles=new ArrayList<>(List.of(patchDir.listFiles()));
        ArrayList<String>srcFiles=new ArrayList<>();
        List.of(srcDir.listFiles()).forEach(f->srcFiles.add(f.getName()));
        for(File f:patchFiles){
            if(!srcFiles.contains(f.getName())){
                if(f.isDirectory())try{FileUtils.copyDirectory(f,additionsDir.toPath().resolve(f.getName()).toFile());}catch(IOException ex){exceptions.add(ex);}
                else try{
                    File k=additionsDir.toPath().resolve(f.getName()).toFile().getParentFile();
                    if(!k.exists()&&!k.mkdirs())throw new IOException("Unable to create directory");
                    Files.copy(f.toPath(),additionsDir.toPath().resolve(f.getName()));
                }catch(IOException ex){exceptions.add(ex);}
                continue;
            }
            if(f.isDirectory())genAdditions(additionsDir.toPath().resolve(f.getName()).toFile(),patchDir.toPath().resolve(f.getName()).toFile(),f);
        }
    }
    void genPatches(File outDir,File patchDir,File srcDir){
        ArrayList<File>patchFiles=new ArrayList<>(List.of(patchDir.listFiles()));
        ArrayList<String>srcFiles=new ArrayList<>();
        if(!srcDir.exists())return;
        List.of(srcDir.listFiles()).forEach(f->srcFiles.add(f.getName()));
        for(File f:patchFiles){
            if(!f.isDirectory()&&srcFiles.contains(f.getName()))try{genPatchFile(outDir.toPath().resolve(f.getName()+".patch").toFile(),f,srcDir.toPath().resolve(f.getName()).toFile());}catch(IOException ex){exceptions.add(ex);}
            if(f.isDirectory())genPatches(outDir.toPath().resolve(f.getName()).toFile(),f,srcDir.toPath().resolve(f.getName()).toFile());
        }
    }
    void genPatchFile(File outFile,File patchSrcFile,File srcFile)throws IOException{
        List<String>patchLines=Files.readAllLines(patchSrcFile.toPath()),srcLines=Files.readAllLines(srcFile.toPath()),patches=new ArrayList<>();
        int sSize=srcLines.size(),pSize=patchLines.size();
        int[][]dp=new int[sSize+1][pSize+1];
        for(int x=sSize-1;x>=0;x--)for(int y=pSize-1;y>=0;y--){
            if(srcLines.get(x).equals(patchLines.get(y)))dp[x][y]=dp[x+1][y+1]+1;
            else dp[x][y]=Math.max(dp[x+1][y],dp[x][y+1]);
        }
        int x=0,y=0,n=0;
        while(x<sSize&&y<pSize){
            if(srcLines.get(x).equals(patchLines.get(y))){
                x++;
                y++;
                n++;
            }
            else if(dp[x+1][y]>=dp[x][y+1]){
                patches.add("-"+n);
                x++;
            }
            else{
                patches.add("+"+n+"   "+patchLines.get(y));
                y++;
                n++;
            }
        }
        for(;x<sSize;x++,n++)patches.add("-"+n);
        for(;y<pSize;y++,n++)patches.add("+"+n+"   "+patchLines.get(y));
        if(!patches.isEmpty())FileUtils.writeLines(outFile,patches);
    }
    void checkErrors(){
        if(!exceptions.isEmpty()){
            for(Exception ex:exceptions)ex.printStackTrace();
            throw new RuntimeException("Exceptions occurred\nOutput is probably incomplete");
        }
    }
}
