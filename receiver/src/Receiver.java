import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class Receiver {
	
	private static final int MAXSIZE = 1024;
	private static DatagramSocket socket = null;
	
	public static void main (String[] args) throws Exception{
//		if (args.length != 2) {
//			System.out.println("Error: Usage:");
//			System.exit(0);
//		}
		
		int recvPort = Integer.parseInt(args[0]);
//		String file = args[1];
		
		
/////////////////////////////////////////////////////////////////////////

		socket = new DatagramSocket(recvPort);
		initConn();
		finnConn();
		
		
		

		return;
	}
	
	private static void initConn () throws IOException, ClassNotFoundException {
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[MAXSIZE];
		byte[] outgoingPayload = null;
		
//		receive syn packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		InetAddress senderAddr = incomingPacket.getAddress();
		int senderPort = incomingPacket.getPort();
		System.out.println("incoming packet size: "+incomingPayload.length);
		getFlag(((STP) recvObj));
		System.out.println("syn packet received\n");
		
//		send synack packet
		stp = new STP(0, true, true, false, 100, 100);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
		socket.send(outgoingPacket);
		System.out.println("outgoingPayload length: "+outgoingPayload.length);
		getFlag(stp);
		System.out.println("synack packet sent\n");
		
//		receive ack packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		System.out.println("incoming packet size: "+incomingPayload.length);
		getFlag(((STP) recvObj));
		System.out.println("ack packet reived\n");
		
		return;
	}
	
	private static void finnConn () throws IOException, ClassNotFoundException {
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[MAXSIZE];
		byte[] outgoingPayload = null;
		
//		receive fin packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		InetAddress senderAddr = incomingPacket.getAddress();
		int senderPort = incomingPacket.getPort();
		System.out.println("incoming packet size: "+incomingPayload.length);
		getFlag(((STP) recvObj));
		System.out.println("fin packet receive\n");
		
//		send ack packet
		stp = new STP(0, false, true, false, 0, 0);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
		socket.send(outgoingPacket);
		System.out.println("outgoingPayload length: "+outgoingPayload.length);
		getFlag(stp);
		System.out.println("ack packet sent\n");
		
//		send fin packet
		stp = new STP(0, false, false, true, 0, 0);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
		socket.send(outgoingPacket);
		System.out.println("outgoingPayload length: "+outgoingPayload.length);
		getFlag(stp);
		System.out.println("fin packet sent\n");
		
//		receive ack packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		System.out.println("incoming packet size: "+incomingPayload.length);
		getFlag(((STP) recvObj));
		System.out.println("ack packet receive\n");
		
		return;
	}

	
//	debugging function
	private static void getFlag(STP obj) {
		if (obj.isSynFlag().booleanValue()) System.out.print("Syn\t");
		if (obj.isAckFlag().booleanValue()) System.out.print("Ack\t");
		if (obj.isFinFlag().booleanValue()) System.out.print("Fin\t");
		
		System.out.println();
		return;
	}
	
}









