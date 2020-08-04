/*
 * Title:        EdgeCloudSim - EdgeHost
 * 
 * Description: 
 * EdgeHost adds location information over CloudSim's Host class
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 * 
 * Modified by Shehenaz Shaik, Qian Wang
 */

package edu.boun.edgecloudsim.edge_server;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;

import edu.auburn.pFogSim.netsim.ESBModel;
import edu.auburn.pFogSim.netsim.NodeSim;
import edu.auburn.pFogSim.util.MobileDevice;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.utils.Location;
import edu.boun.edgecloudsim.utils.SimLogger;


/**
 * 
 * @author szs0117
 *
 */
public class EdgeHost extends Host {
	private Location location;
	private int level;//puddle level
	private double costPerBW;// Qian added for centralOrchestrator
	private double costPerSec;// Qian added for centralOrchestrator
	private int puddleId;// Qian added for puddle
	private EdgeHost parent;//Qian: added for puddle
	private ArrayList<EdgeHost> children = null;//Qian: added for puddle
	private double reserveBW;//Qian: added for service replacement
	private long reserveMips;//Qian: added for service replacement
	private ArrayList<MobileDevice> customers;//Qian: added for service replacement 
	
	
	/**
	 * 
	 * @param id
	 * @param ramProvisioner
	 * @param bwProvisioner
	 * @param storage
	 * @param peList
	 * @param vmScheduler
	 */
	public EdgeHost(int id, RamProvisioner ramProvisioner,
			BwProvisioner bwProvisioner, long storage,
			List<? extends Pe> peList, VmScheduler vmScheduler) {
		super(id, ramProvisioner, bwProvisioner, storage, peList, vmScheduler);
		this.reserveBW = 0;
		this.reserveMips = 0;
		this.customers = new ArrayList<>();

	}
	
	
	//Qian add two parameters for centralOrchestrator
	/**
	 * 
	 * @param id
	 * @param ramProvisioner
	 * @param bwProvisioner
	 * @param storage
	 * @param peList
	 * @param vmScheduler
	 * @param _costPerBW
	 * @param _costPerSec
	 */
	public EdgeHost(int id, RamProvisioner ramProvisioner,
			BwProvisioner bwProvisioner, long storage,
			List<? extends Pe> peList, VmScheduler vmScheduler, double _costPerBW, double _costPerSec) {
		super(id, ramProvisioner, bwProvisioner, storage, peList, vmScheduler);
		this.costPerBW = _costPerBW;
		this.costPerSec = _costPerSec;
		this.reserveBW = 0;
		this.reserveMips = 0;
		this.customers = new ArrayList<>();
	}
	
	
	/**
	 * 
	 * @param _location
	 */
	public void setPlace(Location _location){
		location=_location;
	}
	
	
	/**
	 * 
	 * @return
	 */
	public Location getLocation(){
		return location;
	}
	
	
	/**
	 * 
	 * @return
	 */
	public int getLevel() {
		return level;
	}
	
	
	/**
	 * 
	 * @param _level
	 */
	public void setLevel(int _level) {
		level = _level;
	}
	
	
	/**
	 * 
	 * @return
	 */
	public double getCostPerBW() {
		return costPerBW;
	}
	
	
	/**
	 * 
	 * @return
	 */
	public double getCostPerSec() {
		return costPerSec;
	}
	
	
	//Qian added for puddle
	/**
	 * 
	 * @param id
	 */
	public void setPuddleId(int id) {
		this.puddleId = id;
	} 
	
	
	//Qian added for puddle
	/**
	 * 
	 * @return
	 */
	public int getPuddleId() {
		return this.puddleId;
	}
	
	
	/**
	 * @author Qian
	 * Add for puddle
	 * @param parent parent node in parent puddle
	 */
	public void setParent(EdgeHost _parent) {
		this.parent = _parent;
	}
	
	
	/**
	 * @author Qian
	 * added for puddle
	 * @return parent node in parent puddle
	 */
	public EdgeHost getParent() {
		return this.parent;
	}
	
	
	/**
	 * @author Qian
	 * added for puddle
	 * @param _child child node
	 */
	public void setChild(EdgeHost _child) {
		if (children == null) {
			children = new ArrayList<EdgeHost>();
		}
		children.add(_child);
	}
	
	
	/**
	 * @author Qian
	 * @return children get children list.
	 */
	public ArrayList<EdgeHost> getChildren() {
		return this.children;
	}
	
	
	/**
	 * Check if free Bandwidth available for certain mobile device
	 * @author Qian
	 * @param mb
	 * @return boolean
	 */
	public boolean isBWAvailable(MobileDevice mb) {
		double maxBW = this.getBw();
		Log.printLine("isBWAvailable:maxBW: "+maxBW);
		double tempBW = reserveBW + mb.getBWRequirement();
		if (tempBW < maxBW) {
			return true;
		}
		else {
			mb.setAssignHostStatus(SimLogger.TASK_STATUS.REJECTED_DUE_TO_LACK_OF_NETWORK_BANDWIDTH);
			return false;
		}
	}
	
	
	/**
	 * reserve Bandwidth for certain mobile device
	 * @author Qian
	 *	@param mb
	 */
	public void reserveBW(MobileDevice mb) {
		reserveBW = reserveBW + mb.getBWRequirement();
	}
	

