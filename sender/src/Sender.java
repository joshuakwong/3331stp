import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

//TODO
//seq and ack num
//trigger finnConn
//file serialization
//checksum


public class Sender {
	
	private static final int MAXSIZE = 1024;
	private static DatagramSocket socket = null;
	private static int currSeq = 0;
	private static int currAck = 0;
	
	private static InetAddress recvHost = null;
	private static int recvPort = 0;
	private static String file = null;
	private static int mws = 0;
	private static int mss = 0;
//	private static int gamma = 0;
//	private static double pDrop = 0;
//	private static double pDupl = 0;
//	private static double pCorr= 0;
//	private static double pOrder = 0;
//	private static int maxOrder = 0;
//	private static double pDelay = 0;
//	private static int maxDelay = 0;
//	private static int seed = 0;
	
	public static void main (String[] args) throws Exception{
//		if (args.length != 14) {
//			System.out.println("Error: Usage:");
//			System.exit(0);
//		}
		
		recvHost = InetAddress.getByName(args[0]);
		recvPort = Integer.parseInt(args[1]);
		file = args[2];
		mws = Integer.parseInt(args[3]);
		mss = Integer.parseInt(args[4]);
//		gamma = Integer.parseInt(args[5]);
//		pDrop = Double.parseDouble(args[6]);
//		pDupl = Double.parseDouble(args[7]);
//		pCorr = Double.parseDouble(args[8]);
//		pOrder = Double.parseDouble(args[9]);
//		maxOrder = Integer.parseInt(args[10]);
//		pDelay = Double.parseDouble(args[11]);
//		maxDelay = Integer.parseInt(args[12]);
//		seed = Integer.parseInt(args[13]);
		
//		boolean validated = validate(recvPort, mws, mss, gamma, pDrop, pDupl, pCorr, pOrder, maxOrder, pDelay, maxDelay, seed);
//		if (validated == false) System.exit(1);
		
		STP buffObj = null;
		
		File pdfFile = new File(file);
		if (!pdfFile.exists() || pdfFile.isDirectory()) {
			System.out.println("Error: Fail to open file, file does not exist");
			System.exit(1);
		}
		String path = "./"+file;
		
		
        // Create a datagram socket for receiving and sending UDP packets
        // through the port specified on the command line.
		socket = new DatagramSocket();
		
		buffObj = initConn();
		byte[] buffer = pdfToArray(path);
		currSeq = 1;
		currAck = 1;
		sendPacket(buffer);
		finnConn();
		return;
	}
	

	
	private static void sendPacket (byte[] pdfArray) throws IOException, ClassNotFoundException {
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[5000];
		byte[] outgoingPayload = null;
		int outSeqNum = currSeq;
		int outAckNum = currAck;
		int recvSeqNum = -1;
		int recvAckNum = -1;
		int end = 0;
		
		for (int i=0; i<pdfArray.length; i+=mss) {
			byte[] dataSeg= new byte[mss];
			
			if (i+mss-1 >= pdfArray.length) end = pdfArray.length;
			else end = i+mss;
			
			dataSeg = Arrays.copyOfRange(pdfArray, i, end);
			
			System.out.print("segment: "+i+"\t"+end+"\t| ");
			System.out.print("Seq="+outSeqNum+"\tAck="+outAckNum+"\t| ");
			System.out.println(dataSeg.length);
			
			stp = new STP(false, false, false, outSeqNum, outAckNum, dataSeg);
			outgoingPayload = stp.serialize();
			outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
			socket.send(outgoingPacket);
			
			incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
			socket.receive(incomingPacket);
			recvObj = STP.deserialize(incomingPayload);
			
			recvSeqNum = ((STP) recvObj).getSeqNum();
			recvAckNum = ((STP) recvObj).getAckNum();
			outSeqNum = recvAckNum;
			outAckNum = recvSeqNum;
			
		}
		currSeq = outSeqNum;
		currAck = outAckNum;
		
//		stp = new STP(false, false, true, outSeqNum, outAckNum);
//		outgoingPayload = stp.serialize();
//		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
//		socket.send(outgoingPacket);
		
	}
	
	private static STP initConn () throws IOException, ClassNotFoundException  {
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[MAXSIZE];
		byte[] outgoingPayload = null;
		
//		send syn packet
		stp = new STP(true, false, false, 0, 0);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		socket.send(outgoingPacket);
//		System.out.println("outgoingPayload length: "+outgoingPayload.length);
		getFlag(stp);
//		System.out.println("syn packet sent\n");
		
		
//		receive synack
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
//		System.out.println("incoming packet size: "+incomingPayload.length);
		getFlag(((STP) recvObj));
//		System.out.println("synack packet receive\n");
		
		
//		send ack packet
		stp = new STP(false, true, false, 1, 1);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		socket.send(outgoingPacket);
//		System.out.println("outgoingPayload length: "+outgoingPayload.length);
		getFlag(stp);
//		System.out.println("ack packet sent\n");
		
		return stp;
	}

	private static void finnConn() throws IOException, ClassNotFoundException {
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[MAXSIZE];
		byte[] outgoingPayload = null;
		int outSeqNum = currSeq;
		int outAckNum = currAck;
		int recvSeqNum = -1;
		int recvAckNum = -1;
		int end = 0;
		
		System.out.print("seq = "+outSeqNum+"\tack = "+outAckNum);
//		send fin packet
		stp = new STP(false, false, true, outSeqNum, outAckNum);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		socket.send(outgoingPacket);
//		System.out.print("outgoingPayload length: "+outgoingPayload.length+"\t|");
		getFlag(stp);
//		System.out.println("fin packet sent\n");
		
//		receive ack packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		System.out.print("incoming packet size: "+incomingPayload.length+"\t|");
		getFlag(((STP) recvObj));
		System.out.println("ack packet receive\n");
		
//		receive fin packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
//		System.out.print("incoming packet size: "+incomingPayload.length+"\t|");
		getFlag(((STP) recvObj));
		recvSeqNum = ((STP)recvObj).getSeqNum();
		recvAckNum = ((STP)recvObj).getAckNum();
//		System.out.println("fin packet receive\n");
		
//		send ack packet
		outSeqNum = recvAckNum;
		outAckNum = recvSeqNum+1;
		stp = new STP(false, true, false, outSeqNum, outAckNum);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		socket.send(outgoingPacket);
//		System.out.print("outgoingPayload length: "+outgoingPayload.length+"\t|");
		getFlag(stp);
//		System.out.println("ack packet sent\n");
		
		
		
		return;
	}
	
	private static byte[] pdfToArray(String path){
		InputStream inputStream = null;
		ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		
		try {
			inputStream = new FileInputStream(path);
			
			byte[] buf = new byte[1024];
			bOut = new ByteArrayOutputStream();
			
			int bytesRead;
            while ((bytesRead = inputStream.read(buf)) != -1) {
                bOut.write(buf, 0, bytesRead);
            }
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return bOut.toByteArray();
		
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

//	debugging function
	private static void getFlag(STP obj) {
		System.out.print("seq: "+ obj.getSeqNum()+"\t");
		System.out.print("ack: "+ obj.getAckNum()+"\t");
		
		if (obj.isSynFlag().booleanValue()) System.out.print("Syn\t");
		if (obj.isAckFlag().booleanValue()) System.out.print("Ack\t");
		if (obj.isFinFlag().booleanValue()) System.out.print("Fin\t");
		
		System.out.println();
		return;
	}
	
}















