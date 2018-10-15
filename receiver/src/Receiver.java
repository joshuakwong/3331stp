import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Receiver {
	
	private static final int MAXSIZE = 1024;
	private static DatagramSocket socket = null;
	private static int currSeq = 0;
	private static int currAck = 0;
	
	private static InetAddress senderAddr = null;
	private static int senderPort = 0;
	private static String file = null;
	
	public static void main (String[] args) throws Exception{
//		if (args.length != 2) {
//			System.out.println("Error: Usage:");
//			System.exit(0);
//		}
		
		int recvPort = Integer.parseInt(args[0]);
		file = args[1];
		socket = new DatagramSocket(recvPort);
		
		int currSeq = 0;
		int currAck = 0;

		initConn();
		Object tmp = recvPacket();
		finnConn(tmp);
		
		return;
	}
	
	private static Object recvPacket() throws IOException, ClassNotFoundException {
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[5000];
		byte[] outgoingPayload = null;
		int recvSeqNum = -1;
		int recvAckNum = -1;
		List<Byte> fileDataArrayList = new ArrayList<Byte>();
		int mss = 0;
		int expcSeq = 1;
		int lastRecvSeq = 0;
		
		
		while (true) {
			incomingPacket = new DatagramPacket(incomingPayload, 5000);
			socket.receive(incomingPacket);	
			recvObj = null;
			recvObj = STP.deserialize(incomingPayload);
			recvSeqNum = ((STP)recvObj).getSeqNum();
			recvAckNum = ((STP)recvObj).getAckNum();
			int outSeqNum = 0;
			int outAckNum = 0;
			
			mss = ((STP)recvObj).getMss();
			byte[] recvBuffArray = ((STP)recvObj).getData();
			
			if (((STP)recvObj).isFinFlag()) break;
			
			if (expcSeq == recvSeqNum) {
				lastRecvSeq = recvSeqNum;
				pushToList(fileDataArrayList, recvBuffArray);
				
				outSeqNum = recvAckNum;
				outAckNum = recvSeqNum+((STP)recvObj).getData().length;
				
				stp = new STP(false, false, false, outSeqNum, outAckNum);
				outgoingPayload = stp.serialize();
				outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
				socket.send(outgoingPacket);
				expcSeq+=mss;
				System.out.print("Seq="+outSeqNum+"\t Ack="+outAckNum+"\t| ");
				System.out.print("Data recv: "+((STP)recvObj).getData().length+"\t  Expected SeqNum: "+expcSeq+"\n");
				
			}
			else {
				stp = new STP(false, false, false, outSeqNum, outAckNum);
				outgoingPayload = stp.serialize();
				outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
				socket.send(outgoingPacket);
			}
		}		
		
		byte[] pdfData = toArray(fileDataArrayList);
		OutputStream out = new FileOutputStream(new File(file));
		out.write(pdfData);
		System.out.println("pdf created");
		out.close();
		
		return recvObj;
		
	}

	
	private static void initConn () throws IOException, ClassNotFoundException {
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[MAXSIZE];
		byte[] outgoingPayload = null;
		
		System.out.println("-----------------connection initiate-----------------");
		
//		receive syn packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		senderAddr = incomingPacket.getAddress();
		senderPort = incomingPacket.getPort();
		getFlag(((STP) recvObj));
		
//		send synack packet
		stp = new STP(true, true, false, 0, 1);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
		socket.send(outgoingPacket);
		getFlag(stp);
		
//		receive ack packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		getFlag(((STP) recvObj));
		currSeq = ((STP) recvObj).getSeqNum();
		currAck = ((STP) recvObj).getAckNum();
		return;
	}
	
	private static void finnConn (Object fromPrev) throws IOException, ClassNotFoundException {
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
		
//		receive fin packet
		recvObj = fromPrev;
		System.out.println("incoming packet size: "+incomingPayload.length);
		getFlag(((STP) recvObj));
		recvSeqNum = ((STP)recvObj).getSeqNum();
		recvAckNum = ((STP)recvObj).getAckNum();
		
//		send ack packet
//		if (recvObj == null) System.out.println("recvObj null");
		outSeqNum = recvAckNum;
		outAckNum = recvSeqNum+1;
		stp = new STP(false, true, false, outSeqNum, outAckNum);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
		socket.send(outgoingPacket);
		getFlag(stp);
		
//		send fin packet
		stp = new STP(false, false, true, outSeqNum, outAckNum);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
		socket.send(outgoingPacket);
		getFlag(stp);
		
//		receive ack packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		getFlag(((STP) recvObj));
		
		return;
	}
	
	private static void pushToList(List<Byte> list, byte[] data) {
		if (data == null) System.out.println("null data array");
		Byte[] tmp = new Byte[data.length];
		
		int i=0;
		for(byte b : data) tmp[i++] = b;
		
		for(Byte b: tmp) list.add(b);
		
	}
	
	private static byte[] toArray(List<Byte> list){
		Object[] tmp = list.toArray();
		byte[] data = new byte[tmp.length];
		
		int i=0;
		for (Object b : tmp) data[i++] = ((Byte) b).byteValue();
		return data;
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