	/**
	 * Check if MIPS capacity of this host is sufficient to host the application for given mobile device
	 * @author Shaik
	 *	@param mb
	 *	@return boolean
	 */
	public boolean isMIPSCapacitySufficient(MobileDevice mb) {
		double reqMips = (double)mb.getTaskLengthRequirement();
		double hostMipsCapacity = this.getPeList().get(0).getMips(); //  * (double)1 / 100.0
		
		if (reqMips < hostMipsCapacity) {
			return true;
		}
		return false;
	}
	
	
	/**
	 * Check if free MIPS available for certain mobile device
	 * @author Qian, Shaik
	 *	@param mb
	 *	@return boolean
	 */
	public boolean isMIPSAvailable(MobileDevice mb) {
		long maxMips = this.getTotalMips();
		Log.printLine("isMIPSAvailable:maxMips: "+maxMips); 
		long tempLength = reserveMips + mb.getTaskLengthRequirement();
		if (tempLength < (maxMips * SimSettings.MAX_NODE_MIPS_UTIL_ALLOWED) ) { //  / (double)100
			// Due to large node Mips configurations, allowing only a maximum of 1% utilization, before the requests spill-over to find other node. --Shaik updated 
			//Note: This limitation is specific to our current test environment
			return true;
		}
		else {
			mb.setAssignHostStatus(SimLogger.TASK_STATUS.REJECTED_DUE_TO_LACK_OF_NODE_CAPACITY);
			return false;
		}
	}
	
	
	/**
	 * reserve Mips for certain mobile device
	 * @author Qian
	 *	@param mb
	 */
	private void reserveCPUResource(MobileDevice mb) {
		reserveMips = reserveMips + mb.getTaskLengthRequirement(); 
	}
	
	
	/**
	 * Check if latency requirement is satisfied for certain mobile device
	 * @author Shehenaz Shaik
	 *	@param mb
	 *	@return boolean
	 */
	public boolean isLatencySatisfactory(MobileDevice mb) {
		
		double hostProcessingDelay = (double)(mb.getTaskLengthRequirement()) / this.getVmScheduler().getPeCapacity(); 
		double acceptableLatency = mb.getLatencyRequirement();
		double hostNetworkDelay = ((ESBModel)SimManager.getInstance().getNetworkModel()).getDelay(mb.getLocation(), this.location);
		
		//Consider round trip latency - assuming user is co-located with device, in current test environment.
		hostNetworkDelay = hostNetworkDelay * 2;
		
		double totalDelay = hostProcessingDelay + hostNetworkDelay; 
		
		//System.out.print("Device acceptable delay: "+acceptableLatency+" : totalDelay: "+totalDelay+" : to host id: "+this.getId());
		//System.out.print(" host Processing delay: "+hostProcessingDelay+" : hostNetworkDelay: "+hostNetworkDelay+" : to host id: "+this.getId());
		//System.out.print(" this.getVmScheduler().getPeCapacity(): "+this.getVmScheduler().getPeCapacity());
			
		if (totalDelay < acceptableLatency) {
			//System.out.println();
			return true;
		}
		else {
			mb.setAssignHostStatus(SimLogger.TASK_STATUS.REJECTED_DUE_TO_UNACCEPTABLE_LATENCY);
			//System.out.println(" - REJECTED_DUE_TO_UNACCEPTABLE_LATENCY");
			return false;
		}
	}
	
