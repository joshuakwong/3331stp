import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Random;

public class Sender {
	
	public static DatagramPacket reorderedPacket = null;
	public static int reorderExpPos = -1;
	
	private static InetAddress recvHost = null;
	private static int recvPort = 0;
	private static String file = null;
	private static int mws = 0;
	private static int mss = 0;
	private static int gamma = 0;
	private static double pDrop = 0;
	private static double pDupl = 0;
	private static double pCorr= 0;
	private static double pOrder = 0;
	private static int maxOrder = 0;
	private static double pDelay = 0;
	private static int maxDelay = 0;
	private static int seed = 0;
	
	private static final int MAXSIZE = 1024;
	public static DatagramSocket socket = null;
	public static FilePacket[] segments = null;
	public static FilePacket firstSegment = null;
	public static int currSeq = 0;
	public static int currAck = 0;
	public static PLD pldModule = null;
	public static long startTime = 0;
	

	public static void main (String[] args) throws Exception{
		if (args.length != 14) {
			System.out.println("Error: Usage:");
			System.exit(0);
		}
		
		recvHost = InetAddress.getByName(args[0]);
		recvPort = Integer.parseInt(args[1]);
		file = args[2];
		mws = Integer.parseInt(args[3]);
		mss = Integer.parseInt(args[4]);
		gamma = Integer.parseInt(args[5]);
		pDrop = Double.parseDouble(args[6]);
		pDupl = Double.parseDouble(args[7]);
		pCorr = Double.parseDouble(args[8]);
		pOrder = Double.parseDouble(args[9]);
		maxOrder = Integer.parseInt(args[10]);
		pDelay = Double.parseDouble(args[11]);
		maxDelay = Integer.parseInt(args[12]);
		seed = Integer.parseInt(args[13]);
		
		boolean validated = validate(recvPort, mws, mss, gamma, pDrop, pDupl, pCorr, pOrder, maxOrder, pDelay, maxDelay, seed);
		if (validated == false) System.exit(1);
		
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
		
		startTime = System.currentTimeMillis();
		
		buffObj = initConn();
		byte[] buffer = pdfToArray(path);
		segments = to2D(buffer);
		currSeq = 1;
		currAck = 1;
		sendPacket(segments);
		finnConn();
		
		return;
	}

	
	
