package tax;

import java.io.PrintStream;
import java.util.HashSet;

import stream.Read;

import fileIO.ReadWrite;

import align2.Tools;

/**
 * @author Brian Bushnell
 * @date Nov 30, 2015
 *
 */
public class TaxFilter {
	
	/*--------------------------------------------------------------*/
	/*----------------         Constructors         ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Constructor.
	 * @param args Command line arguments
	 */
	public static TaxFilter makeFilter(String[] args){
		
		String names=null;
		String ids=null;

		String tableFile=null;
		String treeFile=null;
		
		int taxLevel=0;
		boolean include=false;
		
		for(int i=0; i<args.length; i++){
			String arg=args[i];

			//Break arguments into their constituent parts, in the form of "a=b"
			String[] split=arg.split("=");
			String a=split[0].toLowerCase();
			String b=split.length>1 ? split[1] : null;
			if(b==null || b.equalsIgnoreCase("null")){b=null;}
			while(a.startsWith("-")){a=a.substring(1);} //Strip leading hyphens
			
			if(a.equals("table") || a.equals("gi")){
				tableFile=b;
			}else if(a.equals("tree")){
				treeFile=b;
			}else if(a.equals("level") || a.equals("taxlevel")){
				if(Character.isDigit(b.charAt(0))){
					taxLevel=Integer.parseInt(b);
				}else{
					taxLevel=TaxTree.stringToLevel(b.toLowerCase());
				}
			}else if(a.equals("name") || a.equals("names")){
				names=b;
			}else if(a.equals("include")){
				include=Tools.parseBoolean(b);
			}else if(a.equals("exclude")){
				include=!Tools.parseBoolean(b);
			}else if(a.equals("id") || a.equals("ids") || a.equals("taxid") || a.equals("taxids")){
				ids=b;
			}
		}
		
		TaxFilter filter=new TaxFilter(tableFile, treeFile, taxLevel, include, null);
		filter.addNames(names);
		filter.addNumbers(ids);
		
		return filter;
	}
	
	/**
	 * Constructor.
	 * @param tree_
	 * @param taxLevel_
	 * @param include_
	 * @param taxSet_
	 */
	public TaxFilter(TaxTree tree_, int taxLevel_, boolean include_, HashSet<Integer> taxSet_){
		tree=tree_;
		taxLevel=taxLevel_;
		include=include_;
		taxSet=(taxSet_==null ? new HashSet<Integer>() : taxSet_);
	}
	
	/**
	 * Constructor.
	 * @param tableFile
	 * @param treeFile
	 * @param taxLevel_
	 * @param include_
	 * @param taxSet_
	 */
	public TaxFilter(String tableFile, String treeFile, int taxLevel_, boolean include_, HashSet<Integer> taxSet_){
		taxLevel=taxLevel_;
		include=include_;
		taxSet=(taxSet_==null ? new HashSet<Integer>() : taxSet_);
		
		loadGiTable(tableFile);
		tree=loadTree(treeFile);
	}
	
	public static boolean validArgument(String a){
		if(a.equals("table") || a.equals("gi")){
		}else if(a.equals("tree")){
		}else if(a.equals("level") || a.equals("taxlevel")){
		}else if(a.equals("name") || a.equals("names")){
		}else if(a.equals("include")){
		}else if(a.equals("exclude")){
		}else if(a.equals("id") || a.equals("ids") || a.equals("taxid") || a.equals("taxids")){
		}else{
			return false;
		}
		return true;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------        Initialization        ----------------*/
	/*--------------------------------------------------------------*/
	
	static void loadGiTable(String fname){
		if(fname==null){return;}
		if(PRINT_STUFF){outstream.println("Loading gi table.");}
		GiToNcbi.initialize(fname);
	}
	
	static TaxTree loadTree(String fname){
		if(fname==null){return null;}
		if(PRINT_STUFF){outstream.println("Loading tree.");}
		TaxTree tt=ReadWrite.read(TaxTree.class, fname, true);
		if(tt.nameMap==null){
			if(PRINT_STUFF){outstream.println("Hashing names.");}
			tt.hashNames();
		}
		assert(tt.nameMap!=null);
		return tt;
	}
	
	public void addNames(String names){
		if(names==null){return;}
		String[] array=names.split(",");
		for(String name : array){
			addName(name);
		}
	}
	
	public boolean addName(String name){
		TaxNode tn=tree.getNodeByName(name);
		if(tn==null){tn=tree.getNode(name);}
		assert(tn!=null) : "Could not find a node for '"+name+"'";
		return addNode(tn);
	}
	
	public void addNumbers(String numbers){
		if(numbers==null){return;}
		String[] array=numbers.split(",");
		for(String s : array){
			final int x=Integer.parseInt(s);
			addNumber(x);
		}
	}
	
	public boolean addNumber(int taxID){
		TaxNode tn=tree.getNode(taxID);
		assert(tn!=null) : "Could not find a node for '"+taxID+"'";
		return addNode(tn);
	}
	
	public boolean addNode(TaxNode tn){
		if(tn==null){return false;}
		do{
			taxSet.add(tn.id);
			tn=tree.getNode(tn.pid);
		}while(tn!=null && tn.level<=taxLevel && tn.id!=tn.pid);
		return true;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------        Outer Methods         ----------------*/
	/*--------------------------------------------------------------*/
	
	boolean passesFilter(final Read r){
		return passesFilter(r.id);
	}
	
	boolean passesFilter(final String name){
		if(taxSet.isEmpty()){return !include;}
		TaxNode tn=tree.getNode(name);
		if(tn==null){tn=tree.getNodeByName(name);}
		assert(tn!=null) : "Could not find node for '"+name+"'";
		return passesFilter(tn);
	}
	
	boolean passesFilter(final int id){
		if(taxSet.isEmpty()){return !include;}
		TaxNode tn=tree.getNode(id);
		assert(tn!=null) : "Could not find node number "+id;
		return passesFilter(tn);
	}
	
	boolean passesFilter(TaxNode tn){
		if(taxSet.isEmpty()){return !include;}
		assert(tn!=null) : "Null TaxNode.";
		boolean found=false;
		do{
			found=taxSet.contains(tn.id);
			tn=tree.getNode(tn.pid);
		}while(!found && tn!=null && tn.id!=tn.pid);
		return include==found;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------            Fields            ----------------*/
	/*--------------------------------------------------------------*/
	
	private final TaxTree tree;
	
	/** Level at which to filter */
	private final int taxLevel;
	
	/** Set of numeric NCBI TaxIDs */
	private final HashSet<Integer> taxSet;
	
	private final boolean include;
	
	/*--------------------------------------------------------------*/
	/*----------------        Static Fields         ----------------*/
	/*--------------------------------------------------------------*/
	
	/** Print status messages to this output stream */
	private static PrintStream outstream=System.err;
	
	/** Print loading messages */
	static boolean PRINT_STUFF=true;

}