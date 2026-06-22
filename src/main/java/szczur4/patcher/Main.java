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
        System.out.println("Patching (1/1)");
        patch(outDir,patchDir.toPath().resolve("src").toFile(),patchDir.toPath().resolve("additions").toFile(),srcDir);
        checkErrors();
    }
    void patch(File outDir,File patchDir,File additionsDir,File srcDir){
        if(!outDir.exists()&&!outDir.mkdirs())exceptions.add(new IOException("Unable to create output directory"));
        File[]srcFiles=srcDir.listFiles();
        List<File>patchFiles=null;
        if(patchDir.exists()&&patchDir.isDirectory())patchFiles=new ArrayList<>(List.of(patchDir.listFiles()));
        if(patchFiles!=null)for(int i=0;i<patchFiles.size();i++)patchFiles.set(i,new File(patchFiles.get(i).getName()));
        System.out.println("-> /"+srcDir.getPath().substring(Math.min(srcPathLen,srcDir.getPath().length())));
        for(File f:srcFiles){
            if(removals.contains(f.getPath().substring(srcPathLen)))continue;
            if(f.isDirectory())patch(outDir.toPath().resolve(f.getName()).toFile(),patchDir.toPath().resolve(f.getName()).toFile(),additionsDir.toPath().resolve(f.getName()).toFile(),f);
            else if(patchFiles!=null&&patchFiles.contains(new File(f.getName()+".patch")))try{patchFile(outDir.toPath().resolve(f.getName()).toFile(),patchDir.toPath().resolve(f.getName()+".patch").toFile(),f);}catch(IOException ex){exceptions.add(ex);}
            else try{Files.copy(f.toPath(),outDir.toPath().resolve(f.getName()),StandardCopyOption.REPLACE_EXISTING);}catch(IOException ex){exceptions.add(ex);}
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
        System.out.println("(1/3) Generating removals");
        genRemovals(patchDir,srcDir);
        checkErrors();
        FileUtils.writeLines(outDir.toPath().resolve("removals").toFile(),removals);
        System.out.println("(2/3) Generating additions");
        genAdditions(outDir.toPath().resolve("additions").toFile(),patchDir,srcDir);
        checkErrors();
        System.out.println("(3/3) Generating patches");
        genPatches(outDir.toPath().resolve("src").toFile(),patchDir,srcDir);
        checkErrors();
    }
    void genRemovals(File patchDir,File srcDir){
        System.out.println("-> /"+srcDir.getPath().substring(Math.min(srcPathLen,srcDir.getPath().length())));
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
        System.out.println("-> /"+srcDir.getPath().substring(Math.min(srcPathLen,srcDir.getPath().length())));
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
            if(f.isDirectory())genAdditions(additionsDir.toPath().resolve(f.getName()).toFile(),f,srcDir.toPath().resolve(f.getName()).toFile());
        }
    }
    void genPatches(File outDir,File patchDir,File srcDir){
        if(patchDir.listFiles()==null)return;
        ArrayList<File>patchFiles=new ArrayList<>(List.of(patchDir.listFiles()));
        ArrayList<String>srcFiles=new ArrayList<>();
        if(!srcDir.exists())return;
        System.out.println("-> /"+srcDir.getPath().substring(Math.min(srcPathLen,srcDir.getPath().length())));
        List.of(srcDir.listFiles()).forEach(f->srcFiles.add(f.getName()));
        for(File f:patchFiles){
            if(!f.isDirectory()&&srcFiles.contains(f.getName()))try{genPatchFile(outDir.toPath().resolve(f.getName()+".patch").toFile(),f,srcDir.toPath().resolve(f.getName()).toFile());}catch(IOException ex){exceptions.add(ex);}
            if(f.isDirectory())genPatches(outDir.toPath().resolve(f.getName()).toFile(),f,srcDir.toPath().resolve(f.getName()).toFile());
        }
    }
    void genPatchFile(File outFile,File patchSrcFile,File srcFile)throws IOException{
        BufferedReader br=new BufferedReader(new FileReader(patchSrcFile));
        List<String>patchLines=br.readAllLines();
        br.close();
        List<String>srcLines=(br=new BufferedReader(new FileReader(srcFile))).readAllLines();
        br.close();
        int srcSize=srcLines.size(),patchSize=patchLines.size(),max=srcSize+patchSize,offset=max+1;
        int[]V=new int[2*max+3];
        List<int[]>prevVs=new ArrayList<>();
        int D=0;
        for(int d=0;d<=max;d++){
            for(int k=-d;k<=d;k+=2){
                int x;
                if(k==-d||(k!=d&&V[k-1+offset]<V[k+1+offset]))x=V[k+1+offset];
                else x=V[k-1+offset]+1;
                int y=x-k;
                while(x<srcSize&&y<patchSize&&srcLines.get(x).equals(patchLines.get(y))){
                    x++;
                    y++;
                }
                V[k+offset]=x;
            }
            prevVs.add(V.clone());
            if(V[srcSize-patchSize+offset]>=srcSize){
                D=d;
                break;
            }
        }
        if(D==0)return;
        List<String>patches=new ArrayList<>();
        srcSize--;
        patchSize--;
        for(int d=D;d>0;d--){
            int[]prevV=prevVs.get(d-1);
            int k=srcSize-patchSize,x=srcSize,y=patchSize;
            for(;x>0&&y>0&&srcLines.get(x).equals(patchLines.get(y));x--)y--;
            if(k==-d||(k!=d&&prevV[k-1+offset]<prevV[k+1+offset])){
                patches.addFirst("+"+y+"   "+patchLines.get(y));
                srcSize=x;
                patchSize=y-1;
            }
            else{
                patches.addFirst("-"+(y+1));
                srcSize=x-1;
                patchSize=y;
            }
        }
        if(!patches.isEmpty())FileUtils.writeLines(outFile,patches);
        System.gc();
    }
    void checkErrors(){
        if(!exceptions.isEmpty()){
            for(Exception ex:exceptions)ex.printStackTrace();
            throw new RuntimeException("Exceptions occurred\nOutput is probably incomplete");
        }
    }
}