	private static void sendPacket (FilePacket[] segments) throws IOException, ClassNotFoundException {
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		
		byte[] incomingPayload = new byte[5000];
		int outSeqNum = currSeq;
		int outAckNum = currAck;
//		int recvSeqNum = -1;
//		int recvAckNum = -1;
		
		Listener listener = new Listener(socket);
		Thread listenerThread = new Thread(listener);
		listenerThread.start();
		
		
		
		pldModule = new PLD(pDrop, pDupl, pCorr, pOrder, pDelay, seed);
		
		Thread fastRetrans = new Thread(new Runnable(){
			@Override
			public void run() {
				while (checkAllAck() == true) {
					if (Sender.firstSegment.getAckCount() >= 2) {
						try {
							Sender.sendAction(0, pldModule, true, false);
						} catch (IOException e) {}
						Sender.firstSegment.setAckCount(-1);
					}
					int i=0;
					for (i=0; i<Sender.segments.length; i++) {
						if (Sender.segments[i].getAckCount() >= 2) {
							try {
								Sender.sendAction(i+1, pldModule, true, false);
							} catch (IOException e) {}
							Sender.segments[i].setAckCount(-1);
						}
					}
				}
			}
			
			public boolean checkAllAck() {
				for (int i=0; i<Sender.segments.length; i++) 
					if (Sender.segments[i].isAckedFlag() == false) return false;
				
				return true;
			}
		});
		
		fastRetrans.start();
		
		
		
		for (int i=0; i<segments.length; i++) {
			
//			checking for mss
//			if reached, trap in loop and sleep
			while (checkWindow() == true) {
	
				System.out.print("----------mss reached----------\r");
				int last = 0;
				if ((last = checkTimeout()) != -1) {
					System.out.println("\ntimeout now send: "+ last);
					for (int y=0; y < (mws/mss); y++) {
						if ((y+last) == (reorderExpPos-maxOrder)) {
							reorderExpPos = -1;
							reorderedPacket = null;
							sendAction((y+last), pldModule, true, true);
						}
						else {
							sendAction((y+last), pldModule, true, false);
						}
					}
				}
				try {
					Thread.sleep(1);
				}catch (Exception e) {}
			}
			System.out.println();
			
			sendAction(i, pldModule, false, false);
			
			
/**
 * 			check for staged packets.
 * 			if there are any, check and see if it is time to send
 * 			reset 2 variables afterwards
 * 			send the staged packet first
 * 			reset reordering variables
 */
			if (reorderExpPos == i) {
				int originalPos = reorderExpPos-maxOrder;
				if (segments[originalPos].isAckedFlag() == false) {
					socket.send(reorderedPacket);
				}
				reorderExpPos = -1;
				reorderedPacket = null;
			}
		}
		

		int last = 0;
		while (checkWindow() == true) {
			System.out.print("----------mss reached----------\r");
			if ((last = checkTimeout()) != -1) {
				for (int y=0; y < (mws/mss); y++) {
					if ((y+last) == (reorderExpPos-maxOrder)) {
						reorderExpPos = -1;
						reorderedPacket = null;
						sendAction((y+last), pldModule, true, true);
					}
					else 
						sendAction((y+last), pldModule, true, false);
				}
			}
			try {
				Thread.sleep(1);
			}catch (Exception e) {}
		}
		
		
		while ((firstUnacked()) != -1) {
			System.out.print("----------exists unacked packets----------\r");
//			System.out.println("last: "+last);
			while ((last = checkTimeout()) != -1) {
				if (last != -1) {
					if ((last) == (reorderExpPos-maxOrder)) {
						reorderExpPos = -1;
						reorderedPacket = null;
						sendAction(last, pldModule, true, true);
					}
					else
						sendAction(last, pldModule, true, false);
				}
				try {
					Thread.sleep(1);
				}catch (Exception e) {}
			}
		}
		
//		keep staying in the loop until listenerThread is killed
//		listenerThread kill only if all ack-backs have been received
		while (listenerThread.isAlive()) {
			try {
				Thread.sleep(1);
			} catch (Exception e) {}
		}
		
		System.out.println("currAck = "+currAck+"  currSeq = "+currSeq);
		System.out.println("-----------------ending sendFile-----------------");
	}

	private static void sendAction(int segCount, PLD pldModule, boolean rxt, boolean rord) throws IOException{
		String inst = null;
		STP stp = null;
		int outSeqNum;
		int outAckNum = 1;
		int dataLength = 0;
		DatagramPacket outgoingPacket = null;
		byte[] outgoingPayload = null;
		
		outSeqNum = calcSeqNum(segCount);
		outAckNum = 1;
		
		segments[segCount].setExpAck(outSeqNum+segments[segCount].getData().length);
		segments[segCount].setSentFlag(true);
		segments[segCount].setStartTime(System.currentTimeMillis());
		
		stp = new STP(false, false, false, outSeqNum, outAckNum, segments[segCount].getData(), mss);
		dataLength = stp.getData().length;
		inst = pldModule.action(stp);
		outgoingPayload = stp.serialize();	
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		
		System.out.println("Sender: sending segment   "+segCount+"  outSeqNum: "+outSeqNum+"\t expcSeqNum: "
		+(outSeqNum+segments[segCount].getData().length)+"\t inst: "+ inst);
		determinePLD(inst, outgoingPacket, segCount, outSeqNum, outAckNum, dataLength, rxt, rord);
		
	}
	

