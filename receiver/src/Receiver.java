import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

public class Receiver {
	
	private static final int MAXSIZE = 1024;
	private static DatagramSocket socket = null;
	private static int currSeq = 0;
	private static int currAck = 0;
	
	private static InetAddress senderAddr = null;
	private static int senderPort = 0;
	private static String file = null;
	public static long startTime = 0;
	
	public static int dataTotalSize = 0;
	public static int countTotalSeg = 0;
	public static int countDataSeg = 0;
	public static int countSegBitError = 0;
	public static int countDupSeg = 0;
	public static int countDupAck = 0;
	
	
	public static void main (String[] args) throws Exception{
		if (args.length != 2) {
			System.out.println("Error: Usage:");
			System.exit(0);
		}
		
		int recvPort = Integer.parseInt(args[0]);
		file = args[1];
		socket = new DatagramSocket(recvPort);
		startTime = System.currentTimeMillis();

		initConn();
		Object tmp = recvPacket();
		finnConn(tmp);
		summary();
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
			countDataSeg++;
			countTotalSeg++;
			recvObj = STP.deserialize(incomingPayload);

//			break loop if the receoved object contains fin flag, i.e. connection tear down
			if (((STP)recvObj).isFinFlag()) break;
			
			recvSeqNum = ((STP)recvObj).getSeqNum();
			recvAckNum = ((STP)recvObj).getAckNum();
			recvBuffArray = ((STP)recvObj).getData();
			recvBuffLength = recvBuffArray.length;
			mss = ((STP)recvObj).getMss();
			checksum = ((STP)recvObj).getChecksum();
			
//			System.out.print("recvSeqNum: "+recvSeqNum+"\t");

			generatedChecksum = ((STP)recvObj).genChecksum(recvBuffArray);
			
			RecvSegment seg = new RecvSegment(recvSeqNum, recvAckNum, recvBuffLength, recvBuffArray);
			
//			checksum mismatch, duplicated packet
			if (checksum != generatedChecksum) {
//				System.out.println("packed corrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrupted");
				countSegBitError++;
				logger("rcv/corr", "D", recvSeqNum, recvBuffLength, recvAckNum);
				continue;
			}
			
//			packets arrive in order
			else if (recvSeqNum == expcSeq) {
				logger("rcv", "D", recvSeqNum, recvBuffLength, recvAckNum);
//				System.out.print("In order pkt\t | ");
//				send ack packet
				outSeqNum = recvAckNum;
				outAckNum = recvSeqNum + recvBuffLength;
				
				
				if (existsInList(masterBufferList, recvSeqNum) == false && existsInList(secondaryBufferList, recvSeqNum) == false) {
//					System.out.println("pushing to master");
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
				logger("snd", "A", outSeqNum, 0, outAckNum);
//				System.out.println("ack back: "+outAckNum);
				
			}

			else {
//				System.out.print("not in order pkt\t | ");
				logger("rcv", "D", recvSeqNum, recvBuffLength, recvAckNum);

				if (existsInList(masterBufferList, recvSeqNum) == false && existsInList(secondaryBufferList, recvSeqNum) == false) 
					pushToList(secondaryBufferList, seg);
				
				else 
					countDupSeg++;
				
				while (mergable(masterBufferList, secondaryBufferList, mss)) 
					mergeList(masterBufferList, secondaryBufferList);
				
				if (masterBufferList.size() > 0)
					outAckNum = masterBufferList.get(masterBufferList.size()-1).getRecvSeq()
					+masterBufferList.get(masterBufferList.size()-1).getLength();
				
				stp = new STP(false, true, false, outSeqNum, outAckNum);
				outgoingPayload = stp.serialize();
				outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
				socket.send(outgoingPacket);
				countDupAck++;
				logger("snd/DA", "A", outSeqNum, 0, outAckNum);
//				System.out.println("ack back: "+outAckNum);
				
				
			}
//			System.out.println();
//			System.out.println("master list");
//			for (RecvSegment item : masterBufferList) 
//				System.out.print(item.getRecvSeq()+"_");
//			System.out.println();
//			
//			System.out.println("secondary list");
//			for (RecvSegment item : secondaryBufferList) 
//				System.out.print(item.getRecvSeq()+"_");
//			System.out.println();
//			
//			System.out.println("-------------------------------------------------");
		}
		
		System.out.println("=================================breake out of loop==================================");
//		generate pdf with buffer
		
		byte[] pdfData = toArray(masterBufferList);
		OutputStream out = new FileOutputStream(new File(file));
		out.write(pdfData);
		System.out.println("pdf created");
		out.close();
		
		return recvObj;
		
	}
	
	
	public static void logger(String event, String type, int seqNum, int length, int ackNum) throws IOException {
		BufferedWriter out = null;
		String toWrite = null;
		double time = (double)(System.currentTimeMillis()-startTime)/1000;
		if (event == "rcv" || event == "snd") 
			toWrite = event+"     \t\t\t"+time+"\t\t"+type+"\t\t"+seqNum+"\t\t"+length+"\t\t"+ackNum+"\n";
		else
			toWrite = event+"     \t\t\t"+time+"\t\t"+type+"\t\t"+seqNum+"\t\t"+length+"\t\t"+ackNum+"\n";
		
		try {
			FileWriter fStream = new FileWriter("Receiver_log.txt", true);
			out = new BufferedWriter(fStream);
			out.write(toWrite);
		}catch (IOException e) {}
		finally {
			if (out != null) out.close();
		}
	}
	