	/**
	 * Added to allow for cost to be measured in terms of time rather than $
	 * 
	 * @author Bobby 
	 * @param mb
	 * @return
	 */
	public double getLatency(MobileDevice mb) {
		double hostProcessingDelay = (double)(mb.getTaskLengthRequirement()) / this.getVmScheduler().getPeCapacity(); 
		double hostNetworkDelay = ((ESBModel)SimManager.getInstance().getNetworkModel()).getDelay(mb.getLocation(), this.location);
		
		//Consider round trip latency - assuming user is co-located with device, in current test environment.
		hostNetworkDelay = hostNetworkDelay * 2;
		
		double totalDelay = hostProcessingDelay + hostNetworkDelay;
		return totalDelay;
	}
	
	
	/**
	 * make a reservation for a certain mobile device 
	 * @author Qian
	 *	@param mb
	 *	@return
	 */
	public void  makeReservation(MobileDevice mb) {
		Log.print("Before Reservation: Host Id: "+this.getId()+" Prev Reserved Mips: "+this.getReserveMips()+" Prev Reserved BW: "+this.getReserveBW()); 
		reserveBW(mb);
		reserveCPUResource(mb);
		Log.print(" After Reservation: Host Id: "+this.getId()+" Current Reserved Mips: "+this.getReserveMips()+" Current Reserved BW: "+this.getReserveBW());
		customers.add(mb);
		SimLogger.printLine("  Mobile device: "+mb.getId()+"  WAP: "+mb.getLocation().getServingWlanId()+"  Assigned host: "+this.getId());
		Log.printLine();
	}
	
	
	/**
	 * @return the customers
	 */
	public ArrayList<MobileDevice> getCustomers() {
		return customers;
	}
	
	
	/**
	 * @param customers the customers to set
	 */
	public void setCustomers(ArrayList<MobileDevice> customers) {
		this.customers = customers;
	}
	
	
	/**
	 * for puddle canHandle
	 * @author Qian
	 *	@param mb
	 *	@return
	 */
	public boolean canHandle(MobileDevice mb) {
		if (!isMIPSAvailable(mb) || !isBWAvailable(mb)) {
			return false;
		}
		else {
			LinkedList<NodeSim> path = ((ESBModel)SimManager.getInstance().getNetworkModel()).findPath(this, mb);
			for (NodeSim node: path) {
				EdgeHost tempHost = SimManager.getInstance().getLocalServerManager().findHostByWlanId(node.getLocation().getServingWlanId());
				if (!tempHost.isBWAvailable(mb)) {
					return false;
				}
			}
			return true;
		}
	}
	
	
	/**
	 * @return the reserveBW
	 */
	public double getReserveBW() {
		return reserveBW;
	}
	
	
	/**
	 * @param reserveBW the reserveBW to set
	 */
	public void setReserveBW(double reserveBW) {
		this.reserveBW = reserveBW;
	}
	
	
	/**
	 * @return the reserveMips
	 */
	public long getReserveMips() {
		return reserveMips;
	}
	
	
	/**
	 * @param reserveMips the reserveMips to set
	 */
	public void setReserveMips(long reserveMips) {
		this.reserveMips = reserveMips;
	}
	
	
	/**
	 * @param location the location to set
	 */
	public void setLocation(Location location) {
		this.location = location;
	}
	
	
	/**
	 * @param costPerBW the costPerBW to set
	 */
	public void setCostPerBW(double costPerBW) {
		this.costPerBW = costPerBW;
	}
	
	
	/**
	 * @param costPerSec the costPerSec to set
	 */
	public void setCostPerSec(double costPerSec) {
		this.costPerSec = costPerSec;
	}
	
	
	/**
	 * @param children the children to set
	 */
	public void setChildren(ArrayList<EdgeHost> children) {
		this.children = children;
	}
		
	
	/**
	 * @author szs0117
	 * @return double
	 */
	public double getFnMipsUtilization() {
		return (reserveMips * 100.0 / this.getTotalMips());
	}
	
	
	/**
	 * @author szs0117
	 * @return double
	 */
	public double getFnNwUtilization() {
		return (reserveBW * 100.0 / this.getBw());
	}
}