	public static void logger(String event, String type, int seqNum, int length, int ackNum) throws IOException {
		BufferedWriter out = null;
		String toWrite = null;
		double time = (double)(System.currentTimeMillis()-startTime)/1000;
		if (event == "rcv" || event == "snd") 
			toWrite = event+"     \t\t\t"+time+"\t\t"+type+"\t\t"+seqNum+"\t\t"+length+"\t\t"+ackNum+"\n";
		else
			toWrite = event+"     \t\t\t"+time+"\t\t"+type+"\t\t"+seqNum+"\t\t"+length+"\t\t"+ackNum+"\n";
//		System.out.println(toWrite);
		
		try {
			FileWriter fStream = new FileWriter("Sender_log.txt", true);
			out = new BufferedWriter(fStream);
			out.write(toWrite);
		}catch (IOException e) {}
		finally {
			if (out != null) out.close();
		}
	}
	
	
	private static void determinePLD(String inst, DatagramPacket outgoingPacket, int position, int seqNum, int ackNum, int length, boolean rxt, boolean rord) throws IOException {
		
		
		if (inst == "send") {
			if (rxt == false) logger("snd", "D", seqNum, length, ackNum);
			else logger("snd/RXT", "D", seqNum, length, ackNum);
			socket.send(outgoingPacket);
			return;
		}
		
		if (inst == "corr") {
//			if (rxt == false) 
			logger("snd/corr", "D", seqNum, length, ackNum);
//			else logger("snd/RXT/corr", "D", seqNum, length, ackNum);
			socket.send(outgoingPacket);
			return;
		}
		
		if (inst == "delay") {
			Thread threadhandle = new Thread(new Runnable() { 
				@Override 
				public void run() { 
					try {
						int sleep = new Random().nextInt(maxDelay)+1;
						Thread.sleep(sleep);
//						if (rxt == false) 
						logger("snd/delay", "D", seqNum, length, ackNum);
//						else logger("snd/RXT/delay", "D", seqNum, length, ackNum);
						Sender.socket.send(outgoingPacket);
					} catch (InterruptedException | IOException e) {
						e.printStackTrace();
					}
				} 
			}); 
			threadhandle.start();
			return;
		}
		
		if (inst == "drop") {
//			if (rxt == false) 
			logger("drop", "D", seqNum, length, ackNum);
//			else logger("RXT/drop", "D", seqNum, length, ackNum);
			return;
		}
		
		if (inst == "dupl") {
//			if (rxt == false) 
			logger("snd", "D", seqNum, length, ackNum);
//			else logger("snd/RXT", "D", seqNum, length, ackNum);
			socket.send(outgoingPacket);
//			if (rxt == false) 
			logger("snd/dupl", "D", seqNum, length, ackNum);
//			else logger("snd/RXT/dupl", "D", seqNum, length, ackNum);
			socket.send(outgoingPacket);
			return;
		}

		if (inst == "reorder") {
//			store the packet in a variable, along with the position.
//			if reach that position, use sendPacket to send it
			
//			if there are staged packet already, send the packet right away
			if (reorderedPacket != null) {
//				System.out.println("exists reordering packet previously: "+(reorderExpPos-maxOrder));
				if (rxt == false) logger("snd", "D", seqNum, length, ackNum);
				else logger("snd/RXT", "D", seqNum, length, ackNum);
				socket.send(outgoingPacket);
				return;
			}
			
			else {
//				if (position+(mws/mss) < segments.length) reorderExpPos = position+(mws/mss);
//				else reorderExpPos = segments.length;
				reorderExpPos = position+(mws/mss);
				reorderedPacket = outgoingPacket;
			}	
		}
		
		
		return;
	}
	
	/*
	 * check if the leftmost unacked packet has timeout yet
	 */
	private static int checkTimeout() {
		long currTime = System.currentTimeMillis();
		int firstUnackedPackage = firstUnacked();
		long timeoutInterval = 500 + (gamma*250);
		
		if (firstUnackedPackage == -1) return -1;
		
		if (segments[firstUnackedPackage].getStartTime()+timeoutInterval < currTime) 
			return firstUnackedPackage;
		
		return -1;
	}
	
	private static int firstUnacked() {
		for (int i=0; i<segments.length; i++) 
			if (segments[i].isAckedFlag() == false) return i;
		
		return -1;
	}
	
	private static boolean checkWindow() {
		int countSentFlags = 0;
		int countAckedFlags = 0;
		
		for (int i=0; i<segments.length; i++) 
			if (segments[i].isSentFlag() == true) countSentFlags++;
		
		for (int i=0; i<segments.length; i++) 
			if (segments[i].isAckedFlag() == true) countAckedFlags++;
		
		if ((countSentFlags - countAckedFlags) >= (mws/mss)) return true;
		return false;

	}
	