	private static void summary() throws IOException {
		BufferedWriter out = null;
		try {
			FileWriter fStream = new FileWriter("Receiver_log.txt", true);
			out = new BufferedWriter(fStream);
			out.write("=============================================================\n");
			out.write("Amount of data received (bytes)\t\t\t\t"+dataTotalSize+"\n");
			out.write("Total Segments Received\t\t\t\t\t"+countTotalSeg+"\n");
			out.write("Data segments received \t\t\t\t\t"+countDataSeg+"\n");
			out.write("Data segments with Bit Errors\t\t\t\t"+countSegBitError+"\n");
			out.write("Duplicate data segments received\t\t\t"+countDupSeg+"\n");
			out.write("Duplicate ACKs sent\t\t\t\t\t"+countDupAck+"\n");
			out.write("=============================================================\n");
			
		} catch (IOException e) {
		}finally {
			if (out != null) out.close();
		}
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
		
		else if(firstSeqFromSec-lastSeqFromMaster == mss) return true;
		
		else return false;
	}
	
	
	private static void mergeList(List<RecvSegment> master, List<RecvSegment> secondary) {
		master.add(secondary.remove(0));
		return;
	}

	private static byte[] toArray(List<RecvSegment> list){
//		List buff = new ArrayList();
		int size = 0;
		for (RecvSegment item : list) {
			size += item.getLength();
		}
		dataTotalSize = size;
		
//		System.out.println("size: "+size);
		byte[] giantBuffer = new byte[size];
		int i=0;
		while (i<size) {
			for(RecvSegment item : list) {
				for (int y=0; y<item.getData().length; y++) {
					byte[] smallBuff = item.getData();
					giantBuffer[i] = smallBuff[y];
					i++;
				}				
			}
		}
		
		return giantBuffer;
	}
	
	private static void initConn () throws IOException, ClassNotFoundException {
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[MAXSIZE];
		byte[] outgoingPayload = null;
		int recvSeqNum = 0;
		int recvAckNum = 0;
		int outSeqNum = 0;
		int outAckNum = 0;
		int dataLength = 0;
		
		System.out.println("-----------------connection initiate-----------------");
		
//		receive syn packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		recvSeqNum = ((STP)recvObj).getSeqNum();
		recvAckNum = ((STP)recvObj).getAckNum();
		senderAddr = incomingPacket.getAddress();
		senderPort = incomingPacket.getPort();
		if (((STP)recvObj).getData() == null) dataLength = 0;
		else dataLength = ((STP)recvObj).getData().length;
		logger("rcv", "S", recvSeqNum, dataLength, recvAckNum);
		getFlag(((STP) recvObj));
		countTotalSeg++;
		
//		send synack packet
		outSeqNum = recvAckNum;
		outAckNum = recvSeqNum + 1;
		stp = new STP(true, true, false, outSeqNum, outAckNum);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
		logger("snd", "SA", outSeqNum, 0, outAckNum);
		socket.send(outgoingPacket);
		getFlag(stp);
		
//		receive ack packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		recvSeqNum = ((STP)recvObj).getSeqNum();
		recvAckNum = ((STP)recvObj).getAckNum();
		getFlag(((STP) recvObj));
		if (((STP)recvObj).getData() == null) dataLength = 0;
		else dataLength = ((STP)recvObj).getData().length;
		logger("rcv", "S", recvSeqNum, dataLength, recvAckNum);
		currSeq = ((STP) recvObj).getSeqNum();
		currAck = ((STP) recvObj).getAckNum();
		countTotalSeg++;
		
		
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
		int dataLength = 0;

//		receive fin packet
		recvObj = fromPrev;
		getFlag(((STP) recvObj));
		recvSeqNum = ((STP)recvObj).getSeqNum();
		recvAckNum = ((STP)recvObj).getAckNum();
		if (((STP)recvObj).getData() == null) dataLength = 0;
		else dataLength = ((STP)recvObj).getData().length;
		logger("rcv", "F", recvSeqNum, dataLength, recvAckNum);
		countTotalSeg++;
		
//		send ack packet
		outSeqNum = recvAckNum;
		outAckNum = recvSeqNum+1;
		stp = new STP(false, true, false, outSeqNum, outAckNum);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
		socket.send(outgoingPacket);
		logger("snd", "A", outSeqNum, dataLength, outAckNum);
		getFlag(stp);
		
//		send fin packet
		stp = new STP(false, false, true, outSeqNum, outAckNum);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, senderAddr, senderPort);
		socket.send(outgoingPacket);
		logger("snd", "F", outSeqNum, dataLength, outAckNum);
		getFlag(stp);
		
//		receive ack packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		recvSeqNum = ((STP)recvObj).getSeqNum();
		recvAckNum = ((STP)recvObj).getAckNum();
		if (((STP)recvObj).getData() == null) dataLength = 0;
		else dataLength = ((STP)recvObj).getData().length;
		logger("rcv", "A", recvSeqNum, dataLength, recvAckNum);
		getFlag(((STP) recvObj));
		countTotalSeg++;
		
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
	
	
	
}










