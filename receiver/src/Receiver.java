import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

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
		byte[] recvBuffArray = null;
		int recvBuffLength = 0;
		int recvSeqNum = -1;
		int recvAckNum = -1;
		long checksum;
		long generatedChecksum;
		
		List<RecvSegment> masterBufferList = new ArrayList<RecvSegment>();
		List<RecvSegment> secondaryBufferList = new ArrayList<RecvSegment>();
		int mss = 0;
		int expcSeq = 1;
		
		int outSeqNum = 1;
		int outAckNum = 1;
		
		while (true) {
			incomingPacket = new DatagramPacket(incomingPayload, 5000);
			socket.receive(incomingPacket);	
			recvObj = STP.deserialize(incomingPayload);

//			break loop if the receoved object contains fin flag, i.e. connection tear down
			if (((STP)recvObj).isFinFlag()) break;
			
			recvSeqNum = ((STP)recvObj).getSeqNum();
			recvAckNum = ((STP)recvObj).getAckNum();
			recvBuffArray = ((STP)recvObj).getData();
			recvBuffLength = recvBuffArray.length;
			mss = ((STP)recvObj).getMss();
			checksum = ((STP)recvObj).getChecksum();
			System.out.print("recvSeqNum: "+recvSeqNum+"\t");
						
			generatedChecksum = genChecksum(recvBuffArray);
			
			RecvSegment seg = new RecvSegment(recvSeqNum, recvAckNum, recvBuffLength, recvBuffArray);
			
//			checksum mismatch, duplicated packet
			if (checksum != generatedChecksum) 
				System.out.println("packed corrupted");
			
			
//			packets arrive in order
			else if (recvSeqNum == expcSeq) {
				System.out.print("In order pkt\t | ");
//				send ack packet
				outSeqNum = recvAckNum;
				outAckNum = recvSeqNum + recvBuffLength;
				
				
				if (existsInList(masterBufferList, recvSeqNum) == false && existsInList(secondaryBufferList, recvSeqNum) == false) {
					System.out.println("pushing to master");
					pushToList(masterBufferList, seg);
				}
				
				while (mergable(masterBufferList, secondaryBufferList, mss)) 
					mergeList(masterBufferList, secondaryBufferList);
				
				expcSeq = (masterBufferList.get(masterBufferList.size()-1).getRecvSeq())+mss;
				
				if (masterBufferList.size() > 0)
					outAckNum = masterBufferList.get(masterBufferList.size()-1).getRecvSeq()
					+masterBufferList.get(masterBufferList.size()-1).getLength();
				
				stp = new STP(false, true, false, outSeqNum, outAckNum);
				outgoingPayload = stp.serialize();
				outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
				socket.send(outgoingPacket);
				System.out.println("ack back: "+outAckNum);
				
			}
			
			
//			logic needs to be checked again
			else {
				System.out.print("not in order pkt\t | ");
				
				
				if (existsInList(masterBufferList, recvSeqNum) == false && existsInList(secondaryBufferList, recvSeqNum) == false) 
					pushToList(secondaryBufferList, seg);
				
				while (mergable(masterBufferList, secondaryBufferList, mss)) {
					mergeList(masterBufferList, secondaryBufferList);
				}
				if (masterBufferList.size() > 0)
					outAckNum = masterBufferList.get(masterBufferList.size()-1).getRecvSeq()
					+masterBufferList.get(masterBufferList.size()-1).getLength();
				
				
				
				stp = new STP(false, true, false, outSeqNum, outAckNum);
				outgoingPayload = stp.serialize();
				outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
				socket.send(outgoingPacket);
				System.out.println("ack back: "+outAckNum);
				
				
			}
			System.out.println();
			System.out.println("master list");
			for (RecvSegment item : masterBufferList) 
				System.out.print(item.getRecvSeq()+"_");
			System.out.println();
			
			System.out.println("secondary list");
			for (RecvSegment item : secondaryBufferList) 
				System.out.print(item.getRecvSeq()+"_");
			System.out.println();
			
			System.out.println("-------------------------------------------------");
		}

//		generate pdf with buffer
		
//		byte[] pdfData = toArray(fileDataArrayList);
//		OutputStream out = new FileOutputStream(new File(file));
//		out.write(pdfData);
//		System.out.println("pdf created");
//		out.close();
		
		return recvObj;
		
	}

	private static boolean existsInList(List<RecvSegment> list, int recvSeq) {
		for (RecvSegment seg : list) 
			if (seg.getRecvSeq() == recvSeq) return true;
		
		return false;
	}

	private static void pushToList(List<RecvSegment> list, RecvSegment seg) {
		list.add(seg);
		Collections.sort(list);
		
		return;
	}
	
	private static boolean mergable(List<RecvSegment> master, List<RecvSegment> secondary, int mss) {
		if (secondary.size() == 0) return false;
		if (master.size() == 0) return false;
		
		int lastSeqFromMaster = master.get(master.size()-1).getRecvSeq();
		int firstSeqFromSec = secondary.get(0).getRecvSeq();
		
		if (lastSeqFromMaster == firstSeqFromSec) {
			System.out.println("there is a problem, pushing has a problem");
			return false;
		}
//		else if (listInOrder(secondary, mss) == false) return false;
		
		else if(firstSeqFromSec-lastSeqFromMaster == mss) return true;
		
		else return false;
	}
	
	private static boolean listInOrder (List<RecvSegment> secondary, int mss) {
		if (secondary.size() ==1) return true;
		for (int i=0; i<secondary.size()-1; i++) {
			int first = secondary.get(i).getRecvSeq();
			int second = secondary.get(i+1).getRecvSeq();
			if ((second-first) != mss) return false;
		}
		
		return true;
	}
	
	private static void mergeList(List<RecvSegment> master, List<RecvSegment> secondary) {
		System.out.println("list merge");
		master.add(secondary.remove(0));
//		for (RecvSegment seg : secondary) 
//			master.add(seg);
//		secondary.clear();
	}

	private static byte[] toArray(List<Byte> list){
		Object[] tmp = list.toArray();
		byte[] data = new byte[tmp.length];
		
		int i=0;
		for (Object b : tmp) data[i++] = ((Byte) b).byteValue();
		return data;
	}

	private static long genChecksum(byte[] data) {
		long res = 0;
		CRC32 checksum = new CRC32();
		checksum.update(data);
		res = checksum.getValue();
		
		return res;
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
//		System.out.println("incoming packet size: "+incomingPayload.length);
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
	
	
	/*
	 * depreciated function, if not used, delete at the end
	 */
//	private static void pushToList(List<Byte> list, byte[] data) {
//		if (data == null) System.out.println("null data array");
//		Byte[] tmp = new Byte[data.length];
//		
//		int i=0;
//		for(byte b : data) tmp[i++] = b;
//		
//		for(Byte b: tmp) list.add(b);
//		
//	}
	
	
}