	private static FilePacket[] to2D(byte[] buffer) {
		firstSegment = new FilePacket(null, 0, 0);
		int len = 0;
		if (buffer.length%mss == 0) len = buffer.length/mss;
		else len = buffer.length/mss +1;
		int end = 0;
		
		FilePacket[] segments =  new FilePacket[len];
		
		int count = 0;
		for (int i=0; i<buffer.length; i+=mss) {
			byte[] dataSeg= new byte[mss];
			
			if (i+mss-1 >= buffer.length) end = buffer.length;
			else end = i+mss;
			dataSeg = Arrays.copyOfRange(buffer, i, end);
			FilePacket tmp = new FilePacket(dataSeg, i, end);
			segments[count] = tmp;
			count++;
		}
		return segments;	
	}
	
		
	private static STP initConn () throws IOException, ClassNotFoundException  {
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[MAXSIZE];
		byte[] outgoingPayload = null;
		int outSeq = 0;
		int outAck = 0;
		int dataLength = 0;
		int recvSeq = 0;
		int recvAck = 0;
		
//		send syn packet
		stp = new STP(true, false, false, outSeq, outAck, mss);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		if (stp.getData() == null) dataLength = 0;
		logger("snd", "S", outSeq, dataLength, outAck);
		socket.send(outgoingPacket);
		
		
//		receive synack
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		recvSeq = ((STP)recvObj).getSeqNum();
		recvAck = ((STP)recvObj).getAckNum();
		if (((STP)recvObj).getData() == null) dataLength = 0;
		logger("rcv", "SA", recvSeq, dataLength, recvAck);
		
		
//		send ack packet
		outSeq = recvAck;
		outAck = recvSeq+1;
		stp = new STP(false, true, false, outSeq, outAck, mss);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		if (stp.getData() == null) dataLength = 0;
		logger("snd", "S", outSeq, dataLength, outAck);
		socket.send(outgoingPacket);
		
		
		try {
		Thread.sleep(1);
		}catch(Exception e){}
		
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
		int dataLength= 0;
		int end = 0;
		
//		System.out.print("seq = "+outSeqNum+"\tack = "+outAckNum);
//		send fin packet
		stp = new STP(false, false, true, outSeqNum, outAckNum, mss);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		if (stp.getData() == null) dataLength = 0;
		else dataLength = stp.getData().length;
		logger("snd", "F", outSeqNum, dataLength, outAckNum);
		socket.send(outgoingPacket);
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
		
//		receive fin packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		recvSeqNum = ((STP)recvObj).getSeqNum();
		recvAckNum = ((STP)recvObj).getAckNum();
		if (((STP)recvObj).getData() == null) dataLength = 0;
		else dataLength = ((STP)recvObj).getData().length;
		logger("rcv", "F", recvSeqNum, dataLength, recvAckNum);
		getFlag(((STP) recvObj));
		
//		send ack packet
		outSeqNum = recvAckNum;
		outAckNum = recvSeqNum+1;
		stp = new STP(false, true, false, outSeqNum, outAckNum, mss);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		if (stp.getData() == null) dataLength = 0;
		else dataLength = stp.getData().length;
		logger("snd", "A", outSeqNum, dataLength, outAckNum);
		socket.send(outgoingPacket);
		getFlag(stp);
		
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
					e.printStackTrace();
				}
			}
		}
		return bOut.toByteArray();
		
	}

	private static int calcSeqNum(int segmentCount) {
		return mss*segmentCount+1;
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
//		System.out.print(">>>>>>>>>>>>>>>>>>>>");
		System.out.print("seq: "+ obj.getSeqNum()+"\t");
		System.out.print("ack: "+ obj.getAckNum()+"\t");
		if (obj.isSynFlag().booleanValue()) System.out.print("Syn\t");
		if (obj.isAckFlag().booleanValue()) System.out.print("Ack\t");
		if (obj.isFinFlag().booleanValue()) System.out.print("Fin\t");
		
		System.out.println();
		return;
	}
	
}








