package net.floodlightcontroller.util;


import java.util.ArrayList;
import java.util.List;

import net.floodlightcontroller.core.IOFSwitch;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;

/**
 * Tools to help work with OFMessages.
 * 
 * Compare OFMessage-extending objects (e.g. OFFlowMod, OFPacketIn)
 * where the XID does not matter. This is especially useful for
 * unit testing where the XID of the OFMessage might vary whereas
 * the expected OFMessgae must have a set XID.
 *
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 */

public class OFMessageUtils {

	/**
	 * Prevent instantiation
	 */
	private OFMessageUtils() {};

	/**
	 * Get the ingress port of a packet-in message. The manner in which
	 * this is done depends on the OpenFlow version. OF1.0 and 1.1 have
	 * a specific in_port field, while OF1.2+ store this information in
	 * the packet-in's match field.
	 * 
	 * @param pi, the OFPacketIn
	 * @return the ingress OFPort
	 */
	public static OFPort getInPort(OFPacketIn pi) {
		return pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT);
	}

	/**
	 * Set the ingress port of a packet-out message. The manner in which
	 * this is done depends on the OpenFlow version. OF1.0 thru 1.4 have
	 * a specific in_port field, while OF1.5+ store this information in
	 * the packet-out's match field.
	 * 
	 * @param pob, the OFPacketOut.Builder within which to set the in port
	 * @param in, the ingress OFPort
	 */
	public static void setInPort(OFPacketOut.Builder pob, OFPort in) {
		if (pob.getVersion().compareTo(OFVersion.OF_15) < 0) { 
			pob.setInPort(in);
		} else if (pob.getMatch() != null) {
			pob.getMatch().createBuilder()
			.setExact(MatchField.IN_PORT, in)
			.build();
		} else {
			pob.setMatch(OFFactories.getFactory(pob.getVersion())
					.buildMatch()
					.setExact(MatchField.IN_PORT, in)
					.build());
		}
	}

	/**
	 * Get the VLAN on which this packet-in message was received.
	 * @param pi, the OFPacketIn
	 * @return the VLAN
	 */
	public static OFVlanVidMatch getVlan(OFPacketIn pi) {
		return pi.getMatch().get(MatchField.VLAN_VID) == null ? OFVlanVidMatch.UNTAGGED : pi.getMatch().get(MatchField.VLAN_VID);
	}

	/**
	 * Returns true if each object is deeply-equal in the same manner that
	 * Object's equals() does with the exception of the XID field, which is
	 * ignored; otherwise, returns false.
	 * 
	 * NOTE: This function is VERY INEFFICIENT and creates a new OFMessage
	 * object in order to the the comparison minus the XID. It is advised
	 * that you use it sparingly and ideally only within unit tests.
	 * 
	 * @param a; object A to compare
	 * @param b; object B to compare
	 * @return true if A and B are deeply-equal; false otherwise
	 */
	public static boolean equalsIgnoreXid(OFMessage a, OFMessage b) {
		OFMessage.Builder mb = b.createBuilder().setXid(a.getXid());
		return a.equals(mb.build());
	}

	/**
	 * Writes an OFPacketOut message to a switch.
	 * 
	 * @param sw
	 *            The switch to write the PacketOut to.
	 * @param packetInMessage
	 *            The corresponding PacketIn.
	 * @param egressPort
	 *            The switchport to output the PacketOut.
	 */
	public static void writePacketOutForPacketIn(IOFSwitch sw,
			OFPacketIn packetInMessage, OFPort egressPort) {

		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();

		// Set buffer_id, in_port, actions_len
		pob.setBufferId(packetInMessage.getBufferId());
		pob.setInPort(packetInMessage.getVersion().compareTo(OFVersion.OF_12) < 0 ? packetInMessage
				.getInPort() : packetInMessage.getMatch().get(
						MatchField.IN_PORT));

		// set actions
		List<OFAction> actions = new ArrayList<OFAction>(1);
		actions.add(sw.getOFFactory().actions().buildOutput()
				.setPort(egressPort).setMaxLen(0xffFFffFF).build());
		pob.setActions(actions);

		// set data - only if buffer_id == -1
		if (packetInMessage.getBufferId() == OFBufferId.NO_BUFFER) {
			byte[] packetData = packetInMessage.getData();
			pob.setData(packetData);
		}

		// and write it out
		sw.write(pob.build());
	}
}
