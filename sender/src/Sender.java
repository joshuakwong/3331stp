import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Random;

public class Sender {
	
	private static final int MAXSIZE = 1024;
	public static DatagramSocket socket = null;
	public static FilePacket[] segments = null;
	public static int currSeq = 0;
	public static int currAck = 0;
	
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
		STP stp = null;
		Object recvObj = null;
		DatagramPacket incomingPacket = null;
		DatagramPacket outgoingPacket = null;
		byte[] incomingPayload = new byte[5000];
		byte[] outgoingPayload = null;
		int outSeqNum = currSeq;
		int outAckNum = currAck;
//		int recvSeqNum = -1;
//		int recvAckNum = -1;
		String inst = null;
		
		
		Listener listener = new Listener(socket);
		Thread listenerThread = new Thread(listener);
		listenerThread.start();
		
		
		PLD pldModule = new PLD(pDrop, pDupl, pCorr, pOrder, pDelay, seed);
		
		System.out.println("All ackedFlag state before start: ");
		for (int i=0; i<Sender.segments.length; i++)
			System.out.print(Sender.segments[i].isAckedFlag()+"_");
		System.out.println();
		
		for (int i=0; i<segments.length; i++) {
//			try {
//				Thread.sleep(1000);
//			}catch (Exception e) {}
			
			
			if (reorderExpPos == i) {
//				send the staged packet first
				System.out.println("sending reordered packet>>>>>>>>>>>>>>>>>>>>");
				socket.send(reorderedPacket);
//				reset reordering variables
				reorderExpPos = -1;
				reorderedPacket = null;
			}
			
			outSeqNum = calcSeqNum(i);
			System.out.print("Sender: sending segment   "+i+"  outSeqNum: "+outSeqNum+"\t");
			
			segments[i].setExpAck(outSeqNum+segments[i].getData().length);
			segments[i].setSentFlag(true);
			stp = new STP(false, false, false, outSeqNum, 1, segments[i].getData(), mss);
			inst = pldModule.action(stp);
			outgoingPayload = stp.serialize();	
			outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
			
			sendAction(inst, outgoingPacket, i);

			
//			recvSeqNum = ((STP) recvObj).getSeqNum();
//			recvAckNum = ((STP) recvObj).getAckNum();
//			outSeqNum = recvAckNum;
//			outAckNum = recvSeqNum;
			
		}
		if (reorderExpPos == segments.length) {
			System.out.println("reordering at the end");
			socket.send(reorderedPacket);
		}
		currSeq = outSeqNum;
		currAck = outAckNum;
				
		while (listenerThread.isAlive()) {
			try {
				Thread.sleep(5);
			} catch (Exception e) {
			}
		}
		
		System.out.println("-----------------ending sendFile-----------------");
	}

	private static void sendAction(String inst, DatagramPacket outgoingPacket, int position) throws IOException {
		
		if (inst == "send") {
			socket.send(outgoingPacket);
			return;
		}
		
		if (inst == "delay") {
			try {
				int sleep = new Random().nextInt(maxDelay)+1;
				Thread.sleep(sleep);
				socket.send(outgoingPacket);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return;
		}
		
		if (inst == "drop") {
			return;
		}
		
		if (inst == "dupl") {
			socket.send(outgoingPacket);
			socket.send(outgoingPacket);
			return;
		}

//		dunno what to do...
		if (inst == "reorder") {
//			TODO
//			store the packet in a variable, along with the position.
//			if reach that position, use sendPacket to send it
			
//			if there are staged packet already, send the packet right away
			if (reorderedPacket != null) {
				socket.send(outgoingPacket);
				return;
			}
			
			else {
				reorderedPacket = outgoingPacket;
				if (position+(mws/mss) < segments.length) reorderExpPos = position+(mws/mss);
				else reorderExpPos = segments.length;
				
				System.out.println("hit order: reorderExpectedPosition: "+reorderExpPos);
			}	
		}
		
		
		return;
	}
	
	
	
	
	private static FilePacket[] to2D(byte[] buffer) {
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
		
//		send syn packet
		stp = new STP(true, false, false, 0, 0, mss);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		socket.send(outgoingPacket);
//		getFlag(stp);
		
//		receive synack
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
//		getFlag(((STP) recvObj));
		
//		send ack packet
		stp = new STP(false, true, false, 1, 1, mss);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		socket.send(outgoingPacket);
//		getFlag(stp);
		
		
		try {
		Thread.sleep(100);
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
		int end = 0;
		
//		System.out.print("seq = "+outSeqNum+"\tack = "+outAckNum);
//		send fin packet
		stp = new STP(false, false, true, outSeqNum, outAckNum, mss);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
		socket.send(outgoingPacket);
		getFlag(stp);
		
//		receive ack packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		getFlag(((STP) recvObj));
		
//		receive fin packet
		incomingPacket = new DatagramPacket(incomingPayload, MAXSIZE);
		socket.receive(incomingPacket);
		recvObj = STP.deserialize(incomingPayload);
		getFlag(((STP) recvObj));
		recvSeqNum = ((STP)recvObj).getSeqNum();
		recvAckNum = ((STP)recvObj).getAckNum();
		
//		send ack packet
		outSeqNum = recvAckNum;
		outAckNum = recvSeqNum+1;
		stp = new STP(false, true, false, outSeqNum, outAckNum, mss);
		outgoingPayload = stp.serialize();
		outgoingPacket = new DatagramPacket(outgoingPayload, outgoingPayload.length, recvHost, recvPort);
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








