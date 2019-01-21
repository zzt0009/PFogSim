/**
 * Centralized Orchestrator for comparison against Puddle algorithm
 * @author jih0007@auburn.edu
 */
package edu.auburn.pFogSim.orchestrator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import edu.auburn.pFogSim.Radix.DistRadix;
import edu.auburn.pFogSim.netsim.ESBModel;
import edu.auburn.pFogSim.netsim.NetworkTopology;
import edu.auburn.pFogSim.netsim.NodeSim;
import edu.auburn.pFogSim.util.MobileDevice;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeHost;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;

public class CentralOrchestrator extends EdgeOrchestrator {

	ArrayList<EdgeHost> hosts;
	HashMap<NodeSim,HashMap<NodeSim, LinkedList<NodeSim>>> pathTable;
	
	public CentralOrchestrator(String _policy, String _simScenario) {
		super(_policy, _simScenario);
	}
	/**
	 * get all the hosts in the network into one list
	 */
	@Override
	public void initialize() {
		hosts = new ArrayList<EdgeHost>();
		for (Datacenter node : SimManager.getInstance().getLocalServerManager().getDatacenterList()) {
			hosts.add(((EdgeHost) node.getHostList().get(0)));
		}
		pathTable = new HashMap<>();
		ESBModel networkModel = (ESBModel)(SimManager.getInstance().getNetworkModel());
		for (NodeSim src: networkModel.getNetworkTopology().getNodes()) {
			HashMap<NodeSim, LinkedList<NodeSim>> tempMap = new HashMap<>();
			for (NodeSim des: networkModel.getNetworkTopology().getNodes()) {
				LinkedList<NodeSim> tempList = new LinkedList<>();
				tempList = networkModel.findPath(src, des);
				tempMap.put(des, tempList);
			}
			pathTable.put(src, tempMap);
		}

	}
	/**
	 * get the id of the appropriate host
	 */
	@Override
	public int getDeviceToOffload(Task task) {
		return getHost(task).getId();
	}
	/**
	 * the the appropriate VM to run on
	 */
	@Override
	public EdgeVM getVmToOffload(Task task) {
		return ((EdgeVM) getHost(task).getVmList().get(0));
	}
	/**
	 * find the host
	 * @param task
	 * @return
	 */
	private EdgeHost getHost(Task task) {
		MobileDevice mb = SimManager.getInstance().getMobileDeviceManager().getMobileDevices().get(task.getMobileDeviceId());
		task.setPath(mb.getPath());
		return mb.getHost();
	}
	/* (non-Javadoc)
	 * @see edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator#assignHost(edu.auburn.pFogSim.util.MobileDevice)
	 */
	@Override
	public void assignHost(MobileDevice mobile) {
		// TODO Auto-generated method stub
		Map<Double, List<NodeSim>> costMap = new HashMap<>();
		NodeSim src = ((ESBModel)SimManager.getInstance().getNetworkModel()).getNetworkTopology().findNode(mobile.getLocation(), false);
		Map<NodeSim, LinkedList<NodeSim>> desMap = pathTable.get(src);
		
		for (Entry<NodeSim, LinkedList<NodeSim>> entry: desMap.entrySet()) {
			double cost = 0;
			NodeSim des = entry.getKey();
			LinkedList<NodeSim> path = entry.getValue();
			if (path == null || path.size() == 0) {
				EdgeHost k = SimManager.getInstance().getLocalServerManager().findHostByLoc(mobile.getLocation().getXPos(), mobile.getLocation().getYPos());
				//des = ((ESBModel)(SimManager.getInstance().getNetworkModel())).getNetworkTopology().findNode(task.getSubmittedLocation(), false);
				cost = (mobile.getTaskLengthRequirement() / k.getTotalMips() * k.getCostPerSec() + mobile.getBWRequirement() * k.getCostPerBW());
			}
			else {
				//SimLogger.getInstance().getCentralizeLogPrinter().println("**********Path From " + src.getWlanId() + " To " + des.getWlanId() + "**********");
				for (NodeSim node: path) {
					EdgeHost k = SimManager.getInstance().getLocalServerManager().findHostByLoc(node.getLocation().getXPos(), node.getLocation().getYPos());
					double bwCost = mobile.getBWRequirement() * k.getCostPerBW();
					cost = cost + bwCost;
					//SimLogger.getInstance().getCentralizeLogPrinter().println("Level:\t" + node.getLevel() + "\tNode:\t" + node.getWlanId() + "\tBWCost:\t" + bwCost + "\tTotalBWCost:\t" + cost);
				}
				//des = path.peekLast();
				EdgeHost desHost = SimManager.getInstance().getLocalServerManager().findHostByLoc(des.getLocation().getXPos(), des.getLocation().getYPos());
				double exCost = desHost.getCostPerSec() * 
						(mobile.getTaskLengthRequirement() / desHost.getTotalMips());
				cost = cost + exCost;
				//SimLogger.getInstance().getCentralizeLogPrinter().println("Destiation:\t"+ des.getWlanId() + "\tExecuteCost:\t" + exCost + "\tTotalCost:\t" + cost);
			}
			
			if (costMap.containsKey(cost)) {
				if (!costMap.get(cost).contains(des)) {
					costMap.get(cost).add(des);
				}
			}
			else {
				ArrayList<NodeSim> desList = new ArrayList<>();
				desList.add(des);
				costMap.put(cost, desList);
			}
		}
		EdgeHost host = null;
		for (Map.Entry<Double, List<NodeSim>> entry: costMap.entrySet()) {
			for(NodeSim desNode: entry.getValue()) {
				host = SimManager.getInstance().getLocalServerManager().findHostByLoc(desNode.getLocation().getXPos(), desNode.getLocation().getYPos());
				try {
					if(goodHost(host, mobile)) {
						LinkedList<NodeSim> path = ((ESBModel)SimManager.getInstance().getNetworkModel()).findPath(host, mobile);
						mobile.setPath(path);
						mobile.setHost(host);
						mobile.makeReservation();
						return;
						
					}
				}
				catch (NullPointerException e){//THE OTHER LEVER!!!!!!!!!!
					//If there are no nodes in the list that can take the task, send to the cloud
					host = (EdgeHost) cloud.getHostList().get(0);
				}
			}
		}
		if (host == null) {
			host = (EdgeHost) cloud.getHostList().get(0);
		}
		LinkedList<NodeSim> path = ((ESBModel)SimManager.getInstance().getNetworkModel()).findPath(host, mobile);
		mobile.setPath(path);
		mobile.setHost(host);
		mobile.makeReservation();
		return;
	}
	/**
	 * @return the hosts
	 */
	public ArrayList<EdgeHost> getHosts() {
		return hosts;
	}
	/**
	 * @param hosts the hosts to set
	 */
	public void setHosts(ArrayList<EdgeHost> hosts) {
		this.hosts = hosts;
	}
	/**
	 * @return the pathTable
	 */
	public HashMap<NodeSim, HashMap<NodeSim, LinkedList<NodeSim>>> getPathTable() {
		return pathTable;
	}
	/**
	 * @param pathTable the pathTable to set
	 */
	public void setPathTable(HashMap<NodeSim, HashMap<NodeSim, LinkedList<NodeSim>>> pathTable) {
		this.pathTable = pathTable;
	}

}
