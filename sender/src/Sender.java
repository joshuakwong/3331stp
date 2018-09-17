import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Scanner;


public class Sender {
	
	private static final int MAXSIZE = 1024;
	private static DatagramSocket socket = null;
	
	public static void main (String[] args) throws Exception{
//		if (args.length != 14) {
//			System.out.println("Error: Usage:");
//			System.exit(0);
//		}
		
		InetAddress recvHost = InetAddress.getByName(args[0]);
		int recvPort = Integer.parseInt(args[1]);
//		String file = args[2];
//		int mws = Integer.parseInt(args[3]);
//		int mss = Integer.parseInt(args[4]);
//		int gamma = Integer.parseInt(args[5]);
//		double pDrop = Double.parseDouble(args[6]);
//		double pDupl = Double.parseDouble(args[7]);
//		double pCorr = Double.parseDouble(args[8]);
//		double pOrder = Double.parseDouble(args[9]);
//		int maxOrder = Integer.parseInt(args[10]);
//		double pDelay = Double.parseDouble(args[11]);
//		int maxDelay = Integer.parseInt(args[12]);
//		int seed = Integer.parseInt(args[13]);
		
//		boolean validated = validate(recvPort, mws, mss, gamma, pDrop, pDupl, pCorr, pOrder, maxOrder, pDelay, maxDelay, seed);
//		if (validated == false) System.exit(1);
		
//		File pdfFile = new File(file);
//		if (!pdfFile.exists() || pdfFile.isDirectory()) {
//			System.out.println("Error: Fail to open file, file does not exist");
//			System.exit(1);
//		}
		
		
		
        // Create a datagram socket for receiving and sending UDP packets
        // through the port specified on the command line.
		socket = new DatagramSocket();
		
/////////////////////////////////////////////////////////////////////////
//		initiate connection, syn synack ack
		initConn(recvHost, recvPort);
		finnConn(recvHost, recvPort);

		
		
		
		
		
		
		
		

		
		
		
		
		return;
	}
	
	private static boolean validate (int recvPort, int mws, int mss, int gamma, double pDrop, double pDupl, double pCorr, double pOrder, int maxOrder, double pDelay, int maxDelay, int seed) {
		if (recvPort < 1000 || recvPort > 65536) {
			System.out.println("Error: port (2nd arg) needs to be 1 < port < 65536");
			return false;
		}
		
		if (mws < 0) {
			System.out.println("Error: mws (4th arg) needs to be >= 0");
			return false;
		}
		
		if (mss < 0) {
			System.out.println("Error: (5th arg) needs to be >= 0");
			return false;
		}
		
		if (gamma < 0) {
			System.out.println("Error: (6th arg) needs to be >= 0 ");
			return false;
		}
		
		if (pDrop < 0 || pDrop > 1) {
			System.out.println("Error: (7th arg) needs to be 0 < pDrop < 1 ");
			return false;
		}
		
		if (pDupl < 0 || pDupl > 1) {
			System.out.println("Error: (8th arg) needs to be between 0 < pDupl < 1");
			return false;
		}
		
		if (pCorr < 0 || pCorr > 1) {
			System.out.println("Error: (9th arg) needs to be between 0 < pCorr < 1");
			return false;
		}
		
		if (pOrder < 0 || pOrder > 1) {
			System.out.println("Error: (10th arg) needs to be between 0 < pOrder < 1");
			return false;
		}
		
		if (maxOrder < 1 || maxOrder > 6) {
			System.out.println("Error: pOrder (11th arg) needs to be between 1 and 6");
			return false;
		}
		
		if (pDelay < 0 || pDelay > 1) {
			System.out.println("Error: (12th arg) needs to be between 0 < pDelay < 1");
			return false;
		}
		
		if (maxDelay < 0) {
			System.out.println("Error: (13th arg) needs to be >= 0");
			return false;
		}
		
		if (seed < 0) {
			System.out.println("Error: (14th arg) needs to be >= 0");
			return false;
		}
		
		return true;
	}
	
	private static void initConn (InetAddress addr, int port) throws IOException, ClassNotFoundException  {
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[MAXSIZE];
		byte[] outgoingPayload = null;
		
//		send syn packet
		stp = new STP(0, true, false, false, 1, 0);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, addr, port);
		socket.send(outgoingPacket);
		System.out.println("outgoingPayload length: "+outgoingPayload.length);
		getFlag(stp);
		System.out.println("syn packet sent\n");
		
		
//		receive synack
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		System.out.println("incoming packet size: "+incomingPayload.length);
		getFlag(((STP) recvObj));
		System.out.println("synack packet receive\n");
		
		
//		send ack packet
		stp = new STP(0, false, true, false, 1, 0);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, addr, port);
		socket.send(outgoingPacket);
		System.out.println("outgoingPayload length: "+outgoingPayload.length);
		getFlag(stp);
		System.out.println("ack packet sent\n");
		
		return;
	}

	private static void finnConn(InetAddress addr, int port) throws IOException, ClassNotFoundException {
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[MAXSIZE];
		byte[] outgoingPayload = null;
		
//		send fin packet
		stp = new STP(0, false, false, true, 0, 0);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, addr, port);
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
		
//		receive fin packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		System.out.println("incoming packet size: "+incomingPayload.length);
		getFlag(((STP) recvObj));
		System.out.println("fin packet receive\n");
		
//		send ack packet
		stp = new STP(0, false, true, false, 0, 0);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, addr, port);
		socket.send(outgoingPacket);
		System.out.println("outgoingPayload length: "+outgoingPayload.length);
		getFlag(stp);
		System.out.println("ack packet sent\n");
		
		
		
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















