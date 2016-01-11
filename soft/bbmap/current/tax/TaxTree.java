package tax;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

import dna.Timer;

import align2.IntList;
import align2.Tools;

import fileIO.ReadWrite;
import fileIO.TextFile;

/**
 * @author Brian Bushnell
 * @date Mar 6, 2015
 *
 */
public class TaxTree implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1682832560435175041L;
	
	/*--------------------------------------------------------------*/
	/*----------------        Initialization        ----------------*/
	/*--------------------------------------------------------------*/

	public static void main(String[] args){
		ReadWrite.USE_UNPIGZ=true;
		ReadWrite.USE_PIGZ=true;
		ReadWrite.ZIPLEVEL=9;
		Timer t=new Timer();
		TaxTree tree=new TaxTree(args[0], args[1]);
		t.stop();
		System.out.println("Retained "+tree.nodeCount+" nodes:");
		for(int i=tree.treeLevels.length-1; i>=0; i--){
			System.out.print(tree.nodesPerLevel[i]+"\t"+taxaNames[i]);
			if(verbose){
				int lim=10;
				for(int j=0; j<lim && j<tree.treeLevels[i].length; j++){
					TaxNode n=tree.treeLevels[i][j];
					System.out.print("\n"+n+" -> "+tree.nodes[n.pid]);
				}
				for(int j=tree.treeLevels[i].length-lim; j<tree.treeLevels[i].length; j++){
					if(j>=lim){
						TaxNode n=tree.treeLevels[i][j];
						System.out.print("\n"+n+" -> "+tree.nodes[n.pid]);
					}
				}
			}
			System.out.println();
		}
		System.out.println();
		System.out.println("Time: \t"+t);
		
		if(args.length>2){//Write a tree
			ReadWrite.write(tree, args[2], true);
		}
	}
	
	public TaxTree(String namesFile, String nodesFile){
		
		nodes=getNames(namesFile);
		getNodes(nodesFile, nodes);
		
		if(simplify){
			int removed=simplify(nodes);
			if(verbose){System.out.println("Removed "+removed+" nodes.");}
		}
		
		for(TaxNode n : nodes){
			if(n!=null){
				nodesPerLevel[n.level]++;
			}
		}
		
		for(int i=0; i<nodesPerLevel.length; i++){
			treeLevels[i]=new TaxNode[nodesPerLevel[i]];
		}
		
		{
			int[] temp=new int[nodesPerLevel.length];
			for(TaxNode n : nodes){
				if(n!=null){
					int level=n.level;
					treeLevels[level][temp[level]]=n;
					temp[level]++;
				}
			}
		}
		nodeCount=(int)Tools.sum(nodesPerLevel);
		
	}
	
	/*--------------------------------------------------------------*/
	/*----------------         Construction         ----------------*/
	/*--------------------------------------------------------------*/
	
	private static TaxNode[] getNames(String fname){
		ArrayList<TaxNode> list=new ArrayList<TaxNode>(200000);
		int max=0;
		
		TextFile tf=new TextFile(fname, false, false);
		for(String s=tf.nextLine(); s!=null; s=tf.nextLine()){
			if(s.contains("scientific name")){
				String[] split=delimiter.split(s, 3);
				assert(split.length==3) : s;
				int id=Integer.parseInt(split[0]);
				String name=split[1];
				if(id==1 && name.equalsIgnoreCase("root")){name="Life";}
				max=Tools.max(max, id);
				list.add(new TaxNode(id, name));
			}
		}
		
		TaxNode[] nodes=new TaxNode[max+1];
		for(TaxNode n : list){
			assert(nodes[n.id]==null || nodes[n.id].equals(n)) : nodes[n.id]+" -> "+n;
			nodes[n.id]=n;
		}
		
		return nodes;
	}
	
	public void hashNames(){
		assert(nameMap==null);
		assert(nameMapLower==null);
		nameMap=new HashMap<String, ArrayList<TaxNode>>((int)Tools.mid(2, nodes.length*1.5, Integer.MAX_VALUE));
		nameMapLower=new HashMap<String, ArrayList<TaxNode>>((int)Tools.mid(2, nodes.length*1.5, Integer.MAX_VALUE));
		for(TaxNode n : nodes){
			if(n!=null){
				if(n.name!=null && !n.name.equals("environmental samples")){
					{
						ArrayList<TaxNode> list=nameMap.get(n.name);
						if(list==null){
							list=new ArrayList<TaxNode>();
							nameMap.put(n.name, list);
						}
						list.add(n);
					}
					{
						String lc=n.name.toLowerCase();
						ArrayList<TaxNode> list=nameMapLower.get(lc);
						if(list==null){
							list=new ArrayList<TaxNode>();
							nameMapLower.put(lc, list);
						}
						list.add(n);
					}
				}
			}
		}
	}
	
	private static TaxNode[] getNodes(String fname, TaxNode[] nodes){
		
		int max=0;
		
		LinkedHashMap<String, int[]> oddNames=new LinkedHashMap<String, int[]>();
		
		TextFile tf=new TextFile(fname, false, false);
		for(String s=tf.nextLine(); s!=null; s=tf.nextLine()){
			String[] split=delimiter.split(s, 4);
			assert(split.length==4) : s;
			int id=-1, pid=-1, level=-1;
			
			id=Integer.parseInt(split[0]);
			try {
				pid=Integer.parseInt(split[1]);
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.err.println("Bad line: "+s+"\n"+Arrays.toString(split));
			}
			boolean alt=false;
			{
				String key=split[2];
				Integer obj=levelMap.get(key);
				if(obj==null){
					obj=altLevelMap.get(key);
					alt=true;
				}
				if(obj!=null){
					level=obj;
					if(id==pid){
						level=levelMap.get("life");
						alt=false;
					}
				}else{
					if(id==pid){
						level=levelMap.get("life");
						alt=false;
					}else{
						int[] count=oddNames.get(key);
						if(count==null){
							count=new int[1];
							oddNames.put(key, count);
						}
						count[0]++;
					}
				}
			}
			max=Tools.max(max, id);
			TaxNode n=nodes[id];
			assert(n!=null && n.pid<0) : n+" -> "+s;
			n.pid=pid;
			n.level=level;
			n.canonical=!alt;
		}
		
		if(oddNames.size()>0){
			System.out.println("Found "+oddNames.size()+" unknown taxonomic levels:");
			if(verbose){
				for(String s : oddNames.keySet()){
					System.out.println(oddNames.get(s)[0]+"\t"+s);
				}
			}
		}
		
		return nodes;
	}
	
	private int simplify(TaxNode nodes[]){
		
		int failed=test(nodes);
		
		int removed=0;
		int reassigned=0;
		
		if(eliminateUnknown){//Eliminate nodes with unknown taxa
			if(verbose){System.out.println("A0");}
			for(int i=0; i<nodes.length; i++){
				TaxNode n=nodes[i];
				if(n!=null){
					int pid=n.pid;
					TaxNode parent=nodes[pid];
					assert(parent!=null) : n;
					assert(parent!=n || pid==1) : n+", "+pid;
					while(parent.level<0){
						assert(parent.id!=parent.pid);
						parent=nodes[parent.pid];
						n.pid=parent.id;
						reassigned++;
					}
				}
			}
			
			for(int i=0; i<nodes.length; i++){
				if(nodes[i]!=null && nodes[i].level<0){
					nodes[i]=null;
					removed++;
				}
			}
			if(verbose){System.out.println("reassigned: "+reassigned+", removed: "+removed);}
		}
		
		if(inferRankLimit>0){//Infer level for unset nodes (from "no rank")
			if(verbose){System.out.println("A");}
			int changed=1;
			while(changed>0){
				changed=0;
				for(final TaxNode n : nodes){
					if(n!=null){
						if(n.level==0){
							TaxNode parent=nodes[n.pid];
							if(n!=parent && parent.level>0 && parent.level<=inferRankLimit+1){
								n.level=Tools.max(1, parent.level-1);
								assert(n.level>0 && n.level<=parent.level && n.level<=inferRankLimit);
								n.canonical=false;
								changed++;
							}
						}
					}
				}
				if(verbose){System.out.println("changed: "+changed);}
			}
			
//			System.out.println("B");
//			for(TaxNode n : nodes){
//				if(n!=null && n.level==0){
//					n.level=-1;
//				}
//			}
		}
		
		failed=test(nodes);
		
		{//Skip nodes with duplicate taxa
			if(verbose){System.out.println("D");}
			int changed=1;
			while(changed>0){
				changed=0;
				for(final TaxNode n : nodes){
					if(n!=null){
						TaxNode parent=nodes[n.pid];
						TaxNode grandparent=nodes[parent.pid];
						assert(n.level<=parent.level || parent.level<1 || !parent.canonical) : n+" -> "+parent+" -> "+grandparent;
						assert(parent.level<=grandparent.level || grandparent.level<1 || !grandparent.canonical) : n+" -> "+parent+" -> "+grandparent;

						while(parent!=grandparent && (parent.level<0 || (parent.level==grandparent.level && !parent.canonical) || 
								n.level>parent.level || (n.level==parent.level))){
							parent=grandparent;
							grandparent=nodes[parent.pid];
							n.pid=parent.id;
							reassigned++;
							changed++;
						}
					}
				}
				if(verbose){System.out.println("changed: "+changed);}
			}
			if(verbose){System.out.println("E");}
			for(int i=0; i<nodes.length; i++){
				if(nodes[i]!=null && nodes[i].level<0){
					nodes[i]=null;
					removed++;
				}
			}
		}
		
		failed=test(nodes);

		if(verbose){System.out.println("F");}
		{//Ensure the tree is now clean
			for(int i=0; i<nodes.length; i++){
				TaxNode n=nodes[i];
				if(n!=null){
					TaxNode parent=nodes[n.pid];
					TaxNode grandparent=nodes[parent.pid];
					assert(n==parent || n.level<parent.level || !n.canonical) : n+" -> "+parent+" -> "+grandparent;
					assert(parent==grandparent || parent.level<grandparent.level) : n+" -> "+parent+" -> "+grandparent;
				}
			}
		}
		
		if(verbose){System.err.println("Reassignments: "+reassigned);}
		
		return removed;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------          Validation          ----------------*/
	/*--------------------------------------------------------------*/
	
	private static int test(TaxNode[] nodes){
		int failed=0;
		for(final TaxNode n : nodes){
			if(n!=null){
				TaxNode parent=nodes[n.pid];
				assert(n==parent || n.level<=parent.level || parent.level<1 || !parent.canonical) : n+" -> "+parent;
//				assert(n==parent || n.level<parent.level || parent.level<1 || !n.canonical || !parent.canonical) : n+" -> "+parent;
				if(n!=parent && n.level>=parent.level && parent.level>=1 && n.canonical && parent.canonical){
					if(verbose){System.out.println("Error: "+n+" -> "+parent);}
					failed++;
				}
				assert(n!=parent || n.id<=1) : n;
			}
		}
		if(verbose){System.out.println(failed+" nodes failed.");}
		return failed;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------        Outer Methods         ----------------*/
	/*--------------------------------------------------------------*/
	
	
	public static int getID(String s){return GiToNcbi.getID(s);}
	
	public static int getID(byte[] s){return GiToNcbi.getID(s);}
	
	/** Return the ancestor with taxonomic level at least minLevel */
	public TaxNode getNode(String s, int minLevel){
		TaxNode tn=getNode(s);
		while(tn!=null && tn.level<minLevel && tn.pid!=tn.id){
			tn=getNode(tn.pid);
		}
		return tn;
	}
	
	public TaxNode getNode(String s){
		if(s.indexOf('|')>=0){
//			System.err.println("Looking for gi number.");
			return getNode(GiToNcbi.getID(s));
		}
//		System.err.println("Can't process name "+s);
		if(Character.isDigit(s.charAt(0)) && s.length()<=9){
			try {
				return getNode(Integer.parseInt(s));
			} catch (NumberFormatException e) {
				//ignore
			}
		}
		return null;
	}
	public TaxNode getNode(byte[] s){
		if(Tools.indexOf(s, (byte)'|')>=0){return getNode(GiToNcbi.getID(s));}
		if(Character.isDigit(s[0]) && s.length<=9){
			try {
				return getNode(Tools.parseInt(s, 0, s.length));
			} catch (NumberFormatException e) {
				//ignore
			}
		}
		return null;
	}
	public TaxNode getNode(int id){return id<0 ? null : nodes[id];}
	
	public TaxNode getNodeByName(String s){
		TaxNode tn=getNodeByName(s, false);
		if(tn==null){tn=getNodeByName(s, true);}
		return tn;
	}
	private TaxNode getNodeByName(String s, boolean lowercase){
		if(s.indexOf('_')>=0){s=s.replace('_', ' ');}
		if(lowercase){s=s.toLowerCase();}
//		System.err.println("Searching for "+s);
		final HashMap<String, ArrayList<TaxNode>> map=(lowercase ? nameMapLower : nameMap);
		ArrayList<TaxNode> list=map.get(s);
		if(list!=null){
			if(list.size()==1){return list.get(0);}
			assert(false) : "Found multiple nodes for '"+s+"':\n"+list+"\n";
		}
//		System.err.println("No matches for '"+s+"'");
		
//		assert(false) : nameMap.containsKey(s)+", "+nameMapLower.containsKey(s);
		
		String[] split=delimiter2.split(lowercase ? s.toLowerCase() : s, 8);
//		System.err.println("Array: "+Arrays.toString(split));
		list=map.get(split[split.length-1]);
//		System.err.println(list==null ? "No matches for "+split[split.length-1] : "Found list( "+list.size()+")");
		if(list==null || list.isEmpty()){
			return null;
		}
		if(list.size()==1){return list.get(0);}

		TaxNode matching=null;
		for(TaxNode tn : list){
			if(tn.matchesName(split, split.length-1, this)){
				assert(matching==null) : "Found two nodes for '"+s+"':\n"+matching+"\n"+tn;
				matching=tn;
			}
		}
		return matching;
	}
	public ArrayList<TaxNode> getAncestors(int id){
		TaxNode current=getNode(id);
		ArrayList<TaxNode> list=new ArrayList<TaxNode>();
		while(current!=null && current.pid!=current.id){//ignores root
			list.add(current);
			current=getNode(current.pid);
		}
		//optionally add root here
		return list;
	}
	
	public void increment(IntList ids, IntList counts, boolean sync){
		
		ids.sort();
		ids.getUniqueCounts(counts);
		
		if(!sync){
			for(int i=0; i<ids.size; i++){
				int id=ids.get(i);
				int count=counts.get(i);
				incrementRaw(id, count);
			}
		}else{
			synchronized(this){
				for(int i=0; i<ids.size; i++){
					int id=ids.get(i);
					int count=counts.get(i);
					incrementRaw(id, count);
				}
			}
		}
	}
	
	public void incrementRaw(int id, long amt){
		nodes[id].incrementRaw(amt);
	}
	
	public void percolateUp(){
		for(int i=0; i<treeLevels.length; i++){
			percolateUp(i);
		}
	}
	
	public void percolateUp(final int fromLevel){
		final TaxNode[] stratum=treeLevels[fromLevel];
		for(final TaxNode n : stratum){
			n.incrementSum(n.countRaw);
			TaxNode parent=nodes[n.pid];
			if(n!=parent){
				parent.incrementSum(n.countSum);
			}
		}
	}
	
	/** Add this amount to the node and all its ancestors. */
	public void percolateUp(TaxNode node, long amt){
		if(amt==0){return;}
		if(verbose){System.err.println("percolateUp("+amt+") node: "+node);}
		while(node.id!=node.pid){
			node.incrementSum(amt);
			node=nodes[node.pid];
		}
		node.incrementSum(amt);
	}
	
	public ArrayList<TaxNode> gatherNodesAtLeastLimit(final long limit){
		return gatherNodesAtLeastLimit(limit, 0, nodesPerLevel.length-1);
	}
	
	public ArrayList<TaxNode> gatherNodesAtLeastLimit(final long limit, final int minLevel, final int maxLevel){
		ArrayList<TaxNode> list=new ArrayList<TaxNode>();
		for(int i=minLevel; i<nodesPerLevel.length && i<=maxLevel; i++){
			list.addAll(gatherNodesAtLeastLimit(i, limit));
		}
		Collections.sort(list, TaxNode.countComparator);
		return list;
	}
	
	public ArrayList<TaxNode> gatherNodesAtLeastLimit(final int fromLevel, final long limit){
		ArrayList<TaxNode> list=new ArrayList<TaxNode>();
		final TaxNode[] stratum=treeLevels[fromLevel];
		for(final TaxNode n : stratum){
			if(n.countSum>=limit){
				list.add(n);
				TaxNode parent=nodes[n.pid];
				if(n!=parent){
					percolateUp(parent, -n.countSum);
				}
			}
		}
		Collections.sort(list, TaxNode.countComparator);
		return list;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------     Static Initializers      ----------------*/
	/*--------------------------------------------------------------*/

	/**
	 * @return
	 */
	private static HashMap<String, Integer> makeLevelMap() {
		HashMap<String, Integer> map=new HashMap<String, Integer>(31);
		for(int i=0; i<taxaNames.length; i++){
			map.put(taxaNames[i], i);
		}
		return map;
	}

	/**
	 * @return
	 */
	private static HashMap<String, Integer> makeAltLevelMap() {
		HashMap<String, Integer> map=new HashMap<String, Integer>(67);
		for(int i=0; i<taxaNames.length; i++){
			map.put(taxaNames[i], i);
		}
		
		//Add synonyms
		map.put("subfamily", map.get("family"));
		map.put("tribe", map.get("family"));
		map.put("varietas", map.get("subspecies"));
		map.put("subgenus", map.get("genus"));
		map.put("forma", map.get("subspecies"));
		map.put("species group", map.get("genus"));
		map.put("subclass", map.get("class"));
		map.put("species subgroup", map.get("species"));
		map.put("infraorder", map.get("order"));
		map.put("superorder", map.get("class"));
		map.put("subphylum", map.get("phylum"));
		map.put("infraclass", map.get("class"));
		map.put("superkingdom", map.get("division"));
		map.put("parvorder", map.get("order"));
		map.put("superclass", map.get("phylum"));
		map.put("superphylum", map.get("kingdom"));
		map.put("subkingdom", map.get("kingdom"));
		map.put("superfamily", map.get("order"));
		map.put("superkingdom", map.get("domain"));
		map.put("suborder", map.get("order"));
		map.put("subtribe", map.get("family"));
//		map.put("no rank", map.get("subspecies"));
		
		return map;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------            Fields            ----------------*/
	/*--------------------------------------------------------------*/
	
	public final TaxNode[] nodes;
	public final int[] nodesPerLevel=new int[taxaNames.length];
	public final int nodeCount;
	
	public final TaxNode[][] treeLevels=new TaxNode[taxaNames.length][];
	
	HashMap<String, ArrayList<TaxNode>> nameMap;
	HashMap<String, ArrayList<TaxNode>> nameMapLower;
	
	public int minValidTaxa=0;
	
	public boolean simplify=true;
	public boolean inferSpecies=true;
	public boolean eliminateUnknown=false;
	public int inferRankLimit=levelMap.get("species");
	
	/*--------------------------------------------------------------*/
	/*----------------          Constants           ----------------*/
	/*--------------------------------------------------------------*/

	public static final int stringToLevel(String s){return levelMap.containsKey(s) ? levelMap.get(s) : altLevelMap.get(s);}
	public static final String levelToString(int x){return taxaNames[x];}
	
	private static final String[] taxaNames=new String[] {
		"no rank", "subspecies", "species", "genus",
		"family", "order", "class", "phylum",
		"kingdom", "domain", "life"
	};
	
	private static final HashMap<String, Integer> levelMap=makeLevelMap();
	private static final HashMap<String, Integer> altLevelMap=makeAltLevelMap();

	private static final Pattern delimiter = Pattern.compile("\t\\|\t");
	private static final Pattern delimiter2 = Pattern.compile("[\\s_]+");
	
	public static boolean verbose=false;
	
}